package com.example.samplewearmobileapp

import android.Manifest
import android.bluetooth.BluetoothAdapter.*
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.preference.PreferenceManager
import android.provider.DocumentsContract
import android.text.InputType
import android.util.Log
import android.view.*
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.androidplot.xy.XYPlot
import com.example.samplewearmobileapp.BluetoothService.REQUEST_CODE_ENABLE_BLUETOOTH
import com.example.samplewearmobileapp.Constants.ECG_SAMPLE_RATE
import com.example.samplewearmobileapp.Constants.MS_TO_SEC
import com.example.samplewearmobileapp.Constants.PREF_ANALYSIS_VISIBILITY
import com.example.samplewearmobileapp.Constants.PREF_DEVICE_ID
import com.example.samplewearmobileapp.Constants.PREF_ECG_VISIBILITY
import com.example.samplewearmobileapp.Constants.PREF_PATIENT_NAME
import com.example.samplewearmobileapp.Constants.PREF_PPG_GREEN_VISIBILITY
import com.example.samplewearmobileapp.Constants.PREF_PPG_IR_VISIBILITY
import com.example.samplewearmobileapp.Constants.PREF_PPG_RED_VISIBILITY
import com.example.samplewearmobileapp.Constants.PREF_TREE_URI
import com.example.samplewearmobileapp.EcgImager.createImage
import com.example.samplewearmobileapp.constants.Entity.PHONE_APP
import com.example.samplewearmobileapp.constants.MessagePath
import com.example.samplewearmobileapp.constants.codes.ActivityCode
import com.example.samplewearmobileapp.constants.codes.ExtraCode.TOGGLE_ACTIVITY
import com.example.samplewearmobileapp.databinding.ActivityMainBinding
import com.example.samplewearmobileapp.models.*
import com.example.samplewearmobileapp.models.EcgPlotArrays
import com.example.samplewearmobileapp.models.Message
import com.example.samplewearmobileapp.models.PpgPlotArrays
import com.example.samplewearmobileapp.utils.AppUtils
import com.example.samplewearmobileapp.utils.TimestampHelper
import com.example.samplewearmobileapp.utils.UriUtils
import com.google.android.gms.wearable.Node
import com.google.gson.Gson
import com.example.samplewearmobileapp.polar.PolarCallbacks
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApi.DeviceStreamingFeature
import com.polar.sdk.api.PolarBleApiDefaultImpl.defaultImplementation
import com.polar.sdk.api.PolarBleApiDefaultImpl.versionInfo
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarEcgData
import com.polar.sdk.api.model.PolarSensorSetting
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToLong
import kotlin.system.exitProcess

/**
 * Main activity — orchestrates all sensor data collection and UI updates.
 *
 * **Phase 3 refactoring:**
 * - Removed `GoogleApiClient` / deprecated Wearable API → replaced by [WearableClient].
 * - Removed 3 overloaded `toggleState()` functions → delegated to [UiStateManager].
 * - Removed `saveEcgData` / `savePpgData` → delegated to [DataSaver].
 * - Removed `onDataArrived` batch loop → delegated to [PpgDataHandler].
 * - All plotters now receive a [PlotUpdateScheduler] for frame-coalesced redraws.
 * - `Handler.postDelayed` timeout → `lifecycleScope.launch { delay() }`.
 * - Singleton [Gson] in companion object.
 */
class MainActivity : AppCompatActivity(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    WearableClient.Callback {

    // -------------------------------------------------------------------------
    // View binding
    // -------------------------------------------------------------------------
    private lateinit var binding: ActivityMainBinding
    private lateinit var menu: Menu

    // -------------------------------------------------------------------------
    // Plotters & scheduler
    // -------------------------------------------------------------------------
    var ppgGreenPlotter: PpgPlotter? = null
    var ppgIrPlotter: PpgPlotter? = null
    var ppgRedPlotter: PpgPlotter? = null
    var ecgPlotter: EcgPlotter? = null
    var qrsPlotter: QrsPlotter? = null
    var hrPlotter: HrPlotter? = null
    private val plotScheduler = PlotUpdateScheduler()

    // -------------------------------------------------------------------------
    // Collaborators (SRP)
    // -------------------------------------------------------------------------
    private val wearableClient = WearableClient(this)
    private val ppgDataHandler = PpgDataHandler()
    // internal: PolarCallbacks.deviceConnected/Disconnected update UI via uiStateManager
    internal lateinit var uiStateManager: UiStateManager
    private lateinit var dataSaver: DataSaver

    // -------------------------------------------------------------------------
    // Polar (ECG)
    // -------------------------------------------------------------------------
    private var polarApi: PolarBleApi? = null
    private var ecgDisposable: Disposable? = null
    private var qrsDetector: QrsDetector? = null

    // -------------------------------------------------------------------------
    // Wearable state
    // -------------------------------------------------------------------------
    private var connectedNode: List<Node> = emptyList()
    private var wearMessage: Message? = null
    private var appState = 0

    // -------------------------------------------------------------------------
    // Bluetooth
    // -------------------------------------------------------------------------
    private var bluetoothState = STATE_OFF
    private var isBluetoothReceiverRegistered = false

    // -------------------------------------------------------------------------
    // Recording state
    // -------------------------------------------------------------------------
    private enum class SaveType {
        ECG_DATA, PPG_GREEN_DATA, PPG_IR_DATA, PPG_RED_DATA, ALL_PPG, ALL
    }

    private var isRecording = false
    private var isPaused = false
    private var recordingStartMs: Long = 0L
    private var pausedElapsedMs: Long = 0L
    private var timerJob: Job? = null
    private var polarConnectTimeoutJob: Job? = null

    // internal: visible to PolarCallbacks (same :mobile module) — not public API
    internal var isPolarDeviceConnected = false
    internal var isEcgRunning = false
    private var isPpgGreenRunning = false
    private var isPpgIrRunning = false
    private var isPpgRedRunning = false

    private var ppgGreenValueNumber = 0
    private var ppgIrValueNumber = 0
    private var ppgRedValueNumber = 0

    // -------------------------------------------------------------------------
    // Visibility flags (from preferences)
    // -------------------------------------------------------------------------
    private var isPpgGreenVisible = true
    private var isPpgIrVisible = true
    private var isPpgRedVisible = true
    private var isEcgVisible = true
    private var isUsingAnalysis = false

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------
    private var sharedPreferences: SharedPreferences? = null
    // internal: accessible by PolarCallbacks for state updates on connect/disconnect
    internal var deviceId = ""
    internal var deviceFirmware = "NA"
    internal var deviceName = "NA"
    internal var deviceAddress = "NA"
    internal var deviceBatteryLevel = "NA"
    private var stopTime: Date? = null
    private var startTime: Date? = null
    private var deviceStopHr = "NA"
    private var calculatedStopHr = "NA"

    // -------------------------------------------------------------------------
    // View refs
    // -------------------------------------------------------------------------
    private lateinit var textStatusContainerTitle: TextView
    private lateinit var textPpgGreenStatus: TextView
    private lateinit var textPpgIrStatus: TextView
    private lateinit var textPpgRedStatus: TextView
    private lateinit var textEcgStatus: TextView
    private lateinit var ppgContainer: ViewGroup
    private lateinit var ppgGreenPlot: XYPlot
    private lateinit var ppgIrPlot: XYPlot
    private lateinit var ppgRedPlot: XYPlot
    private lateinit var ecgContainer: ViewGroup
    // internal: PolarCallbacks updates textEcgHr from hrNotificationReceived on main thread
    internal lateinit var textEcgHr: TextView
    private lateinit var textEcgInfo: TextView
    private lateinit var textEcgTime: TextView
    private lateinit var ecgPlot: XYPlot
    private lateinit var analysisContainer: ViewGroup
    private lateinit var qrsPlot: XYPlot
    private lateinit var hrPlot: XYPlot

    // -------------------------------------------------------------------------
    // BroadcastReceiver
    // -------------------------------------------------------------------------
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            bluetoothState = intent.getIntExtra(EXTRA_STATE, STATE_OFF)
            when (bluetoothState) {
                STATE_TURNING_OFF -> Toast.makeText(context, "Bluetooth turning off", Toast.LENGTH_SHORT).show()
                STATE_TURNING_ON  -> Toast.makeText(context, "Bluetooth turning on", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Activity result launchers
    // -------------------------------------------------------------------------
    private val openDocumentTreeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        Log.d(TAG, "openDocumentTreeLauncher resultCode=${result.resultCode}")
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        try {
            val treeUri = result.data?.data ?: run {
                AppUtils.errMsg(this, "Failed to get persistent access permissions")
                return@registerForActivityResult
            }
            contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            getPreferences(MODE_PRIVATE).edit()
                .putString(PREF_TREE_URI, treeUri.toString())
                .apply()
            UriUtils.trimPermissions(this, 1)
        } catch (ex: Exception) {
            AppUtils.excMsg(this, "Failed to takePersistableUriPermission", ex)
        }
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _: ActivityResult ->
        val oldDeviceId = deviceId
        deviceId = sharedPreferences!!.getString(PREF_DEVICE_ID, "").toString()
        if (oldDeviceId != deviceId) resetDeviceId(oldDeviceId)
        isPpgGreenVisible = sharedPreferences!!.getBoolean(PREF_PPG_GREEN_VISIBILITY, true)
        isPpgIrVisible    = sharedPreferences!!.getBoolean(PREF_PPG_IR_VISIBILITY, true)
        isPpgRedVisible   = sharedPreferences!!.getBoolean(PREF_PPG_RED_VISIBILITY, true)
        isEcgVisible      = sharedPreferences!!.getBoolean(PREF_ECG_VISIBILITY, true)
        setPlotVisibility()
        isUsingAnalysis   = sharedPreferences!!.getBoolean(PREF_ANALYSIS_VISIBILITY, true)
        setAnalysisVisibility()
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)

        Thread.setDefaultUncaughtExceptionHandler { _, t ->
            Log.e(TAG, "Uncaught exception", t)
            exitProcess(2)
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Permission check
        if (allPermissionsGranted()) setupBluetooth()
        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)

        // View references
        textStatusContainerTitle = binding.statusContainerTitle
        textPpgGreenStatus       = binding.statusPpgGreen
        textPpgIrStatus          = binding.statusPpgIr
        textPpgRedStatus         = binding.statusPpgRed
        textEcgStatus            = binding.statusEcg
        ppgContainer             = binding.ppgContainer
        ppgGreenPlot             = binding.ppgGreenPlot
        ppgIrPlot                = binding.ppgIrPlot
        ppgRedPlot               = binding.ppgRedPlot
        ecgContainer             = binding.ecgContainer
        textEcgHr                = binding.ecgHr
        textEcgInfo              = binding.ecgInfo
        textEcgTime              = binding.ecgTime
        ecgPlot                  = binding.ecgPlot
        analysisContainer        = binding.analysisContainer
        qrsPlot                  = binding.qrsPlot
        hrPlot                   = binding.hrPlot

        // Collaborators
        uiStateManager = UiStateManager(
            this,
            UiStateManager.StatusViews(
                textPpgGreenStatus, textPpgIrStatus, textPpgRedStatus, textEcgStatus
            )
        )
        dataSaver = DataSaver(this)

        // Preferences
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences!!.registerOnSharedPreferenceChangeListener(this)
        isPpgGreenVisible = sharedPreferences!!.getBoolean(PREF_PPG_GREEN_VISIBILITY, true)
        isPpgIrVisible    = sharedPreferences!!.getBoolean(PREF_PPG_IR_VISIBILITY, true)
        isPpgRedVisible   = sharedPreferences!!.getBoolean(PREF_PPG_RED_VISIBILITY, true)
        isEcgVisible      = sharedPreferences!!.getBoolean(PREF_ECG_VISIBILITY, true)
        setPlotVisibility()
        isUsingAnalysis   = sharedPreferences!!.getBoolean(PREF_ANALYSIS_VISIBILITY, false)
        if (!isUsingAnalysis) analysisContainer.visibility = View.GONE
        deviceId          = sharedPreferences!!.getString(PREF_DEVICE_ID, "").toString()

        setLastHr()

        // Initial status text
        uiStateManager.setAllPpgState(PpgUiState.Default)
        uiStateManager.setEcgState(PpgUiState.Default)

        // Bluetooth receiver
        registerReceiver(bluetoothStateReceiver, BluetoothService.BLUETOOTH_STATE_FILTER)
        isBluetoothReceiverRegistered = true

        // Wearable client (modern API — no GoogleApiClient)
        wearableClient.connect(lifecycleScope, this)

        // Click listeners for PPG status cards
        textPpgGreenStatus.setOnClickListener { togglePpgTracker(PpgType.PPG_GREEN) }
        textPpgIrStatus.setOnClickListener    { togglePpgTracker(PpgType.PPG_IR) }
        textPpgRedStatus.setOnClickListener   { togglePpgTracker(PpgType.PPG_RED) }
        textEcgStatus.setOnClickListener {
            if (!isPolarDeviceConnected) connectPolarDevice()
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
    }

    override fun onResume() {
        Log.d(TAG, "onResume")
        super.onResume()

        if (UriUtils.getNPersistedPermissions(this) <= 0) {
            getPreferences(MODE_PRIVATE).edit().putString(PREF_TREE_URI, null).apply()
        }

        polarApi?.foregroundEntered()
        invalidateOptionsMenu()

        // Initialize plotters if not yet done (after layout pass)
        if (ppgGreenPlotter == null) {
            ppgGreenPlot.post {
                ppgGreenPlotter = PpgPlotter(this, ppgGreenPlot, PpgType.PPG_GREEN,
                    plotScheduler, "PPG Green", Color.GREEN, false)
            }
        }
        if (ppgIrPlotter == null) {
            ppgIrPlot.post {
                ppgIrPlotter = PpgPlotter(this, ppgIrPlot, PpgType.PPG_IR,
                    plotScheduler, "PPG Ir", Color.MAGENTA, false)
            }
        }
        if (ppgRedPlotter == null) {
            ppgRedPlot.post {
                ppgRedPlotter = PpgPlotter(this, ppgRedPlot, PpgType.PPG_RED,
                    plotScheduler, "PPG Red", Color.RED, false)
            }
        }
        if (ecgPlotter == null) {
            ecgPlot.post {
                ecgPlotter = EcgPlotter(this, ecgPlot, plotScheduler, "ECG", Color.RED, false)
            }
        }
        if (hrPlotter == null) {
            hrPlot.post { hrPlotter = HrPlotter(this, hrPlot, plotScheduler) }
        }
        if (qrsPlotter == null) {
            qrsPlot.post { qrsPlotter = QrsPlotter(this, qrsPlot, plotScheduler) }
        }

        isUsingAnalysis = sharedPreferences!!.getBoolean(PREF_ANALYSIS_VISIBILITY, false)
        setAnalysisVisibility()

        deviceId = sharedPreferences?.getString(PREF_DEVICE_ID, "").toString()
        Log.d(TAG, "DeviceId=$deviceId")
        if (deviceId.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_device), Toast.LENGTH_SHORT).show()
        } else {
            setupPolar()
            if (polarApi != null && !isPolarDeviceConnected) connectPolarDevice()
        }
    }

    override fun onPause() {
        Log.v(TAG, "onPause")
        super.onPause()
        polarApi?.backgroundEntered()
    }

    override fun onRestart() {
        super.onRestart()
        Log.i(TAG, "onRestart")
        if (!isBluetoothReceiverRegistered) {
            registerReceiver(bluetoothStateReceiver, BluetoothService.BLUETOOTH_STATE_FILTER)
            isBluetoothReceiverRegistered = true
        }
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "onStop")
        if (isBluetoothReceiverRegistered) {
            unregisterReceiver(bluetoothStateReceiver)
            isBluetoothReceiverRegistered = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy")
        wearableClient.disconnect()
        polarApi?.shutDown()
        plotScheduler.cancelAll()
        if (isBluetoothReceiverRegistered) {
            unregisterReceiver(bluetoothStateReceiver)
            isBluetoothReceiverRegistered = false
        }
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        Log.d(TAG, "onBackPressed")
        finish()
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    // =========================================================================
    // WearableClient.Callback — all called on main thread
    // =========================================================================

    override fun onNodeConnected(nodes: List<Node>) {
        connectedNode = nodes
        Log.d(TAG, "Watch connected: ${nodes.map { it.displayName }}")
        uiStateManager.setAllPpgState(PpgUiState.Connected)
        // Request current wear state
        val msg = Message(NAME, ActivityCode.START_ACTIVITY).apply {
            content = "Requesting Wear App current state"
        }
        wearableClient.sendMessage(msg, MessagePath.REQUEST)
    }

    override fun onNodeDisconnected() {
        connectedNode = emptyList()
        Log.d(TAG, "Watch disconnected")
        uiStateManager.setAllPpgState(PpgUiState.Disconnected)
    }

    override fun onPpgDataReceived(type: PpgType, data: PpgData) {
        if (!isRecording || isPaused) return
        val plotter = when (type) {
            PpgType.PPG_GREEN -> ppgGreenPlotter.also { ppgGreenValueNumber += data.size }
            PpgType.PPG_IR    -> ppgIrPlotter.also { ppgIrValueNumber += data.size }
            PpgType.PPG_RED   -> ppgRedPlotter.also { ppgRedValueNumber += data.size }
        }
        ppgDataHandler.process(type, data, plotter)
        uiStateManager.setSinglePpgState(type, PpgUiState.Measuring)
    }

    override fun onHeartDataReceived(data: HeartData) {
        Log.d(TAG, "HR=${data.hr} IBI=${data.ibi} ts=${data.timestamp}")
    }

    override fun onMessageReceived(path: String, message: Message) {
        wearMessage = message
        when (path) {
            MessagePath.COMMAND -> {
                if (message.code == ActivityCode.STOP_ACTIVITY) setAppState(0)
            }
            MessagePath.REQUEST -> Log.d(TAG, "REQUEST from wear: ${message.content}")
            MessagePath.INFO -> when (message.code) {
                ActivityCode.START_ACTIVITY  -> setAppState(1)
                ActivityCode.STOP_ACTIVITY   -> setAppState(0)
                ActivityCode.PAUSE_ACTIVITY  -> uiStateManager.setAllPpgState(PpgUiState.Paused)
                ActivityCode.DO_NOTHING      -> setAppState(0)
                else -> Log.d(TAG, "Unknown ActivityCode: ${message.code}")
            }
            else -> Log.d(TAG, "Unknown message path: $path")
        }
    }

    // =========================================================================
    // Menu
    // =========================================================================

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        this@MainActivity.menu = menu
        menuInflater.inflate(R.menu.main_menu, menu)
        refreshMenuIcons()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.pause -> {
                when {
                    !isRecording -> startRecording()
                    !isPaused    -> pauseRecording()
                    else         -> resumeRecording()
                }
                true
            }
            R.id.stop_recording -> { if (isRecording) stopRecording(); true }
            R.id.save_all           -> { saveDataWithName(SaveType.ALL); true }
            R.id.save_all_ppg_data  -> { saveDataWithName(SaveType.ALL_PPG); true }
            R.id.save_ecg_data      -> { saveDataWithName(SaveType.ECG_DATA); true }
            R.id.save_ppg_green_data -> { saveDataWithName(SaveType.PPG_GREEN_DATA); true }
            R.id.save_ppg_ir_data   -> { saveDataWithName(SaveType.PPG_IR_DATA); true }
            R.id.save_ppg_red_data  -> { saveDataWithName(SaveType.PPG_RED_DATA); true }
            R.id.info               -> { displayPolarInfo(); true }
            R.id.restart_polar_api  -> { restartPolarApi(); true }
            R.id.redo_plot_setup    -> { redoPlotSetup(); true }
            R.id.device_id          -> { selectDeviceId(); true }
            R.id.choose_data_directory -> { chooseDataDirectory(); true }
            R.id.help               -> { showHelp(); true }
            R.id.menu_settings      -> { showSettings(); true }
            else -> false
        }
    }

    private fun refreshMenuIcons() {
        if (!::menu.isInitialized) return
        when {
            isRecording && !isPaused -> {
                menu.findItem(R.id.pause).apply {
                    icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_pause_white_36dp, null)
                    title = "Pause"
                }
                menu.findItem(R.id.stop_recording).isVisible = true
                menu.findItem(R.id.save).isVisible = false
            }
            isRecording && isPaused -> {
                menu.findItem(R.id.pause).apply {
                    icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_play_arrow_white_36dp, null)
                    title = "Resume"
                }
                menu.findItem(R.id.stop_recording).isVisible = true
                menu.findItem(R.id.save).isVisible = false
            }
            else -> {
                menu.findItem(R.id.pause).apply {
                    icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_play_arrow_white_36dp, null)
                    title = "Start"
                }
                menu.findItem(R.id.stop_recording).isVisible = false
                menu.findItem(R.id.save).isVisible = true
            }
        }
    }

    // =========================================================================
    // Recording lifecycle
    // =========================================================================

    private fun startRecording() {
        MobileService.startService(this, "Start recording...")
        setLastHr()
        recordingStartMs = System.currentTimeMillis()
        pausedElapsedMs = 0L
        TimestampHelper.setEcgAnchor(recordingStartMs)
        isRecording = true
        isPaused = false
        setPanBehavior()
        textEcgTime.text = "00:00.000"
        ppgGreenValueNumber = 0; ppgIrValueNumber = 0; ppgRedValueNumber = 0
        ppgGreenPlotter?.clear(); ppgIrPlotter?.clear(); ppgRedPlotter?.clear()
        ecgPlotter?.clear(); qrsPlotter?.clear(); hrPlotter?.clear()
        if (isPolarDeviceConnected && ecgDisposable == null) { toggleEcgStream(); isEcgRunning = true }
        togglePpgTracker()
        startTimer()
        refreshMenuIcons()
    }

    private fun pauseRecording() {
        pausedElapsedMs = getElapsedMs()
        isPaused = true
        stopTimer()
        if (connectedNode.isNotEmpty()) {
            wearableClient.sendMessage(Message(NAME, ActivityCode.PAUSE_ACTIVITY), MessagePath.COMMAND)
        }
        if (ecgDisposable != null) { toggleEcgStream(); isEcgRunning = false }
        uiStateManager.setAllPpgState(PpgUiState.Paused)
        refreshMenuIcons()
        Log.i(TAG, "Paused at ${pausedElapsedMs}ms")
    }

    private fun resumeRecording() {
        recordingStartMs = System.currentTimeMillis()
        isPaused = false
        if (connectedNode.isNotEmpty()) {
            wearableClient.sendMessage(Message(NAME, ActivityCode.START_ACTIVITY), MessagePath.COMMAND)
            uiStateManager.setAllPpgState(PpgUiState.Running)
        }
        if (ecgDisposable == null) { toggleEcgStream(); isEcgRunning = true }
        startTimer()
        refreshMenuIcons()
        Log.i(TAG, "Resumed at ${pausedElapsedMs}ms accumulated")
    }

    private fun stopRecording() {
        val finalElapsedMs = getElapsedMs()
        stopTimer()
        MobileService.stopService(this)
        setLastHr()
        stopTime  = Date()
        startTime = Date(stopTime!!.time - finalElapsedMs)
        isRecording = false
        isPaused = false
        runOnUiThread {
            textEcgTime.text = "00:00.000"
            textStatusContainerTitle.text = getString(R.string.status_container_title)
        }
        setPanBehavior()
        if (ecgDisposable != null) { toggleEcgStream(); isEcgRunning = false }
        togglePpgTracker()
        refreshMenuIcons()
        Log.i(TAG, "Stopped. Total elapsed: ${finalElapsedMs}ms")
    }

    // =========================================================================
    // Timer helpers
    // =========================================================================

    private fun getElapsedMs(): Long =
        pausedElapsedMs + if (!isPaused) System.currentTimeMillis() - recordingStartMs else 0L

    internal data class ElapsedTime(val minutes: Int, val seconds: Int, val millis: Int)

    internal fun decomposeElapsedMs(elapsedMs: Long): ElapsedTime {
        val totalSec = elapsedMs / 1000L
        return ElapsedTime(
            (totalSec / 60L).toInt(),
            (totalSec % 60L).toInt(),
            (elapsedMs % 1000L).toInt()
        )
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            while (isRecording && !isPaused) {
                updateTimerDisplay()
                delay(100L)
            }
        }
    }

    private fun stopTimer() { timerJob?.cancel(); timerJob = null }

    private fun updateTimerDisplay() {
        val (min, sec, ms) = decomposeElapsedMs(getElapsedMs())
        val formatted = getString(R.string.elapsed_time_formatted, min, sec, ms)
        runOnUiThread {
            textEcgTime.text = formatted
            textStatusContainerTitle.text = formatted
        }
    }

    // =========================================================================
    // PPG state helpers
    // =========================================================================

    /** Set all PPG running flags and refresh status UI accordingly. */
    private fun setAllPpgRunning(running: Boolean) {
        isPpgGreenRunning = running; isPpgIrRunning = running; isPpgRedRunning = running
        val state = if (running) PpgUiState.Running else PpgUiState.Stopped
        uiStateManager.setAllPpgState(state)
    }

    /** Toggle a single PPG channel flag and refresh its status UI. */
    private fun setSinglePpgRunning(type: PpgType, running: Boolean) {
        when (type) {
            PpgType.PPG_GREEN -> isPpgGreenRunning = running
            PpgType.PPG_IR    -> isPpgIrRunning = running
            PpgType.PPG_RED   -> isPpgRedRunning = running
        }
        val state = if (running) PpgUiState.Running else PpgUiState.Stopped
        uiStateManager.setSinglePpgState(type, state)
    }

    /** appState field update + derived PPG running flags. */
    private fun setAppState(code: Int) {
        appState = code
        when (code) {
            0 -> setAllPpgRunning(false)
            1 -> setAllPpgRunning(true)
        }
    }

    // =========================================================================
    // Permission results
    // =========================================================================

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_ENABLE_BLUETOOTH) {
            val msg = if (resultCode == RESULT_OK) "Bluetooth enabled." else "Bluetooth has not been enabled."
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        } else {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_PERMISSIONS -> {
                if (allPermissionsGranted()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        BACKGROUND_PERMISSIONS.isNotEmpty() &&
                        ContextCompat.checkSelfPermission(
                            this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        ActivityCompat.requestPermissions(
                            this, BACKGROUND_PERMISSIONS, REQUEST_CODE_BACKGROUND_PERMISSIONS
                        )
                    } else setupBluetooth()
                } else {
                    Toast.makeText(this,
                        "Permissions not granted. Some features may not work.",
                        Toast.LENGTH_LONG).show()
                }
            }
            REQUEST_CODE_BACKGROUND_PERMISSIONS -> setupBluetooth()
        }
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        Log.d(TAG, "onSharedPreferenceChanged: key=$key")
        when (key) {
            PREF_DEVICE_ID -> {
                val newId = prefs?.getString(PREF_DEVICE_ID, "") ?: ""
                if (newId != deviceId) resetDeviceId(deviceId).also { deviceId = newId }
            }
        }
    }

    // =========================================================================
    // Polar (ECG / BLE)
    // =========================================================================

    private fun setupPolar() {
        if (deviceId.isEmpty()) return
        if (polarApi != null) return
        Log.d(TAG, "setupPolar: deviceId=$deviceId")

        // Bug #1 fix: original mask = FEATURE_HR | FEATURE_POLAR_SENSOR_STREAMING (= 9).
        // setLocalTime() — called in toggleEcgStream() — requires FEATURE_POLAR_FILE_TRANSFER (16).
        // Without that bit the setLocalTime RxJava chain errors → ECG stream never starts.
        // Also: FEATURE_DEVICE_INFO (2) and FEATURE_BATTERY_INFO (4) were missing → firmware/battery
        // callbacks never fired. Using ALL_FEATURES enables all 5 bits safely.
        polarApi = defaultImplementation(this, PolarBleApi.ALL_FEATURES)

        // Bug #2 + SRP fix: the 50-line anonymous PolarBleApiCallback has been extracted to
        // PolarCallbacks, which wraps all UI operations in runOnUiThread (thread-safety fix).
        polarApi!!.setApiCallback(PolarCallbacks(this))
        invalidateOptionsMenu()
    }

    private fun restartPolarApi() {
        Log.d(TAG, "restartPolarApi")
        if (ecgDisposable != null) { ecgDisposable!!.dispose(); ecgDisposable = null }
        polarApi?.shutDown()
        polarApi = null
        qrsDetector = null
        setupPolar()
    }

    private fun connectPolarDevice() {
        if (deviceId.isEmpty()) {
            Toast.makeText(this, "Please enter a Polar Device ID in Settings first.", Toast.LENGTH_LONG).show()
            return
        }
        if (polarApi == null) {
            Toast.makeText(this, "Polar API not initialized.", Toast.LENGTH_LONG).show()
            return
        }
        // Coroutine-based timeout (replaces Handler.postDelayed leak)
        polarConnectTimeoutJob?.cancel()
        polarConnectTimeoutJob = lifecycleScope.launch {
            delay(60_000L)
            if (!isPolarDeviceConnected) {
                AppUtils.warnMsg(this@MainActivity, "No connection to $deviceId after 1 minute")
                uiStateManager.setEcgState(PpgUiState.Disconnected)
            }
        }
        try {
            polarApi!!.connectToDevice(deviceId)
            Log.d(TAG, "Connecting to $deviceId...")
            runOnUiThread {
                uiStateManager.setEcgState(PpgUiState.Connecting)
                Toast.makeText(this, "${getString(R.string.connecting)} $deviceId", Toast.LENGTH_SHORT).show()
            }
            setLastHr(); stopTime = Date()
        } catch (ex: PolarInvalidArgument) {
            AppUtils.excMsg(this, "DeviceId=$deviceId ConnectToDevice: Bad argument", ex)
            setLastHr(); stopTime = Date()
        }
        invalidateOptionsMenu()
    }

    private fun toggleEcgStream() {
        if (!isPolarDeviceConnected) { AppUtils.errMsg(this, "Device is not connected yet"); return }
        logEpochInfo("UTC")
        if (ecgDisposable == null) {
            TimestampHelper.resetEcgTimestamps()
            val tz = TimeZone.getTimeZone("UTC")
            val calNow = Calendar.getInstance(tz)
            ecgDisposable = polarApi!!.setLocalTime(deviceId, calNow)
                .andThen(polarApi!!.requestStreamSettings(deviceId, DeviceStreamingFeature.ECG))
                .toFlowable()
                .flatMap { setting: PolarSensorSetting ->
                    polarApi!!.startEcgStreaming(deviceId, setting.maxSettings())
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { polarEcgData: PolarEcgData ->
                        if (qrsDetector == null) qrsDetector = QrsDetector(this@MainActivity)
                        qrsDetector!!.process(polarEcgData)
                    },
                    { t: Throwable ->
                        Log.e(TAG, "ECG Error: ${t.localizedMessage}", t)
                        AppUtils.excMsg(this@MainActivity, "ECG Error", t)
                        ecgDisposable = null
                    },
                    { Log.d(TAG, "ECG streaming complete") }
                )
        } else {
            ecgDisposable?.dispose()
            ecgDisposable = null
            qrsDetector = null
        }
    }

    // =========================================================================
    // PPG control
    // =========================================================================

    private fun togglePpgTracker(ppgType: PpgType? = null) {
        if (connectedNode.isEmpty()) {
            AppUtils.errMsg(this, "Samsung Watch is not connected.")
            return
        }
        when (ppgType) {
            PpgType.PPG_GREEN -> {
                wearableClient.sendMessage(
                    Message(NAME, ActivityCode.START_ACTIVITY, TOGGLE_ACTIVITY),
                    MessagePath.DATA_PPG_GREEN
                )
                setSinglePpgRunning(PpgType.PPG_GREEN, !isPpgGreenRunning)
                return
            }
            PpgType.PPG_IR -> {
                wearableClient.sendMessage(
                    Message(NAME, ActivityCode.START_ACTIVITY, TOGGLE_ACTIVITY),
                    MessagePath.DATA_PPG_IR
                )
                setSinglePpgRunning(PpgType.PPG_IR, !isPpgIrRunning)
                return
            }
            PpgType.PPG_RED -> {
                wearableClient.sendMessage(
                    Message(NAME, ActivityCode.START_ACTIVITY, TOGGLE_ACTIVITY),
                    MessagePath.DATA_PPG_RED
                )
                setSinglePpgRunning(PpgType.PPG_RED, !isPpgRedRunning)
                return
            }
            else -> {}
        }
        if (isRecording) {
            wearableClient.sendMessage(Message(NAME, ActivityCode.START_ACTIVITY), MessagePath.COMMAND)
            setAllPpgRunning(true)
        } else {
            wearableClient.sendMessage(Message(NAME, ActivityCode.STOP_ACTIVITY), MessagePath.COMMAND)
            setAllPpgRunning(false)
        }
    }

    // =========================================================================
    // Save data (delegates to DataSaver)
    // =========================================================================

    /**
     * Shows a filename-input dialog then launches the appropriate save operation(s).
     *
     * **SRP:** This function orchestrates save intent → collects results → shows
     * feedback. File I/O is fully delegated to [DataSaver]; UI feedback is
     * fully delegated to [showSaveResults].
     *
     * **DRY:** All save types funnel through the same result-collection and
     * feedback path — no duplicated toast/dialog logic per save type.
     */
    private fun saveDataWithName(saveType: SaveType) {
        if (Environment.MEDIA_MOUNTED != Environment.getExternalStorageState()) {
            AppUtils.errMsg(this, "External Storage is not available"); return
        }
        val view = layoutInflater.inflate(R.layout.device_id_dialog, null, false)
        val input = view.findViewById<EditText>(R.id.input).apply {
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        }
        AlertDialog.Builder(this, R.style.InverseTheme)
            .setTitle(R.string.filename_dialog_title)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                val filename = input.text.toString()
                val meta = buildSessionMetadata()
                lifecycleScope.launch {
                    // Check tree URI before launching I/O — show error on main thread.
                    if (dataSaver.getTreeUriStr() == null) {
                        AppUtils.errMsg(this@MainActivity, "There is no data directory set")
                        return@launch
                    }
                    // Collect all SaveResults in parallel (awaitAll waits for every
                    // coroutine to finish before surfacing feedback).
                    val results: List<DataSaver.SaveResult> = when (saveType) {
                        SaveType.ECG_DATA -> listOf(
                            dataSaver.saveEcgData(filename, getEcgPlotArrays(), meta)
                        )
                        SaveType.PPG_GREEN_DATA -> listOf(
                            dataSaver.savePpgData(filename, getPpgPlotArrays(ppgGreenPlotter!!), PpgType.PPG_GREEN, meta)
                        )
                        SaveType.PPG_IR_DATA -> listOf(
                            dataSaver.savePpgData(filename, getPpgPlotArrays(ppgIrPlotter!!), PpgType.PPG_IR, meta)
                        )
                        SaveType.PPG_RED_DATA -> listOf(
                            dataSaver.savePpgData(filename, getPpgPlotArrays(ppgRedPlotter!!), PpgType.PPG_RED, meta)
                        )
                        SaveType.ALL_PPG -> awaitAll(
                            async { dataSaver.savePpgData(filename, getPpgPlotArrays(ppgGreenPlotter!!), PpgType.PPG_GREEN, meta) },
                            async { dataSaver.savePpgData(filename, getPpgPlotArrays(ppgIrPlotter!!),   PpgType.PPG_IR,    meta) },
                            async { dataSaver.savePpgData(filename, getPpgPlotArrays(ppgRedPlotter!!),  PpgType.PPG_RED,   meta) }
                        )
                        SaveType.ALL -> awaitAll(
                            async { dataSaver.saveEcgData( filename, getEcgPlotArrays(),                                   meta) },
                            async { dataSaver.savePpgData(filename, getPpgPlotArrays(ppgGreenPlotter!!), PpgType.PPG_GREEN, meta) },
                            async { dataSaver.savePpgData(filename, getPpgPlotArrays(ppgIrPlotter!!),   PpgType.PPG_IR,    meta) },
                            async { dataSaver.savePpgData(filename, getPpgPlotArrays(ppgRedPlotter!!),  PpgType.PPG_RED,   meta) }
                        )
                    }
                    // Back on main thread — show aggregated feedback.
                    showSaveResults(results)
                }
            }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .show()
    }

    /**
     * Surfaces save outcome(s) to the user on the **main thread**.
     *
     * **SRP:** The single authoritative function for post-save UI feedback;
     * no other function in this class should show save confirmations.
     *
     * - All succeeded  → brief [Toast] listing saved filenames.
     * - Any failed     → [AlertDialog] listing failed filenames with causes.
     *   If some succeeded AND some failed, the dialog clearly separates them.
     */
    private fun showSaveResults(results: List<DataSaver.SaveResult>) {
        val successes = results.filterIsInstance<DataSaver.SaveResult.Success>()
        val failures  = results.filterIsInstance<DataSaver.SaveResult.Failure>()

        if (failures.isEmpty()) {
            // All files written — non-blocking Toast (does not interrupt workflow)
            val msg = if (successes.size == 1) {
                "Saved: ${successes.first().fileName}"
            } else {
                "Saved ${successes.size} files:\n" +
                        successes.joinToString("\n") { "• ${it.fileName}" }
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        } else {
            // At least one failure — AlertDialog so the user cannot miss it
            val sb = StringBuilder()
            if (successes.isNotEmpty()) {
                sb.append("Saved ${successes.size} file(s) successfully.\n\n")
            }
            sb.append("Failed to save ${failures.size} file(s):\n")
            failures.forEach { f ->
                sb.append("• ${f.fileName}")
                f.cause?.message?.let { sb.append("\n  ($it)") }
                sb.append("\n")
            }
            AppUtils.errMsg(this, sb.toString().trim())
        }
    }

    private fun buildSessionMetadata(): DataSaver.SessionMetadata = DataSaver.SessionMetadata(
        stopTime        = stopTime ?: Date(),
        startTime       = startTime ?: Date(),
        deviceId        = deviceId,
        deviceName      = deviceName,
        deviceFirmware  = deviceFirmware,
        deviceBatteryLevel = deviceBatteryLevel,
        deviceStopHr    = deviceStopHr,
        calculatedStopHr = calculatedStopHr,
        peakCount       = qrsPlotter?.seriesDataPeaks?.size() ?: 0,
        appVersion      = AppUtils.getVersion(this) ?: "NA"
    )

    // =========================================================================
    // Plot data extraction
    // =========================================================================

    private fun getEcgPlotArrays(): EcgPlotArrays {
        qrsPlotter!!.removeOutOfRangePlotPeakValues()
        val ecgVals   = qrsPlotter!!.seriesDataEcg.getyVals()
        val peakVals  = qrsPlotter!!.seriesDataPeaks.getyVals()
        val peakXVals = qrsPlotter!!.seriesDataPeaks.getxVals()
        val tsVals    = qrsPlotter!!.seriesTimestamp.getyVals()
        val n         = ecgVals.size
        val ecg       = DoubleArray(n)
        val peaks     = BooleanArray(n)
        val timestamps = LongArray(n)
        for (i in 0 until n) {
            ecg[i]        = ecgVals[i].toDouble()
            timestamps[i] = tsVals[i].toLong()
        }
        for (j in 0 until peakVals.size) peaks[peakXVals[j].toInt()] = true
        return EcgPlotArrays(ecg, peaks, timestamps)
    }

    private fun getPpgPlotArrays(plotter: PpgPlotter): PpgPlotArrays {
        val ppgVals = plotter.getDataSeries().getyVals()
        val tsVals  = plotter.getTimestampSeries().getyVals()
        val n       = ppgVals.size
        val ppg     = IntArray(n)
        val ts      = LongArray(n)
        for (i in 0 until n) { ppg[i] = ppgVals[i].toInt(); ts[i] = tsVals[i].toLong() }
        return PpgPlotArrays(ppg, ts)
    }

    // =========================================================================
    // UI helpers
    // =========================================================================

    private fun setLastHr() {
        calculatedStopHr = "NA"
        deviceStopHr     = "NA"
        if (hrPlotter == null) return
        hrPlotter!!.hrSeries1?.takeIf { it.size() > 0 }?.let {
            deviceStopHr = String.format(Locale.US, "%d", it.getyVals().last.toDouble().roundToLong())
        }
        hrPlotter!!.hrSeries2?.takeIf { it.size() > 0 }?.let {
            calculatedStopHr = String.format(Locale.US, "%d", it.getyVals().last.toDouble().roundToLong())
        }
    }

    private fun setPanBehavior() {
        ppgGreenPlotter?.setPanning(!isPpgGreenRunning)
        ppgIrPlotter?.setPanning(!isPpgIrRunning)
        ppgRedPlotter?.setPanning(!isPpgRedRunning)
        ecgPlotter?.setPanning(!isEcgRunning)
        qrsPlotter?.setPanning(!isEcgRunning)
        hrPlotter?.setPanning(!isEcgRunning)
    }

    private fun redoPlotSetup() {
        ppgGreenPlotter?.clear(); ppgIrPlotter?.clear(); ppgRedPlotter?.clear()
        ecgPlotter?.clear(); qrsPlotter?.clear(); hrPlotter?.clear()
        ppgGreenValueNumber = 0; ppgIrValueNumber = 0; ppgRedValueNumber = 0
        listOf(ppgGreenPlot, ppgIrPlot, ppgRedPlot, ecgPlot, qrsPlot, hrPlot).forEach {
            it.invalidate(); it.requestLayout()
        }
        ppgGreenPlot.post {
            ppgGreenPlotter?.setupPlot(); ppgIrPlotter?.setupPlot(); ppgRedPlotter?.setupPlot()
            ecgPlotter?.setupPlot(); qrsPlotter?.setupPlot(); hrPlotter?.setupPlot()
        }
    }

    private fun setPlotVisibility() {
        runOnUiThread {
            ppgGreenPlot.visibility = if (isPpgGreenVisible) View.VISIBLE else View.GONE
            ppgIrPlot.visibility    = if (isPpgIrVisible) View.VISIBLE else View.GONE
            ppgRedPlot.visibility   = if (isPpgRedVisible) View.VISIBLE else View.GONE
            ecgPlot.visibility      = if (isEcgVisible) View.VISIBLE else View.GONE
        }
    }

    private fun setAnalysisVisibility() {
        analysisContainer.visibility = if (isUsingAnalysis) View.VISIBLE else View.GONE
    }

    private fun showHelp() { Log.d(TAG, "showHelp") }

    private fun showSettings() {
        settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun setupBluetooth() {
        BluetoothService.manager = getSystemService(BluetoothManager::class.java)
        BluetoothService.adapter = BluetoothService.manager.adapter
        BluetoothService.enableBluetooth(this, this)
    }

    private fun resetDeviceId(oldDeviceId: String) {
        if (ecgDisposable != null) { ecgDisposable!!.dispose(); ecgDisposable = null }
        try { polarApi?.disconnectFromDevice(oldDeviceId) }
        catch (ex: PolarInvalidArgument) { AppUtils.excMsg(this, "Disconnect failed for $oldDeviceId", ex) }
        polarApi?.shutDown(); polarApi = null; qrsDetector = null
        setupPolar()
    }

    private fun selectDeviceId() { showDeviceIdDialog(null) }

    private fun showDeviceIdDialog(view: View?) {
        val dialog = AlertDialog.Builder(this, R.style.InverseTheme).setTitle(R.string.device_id_item)
        val inflated = layoutInflater.inflate(
            R.layout.device_id_dialog,
            if (view == null) null else view.rootView as ViewGroup, false
        )
        val input = inflated.findViewById<EditText>(R.id.input).also {
            it.inputType = InputType.TYPE_CLASS_TEXT
            deviceId = sharedPreferences?.getString(PREF_DEVICE_ID, "").toString()
            it.setText(deviceId)
        }
        dialog.setView(inflated)
        dialog.setPositiveButton(R.string.ok) { _, _ ->
            val old = deviceId
            deviceId = input.text.toString()
            sharedPreferences!!.edit().putString(PREF_DEVICE_ID, deviceId).apply()
            if (deviceId.isEmpty()) Toast.makeText(this, getString(R.string.no_device), Toast.LENGTH_SHORT).show()
            else if (old != deviceId) resetDeviceId(old)
        }
        dialog.setNegativeButton(R.string.cancel) { d, _ ->
            d.cancel()
            if (deviceId.isEmpty()) Toast.makeText(this, getString(R.string.no_device), Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    private fun chooseDataDirectory() {
        openDocumentTreeLauncher.launch(
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        )
    }

    private fun displayPolarInfo() {
        val sb = StringBuilder()
        sb.append("Polar Device Name: $deviceName\n")
        sb.append("Polar Device Id: $deviceId\n")
        sb.append("Polar Device Address: $deviceAddress\n")
        sb.append("Polar Device Firmware: $deviceFirmware\n")
        sb.append("Polar Device Battery Level: $deviceBatteryLevel\n")
        sb.append("Polar API Connected: ${polarApi != null}\n")
        sb.append("Polar Device Connected: $isPolarDeviceConnected\n")
        sb.append("Recording: $isRecording\n")
        sb.append("Receiving ECG: ${ecgDisposable != null}\n")
        ecgPlotter?.let { p ->
            if (p.getVisibleSeries().getyVals() != null) {
                sb.append("Elapsed: ${getString(R.string.elapsed_time, p.getDataIndex() / ECG_SAMPLE_RATE)}\n")
                sb.append("Points plotted: ${p.getVisibleSeries().getyVals().size}\n")
            }
        }
        sb.append("ECG-App Version: ${AppUtils.getVersion(this)}\n")
        sb.append("Polar BLE API Version: ${versionInfo()}\n")
        sb.append(UriUtils.getRequestedPermissionsInfo(this))
        AppUtils.infoMsg(this, sb.toString())
    }

    // =========================================================================
    // Utility
    // =========================================================================

    private fun logEpochInfo(tz: String) {
        val sdf = SimpleDateFormat("dd:MM:yyyy HH:mm:ss", Locale.US).also {
            it.timeZone = TimeZone.getTimeZone("UTC")
        }
        try {
            val e0 = sdf.parse("01:01:2000 00:00:00")
            val e1 = sdf.parse("01:01:2019 00:00:00")
            if (e0 != null && e1 != null) {
                Log.d(TAG, "epoch=$e0 epoch1=$e1 diff=${e1.time - e0.time} $tz")
            }
        } catch (ex: Exception) { Log.e(TAG, "Error parsing date", ex) }
    }

    // =========================================================================
    // Companion
    // =========================================================================

    companion object {
        private const val TAG = "Mobile.MainActivity"
        private const val NAME = PHONE_APP

        /** Shared Gson instance — eliminates per-call allocation. */
        val GSON: Gson = Gson()

        const val REQUEST_CODE_PERMISSIONS = 10
        const val REQUEST_CODE_BACKGROUND_PERMISSIONS = 11

        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                add(Manifest.permission.BLUETOOTH); add(Manifest.permission.BLUETOOTH_ADMIN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }.toTypedArray()
        

        private val BACKGROUND_PERMISSIONS =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            else emptyArray()
    }
}