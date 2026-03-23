package com.example.samplewearmobileapp

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.wear.ambient.AmbientModeSupport
import com.example.samplewearmobileapp.Constants.PPG_GREEN_BATCH_SIZE
import com.example.samplewearmobileapp.Constants.PPG_GREEN_SAMPLE_RATE
import com.example.samplewearmobileapp.Constants.PPG_IR_RED_BATCH_SIZE
import com.example.samplewearmobileapp.Constants.PPG_IR_RED_BATCH_TICK_RATE
import com.example.samplewearmobileapp.Constants.PPG_IR_RED_SAMPLE_RATE
import com.example.samplewearmobileapp.constants.codes.ActivityCode
import com.example.samplewearmobileapp.constants.Entity
import com.example.samplewearmobileapp.constants.MessagePath
import com.example.samplewearmobileapp.constants.codes.ExtraCode.TOGGLE_ACTIVITY
import com.example.samplewearmobileapp.databinding.ActivityMainBinding
import com.example.samplewearmobileapp.models.Message
import com.example.samplewearmobileapp.models.PpgData
import com.example.samplewearmobileapp.models.PpgType
import com.example.samplewearmobileapp.trackers.Listener
import com.example.samplewearmobileapp.trackers.ppggreen.PpgGreenData
import com.example.samplewearmobileapp.trackers.ppggreen.PpgGreenListener
import com.example.samplewearmobileapp.trackers.ppggreen.PpgGreenStatus
import com.example.samplewearmobileapp.trackers.ppgir.PpgIrData
import com.example.samplewearmobileapp.trackers.ppgir.PpgIrListener
import com.example.samplewearmobileapp.trackers.ppgred.PpgRedData
import com.example.samplewearmobileapp.trackers.ppgred.PpgRedListener
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.samsung.android.service.health.tracking.HealthTrackerException
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity :
    FragmentActivity(),
    GoogleApiClient.ConnectionCallbacks,
    AmbientModeSupport.AmbientCallbackProvider
{
    private val tag = "Wear: MainActivity"
    private lateinit var binding: ActivityMainBinding
    private lateinit var client: GoogleApiClient
    private var connectedNode: List<Node>? = null
    private var message: Message = Message()
    private var currentMessage: Message? = null
    private var currentState = 0
    private var currentPpgGreenDataNumber = 0
    private var currentPpgIrDataNumber = 0
    private var currentPpgRedDataNumber = 0
    private var isOnDemandMeasurementRunning = AtomicBoolean(false)
    private lateinit var ambientController: AmbientModeSupport.AmbientController

    private lateinit var textStatus: TextView
    private lateinit var textTip: TextView
    private lateinit var ppgGreenContainer: LinearLayout
    private lateinit var textPpgGreen: TextView
    private lateinit var textPpgGreenStatus: TextView
    private lateinit var textPpgGreenNumber: TextView
    private lateinit var textPpgGreenTimestamp: TextView
    private lateinit var ppgIrContainer: LinearLayout
    private lateinit var textPpgIr: TextView
    private lateinit var textPpgIrStatus: TextView
    private lateinit var textPpgIrNumber: TextView
    private lateinit var textPpgIrTimestamp: TextView
    private lateinit var ppgRedContainer: LinearLayout
    private lateinit var textPpgRed: TextView
    private lateinit var textPpgRedStatus: TextView
    private lateinit var textPpgRedNumber: TextView
    private lateinit var textPpgRedTimestamp: TextView

    private lateinit var uiUpdateThread: Thread
    private lateinit var connectionManager: ConnectionManager
    private lateinit var ppgGreenListener: PpgGreenListener
    private lateinit var ppgIrListener: PpgIrListener
    private lateinit var ppgRedListener: PpgRedListener
    private var connected = false

    private var ppgGreenRecording = PpgRecording(PpgType.PPG_GREEN)
    private var ppgIrRecording = PpgRecording(PpgType.PPG_IR)
    private var ppgRedRecording = PpgRecording(PpgType.PPG_RED)

    // unused
    private val onDemandCountDownTimer: CountDownTimer = object : CountDownTimer(
        ON_DEMAND_MEASUREMENT_DURATION.toLong(),
        ON_DEMAND_MEASUREMENT_TICK.toLong()
    ) {
        override fun onTick(timeLeft: Long) {
            if (!isOnDemandMeasurementRunning.get())
                cancel()
        }

        override fun onFinish() {
            if (!isOnDemandMeasurementRunning.get()) return
            Log.i(tag, "On-Demand measurement finished")
            runOnUiThread {
                textPpgIrStatus.setText(R.string.status_finished)
                textPpgRedStatus.setText(R.string.status_finished)
            }
            ppgIrListener.stopTracker()
            ppgRedListener.stopTracker()
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            isOnDemandMeasurementRunning.set(false)
        }
    }

    val trackerDataObserver: TrackerDataObserver = object : TrackerDataObserver {

        override fun onPpgGreenTrackerDataChanged(ppgGreenData: PpgGreenData) {
            Log.i(tag,"PPG Green Status: " + ppgGreenData.status)
            when(ppgGreenData.status) {
                PpgGreenStatus.PPG_GREEN_STATUS_GOOD.code -> {
                    Log.i(tag, "Green PPG Data received")
                    currentPpgGreenDataNumber++
                    this@MainActivity.runOnUiThread {
                        textPpgGreenStatus.text = getString(R.string.status_measuring)
                        textPpgGreen.text = ppgGreenData.ppgValue.toString()
                        Log.i(tag, "PPG Green : ${ppgGreenData.ppgValue}")
                        textPpgGreenTimestamp.text = ppgGreenData.timestamp.toString()
                        Log.i(tag, "PPG Green Timestamp : ${ppgGreenData.timestamp}")
                        textPpgGreenNumber.text = currentPpgGreenDataNumber.toString()
                    }
                }
                PpgGreenStatus.PPG_GREEN_STATUS_NONE.code -> {
                    Log.i(tag, "No Green PPG Data")
                }
            }
            ppgGreenRecording.add(ppgGreenData.ppgValue, ppgGreenData.timestamp)
            // if current data number reaches multiple of batch size, send the batch
            if (currentPpgGreenDataNumber % PPG_GREEN_BATCH_SIZE == 0) {
                sendPpgData(ppgGreenRecording)
                ppgGreenRecording.clearFromStartUntil(PPG_GREEN_BATCH_SIZE)
            }
        }

        override fun onPpgIrTrackerDataChanged(ppgIrData: PpgIrData) {
            Log.i(tag, "InfraRed PPG Data received")
            currentPpgIrDataNumber++
            this@MainActivity.runOnUiThread {
                textPpgIrStatus.text = getString(R.string.status_measuring)
                textPpgIr.text = ppgIrData.ppgValue.toString()
                Log.i(tag, "PPG IR : ${ppgIrData.ppgValue}")
                textPpgIrTimestamp.text = ppgIrData.timestamp.toString()
                Log.i(tag, "PPG IR Timestamp : ${ppgIrData.timestamp}")
                textPpgIrNumber.text = currentPpgIrDataNumber.toString()
            }
            ppgIrRecording.add(ppgIrData.ppgValue, ppgIrData.timestamp)
            // if current data number reaches multiple of batch size, send the batch
            if (currentPpgIrDataNumber % PPG_IR_RED_BATCH_SIZE == 0) {
                sendPpgData(ppgIrRecording)
                ppgIrRecording.clearFromStartUntil(PPG_IR_RED_BATCH_SIZE)
            }
        }

        override fun onPpgRedTrackerDataChanged(ppgRedData: PpgRedData) {
            Log.i(tag, "Red PPG Data received")
            currentPpgRedDataNumber++
            this@MainActivity.runOnUiThread {
                textPpgRedStatus.text = getString(R.string.status_measuring)
                textPpgRed.text = ppgRedData.ppgValue.toString()
                Log.i(tag, "PPG Red : ${ppgRedData.ppgValue}")
                textPpgRedTimestamp.text = ppgRedData.timestamp.toString()
                Log.i(tag, "PPG Red Timestamp : ${ppgRedData.timestamp}")
                textPpgRedNumber.text = currentPpgRedDataNumber.toString()
            }
            ppgRedRecording.add(ppgRedData.ppgValue, ppgRedData.timestamp)
            // if current data number reaches multiple of batch size, send the batch
            if (currentPpgRedDataNumber % PPG_IR_RED_BATCH_SIZE == 0) {
                sendPpgData(ppgRedRecording)
                ppgRedRecording.clearFromStartUntil(PPG_IR_RED_BATCH_SIZE)
            }
        }

        override fun onError(errorResourceId: Int) {
            runOnUiThread {
                Toast.makeText(
                    applicationContext,
                    getString(errorResourceId),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private val connectionObserver: ConnectionObserver = object : ConnectionObserver {
        override fun onConnectionResult(stringResourceId: Int) {
            runOnUiThread {
                Toast.makeText(
                    applicationContext, getString(stringResourceId), Toast.LENGTH_LONG
                ).show()
            }
            if (stringResourceId != R.string.ConnectedToHs) {
                finish()
            }

            connected = true
            TrackerDataNotifier.instance?.addObserver(trackerDataObserver)
//            heartRateListener = HeartRateListener()
            ppgGreenListener = PpgGreenListener()
            ppgIrListener = PpgIrListener()
            ppgRedListener = PpgRedListener()

//            connectionManager.initHeartRate(heartRateListener)
            connectionManager.initPpgGreen(ppgGreenListener)
            connectionManager.initPpgIr(ppgIrListener)
            connectionManager.initPpgRed(ppgRedListener)

            // commented out because tracker started at other point of the app
            //heartRateListener.startTracker()
        }

        override fun onError(e: HealthTrackerException?) {
            if (e != null) {
                if (e.errorCode == HealthTrackerException.OLD_PLATFORM_VERSION
                    || e.errorCode == HealthTrackerException.PACKAGE_NOT_INSTALLED)
                    runOnUiThread {
                        Toast.makeText(
                            applicationContext,
                            getString(R.string.HealthPlatformVersionIsOutdated),
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            if (e != null) {
                if (e.hasResolution()) {
                    e.resolve(this@MainActivity)
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            applicationContext, getString(R.string.ConnectionError), Toast.LENGTH_LONG
                        ).show()
                    }
                    Log.e(tag, "Could not connect to Health Tracking Service: " + e.message)
                }
            }
            finish()
        }
    }

    private class MyAmbientCallback : AmbientModeSupport.AmbientCallback() {
        override fun onEnterAmbient(ambientDetails: Bundle?) {
            super.onEnterAmbient(ambientDetails)
            Log.i("Wear: Ambient","Entered ambient mode")
        }

        override fun onExitAmbient() {
            super.onExitAmbient()
            Log.i("Wear: Ambient","Exited ambient mode")
        }
    }

    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback = MyAmbientCallback()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("Wear","onCreate")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ambientController = AmbientModeSupport.attach(this)

        message.sender = Entity.WEAR_APP

        currentPpgGreenDataNumber = 0
        currentPpgIrDataNumber = 0
        currentPpgRedDataNumber = 0

        uiUpdateThread = Thread {}
        uiUpdateThread.start()

        // requests permission
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        } else createConnectionManager()

        // set UI vars
        textStatus = binding.statusMsg
        textTip = binding.message
        ppgGreenContainer = binding.ppgGreenContainer
        textPpgGreenStatus = binding.ppgGreenStatus
        textPpgGreenNumber = binding.ppgGreenNumber
        textPpgGreen = binding.ppgGreen
        textPpgGreenTimestamp = binding.ppgGreenTimestamp
        ppgIrContainer = binding.ppgIrContainer
        textPpgIrStatus = binding.ppgIrStatus
        textPpgIrNumber = binding.ppgIrNumber
        textPpgIr = binding.ppgIr
        textPpgIrTimestamp = binding.ppgIrTimestamp
        ppgRedContainer = binding.ppgRedContainer
        textPpgRedStatus = binding.ppgRedStatus
        textPpgRedNumber = binding.ppgRedNumber
        textPpgRed = binding.ppgRed
        textPpgRedTimestamp = binding.ppgRedTimestamp

        // set initial UI
        textStatus.text = getString(R.string.default_status)
        textTip.text = getString(R.string.message_placeholder)
        textPpgGreenStatus.text = getString(R.string.default_status)
        textPpgGreenNumber.text = getString(R.string.default_value)
        textPpgGreen.text = getString(R.string.default_value)
        textPpgGreenTimestamp.text = getString(R.string.default_value)
        textPpgIrStatus.text = getString(R.string.default_status)
        textPpgIrNumber.text = getString(R.string.default_value)
        textPpgIr.text = getString(R.string.default_value)
        textPpgIrTimestamp.text = getString(R.string.default_value)
        textPpgRedStatus.text = getString(R.string.default_status)
        textPpgRedNumber.text = getString(R.string.default_value)
        textPpgRed.text = getString(R.string.default_value)
        textPpgRedTimestamp.text = getString(R.string.default_value)

        textTip.visibility = View.VISIBLE
        ppgGreenContainer.visibility = View.GONE
        ppgIrContainer.visibility = View.GONE
        ppgRedContainer.visibility = View.GONE

        // build Google API Client with access to Wearable API
        client = GoogleApiClient.Builder(this)
            .addConnectionCallbacks(this)
            .addApi(Wearable.API)
            .build()
        client.connect()
        Log.d("Wear","build Google API passed")
    }

    /**
     * Invalidate the app status by checking all
     * tracker is currently running or not.
     * If all tracker is not running, invalidate app
     * and update view accordingly.
     *
     * @return A boolean value. True if invalidation
     * occurs. False if nothing done.
     */
    private fun invalidateAppStatus(): Boolean {
        Log.d("Wear", "invalidateAppStatus\n" +
                "PpgGreen Tracking: ${ppgGreenListener.isTracking()}\n" +
                "PpgIR Tracking: ${ppgIrListener.isTracking()}\n" +
                "PpgRed Tracking: ${ppgRedListener.isTracking()}\n")
        return if (!ppgGreenListener.isTracking() &&
            !ppgIrListener.isTracking() &&
            !ppgRedListener.isTracking()) {
            runOnUiThread {
                textStatus.text = getString(R.string.status_stopped)
                textPpgGreenStatus.text = getString(R.string.status_stopped)
                textPpgIrStatus.text = getString(R.string.status_stopped)
                textPpgRedStatus.text = getString(R.string.status_stopped)
            }

            MainService.stopService(this)

            true
        } else false
    }

    override fun onDestroy() {
        Log.d(tag, "onDestroy")
        super.onDestroy()
        if (connected) {
            ppgGreenListener.stopTracker()
            ppgIrListener.stopTracker()
            ppgRedListener.stopTracker()
            TrackerDataNotifier.instance?.removeObserver(trackerDataObserver)
            connectionManager.disconnect()
        }
    }

    private fun createConnectionManager() {
        try {
            connectionManager = ConnectionManager(connectionObserver)
            connectionManager.connect(applicationContext)
        } catch (t: Throwable) {
            Log.e(tag, t.message!!)
        }
    }

    override fun onConnected(bundle: Bundle?) {
        Wearable.NodeApi.getConnectedNodes(client).setResultCallback {
            connectedNode = it.nodes
            Log.d("Wear","Node connected")

            // prompt to reset phone app state
            // onConnected is not immediately run when onCreate
            // so the prompt should be executed on connection
            message.code = ActivityCode.STOP_ACTIVITY
            message.content = "Reset Phone App State"
            sendMessage(message, MessagePath.COMMAND)
            Log.d("Wear","prompt reset passed")
        }
        Wearable.MessageApi.addListener(client) { messageEvent ->
            currentMessage = Gson().fromJson(String(messageEvent.data), Message::class.java)
            onMessageArrived(messageEvent.path)
        }
    }

    override fun onConnectionSuspended(code: Int) {
        Log.w("Wear", "Google Api Client connection suspended!")
    }

    private fun onMessageArrived(messagePath: String) {
        // Guard: Samsung Health listeners may not be initialized yet
        if (!connected) {
            Log.w("Wear", "onMessageArrived: Ignoring message on path '$messagePath' " +
                    "— Samsung Health not connected yet")
            return
        }
        currentMessage?.let {
            when (messagePath) {
                MessagePath.COMMAND -> {
                    when (it.code) {
                        ActivityCode.START_ACTIVITY -> { // start all tracker
                            runOnUiThread {
//                                hrContainer.visibility = View.VISIBLE
                                ppgGreenContainer.visibility = View.VISIBLE
                                ppgIrContainer.visibility = View.VISIBLE
                                ppgRedContainer.visibility = View.VISIBLE
                                textTip.visibility = View.GONE
                                textStatus.text = getString(R.string.status_running)
                            }

                            switchState(1)
                            startTracker(ppgGreenListener)
                            startTracker(ppgIrListener)
                            startTracker(ppgRedListener)
                        }
                        ActivityCode.STOP_ACTIVITY -> { // stop all tracker
                            // Flush any remaining buffered data before stopping
                            flushRemainingData()

                            runOnUiThread {
                                textStatus.text = getString(R.string.status_stopped)
                                textPpgGreenStatus.text = getString(R.string.status_stopped)
                                textPpgIrStatus.text = getString(R.string.status_stopped)
                                textPpgRedStatus.text = getString(R.string.status_stopped)
                            }

                            switchState(0)
                            ppgGreenListener.stopTracker()
                            ppgIrListener.stopTracker()
                            ppgRedListener.stopTracker()
                        }
                        ActivityCode.PAUSE_ACTIVITY -> { // pause all trackers
                            runOnUiThread {
                                textStatus.text = getString(R.string.status_paused)
                                textPpgGreenStatus.text = getString(R.string.status_paused)
                                textPpgIrStatus.text = getString(R.string.status_paused)
                                textPpgRedStatus.text = getString(R.string.status_paused)
                            }

                            ppgGreenListener.stopTracker()
                            ppgIrListener.stopTracker()
                            ppgRedListener.stopTracker()

                            // Notify Phone of paused state
                            switchState(ActivityCode.PAUSE_ACTIVITY)
                        }
                        ActivityCode.DO_NOTHING -> {
                            runOnUiThread {
                                Toast.makeText(
                                    applicationContext,
                                    getString(R.string.no_content),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
                MessagePath.REQUEST -> {
                    if (it.code == ActivityCode.START_ACTIVITY) { // receive state request
                        Log.i("Wear","A request received!")
                        switchState(currentState)
                    }
                }
                MessagePath.INFO -> {
                    TODO("Not yet implemented")
                }
                MessagePath.DATA_PPG_GREEN -> {
                    if (it.code == ActivityCode.START_ACTIVITY) { // start tracker
                        runOnUiThread {
                            ppgGreenContainer.visibility = View.VISIBLE
                            textTip.visibility = View.GONE
                            textStatus.text = getString(R.string.status_running)
                        }

                        if (it.extraCode == TOGGLE_ACTIVITY) { // if toggle is instructed
                            toggleTracker(ppgGreenListener)
                            invalidateAppStatus()
                            return
                        }

                        startTracker(ppgGreenListener)
                    }
                    else if (it.code == ActivityCode.STOP_ACTIVITY) { // stop tracker
                        ppgGreenListener.stopTracker()
                        invalidateAppStatus()
                    }
                }
                MessagePath.DATA_PPG_IR -> {
                    if (it.code == ActivityCode.START_ACTIVITY) { // start tracker
                        runOnUiThread {
                            ppgIrContainer.visibility = View.VISIBLE
                            textTip.visibility = View.GONE
                            textStatus.text = getString(R.string.status_running)
                        }

                        if (it.extraCode == TOGGLE_ACTIVITY) { // if toggle instructed
                            toggleTracker(ppgIrListener)
                            if (!ppgIrListener.isTracking()) runOnUiThread {
                                textPpgIrStatus.text = getString(R.string.status_stopped)
                            }
                            invalidateAppStatus()
                            return
                        }

                        startTracker(ppgIrListener)
                    }
                    else if (it.code == ActivityCode.STOP_ACTIVITY) { // stop tracker
                        ppgIrListener.stopTracker()
                        if (!ppgIrListener.isTracking()) runOnUiThread {
                            textPpgIrStatus.text = getString(R.string.status_stopped)
                        }
                        invalidateAppStatus()
                    }
                }
                MessagePath.DATA_PPG_RED -> {
                    if (it.code == ActivityCode.START_ACTIVITY) { // start tracker
                        runOnUiThread {
                            ppgRedContainer.visibility = View.VISIBLE
                            textTip.visibility = View.GONE
                            textStatus.text = getString(R.string.status_running)
                        }

                        if (it.extraCode == TOGGLE_ACTIVITY) { // if toggle instructed
                            toggleTracker(ppgRedListener)
                            if (!ppgRedListener.isTracking()) runOnUiThread {
                                textPpgRedStatus.text = getString(R.string.status_stopped)
                            }
                            invalidateAppStatus()
                            return
                        }

                        startTracker(ppgRedListener)
                    }
                    else if (it.code == ActivityCode.STOP_ACTIVITY) { // stop tracker
                        ppgRedListener.stopTracker()
                        if (!ppgRedListener.isTracking()) runOnUiThread {
                            textPpgRedStatus.text = getString(R.string.status_stopped)
                        }
                        invalidateAppStatus()
                    }
                }
            }
        }
    }

    private fun sendMessage(message: Message, path: String) {
        val gson = Gson()
        connectedNode?.forEach { node ->
            val bytes = gson.toJson(message).toByteArray()
            Wearable.MessageApi.sendMessage(client, node.id, path, bytes)
            Log.i("Wear","Message sent!")
            Log.i("Wear","$message")
        }
    }
    private fun sendPpgData(ppgRecording: PpgRecording, actualSize: Int? = null) {
        val path: String
        val windowSize: Int

        when (ppgRecording.ppgType) {
            PpgType.PPG_GREEN -> {
                path = MessagePath.DATA_PPG_GREEN
                windowSize = actualSize ?: PPG_GREEN_BATCH_SIZE
            }
            PpgType.PPG_IR -> {
                path = MessagePath.DATA_PPG_IR
                windowSize = actualSize ?: PPG_IR_RED_BATCH_SIZE
            }
            PpgType.PPG_RED -> {
                path = MessagePath.DATA_PPG_RED
                windowSize = actualSize ?: PPG_IR_RED_BATCH_SIZE
            }
        }

        // prep the PpgData
        val ppgData = PpgData(windowSize, ppgRecording.ppgType)
        for (i in 0 until windowSize) {
            ppgData.ppgValues[i] = ppgRecording.values[i]
            ppgData.timestamps[i] = ppgRecording.timestamps[i]
            ppgData.size++
        }
        Log.d("Wear: Sending PPG","Preparing to send PPG Data\n" +
                "PPG_GREEN_BATCH_SIZE: $PPG_GREEN_BATCH_SIZE\n" +
                "PPG_GREEN_SAMPLE_RATE: $PPG_GREEN_SAMPLE_RATE\n" +
                "PPG_IR_RED_BATCH_SIZE: $PPG_IR_RED_BATCH_SIZE\n" +
                "PPG_IR_RED_SAMPLE_RATE: $PPG_IR_RED_SAMPLE_RATE\n" +
                "PPG_IR_RED_BATCH_TICK_RATE: $PPG_IR_RED_BATCH_TICK_RATE")

        // send it
        val bytes = Gson().toJson(ppgData).toByteArray()
        Wearable.DataApi.putDataItem(client,
            PutDataRequest.create(path).setData(bytes).setUrgent()
        )
        Log.i("Wear","PPG Data sent via DataApi!\n$ppgData")
    }

    /**
     * Flushes any remaining buffered PPG data that hasn't
     * reached a full batch size. Called before stopping trackers
     * to prevent data loss.
     */
    private fun flushRemainingData() {
        val greenSize = ppgGreenRecording.getSize() ?: 0
        if (greenSize > 0) {
            sendPpgData(ppgGreenRecording, greenSize)
            ppgGreenRecording.clearFromStartUntil(greenSize)
            Log.i(tag, "Flushed $greenSize remaining PPG Green data points")
        }
        val irSize = ppgIrRecording.getSize() ?: 0
        if (irSize > 0) {
            sendPpgData(ppgIrRecording, irSize)
            ppgIrRecording.clearFromStartUntil(irSize)
            Log.i(tag, "Flushed $irSize remaining PPG IR data points")
        }
        val redSize = ppgRedRecording.getSize() ?: 0
        if (redSize > 0) {
            sendPpgData(ppgRedRecording, redSize)
            ppgRedRecording.clearFromStartUntil(redSize)
            Log.i(tag, "Flushed $redSize remaining PPG Red data points")
        }
    }

    private fun toggleTracker(listener: Listener) {
        when (listener) {
            ppgGreenListener -> {
                if (ppgGreenListener.isTracking()) ppgGreenListener.stopTracker()
                else startTracker(ppgGreenListener)
            }
            ppgIrListener -> {
                if (ppgIrListener.isTracking()) ppgIrListener.stopTracker()
                else startTracker(ppgIrListener)
            }
            ppgRedListener -> {
                if (ppgRedListener.isTracking()) ppgRedListener.stopTracker()
                else startTracker(ppgRedListener)
            }
        }
    }

    private fun startTracker(listener: Listener) {
        when (listener) {
            ppgGreenListener -> {
                ppgGreenListener.startTracker()
            }
            ppgIrListener -> {
                ppgIrListener.startTracker()
            }
            ppgRedListener -> {
                ppgRedListener.startTracker()
            }
        }

        // start the foreground service
        MainService.startService(this, "Tracker is running...")
    }

    /**
     * Sets the app state and sends it to the Phone via INFO message.
     *
     * Supports state codes:
     * - 0: DO_NOTHING / Stopped
     * - 1: START_ACTIVITY / Running
     * - 2: STOP_ACTIVITY / Stopped
     * - 3: PAUSE_ACTIVITY / Paused
     *
     * If no `forceCode` is given, toggles between 0 and 1.
     *
     * @param forceCode Optional. Sets the state directly.
     */
    private fun switchState(forceCode: Int = 99) {
        if (forceCode != 99) {
            currentState = forceCode
        }
        else if (currentState == 0) {
            currentState = 1
        }
        else if (currentState == 1 || currentState == ActivityCode.PAUSE_ACTIVITY) {
            currentState = 0
        }
        message.code = currentState
        message.content = "Wear state: $currentState"
        sendMessage(message, MessagePath.INFO)
        Log.d("Wear","stateNum changed to: $currentState")
    }

    /**
     * Checks whether all permissions required has been granted.
     * @return Boolean value of whether all permissions required has been granted
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) ==
                PackageManager.PERMISSION_GRANTED
    }

    companion object {
        /**
         * Measurement duration for On-Demand data type in ms.
         */
        private const val ON_DEMAND_MEASUREMENT_DURATION = 30000 // 30k ms = 30 secs
        private const val ON_DEMAND_MEASUREMENT_TICK = 250 // for ticking countdown timer
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.BODY_SENSORS,
                Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            ).toTypedArray()
    }
}