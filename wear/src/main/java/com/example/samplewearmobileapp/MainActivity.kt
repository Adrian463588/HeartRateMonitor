package com.example.samplewearmobileapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.wear.ambient.AmbientModeSupport
import com.example.samplewearmobileapp.Constants.PPG_GREEN_BATCH_SIZE
import com.example.samplewearmobileapp.Constants.PPG_IR_RED_BATCH_SIZE
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
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.samsung.android.service.health.tracking.HealthTrackerException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Main activity for the Wear OS module.
 *
 * Responsibilities:
 * - Manages Samsung Health sensor lifecycle via [ConnectionManager].
 * - Communicates with the mobile module using the modern Wearable Data Layer
 *   ([MessageClient], [DataClient], [NodeClient]).
 * - Batches PPG readings and sends them to the phone for plotting.
 *
 * **SOLID compliance:**
 * - SRP: sensor-specific logic is in [PpgGreenListener] / [PpgIrListener] /
 *   [PpgRedListener]; this Activity only orchestrates.
 * - OCP: new message paths can be added to [onMessageArrived] without
 *   modifying existing branches.
 * - DIP: depends on [ConnectionObserver], [TrackerDataObserver], [PermissionManager.PermissionCallback].
 */
class MainActivity :
    FragmentActivity(),
    MessageClient.OnMessageReceivedListener,
    AmbientModeSupport.AmbientCallbackProvider,
    PermissionManager.PermissionCallback
{
    private val tag = "Wear:MainActivity"

    // -------------------------------------------------------------------------
    // View binding
    // -------------------------------------------------------------------------

    private lateinit var binding: ActivityMainBinding
    private lateinit var ambientController: AmbientModeSupport.AmbientController

    // -------------------------------------------------------------------------
    // Permission manager (SRP — all permission logic lives here)
    // -------------------------------------------------------------------------

    private lateinit var permissionManager: PermissionManager

    // -------------------------------------------------------------------------
    // Wearable Data Layer (modern API — replaces deprecated GoogleApiClient)
    // -------------------------------------------------------------------------

    private lateinit var messageClient: MessageClient
    private lateinit var dataClient: DataClient
    private lateinit var nodeClient: NodeClient

    // -------------------------------------------------------------------------
    // Samsung Health SDK
    // -------------------------------------------------------------------------

    private lateinit var connectionManager: ConnectionManager
    private lateinit var ppgGreenListener: PpgGreenListener
    private lateinit var ppgIrListener: PpgIrListener
    private lateinit var ppgRedListener: PpgRedListener
    private var isSamsungHealthConnected = false

    // -------------------------------------------------------------------------
    // Session state
    // -------------------------------------------------------------------------

    private val gson = Gson()
    private var currentMessage: Message? = null
    private var currentState = 0

    private var currentPpgGreenDataNumber = 0
    private var currentPpgIrDataNumber = 0
    private var currentPpgRedDataNumber = 0

    private var ppgGreenRecording = PpgRecording(PpgType.PPG_GREEN)
    private var ppgIrRecording    = PpgRecording(PpgType.PPG_IR)
    private var ppgRedRecording   = PpgRecording(PpgType.PPG_RED)

    // -------------------------------------------------------------------------
    // UI references (bound in onCreate after setContentView)
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Samsung Health observer — receives sensor data from listener threads
    // -------------------------------------------------------------------------

    private val trackerDataObserver: TrackerDataObserver = object : TrackerDataObserver {

        override fun onPpgGreenTrackerDataChanged(ppgGreenData: PpgGreenData) {
            Log.i(tag, "PPG Green status: ${ppgGreenData.status}")
            when (ppgGreenData.status) {
                PpgGreenStatus.PPG_GREEN_STATUS_GOOD.code -> {
                    currentPpgGreenDataNumber++
                    runOnUiThread {
                        textPpgGreenStatus.text = getString(R.string.status_measuring)
                        textPpgGreen.text = ppgGreenData.ppgValue.toString()
                        textPpgGreenTimestamp.text = ppgGreenData.timestamp.toString()
                        textPpgGreenNumber.text = currentPpgGreenDataNumber.toString()
                    }
                }
                PpgGreenStatus.PPG_GREEN_STATUS_NONE.code -> {
                    Log.i(tag, "No PPG Green data this tick")
                }
            }
            ppgGreenRecording.add(ppgGreenData.ppgValue, ppgGreenData.timestamp)
            if (currentPpgGreenDataNumber % PPG_GREEN_BATCH_SIZE == 0) {
                sendPpgData(ppgGreenRecording)
                ppgGreenRecording.clearFromStartUntil(PPG_GREEN_BATCH_SIZE)
            }
        }

        override fun onPpgIrTrackerDataChanged(ppgIrData: PpgIrData) {
            currentPpgIrDataNumber++
            runOnUiThread {
                textPpgIrStatus.text = getString(R.string.status_measuring)
                textPpgIr.text = ppgIrData.ppgValue.toString()
                textPpgIrTimestamp.text = ppgIrData.timestamp.toString()
                textPpgIrNumber.text = currentPpgIrDataNumber.toString()
            }
            ppgIrRecording.add(ppgIrData.ppgValue, ppgIrData.timestamp)
            if (currentPpgIrDataNumber % PPG_IR_RED_BATCH_SIZE == 0) {
                sendPpgData(ppgIrRecording)
                ppgIrRecording.clearFromStartUntil(PPG_IR_RED_BATCH_SIZE)
            }
        }

        override fun onPpgRedTrackerDataChanged(ppgRedData: PpgRedData) {
            currentPpgRedDataNumber++
            runOnUiThread {
                textPpgRedStatus.text = getString(R.string.status_measuring)
                textPpgRed.text = ppgRedData.ppgValue.toString()
                textPpgRedTimestamp.text = ppgRedData.timestamp.toString()
                textPpgRedNumber.text = currentPpgRedDataNumber.toString()
            }
            ppgRedRecording.add(ppgRedData.ppgValue, ppgRedData.timestamp)
            if (currentPpgRedDataNumber % PPG_IR_RED_BATCH_SIZE == 0) {
                sendPpgData(ppgRedRecording)
                ppgRedRecording.clearFromStartUntil(PPG_IR_RED_BATCH_SIZE)
            }
        }

        override fun onError(errorResourceId: Int) {
            runOnUiThread {
                Toast.makeText(applicationContext, getString(errorResourceId), Toast.LENGTH_LONG).show()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Samsung Health connection observer
    // -------------------------------------------------------------------------

    private val connectionObserver: ConnectionObserver = object : ConnectionObserver {

        override fun onConnectionResult(stringResourceId: Int) {
            if (stringResourceId != R.string.ConnectedToHs) {
                runOnUiThread {
                    Toast.makeText(applicationContext, getString(stringResourceId), Toast.LENGTH_LONG).show()
                }
                return
            }
            isSamsungHealthConnected = true
            TrackerDataNotifier.instance?.addObserver(trackerDataObserver)
            ppgGreenListener = PpgGreenListener()
            ppgIrListener    = PpgIrListener()
            ppgRedListener   = PpgRedListener()
            connectionManager.initPpgGreen(ppgGreenListener)
            connectionManager.initPpgIr(ppgIrListener)
            connectionManager.initPpgRed(ppgRedListener)
        }

        override fun onError(e: HealthTrackerException?) {
            if (e == null) return
            val messageRes = when {
                e.errorCode == HealthTrackerException.OLD_PLATFORM_VERSION ||
                e.errorCode == HealthTrackerException.PACKAGE_NOT_INSTALLED ->
                    R.string.HealthPlatformVersionIsOutdated
                else -> R.string.ConnectionError
            }
            runOnUiThread {
                Toast.makeText(applicationContext, getString(messageRes), Toast.LENGTH_LONG).show()
            }
            if (e.hasResolution()) {
                e.resolve(this@MainActivity)
            } else {
                Log.e(tag, "Samsung Health connection error (no resolution): ${e.message}")
                finish()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Ambient mode
    // -------------------------------------------------------------------------

    private class WearAmbientCallback : AmbientModeSupport.AmbientCallback() {
        override fun onEnterAmbient(ambientDetails: Bundle?) {
            Log.i("Wear:Ambient", "Entered ambient mode")
        }
        override fun onExitAmbient() {
            Log.i("Wear:Ambient", "Exited ambient mode")
        }
    }

    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback = WearAmbientCallback()

    // -------------------------------------------------------------------------
    // Activity lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(tag, "onCreate")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ambientController = AmbientModeSupport.attach(this)

        bindViews()
        resetUi()

        // Initialise modern Wearable Data Layer clients
        messageClient = Wearable.getMessageClient(this)
        dataClient    = Wearable.getDataClient(this)
        nodeClient    = Wearable.getNodeClient(this)

        // Delegate all permission logic to PermissionManager (SRP)
        permissionManager = PermissionManager(this, this)
        permissionManager.startFlow()
    }

    override fun onResume() {
        super.onResume()
        // Register for Wearable messages while the Activity is in the foreground
        messageClient.addListener(this)
        Log.d(tag, "MessageClient listener registered")

        // On resume, prompt the phone to reset its state (in case the watch
        // was restarted mid-session)
        val resetMsg = Message(Entity.WEAR_APP, ActivityCode.STOP_ACTIVITY).apply {
            content = "Reset Phone App State"
        }
        sendMessageToPhone(resetMsg, MessagePath.COMMAND)
    }

    override fun onPause() {
        super.onPause()
        messageClient.removeListener(this)
        Log.d(tag, "MessageClient listener removed")
    }

    override fun onDestroy() {
        Log.d(tag, "onDestroy")
        if (isSamsungHealthConnected) {
            ppgGreenListener.stopTracker()
            ppgIrListener.stopTracker()
            ppgRedListener.stopTracker()
            TrackerDataNotifier.instance?.removeObserver(trackerDataObserver)
            connectionManager.disconnect()
        }
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // Permission handling — delegated to PermissionManager (SRP)
    // -------------------------------------------------------------------------

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionManager.onPermissionResult(requestCode, permissions, grantResults)
    }

    // -- PermissionManager.PermissionCallback --

    /** All required permissions granted; bootstrap Samsung Health. */
    override fun onAllPermissionsGranted() {
        Log.d(tag, "All permissions granted — connecting to Samsung Health")
        createConnectionManager()
    }

    /**
     * User denied at least one permission but can still be asked again.
     * Show a rationale toast; do NOT close the app.
     */
    override fun onPermissionsDenied() {
        Log.w(tag, "Permissions denied — showing rationale")
        Toast.makeText(this, getString(R.string.permission_rationale), Toast.LENGTH_LONG).show()
    }

    /**
     * Permission permanently denied or background sensor needs manual Settings toggle.
     * Opens the app's Settings page so the user can grant it manually.
     */
    override fun onPermissionsPermanentlyDenied() {
        Log.w(tag, "Permissions permanently denied — opening Settings")
        Toast.makeText(this, getString(R.string.permission_settings_guide), Toast.LENGTH_LONG).show()
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    // -------------------------------------------------------------------------
    // MessageClient.OnMessageReceivedListener — receives from phone
    // -------------------------------------------------------------------------

    /**
     * Called on a background thread by the Wearable Data Layer whenever
     * the phone sends a message to this watch app.
     */
    override fun onMessageReceived(messageEvent: MessageEvent) {
        currentMessage = gson.fromJson(String(messageEvent.data), Message::class.java)
        onMessageArrived(messageEvent.path)
    }

    // -------------------------------------------------------------------------
    // Message dispatch
    // -------------------------------------------------------------------------

    private fun onMessageArrived(messagePath: String) {
        if (!isSamsungHealthConnected) {
            Log.w(tag, "onMessageArrived: Samsung Health not ready, ignoring '$messagePath'")
            return
        }
        currentMessage?.let { msg ->
            when (messagePath) {
                MessagePath.COMMAND -> handleCommandMessage(msg)
                MessagePath.REQUEST -> handleRequestMessage(msg)
                MessagePath.INFO    -> Log.d(tag, "INFO path received (not yet implemented): $msg")
                MessagePath.DATA_PPG_GREEN -> handlePpgMessage(msg, ppgGreenListener,
                    showContainer = { showPpgGreenContainer() },
                    stopStatus   = { setPpgGreenStatus(R.string.status_stopped) })
                MessagePath.DATA_PPG_IR  -> handlePpgMessage(msg, ppgIrListener,
                    showContainer = { showPpgIrContainer() },
                    stopStatus   = { setPpgIrStatus(R.string.status_stopped) })
                MessagePath.DATA_PPG_RED -> handlePpgMessage(msg, ppgRedListener,
                    showContainer = { showPpgRedContainer() },
                    stopStatus   = { setPpgRedStatus(R.string.status_stopped) })
                else -> Log.w(tag, "Unknown message path: $messagePath")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Command / Request handlers (DRY: each branch is a single function call)
    // -------------------------------------------------------------------------

    private fun handleCommandMessage(msg: Message) {
        when (msg.code) {
            ActivityCode.START_ACTIVITY -> {
                runOnUiThread {
                    ppgGreenContainer.visibility = View.VISIBLE
                    ppgIrContainer.visibility    = View.VISIBLE
                    ppgRedContainer.visibility   = View.VISIBLE
                    textTip.visibility           = View.GONE
                    textStatus.text              = getString(R.string.status_running)
                }
                switchState(ActivityCode.START_ACTIVITY)
                startAllTrackers()
            }
            ActivityCode.STOP_ACTIVITY -> {
                flushRemainingData()
                runOnUiThread {
                    textStatus.text         = getString(R.string.status_stopped)
                    textPpgGreenStatus.text = getString(R.string.status_stopped)
                    textPpgIrStatus.text    = getString(R.string.status_stopped)
                    textPpgRedStatus.text   = getString(R.string.status_stopped)
                }
                switchState(ActivityCode.STOP_ACTIVITY)
                stopAllTrackers()
            }
            ActivityCode.PAUSE_ACTIVITY -> {
                runOnUiThread {
                    textStatus.text         = getString(R.string.status_paused)
                    textPpgGreenStatus.text = getString(R.string.status_paused)
                    textPpgIrStatus.text    = getString(R.string.status_paused)
                    textPpgRedStatus.text   = getString(R.string.status_paused)
                }
                stopAllTrackers()
                switchState(ActivityCode.PAUSE_ACTIVITY)
            }
            ActivityCode.DO_NOTHING -> {
                runOnUiThread {
                    Toast.makeText(applicationContext, getString(R.string.no_content), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun handleRequestMessage(msg: Message) {
        if (msg.code == ActivityCode.START_ACTIVITY) {
            Log.i(tag, "State request received from phone")
            switchState(currentState)
        }
    }

    /**
     * DRY handler for individual PPG channel messages.
     * All three channels (Green, IR, Red) share identical start/stop/toggle logic.
     */
    private fun handlePpgMessage(
        msg: Message,
        listener: Listener,
        showContainer: () -> Unit,
        stopStatus: () -> Unit
    ) {
        when (msg.code) {
            ActivityCode.START_ACTIVITY -> {
                runOnUiThread {
                    showContainer()
                    textTip.visibility = View.GONE
                    textStatus.text = getString(R.string.status_running)
                }
                if (msg.extraCode == TOGGLE_ACTIVITY) {
                    toggleTracker(listener)
                    invalidateAppStatus()
                } else {
                    startTracker(listener)
                }
            }
            ActivityCode.STOP_ACTIVITY -> {
                stopTracker(listener)
                runOnUiThread { stopStatus() }
                invalidateAppStatus()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Tracker management
    // -------------------------------------------------------------------------

    private fun startAllTrackers() {
        startTracker(ppgGreenListener)
        startTracker(ppgIrListener)
        startTracker(ppgRedListener)
    }

    private fun stopAllTrackers() {
        ppgGreenListener.stopTracker()
        ppgIrListener.stopTracker()
        ppgRedListener.stopTracker()
    }

    private fun startTracker(listener: Listener) {
        listener.startTracker()
        MainService.startService(this, "Tracker is running...")
    }

    private fun stopTracker(listener: Listener) {
        listener.stopTracker()
    }

    private fun toggleTracker(listener: Listener) {
        if (listener.isTracking()) listener.stopTracker()
        else startTracker(listener)
    }

    /**
     * If all trackers have stopped, resets the UI status labels and
     * stops the foreground [MainService].
     *
     * @return `true` if invalidation occurred (all stopped).
     */
    private fun invalidateAppStatus(): Boolean {
        val allStopped = !ppgGreenListener.isTracking() &&
                         !ppgIrListener.isTracking()    &&
                         !ppgRedListener.isTracking()
        if (allStopped) {
            runOnUiThread {
                textStatus.text         = getString(R.string.status_stopped)
                textPpgGreenStatus.text = getString(R.string.status_stopped)
                textPpgIrStatus.text    = getString(R.string.status_stopped)
                textPpgRedStatus.text   = getString(R.string.status_stopped)
            }
            MainService.stopService(this)
        }
        return allStopped
    }

    // -------------------------------------------------------------------------
    // Data transmission — modern DataClient (replaces deprecated DataApi)
    // -------------------------------------------------------------------------

    /**
     * Serialises a [PpgRecording] window and sends it to the phone
     * via the Wearable Data Layer [DataClient].
     *
     * @param recording The buffer to send.
     * @param actualSize If non-null, overrides the default batch size
     *                   (used for partial flushes on stop).
     */
    private fun sendPpgData(recording: PpgRecording, actualSize: Int? = null) {
        val (path, windowSize) = when (recording.ppgType) {
            PpgType.PPG_GREEN -> MessagePath.DATA_PPG_GREEN to (actualSize ?: PPG_GREEN_BATCH_SIZE)
            PpgType.PPG_IR    -> MessagePath.DATA_PPG_IR    to (actualSize ?: PPG_IR_RED_BATCH_SIZE)
            PpgType.PPG_RED   -> MessagePath.DATA_PPG_RED   to (actualSize ?: PPG_IR_RED_BATCH_SIZE)
        }

        val ppgData = PpgData(windowSize, recording.ppgType).apply {
            for (i in 0 until windowSize) {
                ppgValues[i]  = recording.values[i]
                timestamps[i] = recording.timestamps[i]
                size++
            }
        }

        val bytes = gson.toJson(ppgData).toByteArray()
        lifecycleScope.launch {
            runCatching {
                dataClient.putDataItem(
                    PutDataRequest.create(path).setData(bytes).setUrgent()
                ).await()
            }.onFailure { e ->
                Log.e(tag, "sendPpgData failed for $path: ${e.message}")
            }
        }
        Log.i(tag, "PPG Data sent via DataClient — path=$path size=$windowSize")
    }

    /**
     * Flushes any partial PPG data remaining in all three buffers.
     * Called before stopping trackers to prevent data loss at the end
     * of a recording session.
     */
    private fun flushRemainingData() {
        listOf(ppgGreenRecording, ppgIrRecording, ppgRedRecording).forEach { recording ->
            val remaining = recording.getSize() ?: 0
            if (remaining > 0) {
                sendPpgData(recording, remaining)
                recording.clearFromStartUntil(remaining)
                Log.i(tag, "Flushed $remaining remaining ${recording.ppgType} data points")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Message transmission — modern MessageClient (replaces deprecated MessageApi)
    // -------------------------------------------------------------------------

    /**
     * Sends a [Message] to all connected phone nodes using [MessageClient].
     *
     * Runs in a coroutine on [lifecycleScope] so it never blocks the UI thread.
     */
    private fun sendMessageToPhone(message: Message, path: String) {
        lifecycleScope.launch {
            runCatching {
                val nodes = nodeClient.connectedNodes.await()
                if (nodes.isEmpty()) {
                    Log.w(tag, "sendMessageToPhone: no connected nodes for path=$path")
                    return@launch
                }
                val bytes = gson.toJson(message).toByteArray()
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, path, bytes).await()
                    Log.i(tag, "Message sent to ${node.displayName} — path=$path msg=$message")
                }
            }.onFailure { e ->
                Log.e(tag, "sendMessageToPhone failed: ${e.message}")
            }
        }
    }

    // -------------------------------------------------------------------------
    // State machine
    // -------------------------------------------------------------------------

    /**
     * Sets [currentState] and notifies the phone of the new state via an
     * INFO message.
     *
     * Supported codes:
     * - [ActivityCode.DO_NOTHING] / 0 — Stopped
     * - [ActivityCode.START_ACTIVITY] / 1 — Running
     * - [ActivityCode.STOP_ACTIVITY] / 2 — Stopped
     * - [ActivityCode.PAUSE_ACTIVITY] / 3 — Paused
     *
     * @param forceCode When provided, sets the state directly.
     *                  When omitted (default), toggles between 0 and 1.
     */
    private fun switchState(forceCode: Int = STATE_TOGGLE) {
        currentState = when {
            forceCode != STATE_TOGGLE -> forceCode
            currentState == 0         -> ActivityCode.START_ACTIVITY
            else                      -> ActivityCode.DO_NOTHING
        }
        val stateMessage = Message(Entity.WEAR_APP, currentState).apply {
            content = "Wear state: $currentState"
        }
        sendMessageToPhone(stateMessage, MessagePath.INFO)
        Log.d(tag, "State changed to: $currentState")
    }

    // -------------------------------------------------------------------------
    // Samsung Health connection bootstrap
    // -------------------------------------------------------------------------

    private fun createConnectionManager() {
        try {
            connectionManager = ConnectionManager(connectionObserver)
            connectionManager.connect(applicationContext)
        } catch (t: Throwable) {
            Log.e(tag, "Failed to create ConnectionManager: ${t.message ?: "unknown error"}")
        }
    }

    // -------------------------------------------------------------------------
    // UI helpers
    // -------------------------------------------------------------------------

    private fun bindViews() {
        textStatus = binding.statusMsg
        textTip    = binding.message

        ppgGreenContainer   = binding.ppgGreenContainer
        textPpgGreenStatus  = binding.ppgGreenStatus
        textPpgGreenNumber  = binding.ppgGreenNumber
        textPpgGreen        = binding.ppgGreen
        textPpgGreenTimestamp = binding.ppgGreenTimestamp

        ppgIrContainer   = binding.ppgIrContainer
        textPpgIrStatus  = binding.ppgIrStatus
        textPpgIrNumber  = binding.ppgIrNumber
        textPpgIr        = binding.ppgIr
        textPpgIrTimestamp = binding.ppgIrTimestamp

        ppgRedContainer   = binding.ppgRedContainer
        textPpgRedStatus  = binding.ppgRedStatus
        textPpgRedNumber  = binding.ppgRedNumber
        textPpgRed        = binding.ppgRed
        textPpgRedTimestamp = binding.ppgRedTimestamp
    }

    private fun resetUi() {
        textStatus.text           = getString(R.string.default_status)
        textTip.text              = getString(R.string.message_placeholder)
        textPpgGreenStatus.text   = getString(R.string.default_status)
        textPpgGreenNumber.text   = getString(R.string.default_value)
        textPpgGreen.text         = getString(R.string.default_value)
        textPpgGreenTimestamp.text = getString(R.string.default_value)
        textPpgIrStatus.text      = getString(R.string.default_status)
        textPpgIrNumber.text      = getString(R.string.default_value)
        textPpgIr.text            = getString(R.string.default_value)
        textPpgIrTimestamp.text   = getString(R.string.default_value)
        textPpgRedStatus.text     = getString(R.string.default_status)
        textPpgRedNumber.text     = getString(R.string.default_value)
        textPpgRed.text           = getString(R.string.default_value)
        textPpgRedTimestamp.text  = getString(R.string.default_value)

        textTip.visibility        = View.VISIBLE
        ppgGreenContainer.visibility = View.GONE
        ppgIrContainer.visibility    = View.GONE
        ppgRedContainer.visibility   = View.GONE
    }

    // DRY: per-container show helpers (used in handlePpgMessage lambdas)
    private fun showPpgGreenContainer() { ppgGreenContainer.visibility = View.VISIBLE }
    private fun showPpgIrContainer()    { ppgIrContainer.visibility    = View.VISIBLE }
    private fun showPpgRedContainer()   { ppgRedContainer.visibility   = View.VISIBLE }

    // DRY: per-channel stopped-status helpers
    private fun setPpgGreenStatus(res: Int) { textPpgGreenStatus.text = getString(res) }
    private fun setPpgIrStatus(res: Int)    { textPpgIrStatus.text    = getString(res) }
    private fun setPpgRedStatus(res: Int)   { textPpgRedStatus.text   = getString(res) }

    // -------------------------------------------------------------------------
    // Companion — constants
    // -------------------------------------------------------------------------

    companion object {
        /** Sentinel value: when passed to [switchState], toggles 0↔1. */
        private const val STATE_TOGGLE = -1
    }
}