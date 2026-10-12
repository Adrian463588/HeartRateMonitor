package com.example.samplewearmobileapp

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.wear.ambient.AmbientModeSupport
import com.example.samplewearmobileapp.constants.Entity
import com.example.samplewearmobileapp.constants.MessagePath
import com.example.samplewearmobileapp.constants.codes.ActivityCode
import com.example.samplewearmobileapp.constants.codes.ExtraCode.TOGGLE_ACTIVITY
import com.example.samplewearmobileapp.databinding.ActivityMainBinding
import com.example.samplewearmobileapp.models.AccelerometerData
import com.example.samplewearmobileapp.models.Message
import com.example.samplewearmobileapp.models.PpgData
import com.example.samplewearmobileapp.models.PpgType
import com.example.samplewearmobileapp.models.WatchControl
import com.example.samplewearmobileapp.trackers.PpgContinuousListener
import com.example.samplewearmobileapp.utils.TimestampHelper
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.MessageApi
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.samsung.android.service.health.tracking.HealthTrackerException
import java.util.ArrayDeque

class MainActivity : FragmentActivity(), GoogleApiClient.ConnectionCallbacks,
    AmbientModeSupport.AmbientCallbackProvider {
    private lateinit var binding: ActivityMainBinding
    private lateinit var client: GoogleApiClient
    private lateinit var connectionManager: ConnectionManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val gson = Gson()
    private var currentState = ActivityCode.DO_NOTHING
    private var sessionId = 0L
    private var batchNumber = 0L
    private var destroying = false
    private val control = WatchControl()
    private val safetyTimeout = Runnable {
        if (!destroying && currentState == ActivityCode.START_ACTIVITY &&
            control.isExpired(SystemClock.elapsedRealtime())) {
            stopSession(ActivityCode.PAUSE_ACTIVITY)
            binding.statusMsg.text = "Controller unavailable; sensors paused"
        }
    }
    private val releaseRetry = Runnable { stopSession(currentState) }
    private val recordings = PpgType.values().associateWith { PpgRecording(it) }
    private val counts = PpgType.values().associateWith { 0 }.toMutableMap()
    private val ppgListener = PpgContinuousListener(::recordPpg, ::showPpgError)
    private var ppgAvailable = false
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var isAccTracking = false
    private var isAccRegistered = false
    private var accCount = 0
    private var accUnixAnchorMs = 0L
    private var accElapsedAnchorNanos = 0L
    private val accRecording = AccelerometerRecording()
    private val uploads = ArrayDeque<Pair<String, ByteArray>>()
    private var uploadInFlight = false

    private val accelerometerListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (!isAccTracking || event.sensor.type != Sensor.TYPE_ACCELEROMETER ||
                event.values.size < 3 || event.values.take(3).any { !it.isFinite() }) return
            val timestamp = TimestampHelper.elapsedNanosToUnixMillis(
                event.timestamp, accElapsedAnchorNanos, accUnixAnchorMs
            )
            if (accRecording.timestamps.lastOrNull()?.let { timestamp <= it } == true) return
            accRecording.add(event.values[0], event.values[1], event.values[2], timestamp)
            accCount++
            if (accRecording.getSize() >= Constants.ACCELEROMETER_BATCH_SIZE) {
                sendAccelerometerData(Constants.ACCELEROMETER_BATCH_SIZE)
                updateAccelerometerStatus()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val messageListener = MessageApi.MessageListener { event ->
        val path = event.path
        val bytes = event.data.copyOf()
        runOnUiThread {
            if (!destroying) {
                try {
                    val message = gson.fromJson(String(bytes, Charsets.UTF_8), Message::class.java)
                    if (message != null) onMessageArrived(path, message)
                } catch (error: RuntimeException) {
                    Log.w(TAG, "Rejected malformed control message", error)
                }
            }
        }
    }

    private val connectionObserver = object : ConnectionObserver {
        override fun onConnectionResult(stringResourceId: Int) {
            runOnUiThread {
                if (destroying) return@runOnUiThread
                if (stringResourceId != R.string.ConnectedToHs) {
                    ppgAvailable = false
                    ppgListener.stopTracker()
                    showPpgError(stringResourceId)
                    return@runOnUiThread
                }
                try {
                    ppgAvailable = connectionManager.initPpg(ppgListener)
                    if (currentState == ActivityCode.START_ACTIVITY) startPpg()
                    if (!ppgAvailable) showPpgError(R.string.NoContinuousPpgSupport)
                } catch (error: RuntimeException) {
                    ppgAvailable = false
                    Log.e(TAG, "PPG capability setup failed", error)
                    showPpgError(R.string.ConnectionError)
                }
            }
        }

        override fun onError(error: HealthTrackerException?) {
            runOnUiThread {
                ppgAvailable = false
                ppgListener.stopTracker()
                showPpgError(R.string.ConnectionError)
                if (error?.hasResolution() == true) error.resolve(this@MainActivity)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        getPreferences(MODE_PRIVATE).let { saved ->
            control.accept(saved.getLong("session", 0L), saved.getLong("revision", 0L),
                saved.getInt("state", ActivityCode.DO_NOTHING), SystemClock.elapsedRealtime())
        }
        AmbientModeSupport.attach(this)
        sensorManager = getSystemService(SensorManager::class.java)
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        connectionManager = ConnectionManager(connectionObserver)
        client = GoogleApiClient.Builder(this)
            .addConnectionCallbacks(this).addApi(Wearable.API).build()
        client.connect()
        updateAccelerometerStatus()
        if (hasBodySensorPermission()) connectSamsung()
        else ActivityCompat.requestPermissions(this,
            if (Build.VERSION.SDK_INT >= 34) arrayOf(Manifest.permission.BODY_SENSORS, Manifest.permission.ACTIVITY_RECOGNITION)
            else arrayOf(Manifest.permission.BODY_SENSORS), REQUEST_PERMISSIONS)
    }

    private fun connectSamsung() {
        try {
            connectionManager.connect(applicationContext)
        } catch (error: RuntimeException) {
            Log.e(TAG, "Samsung service unavailable", error)
            showPpgError(R.string.ConnectionError)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (hasBodySensorPermission()) connectSamsung()
            else showPpgError(R.string.NoPermission)
        }
    }

    override fun onConnected(bundle: Bundle?) {
        Wearable.MessageApi.addListener(client, messageListener)
        sendState()
        drainUploads()
    }

    override fun onConnectionSuspended(code: Int) {
        Log.w(TAG, "Wearable transport suspended; unsent batches retained")
    }

    private fun onMessageArrived(path: String, message: Message) {
        when (path) {
            MessagePath.COMMAND -> {
                if (!control.accept(message.sessionId, message.commandRevision, message.code,
                        SystemClock.elapsedRealtime())) {
                    sendState()
                    return
                }
                persistControl()
                mainHandler.removeCallbacks(safetyTimeout)
                if (message.code == ActivityCode.START_ACTIVITY) {
                    startSession(message.sessionId)
                    mainHandler.postDelayed(safetyTimeout, WatchControl.TIMEOUT_MS)
                } else {
                    // End must also be acknowledged when its Start was lost in transit.
                    if (message.sessionId != sessionId) {
                        stopSession(message.code)
                        if (ppgListener.isRegistered() || isAccRegistered) return
                        resetSession(message.sessionId)
                    }
                    stopSession(message.code)
                }
            }
            MessagePath.REQUEST -> sendState()
            MessagePath.INFO -> Unit
            MessagePath.DATA_PPG_GREEN, MessagePath.DATA_PPG_IR, MessagePath.DATA_PPG_RED -> {
                if (currentState != ActivityCode.START_ACTIVITY || message.sessionId != sessionId) return
                val type = when (path) {
                    MessagePath.DATA_PPG_GREEN -> PpgType.PPG_GREEN
                    MessagePath.DATA_PPG_IR -> PpgType.PPG_IR
                    else -> PpgType.PPG_RED
                }
                if (message.code == ActivityCode.STOP_ACTIVITY ||
                    (message.extraCode == TOGGLE_ACTIVITY && type in ppgListener.channels)) {
                    ppgListener.channels.remove(type)
                    sendPpgData(type, recordings.getValue(type).getSize() ?: 0)
                } else if (message.code == ActivityCode.START_ACTIVITY) {
                    ppgListener.channels.add(type)
                }
                if (ppgListener.channels.isEmpty()) ppgListener.stopTracker() else startPpg()
                updatePpgStatuses()
                sendState()
            }
        }
    }

    private fun startSession(id: Long) {
        if (id <= 0L) {
            Toast.makeText(this, R.string.UpdateBothApps, Toast.LENGTH_LONG).show()
            return
        }
        if (id == sessionId && currentState == ActivityCode.START_ACTIVITY) {
            sendState()
            return
        }
        mainHandler.removeCallbacks(releaseRetry)
        if (id != sessionId) {
            if (!stopTrackers()) {
                stopSession(ActivityCode.PAUSE_ACTIVITY)
                return
            }
            flushRemainingData()
            resetSession(id)
        } else if (ppgListener.isRegistered() || isAccRegistered) {
            if (!stopTrackers()) {
                stopSession(ActivityCode.PAUSE_ACTIVITY)
                return
            }
        }
        try {
            MainService.startService(this, "Recording sensors")
        } catch (error: RuntimeException) {
            Log.e(TAG, "Foreground recording service could not start", error)
            binding.statusMsg.setText(R.string.status_failed)
            currentState = ActivityCode.STOP_ACTIVITY
            sendState()
            return
        }
        currentState = ActivityCode.START_ACTIVITY
        startAccelerometerTracker()
        startPpg()
        binding.statusMsg.setText(R.string.status_running)
        updatePpgStatuses()
        sendState()
    }

    private fun resetSession(id: Long) {
        recordings.values.forEach { it.clear() }
        counts.keys.forEach { counts[it] = 0 }
        accRecording.clear()
        accCount = 0
        sessionId = id
        batchNumber = 0L
        ppgListener.channels.clear()
        ppgListener.channels.addAll(PpgType.values())
    }

    private fun persistControl() {
        getPreferences(MODE_PRIVATE).edit().putLong("session", control.sessionId)
            .putLong("revision", control.revision).putInt("state", control.code).apply()
    }

    private fun stopSession(state: Int) {
        mainHandler.removeCallbacks(safetyTimeout)
        mainHandler.removeCallbacks(releaseRetry)
        currentState = state
        val released = stopTrackers()
        flushRemainingData()
        binding.statusMsg.setText(if (!released) R.string.status_failed
            else if (state == ActivityCode.PAUSE_ACTIVITY) R.string.status_paused else R.string.status_stopped)
        updatePpgStatuses()
        updateAccelerometerStatus()
        sendState()
        // Release the wake lock immediately, including while final uploads are pending.
        if (state == ActivityCode.PAUSE_ACTIVITY || uploads.isNotEmpty() || !released) {
            MainService.startService(this, "Paused")
        } else MainService.stopService(this)
        if (!released) mainHandler.postDelayed(releaseRetry, 1_000L)
    }

    private fun startAccelerometerTracker() {
        if (isAccTracking) return
        accUnixAnchorMs = System.currentTimeMillis()
        accElapsedAnchorNanos = SystemClock.elapsedRealtimeNanos()
        isAccTracking = try {
            accelerometer != null && sensorManager?.registerListener(
                accelerometerListener, accelerometer, 40_000
            ) == true
        } catch (error: RuntimeException) {
            Log.e(TAG, "Accelerometer registration failed", error)
            false
        }
        isAccRegistered = isAccTracking
        updateAccelerometerStatus()
    }

    private fun startPpg() {
        if (ppgAvailable && hasBodySensorPermission() && ppgListener.channels.isNotEmpty()) {
            ppgListener.startTracker()
        }
    }

    private fun stopTrackers(): Boolean {
        val ppgReleased = ppgListener.stopTracker()
        isAccTracking = false
        try {
            sensorManager?.unregisterListener(accelerometerListener)
            isAccRegistered = false
        } catch (error: RuntimeException) {
            Log.e(TAG, "Accelerometer release failed; retained for retry", error)
        }
        return ppgReleased && !isAccRegistered
    }

    private fun recordPpg(type: PpgType, value: Int, timestamp: Long) {
        if (currentState != ActivityCode.START_ACTIVITY) return
        val recording = recordings.getValue(type)
        if (recording.timestamps.lastOrNull()?.let { timestamp <= it } == true) return
        recording.add(value, timestamp)
        counts[type] = counts.getValue(type) + 1
        val fields = when (type) {
            PpgType.PPG_GREEN -> listOf(binding.ppgGreen, binding.ppgGreenTimestamp, binding.ppgGreenNumber, binding.ppgGreenStatus)
            PpgType.PPG_IR -> listOf(binding.ppgIr, binding.ppgIrTimestamp, binding.ppgIrNumber, binding.ppgIrStatus)
            PpgType.PPG_RED -> listOf(binding.ppgRed, binding.ppgRedTimestamp, binding.ppgRedNumber, binding.ppgRedStatus)
        }
        fields[0].text = value.toString()
        fields[1].text = timestamp.toString()
        fields[2].text = counts.getValue(type).toString()
        fields[3].setText(R.string.status_measuring)
        if ((recording.getSize() ?: 0) >= Constants.PPG_GREEN_BATCH_SIZE) {
            sendPpgData(type, Constants.PPG_GREEN_BATCH_SIZE)
        }
    }

    private fun sendPpgData(type: PpgType, size: Int) {
        if (size <= 0) return
        val recording = recordings.getValue(type)
        val batch = PpgData(
            recording.values.take(size).toIntArray(), recording.timestamps.take(size).toLongArray(),
            type, size, size, sessionId
        )
        val path = when (type) {
            PpgType.PPG_GREEN -> MessagePath.DATA_PPG_GREEN
            PpgType.PPG_IR -> MessagePath.DATA_PPG_IR
            PpgType.PPG_RED -> MessagePath.DATA_PPG_RED
        }
        queueBatch(path, batch)
        recording.clearFromStartUntil(size)
    }

    private fun sendAccelerometerData(size: Int) {
        if (size <= 0) return
        val batch = AccelerometerData(
            accRecording.xValues.take(size).toFloatArray(), accRecording.yValues.take(size).toFloatArray(),
            accRecording.zValues.take(size).toFloatArray(), accRecording.timestamps.take(size).toLongArray(),
            size, size, sessionId
        )
        queueBatch(MessagePath.DATA_ACCELEROMETER, batch)
        accRecording.clearFromStartUntil(size)
    }

    private fun flushRemainingData() {
        for ((type, recording) in recordings) sendPpgData(type, recording.getSize() ?: 0)
        sendAccelerometerData(accRecording.getSize())
    }

    private fun queueBatch(path: String, batch: Any) {
        uploads.addLast(Pair("$path/$sessionId/${batchNumber++}", gson.toJson(batch).toByteArray(Charsets.UTF_8)))
        drainUploads()
    }

    private fun drainUploads() {
        if (uploadInFlight || !client.isConnected) return
        if (uploads.isEmpty()) {
            if ((currentState == ActivityCode.STOP_ACTIVITY || currentState == ActivityCode.DO_NOTHING) &&
                !ppgListener.isRegistered() && !isAccRegistered) {
                MainService.stopService(this)
            }
            if (destroying) client.disconnect()
            return
        }
        uploadInFlight = true
        val batch = uploads.first
        Wearable.DataApi.putDataItem(client, PutDataRequest.create(batch.first).setData(batch.second).setUrgent())
            .setResultCallback { result ->
                uploadInFlight = false
                if (result.status.isSuccess) {
                    uploads.removeFirst()
                    drainUploads()
                } else {
                    Log.e(TAG, "Batch upload failed; retained for retry: ${result.status.statusCode}")
                    mainHandler.postDelayed({ drainUploads() }, 1000L)
                }
            }
    }

    private fun sendState() {
        if (!client.isConnected) return
        val mask = if (ppgListener.isTracking()) ppgListener.channels.sumOf { it.mask } else 0
        val state = Message(Entity.WEAR_APP, "Watch sensor state", currentState, mask, sessionId,
            intArrayOf(counts.getValue(PpgType.PPG_GREEN), counts.getValue(PpgType.PPG_IR),
                counts.getValue(PpgType.PPG_RED), accCount), control.revision,
            !ppgListener.isRegistered() && !isAccRegistered)
        val bytes = gson.toJson(state).toByteArray(Charsets.UTF_8)
        Wearable.NodeApi.getConnectedNodes(client).setResultCallback { nodes ->
            for (node in nodes.nodes) Wearable.MessageApi.sendMessage(client, node.id, MessagePath.INFO, bytes)
        }
    }

    private fun showPpgError(resource: Int) {
        Toast.makeText(this, resource, Toast.LENGTH_LONG).show()
        binding.ppgGreenStatus.setText(resource)
        binding.ppgIrStatus.setText(resource)
        binding.ppgRedStatus.setText(resource)
        sendState()
    }

    private fun updatePpgStatuses() {
        binding.ppgGreenContainer.visibility = View.VISIBLE
        binding.ppgIrContainer.visibility = View.VISIBLE
        binding.ppgRedContainer.visibility = View.VISIBLE
        val statuses = listOf(binding.ppgGreenStatus, binding.ppgIrStatus, binding.ppgRedStatus)
        for (type in PpgType.values()) {
            statuses[type.ordinal].setText(when {
                !ppgAvailable || !hasBodySensorPermission() -> R.string.NoContinuousPpgSupport
                currentState == ActivityCode.PAUSE_ACTIVITY -> R.string.status_paused
                currentState != ActivityCode.START_ACTIVITY || type !in ppgListener.channels -> R.string.status_stopped
                else -> R.string.status_waiting
            })
        }
    }

    private fun updateAccelerometerStatus() {
        binding.message.visibility = View.VISIBLE
        binding.message.text = when {
            accelerometer == null -> getString(R.string.NoAccelerometerSupport)
            currentState == ActivityCode.START_ACTIVITY && !isAccTracking -> getString(R.string.AccelerometerFailed)
            else -> getString(R.string.accelerometer_samples, accCount)
        }
    }

    private fun hasBodySensorPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.BODY_SENSORS
    ) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        destroying = true
        control.accept(control.sessionId, control.revision + 1L, ActivityCode.STOP_ACTIVITY,
            SystemClock.elapsedRealtime())
        persistControl()
        stopSession(ActivityCode.STOP_ACTIVITY)
        connectionManager.disconnect()
        mainHandler.removeCallbacks(releaseRetry)
        Wearable.MessageApi.removeListener(client, messageListener)
        drainUploads()
        if (uploads.isEmpty()) MainService.stopService(this)
        super.onDestroy()
    }

    override fun getAmbientCallback() = object : AmbientModeSupport.AmbientCallback() {}

    companion object {
        private const val TAG = "Wear: MainActivity"
        private const val REQUEST_PERMISSIONS = 10
    }
}
