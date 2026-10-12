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
import com.example.samplewearmobileapp.Constants.PPG_GREEN_SAMPLE_RATE
import com.example.samplewearmobileapp.Constants.PPG_IR_RED_SAMPLE_RATE
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
import com.example.samplewearmobileapp.models.Message
import com.example.samplewearmobileapp.utils.AppUtils
import com.example.samplewearmobileapp.utils.TimestampHelper
import com.example.samplewearmobileapp.utils.UriUtils
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApi.DeviceStreamingFeature
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl.defaultImplementation
import com.polar.sdk.api.PolarBleApiDefaultImpl.versionInfo
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarEcgData
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarSensorSetting
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(), GoogleApiClient.ConnectionCallbacks,
    SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var client: GoogleApiClient
    private lateinit var menu: Menu
    private val plotUpdateScheduler by lazy { PlotUpdateScheduler() }
    private var isUiActive = false

    internal fun requestPlotRedraw(plot: XYPlot) {
        runOnUiThread {
            if (isUiActive && !isDestroyed) plotUpdateScheduler.requestRedraw(plot)
        }
    }

    var ppgGreenPlotter: PpgPlotter? = null
    var ppgIrPlotter: PpgPlotter? = null
    var ppgRedPlotter: PpgPlotter? = null

//    private lateinit var acquaintedDevices: MutableList<DeviceInfo>
    private var polarApi: PolarBleApi? = null
    private val ecgStream = EcgStreamController()
    private var ecgRetryJob: Job? = null
    private var ecgStoppedAtMs = 0L
    var ecgPlotter: EcgPlotter? = null
    private var qrsDetector: QrsDetector? = null
    var qrsPlotter: QrsPlotter? = null
    var hrPlotter: HrPlotter? = null

    private var connectedNode: List<Node>? = null
//    private var message: Message = Message(PHONE_APP)
    private var wearMessage: Message? = null
    private var appState = 0
    private var bluetoothState = STATE_OFF
    private var isBluetoothReceiverRegistered = false

    private var ppgGreenValueNumber = 0
    private var ppgIrValueNumber = 0
    private var ppgRedValueNumber = 0
    private var accValueNumber = 0
    private val accXList = mutableListOf<Float>()
    private val accYList = mutableListOf<Float>()
    private val accZList = mutableListOf<Float>()
    private val accTimestampList = mutableListOf<Long>()
    private data class DeviceHrSample(val timestamp: Long, val hr: Int, val rrMs: List<Int>)
    private val deviceHrSamples = mutableListOf<DeviceHrSample>()

    /**
     * Whether to save as CSV, Plot, or both.
     */
    private enum class SaveType {
        ECG_DATA, HR_DATA, PPG_GREEN_DATA, PPG_IR_DATA, PPG_RED_DATA, ACCELEROMETER_DATA, ALL_PPG, ALL
//        , PLOT, BOTH
//        , DEVICE_HR, QRS_HR, ALL
    }

    private var isRecording = false
    private var isPaused = false
    private var elapsedSeconds: Long = 0L
    private var timerJob: Job? = null
    private var isPolarDeviceConnected = false
    private var isEcgReady = false
    private val watchSession = WatchSession()
    private val watchControl = WatchControl()
    private val controlHandler = Handler(Looper.getMainLooper())
    private var desiredWatchCommand: Message? = null
    private var watchAcknowledged = false
    private var destroying = false
    private val watchRetry = object : Runnable {
        override fun run() {
            val command = desiredWatchCommand ?: return
            if (destroying) return
            sendMessage(command, MessagePath.COMMAND)
            if (command.code == ActivityCode.START_ACTIVITY || !watchAcknowledged) {
                controlHandler.postDelayed(this, WatchControl.HEARTBEAT_MS)
            }
        }
    }
    private var isRestartingPolar = false
    private var isEcgRunning = false
    private var isPpgGreenRunning = false
    private var isPpgIrRunning = false
    private var isPpgRedRunning = false

    private var isPpgGreenVisible = true
    private var isPpgIrVisible = true
    private var isPpgRedVisible = true
    private var isEcgVisible = true
    private var isUsingAnalysis = false

    private var sharedPreferences: SharedPreferences? = null

    private var deviceId = ""
    private var deviceFirmware = "NA"
    private var deviceName = "NA"
    private var deviceAddress = "NA"
    private var polarConnectedId = ""
    private var deviceBatteryLevel = "NA"

    //* Date when stopped playing. Updated whenever playing started or
    // stopped. */
    private var stopTime: Date? = null
    //* Date when stopped playing. Updated whenever playing started */
    private var startTime: Date? = null

    private var deviceStopHr = "NA"
    private var calculatedStopHr = "NA"

    private lateinit var textStatusContainerTitle: TextView
    private lateinit var textPpgGreenStatus: TextView
    private lateinit var textPpgIrStatus: TextView
    private lateinit var textPpgRedStatus: TextView
    private lateinit var textEcgStatus: TextView
    private lateinit var ppgContainer: ViewGroup
    private lateinit var ppgGreenPlot: XYPlot
    private lateinit var ppgIrPlot: XYPlot
    private lateinit var ppgRedPlot: XYPlot
//    private lateinit var buttonPpgGreen: Button
//    private lateinit var buttonPpgIr: Button
//    private lateinit var buttonPpgRed: Button
    private lateinit var ecgContainer: ViewGroup
    private lateinit var textEcgHr: TextView
    private lateinit var textEcgInfo: TextView
    private lateinit var textEcgTime: TextView
    private lateinit var ecgPlot: XYPlot
    private lateinit var analysisContainer: ViewGroup
    private lateinit var qrsPlot: XYPlot
    private lateinit var hrPlot: XYPlot

//    private lateinit var textWearStatus: TextView
//    private lateinit var textWearHr: TextView
//    private lateinit var textWearIbi: TextView
//    private lateinit var textWearTimestamp: TextView
//    private lateinit var textHrmStatus: TextView
//    private lateinit var textHrmHr: TextView
//    private lateinit var textHrmIbi: TextView
//    private lateinit var textHrmTimestamp: TextView
//    private lateinit var textTooltip: TextView
//    private lateinit var buttonMain: Button
//    private lateinit var buttonView: TextView
//    private lateinit var buttonConnectHrm: TextView
//    private lateinit var containerWear: LinearLayout
//    private lateinit var containerHrm: LinearLayout

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            bluetoothState = intent.getIntExtra(EXTRA_STATE, STATE_OFF)
            when (bluetoothState) {
                STATE_TURNING_OFF -> {
                    Toast.makeText(context,
                        "Bluetooth turning off",
                        Toast.LENGTH_SHORT).show()
                }
                STATE_TURNING_ON -> {
                    Toast.makeText(context,
                        "Bluetooth turning on",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Launcher for opening document tree.
     *
     * Successful launch provides PREF_TREE_URI.
     */
    private val openDocumentTreeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        Log.d(
            TAG, "openDocumentTreeLauncher: result" +
                    ".getResultCode()=" + result.resultCode
        )
        // Find the UID for this application
        Log.d(TAG, "URI=" + UriUtils.getApplicationUid(this))
        Log.d(
            TAG, "Current permissions (initial): "
                    + UriUtils.getNPersistedPermissions(this)
        )
        try {
            if (result.resultCode == RESULT_OK) {
                // Get Uri from Storage Access Framework.
                val treeUri = result.data!!.data
                val editor =
                    getPreferences(MODE_PRIVATE)
                        .edit()
                if (treeUri == null) {
                    editor.putString(PREF_TREE_URI, null)
                    editor.apply()
                    AppUtils.errMsg(
                        this, ("Failed to get " +
                                "persistent " +
                                "access permissions")
                    )
                    return@registerForActivityResult
                }
                // Persist access permissions.
                try {
                    this.contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    // Save the current treeUri as PREF_TREE_URI
                    editor.putString(
                        PREF_TREE_URI,
                        treeUri.toString()
                    )
                    editor.apply()
                    // Trim the persisted permissions
                    UriUtils.trimPermissions(this, 1)
                } catch (ex: java.lang.Exception) {
                    val msg = ("Failed to " +
                            "takePersistableUriPermission for "
                            + treeUri.path)
                    AppUtils.excMsg(this, msg, ex)
                }
                Log.d(TAG, ("Current permissions (final): "
                        + UriUtils.getNPersistedPermissions(this))
                )
            }
        } catch (ex: java.lang.Exception) {
            Log.e(
                TAG, "Error in openDocumentTreeLauncher: " +
                        "startActivity for result", ex
            )
        }
    }

    // Launcher for Settings
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        var code = "Unknown"
        if (result.resultCode == RESULT_OK) {
            code = "RESULT_OK"
        } else if (result.resultCode == RESULT_CANCELED) {
            code = "RESULT_CANCELED"
        }
        // PREF_DEVICE_ID
        val oldDeviceId: String = deviceId
        deviceId = sharedPreferences!!.getString(
            PREF_DEVICE_ID,
            ""
        ).toString()
        Log.d(
            TAG, "settingsLauncher: resultCode=" + code
                    + " oldDeviceId=" + oldDeviceId
                    + " DeviceId=" + deviceId
        )
        if (oldDeviceId != deviceId) {
            resetDeviceId(oldDeviceId)
        }
        // setup plot visibility preferences
        isPpgGreenVisible = sharedPreferences!!.getBoolean(
            PREF_PPG_GREEN_VISIBILITY, true
        )
        isPpgIrVisible = sharedPreferences!!.getBoolean(
            PREF_PPG_IR_VISIBILITY, true
        )
        isPpgRedVisible = sharedPreferences!!.getBoolean(
            PREF_PPG_RED_VISIBILITY, true
        )
        isEcgVisible = sharedPreferences!!.getBoolean(
            PREF_ECG_VISIBILITY, true
        )
        setPlotVisibility()

        // PREF_ANALYSIS_VISIBILITY
        isUsingAnalysis = sharedPreferences!!.getBoolean(
            PREF_ANALYSIS_VISIBILITY, true
        )
        setAnalysisVisibility()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, this.javaClass.simpleName + " onCreate")
        super.onCreate(savedInstanceState)

        // Capture global exceptions
        Thread.setDefaultUncaughtExceptionHandler { _, paramThrowable ->
            Log.e(TAG, "Unexpected exception :", paramThrowable)
            // Any non-zero exit code
            exitProcess(2)
        }

        // Keep the screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // request for permissions
        if (allPermissionsGranted()) {
            // start Bluetooth
            setupBluetooth()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

//        message.sender = Entity.PHONE_APP

        // set UI vars
        textStatusContainerTitle = binding.statusContainerTitle
        textPpgGreenStatus = binding.statusPpgGreen
        textPpgIrStatus = binding.statusPpgIr
        textPpgRedStatus = binding.statusPpgRed
        textEcgStatus = binding.statusEcg
        ppgContainer = binding.ppgContainer
        ppgGreenPlot = binding.ppgGreenPlot
        ppgIrPlot = binding.ppgIrPlot
        ppgRedPlot = binding.ppgRedPlot
//        buttonPpgGreen = binding.buttonPpgGreen
//        buttonPpgIr = binding.buttonPpgIr
//        buttonPpgRed = binding.buttonPpgRed
        ecgContainer = binding.ecgContainer
        textEcgHr = binding.ecgHr
        textEcgInfo = binding.ecgInfo
        textEcgTime = binding.ecgTime
        ecgPlot = binding.ecgPlot
        analysisContainer = binding.analysisContainer
        qrsPlot = binding.qrsPlot
        hrPlot = binding.hrPlot

//        textWearStatus = binding.wearStatus
//        textWearHr = binding.wearHr
//        textWearIbi = binding.wearIbi
//        textWearTimestamp = binding.wearTime
//        textHrmStatus = binding.hrmStatus
//        textHrmHr = binding.hrmHr
//        textHrmIbi = binding.hrmIbi
//        textHrmTimestamp = binding.hrmTime
//        textTooltip = binding.buttonTooltip
//        buttonMain = binding.buttonMain
//        buttonView = binding.buttonView
//        buttonConnectHrm = binding.hrmConnect
//        containerWear = binding.wearContainer
//        containerHrm = binding.hrmContainer

//        runOnUiThread {
//            textTooltip.text = getString(R.string.click_me_text)
//            buttonMain.text = getString(R.string.button_start)
//            buttonView.visibility = View.GONE
//        }

        runOnUiThread {
            textPpgGreenStatus.text = getString(R.string.ppg_green_status,
                getString(R.string.status_default))
            textPpgIrStatus.text = getString(R.string.ppg_ir_status,
                getString(R.string.status_default))
            textPpgRedStatus.text = getString(R.string.ppg_red_status,
                getString(R.string.status_default))
            textEcgStatus.text = getString(R.string.ecg_status,
                getString(R.string.status_default))
        }

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        // register preference listener
        sharedPreferences!!.registerOnSharedPreferenceChangeListener(this)

        // get visibility preferences
        isPpgGreenVisible = sharedPreferences!!.getBoolean(
            PREF_PPG_GREEN_VISIBILITY, true)
        isPpgIrVisible = sharedPreferences!!.getBoolean(
            PREF_PPG_IR_VISIBILITY, true)
        isPpgRedVisible = sharedPreferences!!.getBoolean(
            PREF_PPG_RED_VISIBILITY, true)
        isEcgVisible = sharedPreferences!!.getBoolean(
            PREF_ECG_VISIBILITY, true)
        setPlotVisibility()
        isUsingAnalysis = sharedPreferences!!.getBoolean(
            PREF_ANALYSIS_VISIBILITY, false)
        if (!isUsingAnalysis) {
            analysisContainer.visibility = View.GONE
        }

        setLastHr()
//        stopTime = Date()

        // get device ID from preferences if there is any
        deviceId = sharedPreferences!!.getString(PREF_DEVICE_ID, "").toString()
        Log.d(TAG, "DeviceId=$deviceId")

        // register bluetooth state broadcast receiver
        registerReceiver(bluetoothStateReceiver, BluetoothService.BLUETOOTH_STATE_FILTER)
        isBluetoothReceiverRegistered = true

        // build Google API Client with access to Wearable API
        client = GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .addConnectionCallbacks(this)
            .build()
        client.connect()

        // set click listener
        textPpgGreenStatus.setOnClickListener {
            togglePpgTracker(PpgType.PPG_GREEN)
        }
        textPpgIrStatus.setOnClickListener {
            togglePpgTracker(PpgType.PPG_IR)
        }
        textPpgRedStatus.setOnClickListener {
            togglePpgTracker(PpgType.PPG_RED)
        }
        textEcgStatus.setOnClickListener {
            if (!isPolarDeviceConnected) {
                connectPolarDevice()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        this@MainActivity.menu = menu
        menuInflater.inflate(R.menu.main_menu, menu)
        if (isRecording && !isPaused) {
            menu.findItem(R.id.pause).icon = ResourcesCompat.getDrawable(
                resources, R.drawable.ic_pause_white_36dp, null
            )
            menu.findItem(R.id.pause).title = "Pause"
            menu.findItem(R.id.stop_recording).isVisible = true
            menu.findItem(R.id.save).isVisible = false
        } else if (isRecording && isPaused) {
            menu.findItem(R.id.pause).icon = ResourcesCompat.getDrawable(
                resources, R.drawable.ic_play_arrow_white_36dp, null
            )
            menu.findItem(R.id.pause).title = "Resume"
            menu.findItem(R.id.stop_recording).isVisible = true
            menu.findItem(R.id.save).isVisible = false
        } else {
            menu.findItem(R.id.pause).icon = ResourcesCompat.getDrawable(
                resources, R.drawable.ic_play_arrow_white_36dp, null
            )
            menu.findItem(R.id.pause).title = "Start"
            menu.findItem(R.id.stop_recording).isVisible = false
            menu.findItem(R.id.save).isVisible = true
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.pause) {
            if (!isRecording) {
                // === START RECORDING ===
                MobileService.startService(this, "Start recording...")
                setLastHr()
                startTime = Date(maxOf(System.currentTimeMillis(), watchSession.sessionId + 1L))
                stopTime = Date()
                watchSession.start(startTime!!.time)
                deviceHrSamples.clear()
                qrsDetector = null
                isRecording = true
                isPaused = false
                elapsedSeconds = 0L
                setPanBehavior()
                updateTimerDisplay()
                textEcgTime.text = getString(R.string.elapsed_time, 0.0)
                // Clear the plots and accelerometer buffers
                ppgGreenValueNumber = 0
                ppgIrValueNumber = 0
                ppgRedValueNumber = 0
                accValueNumber = 0
                accXList.clear()
                accYList.clear()
                accZList.clear()
                accTimestampList.clear()
                ppgGreenPlotter?.clear()
                ppgIrPlotter?.clear()
                ppgRedPlotter?.clear()
                ecgPlotter?.clear()
                qrsPlotter?.clear()
                hrPlotter?.clear()
                // Start ECG stream only if Polar device is connected
                ecgStream.resetFailures()
                if (!isPolarDeviceConnected && polarApi != null) connectPolarDevice()
                startEcgStream()
                togglePpgTracker()
                startTimer()
                // Update menu icons
                menu.findItem(R.id.pause).icon = ResourcesCompat.getDrawable(
                    resources, R.drawable.ic_pause_white_36dp, null
                )
                menu.findItem(R.id.pause).title = "Pause"
                menu.findItem(R.id.stop_recording).isVisible = true
                menu.findItem(R.id.save).isVisible = false
            } else if (!isPaused) {
                // === PAUSE RECORDING ===
                pauseRecording()
                menu.findItem(R.id.pause).icon = ResourcesCompat.getDrawable(
                    resources, R.drawable.ic_play_arrow_white_36dp, null
                )
                menu.findItem(R.id.pause).title = "Resume"
            } else {
                // === RESUME RECORDING ===
                resumeRecording()
                menu.findItem(R.id.pause).icon = ResourcesCompat.getDrawable(
                    resources, R.drawable.ic_pause_white_36dp, null
                )
                menu.findItem(R.id.pause).title = "Pause"
            }
            return true
        } else if (id == R.id.stop_recording) {
            // === STOP RECORDING ===
            if (isRecording) {
                stopRecording()
            }
            return true
        } else if (id == R.id.save_device_hr_data) {
            saveDataWithName(SaveType.HR_DATA)
            return true
        } else if (id == R.id.save_all) {
            saveDataWithName(SaveType.ALL)
            return true
        }
        else if (id == R.id.save_all_ppg_data) {
            saveDataWithName(SaveType.ALL_PPG)
            return true
        }
        else if (id == R.id.save_ecg_data) {
            saveDataWithName(SaveType.ECG_DATA)
            return true
        }
        else if (id == R.id.save_ppg_green_data) {
            saveDataWithName(SaveType.PPG_GREEN_DATA)
            return true
        }
        else if (id == R.id.save_ppg_ir_data) {
            saveDataWithName(SaveType.PPG_IR_DATA)
            return true
        }
        else if (id == R.id.save_ppg_red_data) {
            saveDataWithName(SaveType.PPG_RED_DATA)
            return true
        }
        else if (id == R.id.save_accelerometer_data) {
            saveDataWithName(SaveType.ACCELEROMETER_DATA)
            return true
        }
//        else if (id == R.id.save_plot) {
//            saveDataWithNote(SaveType.PLOT)
//            return true
//        } else if (id == R.id.save_data) {
//            saveDataWithNote(SaveType.DATA)
//            return true
//        } else if (id == R.id.save_both) {
//            saveDataWithNote(SaveType.BOTH)
//            return true
//        }
        else if (id == R.id.info) {
            displayPolarInfo()
            return true
        } else if (id == R.id.restart_polar_api) {
            restartPolarApi()
            return true
        } else if (id == R.id.redo_plot_setup) {
            redoPlotSetup()
            return true
        } else if (id == R.id.device_id) {
            selectDeviceId()
            return true
        } else if (id == R.id.choose_data_directory) {
            chooseDataDirectory()
            return true
        } else if (id == R.id.help) {
            showHelp()
            return true
        } else if (item.itemId == R.id.menu_settings) {
            showSettings()
            return true
        }
        return false
    }

    // =========================================================================
    // Timer & Recording State Helpers
    // =========================================================================

    /**
     * Starts the coroutine-based elapsed timer.
     * Increments [elapsedSeconds] every second while
     * [isRecording] is true and [isPaused] is false.
     */
    private fun startTimer() {
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            while (isRecording && !isPaused) {
                delay(1000L)
                if (isRecording && !isPaused) {
                    elapsedSeconds++
                    updateTimerDisplay()
                }
            }
        }
    }

    /**
     * Cancels the running timer coroutine.
     */
    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    /**
     * Updates the elapsed time display on the UI
     * using [elapsedSeconds].
     */
    private fun updateTimerDisplay() {
        val minutes = (elapsedSeconds / 60).toInt()
        val seconds = (elapsedSeconds % 60).toInt()
        runOnUiThread {
            textStatusContainerTitle.text = getString(
                R.string.elapsed_time_formatted, minutes, seconds
            )
            textEcgTime.text = getString(
                R.string.elapsed_time_formatted, minutes, seconds
            )
        }
    }

    /**
     * Pauses the current recording session.
     * Stops the timer, pauses data streams on wear,
     * and pauses ECG stream.
     */
    private fun pauseRecording() {
        isPaused = true
        MobileService.startService(this, "Paused")
        stopTimer()
        // Send pause command to wear
        requestWatchControl(ActivityCode.PAUSE_ACTIVITY)
        // Pause ECG stream
        stopEcgStream()
        Log.i(TAG, "Recording paused at ${elapsedSeconds}s")
    }

    /**
     * Resumes the recording session from paused state.
     * Restarts the timer (continuing from current [elapsedSeconds]),
     * resumes data streams on wear, and resumes ECG stream.
     */
    private fun resumeRecording() {
        isPaused = false
        MobileService.startService(this, "Recording data")
        // Send resume (start) command to wear
        requestWatchControl(ActivityCode.START_ACTIVITY)
        // Resume ECG stream
        ecgStream.resetFailures()
        if (!isPolarDeviceConnected && polarApi != null) connectPolarDevice()
        startEcgStream()
        startTimer()
        Log.i(TAG, "Recording resumed at ${elapsedSeconds}s")
    }

    /**
     * Fully stops the recording session.
     * Stops timer, stops all data streams, updates UI.
     */
    private fun stopRecording() {
        stopTimer()
        MobileService.stopService(this)
        setLastHr()
        stopTime = Date()
        isRecording = false
        isPaused = false
        setPanBehavior()
        // Stop ECG stream
        stopEcgStream()
        // Keep accepting this session's final Watch batches after Stop.
        watchSession.requestStop()
        togglePpgTracker()
        // Update menu
        menu.findItem(R.id.pause).icon = ResourcesCompat.getDrawable(
            resources, R.drawable.ic_play_arrow_white_36dp, null
        )
        menu.findItem(R.id.pause).title = "Start"
        menu.findItem(R.id.stop_recording).isVisible = false
        menu.findItem(R.id.save).isVisible = true
        Log.i(TAG, "Recording stopped. Total elapsed: ${elapsedSeconds}s")
    }

    private fun invalidatePpgState() {
        runOnUiThread {
            textPpgGreenStatus.text = getString(R.string.ppg_green_status,
                getString(if (isPpgGreenRunning) R.string.status_running else R.string.status_stopped))
            textPpgIrStatus.text = getString(R.string.ppg_ir_status,
                getString(if (isPpgIrRunning) R.string.status_running else R.string.status_stopped))
            textPpgRedStatus.text = getString(R.string.ppg_red_status,
                getString(if (isPpgRedRunning) R.string.status_running else R.string.status_stopped))
        }
    }

    override fun onConnected(p0: Bundle?) {
        Wearable.NodeApi.getConnectedNodes(client).setResultCallback {
            connectedNode = it.nodes
            Log.d(TAG,"Node connected")

//            runOnUiThread {
//                textWearStatus.text = getString(R.string.status_connected)
//            }

            runOnUiThread {
                textPpgGreenStatus.text = getString(R.string.ppg_green_status,
                    getString(if (connectedNode.isNullOrEmpty()) R.string.status_disconnected else R.string.status_connected))
                textPpgIrStatus.text = getString(R.string.ppg_ir_status,
                    getString(if (connectedNode.isNullOrEmpty()) R.string.status_disconnected else R.string.status_connected))
                textPpgRedStatus.text = getString(R.string.ppg_red_status,
                    getString(if (connectedNode.isNullOrEmpty()) R.string.status_disconnected else R.string.status_connected))
            }

            // request Wear App current state
            // request on connection to ensure message is sent
            val message = Message(NAME, ActivityCode.START_ACTIVITY)
            message.content = "Requesting Wear App current state"
//            message.code = ActivityCode.START_ACTIVITY
            sendMessage(message, MessagePath.REQUEST)
        }
        Wearable.MessageApi.addListener(client) { messageEvent ->
            val path = messageEvent.path
            val bytes = messageEvent.data.copyOf()
            runOnUiThread {
                try {
                    wearMessage = Gson().fromJson(String(bytes, Charsets.UTF_8), Message::class.java)
                    onMessageArrived(path)
                } catch (error: RuntimeException) {
                    Log.w(TAG, "Rejected malformed Watch message", error)
                }
            }
        }
        Wearable.DataApi.addListener(client) { data ->
//            Log.d(TAG, "Data count arrived : ${data.count}")
            for (dataEvent in data) {
                Log.d(TAG, "Data Event\n" +
                        "URI Last Path Segment: ${dataEvent.dataItem.uri.lastPathSegment}\n" +
                        "URI Path: ${dataEvent.dataItem.uri.path}\n" +
                        "URI encoded path: ${dataEvent.dataItem.uri.encodedPath}\n" +
                        "URI Host: ${dataEvent.dataItem.uri.host}")
                onDataArrived(dataEvent)
            }
        }.setResultCallback { status ->
            if (status.isSuccess) {
                Wearable.DataApi.getDataItems(client).setResultCallback { items ->
                    try {
                        for (item in items) {
                            val uri = item.uri
                            val bytes = item.data?.copyOf() ?: continue
                            runOnUiThread { ingestWatchBatch(uri, bytes) }
                        }
                    } finally {
                        items.release()
                    }
                }
            } else Log.w(TAG, "Watch data listener registration failed")
        }
    }

    override fun onConnectionSuspended(p0: Int) {
        connectedNode = null
//        runOnUiThread {
//            textWearStatus.text = getString(R.string.status_disconnected)
//        }
        runOnUiThread {
            textPpgGreenStatus.text = getString(R.string.ppg_green_status,
                getString(R.string.status_disconnected))
            textPpgIrStatus.text = getString(R.string.ppg_ir_status,
                getString(R.string.status_disconnected))
            textPpgRedStatus.text = getString(R.string.ppg_red_status,
                getString(R.string.status_disconnected))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_ENABLE_BLUETOOTH) {
            when (resultCode) {
                RESULT_OK -> {
                    Toast.makeText(this,
                        "Bluetooth enabled.",
                        Toast.LENGTH_SHORT).show()
                }
                RESULT_CANCELED -> {
                    Toast.makeText(this,
                        "Bluetooth has not been enabled.",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
        else super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_PERMISSIONS -> {
                if (allPermissionsGranted()) {
                    // Check if we need to request background location separately
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && 
                        BACKGROUND_PERMISSIONS.isNotEmpty() &&
                        ContextCompat.checkSelfPermission(
                            this, 
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED) {
                        // Request background location permission
                        ActivityCompat.requestPermissions(
                            this, 
                            BACKGROUND_PERMISSIONS, 
                            REQUEST_CODE_BACKGROUND_PERMISSIONS
                        )
                    } else {
                        // All permissions granted, setup Bluetooth
                        setupBluetooth()
                    }
                } else {
                    Toast.makeText(this,
                        "Permissions not granted. Some features may not work. " +
                        "You can grant permissions in Settings.",
                        Toast.LENGTH_LONG).show()
                }
            }
            REQUEST_CODE_BACKGROUND_PERMISSIONS -> {
                // Background location permission result
                if (grantResults.isNotEmpty() && 
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Background location permission granted")
                } else {
                    Toast.makeText(this,
                        "Background location permission denied. Some features may be limited.",
                        Toast.LENGTH_LONG).show()
                }
                // Continue with Bluetooth setup regardless
                setupBluetooth()
            }
        }
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences?,
        key: String
    ) {
        Log.d(TAG, "onSharedPreferenceChanged: key=$key")
    }

    /**
     * Tries to disconnect deviceId from the current polarAPI and restarts the polarAPI.
     */
    private fun restartPolarApi() {
        Log.d(TAG, this.javaClass.simpleName + " restartApi:")
        // Debounce: prevent rapid taps
        if (isRestartingPolar) {
            Log.w(TAG, "Polar API restart already in progress, ignoring")
            return
        }
        isRestartingPolar = true
        if (isRecording) stopRecording()
        isEcgReady = false

        // Step 1: Reset state flags
        isPolarDeviceConnected = false
        isEcgRunning = false

        // Step 2: Dispose ECG stream
        stopEcgStream()

        // Step 3: Disconnect + Shutdown old API
        if (polarApi != null) {
            try {
                polarApi!!.disconnectFromDevice(deviceId)
            } catch (ex: Exception) {
                Log.e(TAG, "restartPolarApi: disconnect error", ex)
            }
            polarApi!!.shutDown()
            polarApi = null
        }
        qrsDetector = null

        runOnUiThread {
            textEcgStatus.text = getString(R.string.ecg_status,
                getString(R.string.status_connecting))
        }

        // Step 4: Wait 3s for BLE stack to fully release, then re-init + connect
        lifecycleScope.launch {
            delay(3000)
            try {
                setupPolar()
                connectPolarDevice()
            } catch (ex: Exception) {
                Log.e(TAG, "Error reconnecting after Polar API restart", ex)
                runOnUiThread {
                    AppUtils.excMsg(this@MainActivity,
                        "Error reconnecting Polar API", ex)
                }
            } finally {
                isRestartingPolar = false
            }
        }
    }

    /**
     * Setup Polar ECG Part of the application.
     * Use for first time initialization and reinitializing
     * the Polar API, connection, and plot setup.
     */
    private fun setupPolar() {
        Log.d(
            TAG, this.javaClass.simpleName + " restart:"
                    + " PolarApi=" + polarApi
                    + " DeviceId=" + deviceId
        )
        if (polarApi != null || deviceId == null || deviceId.isEmpty()) {
            return
        }
        stopEcgStream()
        isEcgReady = false
        isEcgRunning = false
        textEcgHr.text = ""
        textEcgInfo.text = ""
        textEcgTime.text = ""
        invalidateOptionsMenu()

//        // Don't use SDK if BT is not enabled or permissions are not granted.
//        if (!mBleSupported) return
        if (!allPermissionsGranted()) {
//            if (!mAllPermissionsAsked) {
//                mAllPermissionsAsked = true
//                Utils.warnMsg(this, getString(R.string.permission_not_granted))
//            }
//            return
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
            return
        }

        // setup Polar API
        polarApi = defaultImplementation(
            this,
            PolarBleApi.FEATURE_POLAR_SENSOR_STREAMING or
                    PolarBleApi.FEATURE_BATTERY_INFO or
                    PolarBleApi.FEATURE_DEVICE_INFO or
                    PolarBleApi.FEATURE_POLAR_FILE_TRANSFER or
                    PolarBleApi.FEATURE_HR
        )

        // Post a Runnable to have plots to be setup again in 1 sec

        // setup Polar API Callback
        val configuredApi = polarApi!!
        val configuredId = deviceId
        fun acceptsCallback(identifier: String) = !destroying && polarApi === configuredApi &&
            (identifier.equals(configuredId, true) || identifier.equals(polarConnectedId, true) ||
                identifier.equals(deviceAddress, true))
        configuredApi.setApiCallback(object: PolarBleApiCallback() {
            override fun blePowerStateChanged(powered: Boolean) {
                Log.d(TAG, "BluetoothStateChanged $powered")
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                runOnUiThread {
                    if (destroying || polarApi !== configuredApi ||
                        (!polarDeviceInfo.deviceId.equals(configuredId, true) &&
                            !polarDeviceInfo.address.equals(configuredId, true))) return@runOnUiThread
                    Log.d(TAG, "*Device connected " + polarDeviceInfo.deviceId)
                    deviceAddress = polarDeviceInfo.address
                    polarConnectedId = polarDeviceInfo.deviceId
                    deviceName = polarDeviceInfo.name
                    isPolarDeviceConnected = true
                    textEcgStatus.text = getString(R.string.ecg_status,
                        getString(R.string.status_connected))
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.connected_string, polarDeviceInfo.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    startEcgStream()
                }
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "*Device disconnected $polarDeviceInfo")
                runOnUiThread {
                    if (!acceptsCallback(polarDeviceInfo.deviceId) &&
                        !acceptsCallback(polarDeviceInfo.address)) return@runOnUiThread
                    isPolarDeviceConnected = false
                    isEcgReady = false
                    stopEcgStream()
                    textEcgStatus.text = getString(R.string.ecg_status,
                        getString(R.string.status_disconnected))
                    if (isRecording && !isPaused && ecgStream.failures <= 2) {
                        ecgRetryJob = lifecycleScope.launch {
                            delay(1_000L)
                            if (polarApi === configuredApi && isRecording && !isPaused && !isPolarDeviceConnected) {
                                connectPolarDevice()
                            }
                        }
                    }
                }
            }

            override fun streamingFeaturesReady(
                identifier: String,
                features: Set<DeviceStreamingFeature>
            ) {
                runOnUiThread {
                    if (!acceptsCallback(identifier)) return@runOnUiThread
                    isEcgReady = DeviceStreamingFeature.ECG in features
                    if (isEcgReady) startEcgStream()
                    if (!isEcgReady) textEcgStatus.text = "ECG unavailable on this Polar device; HR remains supported"
                }
            }


            override fun hrFeatureReady(identifier: String) {
                Log.d(TAG, "*HR Feature ready $identifier")
            }

            override fun disInformationReceived(
                identifier: String,
                uuid: UUID,
                value: String
            ) {
                runOnUiThread {
                    if (!acceptsCallback(identifier)) return@runOnUiThread
                    if (uuid == UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")) {
                        deviceFirmware = value.trim { it <= ' ' }
                        textEcgInfo.text = getString(R.string.info_string,
                            deviceName, deviceBatteryLevel, deviceFirmware, deviceId)
                    }
                }
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                runOnUiThread {
                    if (!acceptsCallback(identifier)) return@runOnUiThread
                    deviceBatteryLevel = level.toString()
                    textEcgInfo.text = getString(R.string.info_string,
                        deviceName, deviceBatteryLevel, deviceFirmware, deviceId)
                }
            }

            override fun hrNotificationReceived(
                identifier: String,
                data: PolarHrData
            ) {
                runOnUiThread {
                    if (!acceptsCallback(identifier) || !isRecording || isPaused || data.hr <= 0 ||
                        (data.contactStatusSupported && !data.contactStatus)) return@runOnUiThread
                    val timestamp = System.currentTimeMillis()
                    val rrMs = if (data.rrAvailable) data.rrsMs.toList() else emptyList()
                    deviceHrSamples.add(DeviceHrSample(timestamp, data.hr, rrMs))
                    textEcgHr.text = data.hr.toString()
                    hrPlotter?.addValues1(timestamp.toDouble(), data.hr.toDouble(), rrMs)
                    hrPlotter?.fullUpdate()
                }
            }


        })
//        // try connect to device
//        try {
//            polarApi!!.connectToDevice(deviceId)
//            isPlaying = true
//            setLastHr()
//            stopTime = Date()
//        } catch (ex: PolarInvalidArgument) {
//            val msg = """
//                DeviceId=$deviceId
//                ConnectToDevice: Bad argument:
//                """.trimIndent()
//            AppUtils.excMsg(this, msg, ex)
//            Log.d(TAG, "restart: $msg")
//            isPlaying = false
//            setLastHr()
//            stopTime = Date()
//        }
        invalidateOptionsMenu()
    }

    private fun connectPolarDevice() {
        if (deviceId.isNullOrEmpty()) {
            Toast.makeText(this,
                "Please enter a Polar Device ID in Settings first.",
                Toast.LENGTH_LONG).show()
            return
        }
        if (polarApi == null) {
            Toast.makeText(this,
                "Polar API is not initialized. Please enter a valid Device ID in Settings.",
                Toast.LENGTH_LONG).show()
            return
        }
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            if (!isDestroyed && !isFinishing && !isPolarDeviceConnected) {
                AppUtils.warnMsg(
                    this@MainActivity, "No connection to " + deviceId
                            + " after 1 minute"
                )
                runOnUiThread {
                    textEcgStatus.text = getString(R.string.ecg_status,
                        getString(R.string.status_disconnected))
                }
            }
        }, 60000)

        // try connect to device
        try {
            polarApi!!.connectToDevice(deviceId)
            Log.d(TAG, "Connecting to Polar device...\n" +
                    "DeviceId: $deviceId")
            runOnUiThread {
                textEcgStatus.text = getString(R.string.ecg_status,
                    getString(R.string.status_connecting))
                Toast.makeText(
                    this,
                    getString(R.string.connecting) + " " + deviceId,
                    Toast.LENGTH_SHORT
                ).show()
            }
//            isPlaying = true
            setLastHr()
            stopTime = Date()
        } catch (ex: PolarInvalidArgument) {
            val msg = """
                DeviceId=$deviceId
                ConnectToDevice: Bad argument:
                """.trimIndent()
            AppUtils.excMsg(this, msg, ex)
            Log.d(TAG, "connectPolarDevice: $msg")
//            isPlaying = false
            setLastHr()
            stopTime = Date()
        }
        invalidateOptionsMenu()
    }

    override fun onResume() {
        Log.d(TAG, this.javaClass.simpleName + " onResume:")
        super.onResume()
        isUiActive = true

        // Check if PREF_TREE_URI is valid and remove it if not
        if (UriUtils.getNPersistedPermissions(this) <= 0) {
            val editor = getPreferences(MODE_PRIVATE)
                .edit()
            editor.putString(PREF_TREE_URI, null)
            editor.apply()
        }

        polarApi?.foregroundEntered()
        invalidateOptionsMenu()

        // Setup the plots if not done
        if (ppgGreenPlotter == null) {
            ppgGreenPlot.post {
                ppgGreenPlotter = PpgPlotter(
                    this, ppgGreenPlot, PpgType.PPG_GREEN,
                    "PPG Green", Color.GREEN, false
                )
            }
        }
        if (ppgIrPlotter == null) {
            ppgIrPlot.post {
                ppgIrPlotter = PpgPlotter(
                    this, ppgIrPlot, PpgType.PPG_IR,
                    "PPG Ir", Color.MAGENTA, false
                )
            }
        }
        if (ppgRedPlotter == null) {
            ppgRedPlot.post {
                ppgRedPlotter = PpgPlotter(
                    this, ppgRedPlot, PpgType.PPG_RED,
                    "PPG Red", Color.RED, false
                )
            }
        }
        if (ecgPlotter == null) {
            ecgPlot.post {
                ecgPlotter = EcgPlotter(
                    this, ecgPlot,
                    "ECG", Color.RED, false
                )
            }
        }
        if (hrPlotter == null) {
            hrPlot.post { hrPlotter = HrPlotter(this, hrPlot) }
        }
        if (qrsPlotter == null) {
            qrsPlot.post { qrsPlotter = QrsPlotter(this, qrsPlot) }
        }

        // Set the visibility of the Analysis plot
        isUsingAnalysis = sharedPreferences!!.getBoolean(PREF_ANALYSIS_VISIBILITY, false)
        setAnalysisVisibility()

        // Start the connection to the device
        Log.d(TAG, "DeviceId=$deviceId")
        Log.d(TAG, "polarApi=$polarApi")
        deviceId = sharedPreferences?.getString(PREF_DEVICE_ID, "").toString()
        if (deviceId == null || deviceId.isEmpty()) {
            Toast.makeText(
                this,
                getString(R.string.no_device),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            setupPolar()
            // Auto-connect after setup if API is ready but device not connected
            if (polarApi != null && !isPolarDeviceConnected) {
                connectPolarDevice()
            }
        }
    }

    override fun onPause() {
        Log.v(TAG, this.javaClass.simpleName + " onPause")
        super.onPause()
        isUiActive = false
        plotUpdateScheduler.stop()

        polarApi?.backgroundEntered()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // This seems to be necessary with Android 12
        // Otherwise onDestroy is not called
        Log.d(TAG, this.javaClass.simpleName + ": onBackPressed")
        finish()
        super.onBackPressed()
    }

    override fun onRestart() {
        super.onRestart()
        Log.i(TAG,"Lifecycle: onRestart()")
        // re-register bluetooth state receiver (guard against duplicate registration)
        if (!isBluetoothReceiverRegistered) {
            registerReceiver(bluetoothStateReceiver, BluetoothService.BLUETOOTH_STATE_FILTER)
            isBluetoothReceiverRegistered = true
        }
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "Lifecycle: onStop()")
        // unregister receiver
        if (isBluetoothReceiverRegistered) {
            unregisterReceiver(bluetoothStateReceiver)
            isBluetoothReceiverRegistered = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG,"Lifecycle: onDestroy()")
        stopEcgStream()
        stopTimer()
        MobileService.stopService(this)
        if (isRecording) requestWatchControl(ActivityCode.STOP_ACTIVITY)
        desiredWatchCommand?.takeIf { it.code == ActivityCode.STOP_ACTIVITY && client.isConnected }?.let { command ->
            val bytes = Gson().toJson(command).toByteArray(Charsets.UTF_8)
            connectedNode?.forEach { Wearable.MessageApi.sendMessage(client, it.id, MessagePath.COMMAND, bytes) }
        }
        destroying = true
        controlHandler.removeCallbacks(watchRetry)
        isRecording = false
        plotUpdateScheduler.stop()
        polarApi?.shutDown()
        // Give the final best-effort End time to send; the Watch lease covers lost transport.
        controlHandler.postDelayed({ client.disconnect() }, 500L)
        // unregister receiver
        if (isBluetoothReceiverRegistered) {
            unregisterReceiver(bluetoothStateReceiver)
            isBluetoothReceiverRegistered = false
        }
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    /**
     * Sets the analysis plots visibility using the current value of isUsingAnalysis.
     */
    private fun setAnalysisVisibility() {
        Log.d(TAG, this.javaClass.simpleName + " setAnalysisVisibility: " +
                "$isUsingAnalysis")
        if (isUsingAnalysis) {
            analysisContainer.visibility = View.VISIBLE
        } else {
            analysisContainer.visibility = View.GONE
        }
    }

    /**
     * Show the help.
     */
    private fun showHelp() {
        AppUtils.infoMsg(this, "Install matching phone and Watch versions. Grant Watch Body Sensors permission " +
            "and configure Samsung Health Sensor access. Start records native Watch acceleration and supported " +
            "continuous Green/IR/Red PPG at 25 Hz. Enter a Polar device ID to connect; H10 ECG uses 130 Hz, " +
            "while HR/RR depends on device support. Pause keeps the session; Resume continues it. " +
            "Stop flushes remaining Watch samples. Choose a data directory before saving CSV. " +
            "Pending Watch transfers are marked incomplete if you choose Save partial.")
    }

    /**
     * Calls the settings activity.
     */
    private fun showSettings() {
        Log.d(TAG, "showSettings")
        val intent = Intent(
            this@MainActivity,
            SettingsActivity::class.java
        )
        settingsLauncher.launch(intent)
    }

    /**
     * Checks whether all permissions required has been granted.
     * @return Boolean value of whether all permissions required has been granted
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) ==
                PackageManager.PERMISSION_GRANTED
    }

    /**
     * Setting up Bluetooth Manager and Adapter for use.
     */
    private fun setupBluetooth() {
        // setup bluetooth adapter
        BluetoothService.manager = getSystemService(BluetoothManager::class.java)
        BluetoothService.adapter = BluetoothService.manager.adapter

        // check bluetooth availability
//        if (BluetoothService.adapter == null) {
//            runOnUiThread {
//                textHrmStatus.text = getString(R.string.status_no_support)
//            }
//        }

        BluetoothService.enableBluetooth(this, this)
    }

    /***
     * Sets the last HR from the series in the HR plotter.
     */
    private fun setLastHr() {
        calculatedStopHr = "NA"
        deviceStopHr = "NA"

        if (hrPlotter == null) return
        var lastVal: Long
        if (hrPlotter!!.hrSeries1 != null && hrPlotter!!.hrSeries1!!.size() > 0) {
            lastVal = hrPlotter!!.hrSeries1!!.getyVals().last.toDouble().roundToLong()
            deviceStopHr = String.format(Locale.US, "%d", lastVal)
        }
        if (hrPlotter!!.hrSeries2 != null && hrPlotter!!.hrSeries2!!.size() > 0) {
            lastVal = hrPlotter!!.hrSeries2!!.getyVals().last.toDouble().roundToLong()
            calculatedStopHr = String.format(Locale.US, "%d", lastVal)
        }
    }

    /**
     * Panning while collecting data causes problems. Turn it off when
     * playing and turn it on with stopped. Zooming is not enabled.
     */
    private fun setPanBehavior() {
        ppgGreenPlotter?.setPanning(!isPpgGreenRunning)
        ppgIrPlotter?.setPanning(!isPpgIrRunning)
        ppgRedPlotter?.setPanning(!isPpgRedRunning)
        ecgPlotter?.setPanning(!isEcgRunning)
        qrsPlotter?.setPanning(!isEcgRunning)
        hrPlotter?.setPanning(!isEcgRunning)
    }

    private fun redoPlotSetup() {
        // Reconfigure layout without erasing the recorded session.
        // Force layout recalculation so gridRect is available for setupPlot
        ppgGreenPlot.invalidate()
        ppgIrPlot.invalidate()
        ppgRedPlot.invalidate()
        ecgPlot.invalidate()
        qrsPlot.invalidate()
        hrPlot.invalidate()
        ppgGreenPlot.requestLayout()
        ecgPlot.requestLayout()
        qrsPlot.requestLayout()
        hrPlot.requestLayout()
        // Post setupPlot after layout pass completes
        ppgGreenPlot.post {
            ppgGreenPlotter?.setupPlot()
            ppgIrPlotter?.setupPlot()
            ppgRedPlotter?.setupPlot()
            ecgPlotter?.setupPlot()
            qrsPlotter?.setupPlot()
            hrPlotter?.setupPlot()
        }
    }

    private fun setPlotVisibility() {
        runOnUiThread {
            ppgGreenPlot.visibility = when (isPpgGreenVisible) {
                true -> View.VISIBLE
                false -> View.GONE
            }
            ppgIrPlot.visibility = when (isPpgIrVisible) {
                true -> View.VISIBLE
                false -> View.GONE
            }
            ppgRedPlot.visibility = when (isPpgRedVisible) {
                true -> View.VISIBLE
                false -> View.GONE
            }
            ecgPlot.visibility = when (isEcgVisible) {
                true -> View.VISIBLE
                false -> View.GONE
            }
        }
    }

//    /**
//     * Setup a new device into the shared preferences.
//     * This will remove the earliest device added to the list
//     * if the list is on max capacity.
//     * @param deviceInfo info of the new device
//     */
//    private fun setDevicePreferences(deviceInfo: DeviceInfo) {
//        val editor: SharedPreferences.Editor = sharedPreferences!!.edit()
//        // Remove any found so the new one will be added at the beginning
//        val removeList: MutableList<DeviceInfo> = ArrayList()
//        for (acquaintedDeviceInfo in acquaintedDevices) {
//            if (deviceInfo.name == acquaintedDeviceInfo.name &&
//                deviceInfo.id == acquaintedDeviceInfo.id) {
//                removeList.add(acquaintedDeviceInfo)
//            }
//        }
//        for (device in removeList) {
//            acquaintedDevices.remove(device)
//        }
//        // Remove at end if size exceed max
//        if (acquaintedDevices.size != 0 && acquaintedDevices.size == MAX_DEVICES) {
//            acquaintedDevices.removeAt(acquaintedDevices.size - 1)
//        }
//        // Add at the beginning
//        acquaintedDevices.add(0, deviceInfo)
//        val gson = Gson()
//        val json = gson.toJson(acquaintedDevices)
//        editor.putString(PREF_ACQ_DEVICE_IDS, json)
//        deviceId = deviceInfo.id
//        editor.putString(PREF_DEVICE_ID, deviceInfo.id)
//        editor.apply()
//    }

    /**
     * Commence selecting device ID.
     */
    private fun selectDeviceId() {
//        if (acquaintedDevices.size == 0) {
//            showDeviceIdDialog(null)
//            return
//        }
//        val dialog = arrayOf(AlertDialog.Builder(
//                this@MainActivity,
//                R.style.InverseTheme)
//        )
//        dialog[0].setTitle(R.string.device_id_item)
//        val items = arrayOfNulls<String>(acquaintedDevices.size + 1)
//        var deviceInfo: DeviceInfo
//        for (i in acquaintedDevices.indices) {
//            deviceInfo = acquaintedDevices[i]
//            items[i] = deviceInfo.name
//        }
//        items[acquaintedDevices.size] = "New"
//        val checkedItem = 0
//        dialog[0].setSingleChoiceItems(
//            items, checkedItem
//        ) { dialogInterface: DialogInterface, which: Int ->
//            if (which < acquaintedDevices.size) {
//                val deviceInfo1: DeviceInfo = acquaintedDevices[which]
//                val oldDeviceId: String = deviceId
//                setDevicePreferences(deviceInfo1)
//                Log.d(
//                    TAG, "which=" + which
//                            + " name=" + deviceInfo1.name + " id="
//                            + deviceInfo1.id
//                )
//                Log.d(
//                    TAG,
//                    "selectDeviceId: oldDeviceId=" + oldDeviceId
//                            + " mDeviceId=" + deviceId
//                )
//                if (oldDeviceId != deviceId) {
//                    resetDeviceId(oldDeviceId)
//                }
//            } else {
//                showDeviceIdDialog(null)
//            }
//            dialogInterface.dismiss()
//        }
//        dialog[0].setNegativeButton(
//            R.string.cancel
//        ) { dialog1, _ -> dialog1.dismiss() }
//        val alert = dialog[0].create()
//        alert.setCanceledOnTouchOutside(false)
//        alert.show()

        showDeviceIdDialog(null)
    }

    /**
     * Show Device ID dialog input.
     */
    private fun showDeviceIdDialog(view: View?) {
        val dialog = AlertDialog.Builder(
            this,
            R.style.InverseTheme
        )
        dialog.setTitle(R.string.device_id_item)

        val viewInflated: View = LayoutInflater.from(applicationContext).inflate(
            R.layout.device_id_dialog,
            if (view == null) null else view.rootView as ViewGroup,
            false
        )

        val input = viewInflated.findViewById<EditText>(R.id.input)
        input.inputType = InputType.TYPE_CLASS_TEXT
        deviceId = sharedPreferences?.getString(PREF_DEVICE_ID, "").toString()
        input.setText(deviceId)
        dialog.setView(viewInflated)

        dialog.setPositiveButton(
            R.string.ok
        ) { _, _ ->
            val oldDeviceId: String = deviceId
            deviceId = input.text.toString()
            Log.d(
                TAG, "showDeviceIdDialog: OK:  oldDeviceId="
                        + oldDeviceId + " newDeviceId="
                        + deviceId
            )
            val editor: SharedPreferences.Editor = sharedPreferences!!.edit()
            editor.putString(PREF_DEVICE_ID, deviceId)
            editor.apply()
            if (deviceId == null || deviceId.isEmpty()) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.no_device),
                    Toast.LENGTH_SHORT
                ).show()
            } else if (oldDeviceId != deviceId) {
                resetDeviceId(oldDeviceId)
            }
        }
        dialog.setNegativeButton(
            R.string.cancel
        ) { dialog1, _ ->
            Log.d(
                TAG,
                "showDeviceIdDialog: Cancel:  mDeviceId=$deviceId"
            )
            dialog1.cancel()
            if (deviceId == null || deviceId.isEmpty()) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.no_device),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        dialog.show()
    }

    /**
     * Tries to disconnect the device from the current API and restarts the API,
     * which will use the current mDeviceId.
     *
     * @param oldDeviceId The previous deviceId.
     */
    private fun resetDeviceId(oldDeviceId: String) {
        Log.d(TAG, this.javaClass.simpleName + " resetDeviceId:")
        if (isRecording) stopRecording()
        isPolarDeviceConnected = false
        isEcgReady = false
        isEcgRunning = false
        if (polarApi != null) {
            stopEcgStream()
            try {
                polarApi!!.disconnectFromDevice(oldDeviceId)
            } catch (ex: PolarInvalidArgument) {
                val msg = """
                oldDeviceId=$oldDeviceId
                DisconnectFromDevice: Bad argument:
                """.trimIndent()
                AppUtils.excMsg(this@MainActivity, msg, ex)
                Log.d(
                    TAG, this.javaClass.simpleName
                            + " resetDeviceId: " + msg
                )
            }
            polarApi!!.shutDown()
            polarApi = null
        }
        qrsDetector = null
        setupPolar()
    }

    /**
     * Commence saving the current samples as a file.
     * Prompts for a name to used as the file name,
     * then calls the appropriate save method.
     * @param saveType The SaveType.
     */
    private fun saveDataWithName(saveType: SaveType, allowPartial: Boolean = false) {
        if (startTime == null) {
            AppUtils.warnMsg(this, "Record a session before saving")
            return
        }
        if (!watchSession.isComplete && !allowPartial) {
            AlertDialog.Builder(this)
                .setMessage("Final Watch samples are still pending. Save currently received samples as an incomplete session?")
                .setPositiveButton("Save partial") { _, _ -> saveDataWithName(saveType, true) }
                .setNegativeButton(R.string.cancel, null).show()
            return
        }
        val msg: String
        val state = Environment.getExternalStorageState()
        if (Environment.MEDIA_MOUNTED != state) {
            msg = "External Storage is not available"
            Log.e(TAG, msg)
            AppUtils.errMsg(this, msg)
            return
        }
        // Get file name
        val dialog = AlertDialog.Builder(
            this,
            R.style.InverseTheme
        )
        dialog.setTitle(R.string.filename_dialog_title)
        val viewInflated: View = LayoutInflater.from(applicationContext)
            .inflate(R.layout.device_id_dialog, null, false)
        val input = viewInflated.findViewById<EditText>(R.id.input)
        input.inputType = (InputType.TYPE_CLASS_TEXT
                or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        dialog.setView(viewInflated)
        dialog.setPositiveButton(
            R.string.ok
        ) { _, _ ->
            when (saveType) {
                SaveType.ALL -> {
                    saveEcgData(input.text.toString())
                    saveDeviceHrData(input.text.toString())
                    savePpgData(input.text.toString(), ppgGreenPlotter!!)
                    savePpgData(input.text.toString(), ppgIrPlotter!!)
                    savePpgData(input.text.toString(), ppgRedPlotter!!)
                    saveAccelerometerData(input.text.toString())
                }
                SaveType.HR_DATA -> saveDeviceHrData(input.text.toString())
                SaveType.ECG_DATA -> saveEcgData(input.text.toString())
                SaveType.PPG_GREEN_DATA -> savePpgData(input.text.toString(), ppgGreenPlotter!!)
                SaveType.PPG_IR_DATA -> savePpgData(input.text.toString(), ppgIrPlotter!!)
                SaveType.PPG_RED_DATA -> savePpgData(input.text.toString(), ppgRedPlotter!!)
                SaveType.ACCELEROMETER_DATA -> saveAccelerometerData(input.text.toString())
                SaveType.ALL_PPG -> {
                    savePpgData(input.text.toString(), ppgGreenPlotter!!)
                    savePpgData(input.text.toString(), ppgIrPlotter!!)
                    savePpgData(input.text.toString(), ppgRedPlotter!!)
                }
//                SaveType.DATA -> saveData(input.text.toString())
//                SaveType.PLOT -> savePlot(input.text.toString())
//                SaveType.BOTH -> {
//                    saveData(input.text.toString())
//                    savePlot(input.text.toString())
//                }
//                SaveType.ALL -> {
//                    saveData(input.text.toString())
//                    savePlot(input.text.toString())
//                    saveSessionData(SaveType.DEVICE_HR)
//                    saveSessionData(SaveType.QRS_HR)
//                }
//                SaveType.DEVICE_HR, SaveType.QRS_HR -> saveSessionData(saveType)
            }
        }
        dialog.setNegativeButton(
            R.string.cancel
        ) { _, _ -> }
        dialog.show()
    }

    /**
     * Finishes the savePlot after getting the note.
     * This only saves plot of the last 30 seconds of ECG recording.
     *
     * @param note The note.
     */
    private fun savePlot(note: String) {
        val prefs = getPreferences(MODE_PRIVATE)
        val treeUriStr = prefs.getString(PREF_TREE_URI, null)
        if (treeUriStr == null) {
            AppUtils.errMsg(this, "There is no data directory set")
            return
        }
        val patientName = prefs.getString(PREF_PATIENT_NAME, "")
        val duration = (stopTime!!.time - startTime!!.time) * MS_TO_SEC
        var msg: String
        val format = "yyyy-MM-dd_HH-mm"
        val df = SimpleDateFormat(format, Locale.US)
        val fileName = "ECG-" + df.format(stopTime!!) + ".png"
        try {
            val treeUri = Uri.parse(treeUriStr)
            val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
            val docTreeUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                treeDocumentId
            )
            val resolver = this.contentResolver
            val pfd: ParcelFileDescriptor?
            val docUri = DocumentsContract.createDocument(
                resolver, docTreeUri,
                "image/png", fileName
            )
            pfd = contentResolver.openFileDescriptor(docUri!!, "w")
            FileOutputStream(pfd!!.fileDescriptor).use { strm ->
//                val logo = BitmapFactory.decodeResource(
//                    this.resources,
//                    R.drawable.polar_ecg
//                )
                val arrays: EcgPlotArrays = getEcgPlotArrays()
                val ecgValues: DoubleArray = arrays.ecg
                val peakValues: BooleanArray = arrays.peaks
//                ecgPlotter!!.getVisibleSeries().getyVals()
                val sampleCount = ecgValues.size
                val bm: Bitmap = createImage(
                    ECG_SAMPLE_RATE,
//                    logo,
                    patientName,
                    stopTime.toString(),
                    deviceId,
                    deviceFirmware,
                    deviceBatteryLevel,
                    note,
                    deviceStopHr,
                    calculatedStopHr,
                    java.lang.String.format(
                        Locale.US, "%d",
                        qrsPlotter!!.seriesDataPeaks.size()
                    ),
                    java.lang.String.format(
                        Locale.US,
                        "%.1f sec",
//                        sampleCount / ECG_SAMPLE_RATE
                        duration
                    ),
                    ecgValues,
                    peakValues
                )
                bm.compress(Bitmap.CompressFormat.PNG, 80, strm)
                strm.close()
                msg = "Wrote " + docUri.lastPathSegment
                Log.d(TAG, msg)
                AppUtils.infoMsg(this, msg)
            }
            pfd.close()
        } catch (ex: Exception) {
            msg = "Error saving plot"
            Log.e(TAG, msg)
            Log.e(TAG, Log.getStackTraceString(ex))
            AppUtils.excMsg(this, msg, ex)
        }
    }

    /**
     * Finishes the saveData after getting the name.
     * Only saves the ECG recording data.
     * @param filename The name.
     */
    private fun saveEcgData(filename: String) {
        val prefs = getPreferences(MODE_PRIVATE)
        val treeUriStr = prefs.getString(PREF_TREE_URI, null)
        if (treeUriStr == null) {
            AppUtils.errMsg(this, "There is no data directory set")
            return
        }
        // Use the coroutine-based elapsed time instead of startTime/stopTime
        val duration = elapsedSeconds.toDouble()
        var msg: String
        val format = "yyyy-MM-dd_HH-mm"
        val df = SimpleDateFormat(format, Locale.US)
        val fileName = filename + "_ECG_" + df.format(stopTime!!) + ".csv"
        try {
            val treeUri = Uri.parse(treeUriStr)
            val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
            val docTreeUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                treeDocumentId
            )
            val resolver = this.contentResolver
            val pfd: ParcelFileDescriptor?
            val docUri = DocumentsContract.createDocument(
                resolver, docTreeUri,
                "text/csv", fileName
            )
            pfd = contentResolver.openFileDescriptor(docUri!!, "w")
            FileWriter(pfd!!.fileDescriptor).use { writer ->
                PrintWriter(writer).use { out ->
                    // Write header
                    val arrays: EcgPlotArrays = getEcgPlotArrays()
                    val ecgValues: DoubleArray = arrays.ecg
                    val peakValues: BooleanArray = arrays.peaks
                    val timestamps: LongArray = arrays.timestamp
                    val peakCount: Int = qrsPlotter!!.seriesDataPeaks.size()
                    val sampleCount = ecgValues.size
                    val durationString = java.lang.String.format(
                        Locale.US, "%.1f sec",
                        duration
//                        sampleCount / ECG_SAMPLE_RATE
                    )
                    out.write(
                        ("application=" + "SamplingApp Version: "
                                + AppUtils.getVersion(this)) + "\n"
                    )
                    out.write("datatype=ECG\n")
                    out.write("timestamp_unit=unix_ms\n")
                    out.write("timestamp_source=polar_sample_ns_anchored_to_first_packet_receipt\n")
                    out.write(
                        """
                        stoptime=${stopTime.toString()}
                        
                        """.trimIndent() + "\n"
                    )
                    out.write("duration=$durationString\n")
                    out.write("samplescount=$sampleCount\n")
                    out.write(
                        """
                        ${"samplingrate=$ECG_SAMPLE_RATE"}
                        
                        """.trimIndent() + "\n"
                    )
                    out.write("stopdevicehr=$deviceStopHr\n")
                    out.write("stopcalculatedhr=$calculatedStopHr\n")
                    out.write("peakscount=$peakCount\n")
                    out.write("devicename=$deviceName\n")
                    out.write("deviceid=$deviceId\n")
                    out.write("battery=$deviceBatteryLevel\n")
                    out.write("firmware=$deviceFirmware\n")
//                    out.write("note=$filename\n")

                    // Write samples
                    for (i in 0 until sampleCount) {
                        out.write(
                            String.format(
                                Locale.US, "%.3f,%d,%d,%s\n",
                                ecgValues[i],
                                if (peakValues[i]) 1 else 0,
                                timestamps[i],
                                timestampFormat.format(Date(timestamps[i]))
                            )
                        )
                    }
                    out.flush()
                    msg = "Wrote " + docUri.lastPathSegment
                    Log.d(TAG, msg)
                    AppUtils.infoMsg(this, msg)
                }
            }
            pfd.close()
        } catch (ex: java.lang.Exception) {
            msg = "Error writing CSV file"
            Log.e(TAG, msg)
            Log.e(TAG, Log.getStackTraceString(ex))
            AppUtils.excMsg(this, msg, ex)
        }
    }

    /**
     * Finishes the saveData after getting the name.
     * Only saves the PPG recording data that corresponds
     * to the given PPG Plotter.
     * @param filename The name.
     * @param ppgPlotter A PPG Plotter.
     */
    private fun savePpgData(filename: String, ppgPlotter: PpgPlotter) {
        val prefs = getPreferences(MODE_PRIVATE)
        val treeUriStr = prefs.getString(PREF_TREE_URI, null)
        if (treeUriStr == null) {
            AppUtils.errMsg(this, "There is no data directory set")
            return
        }
        // Use the coroutine-based elapsed time instead of startTime/stopTime
        val duration = elapsedSeconds.toDouble()
        var msg: String
        val format = "yyyy-MM-dd_HH-mm"
        val df = SimpleDateFormat(format, Locale.US)
        val ppgTypeString = when (ppgPlotter.getPpgType()) {
            PpgType.PPG_GREEN -> "PPG Green"
            PpgType.PPG_IR -> "PPG IR"
            PpgType.PPG_RED -> "PPG Red"
        }
        val fileName = filename + "-" + ppgTypeString + "-" + df.format(stopTime!!) + ".csv"
        try {
            val treeUri = Uri.parse(treeUriStr)
            val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
            val docTreeUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                treeDocumentId
            )
            val resolver = this.contentResolver
            val pfd: ParcelFileDescriptor?
            val docUri = DocumentsContract.createDocument(
                resolver, docTreeUri,
                "text/csv", fileName
            )
            pfd = contentResolver.openFileDescriptor(docUri!!, "w")
            FileWriter(pfd!!.fileDescriptor).use { writer ->
                PrintWriter(writer).use { out ->
                    // Write header
                    val arrays: PpgPlotArrays = getPpgPlotArrays(ppgPlotter)
                    val ppgValues: IntArray = arrays.ppg
                    val timestamps: LongArray = arrays.timestamp
                    val sampleCount = ppgValues.size
                    val sampleRate = when (ppgPlotter.getPpgType()) {
                        PpgType.PPG_GREEN -> PPG_GREEN_SAMPLE_RATE
                        PpgType.PPG_IR, PpgType.PPG_RED -> PPG_IR_RED_SAMPLE_RATE
                    }
                    val durationString = java.lang.String.format(
                        Locale.US, "%.1f sec",
                        duration
//                        sampleCount / sampleRate
                    )
                    out.write(
                        ("application=" + "SamplingApp Version: "
                                + AppUtils.getVersion(this)) + "\n"
                    )
                    out.write("datatype=$ppgTypeString\n")
                    out.write("timestamp_unit=unix_ms\n")
                    out.write("watch_transfer_complete=${watchSession.isComplete}\n")
                    out.write(
                        """
                        stoptime=${stopTime.toString()}
                        
                        """.trimIndent() + "\n"
                    )
                    out.write("duration=$durationString\n")
                    out.write("samplescount=$sampleCount\n")
                    out.write(
                        """
                        ${"samplingrate=$sampleRate"}
                        
                        """.trimIndent() + "\n"
                    )
                    out.write("stopcalculatedhr=$calculatedStopHr\n")
//                    out.write("note=$filename\n")

                    // Write samples
                    for (i in 0 until sampleCount) {
                        out.write(
                            String.format(
                                Locale.US, "%d,%d,%s\n",
                                ppgValues[i],
                                timestamps[i],
                                timestampFormat.format(Date(timestamps[i]))
                            )
                        )
                    }
                    out.flush()
                    msg = "Wrote " + docUri.lastPathSegment
                    Log.d(TAG, msg)
                    AppUtils.infoMsg(this, msg)
                }
            }
            pfd.close()
        } catch (ex: java.lang.Exception) {
            msg = "Error writing CSV file"
            Log.e(TAG, msg)
            Log.e(TAG, Log.getStackTraceString(ex))
            AppUtils.excMsg(this, msg, ex)
        }
    }

    /**
     * Finishes the saveData after getting the name.
     * Only saves the Accelerometer recording data.
     * @param filename The name.
     */
    private fun saveDeviceHrData(filename: String) {
        val treeUriStr = getPreferences(MODE_PRIVATE).getString(PREF_TREE_URI, null)
        if (treeUriStr == null) {
            AppUtils.errMsg(this, "There is no data directory set")
            return
        }
        try {
            val tree = Uri.parse(treeUriStr)
            val directory = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(stopTime!!)
            val document = DocumentsContract.createDocument(contentResolver, directory, "text/csv",
                "$filename-PolarHR-$stamp.csv") ?: error("Could not create HR CSV")
            contentResolver.openFileDescriptor(document, "w")?.use { descriptor ->
                FileWriter(descriptor.fileDescriptor).use { out ->
                    out.write("application=SamplingApp Version: ${AppUtils.getVersion(this)}\n")
                    out.write("datatype=PolarHR\nsamplescount=${deviceHrSamples.size}\n")
                    out.write("samplingrate=event_driven\ntimestamp_unit=unix_ms\n")
                    out.write("timestamp_source=phone_notification_receipt\n")
                    out.write("timestamp,hr_bpm,rr_ms,formatted_time\n")
                    for (sample in deviceHrSamples) {
                        out.write("${sample.timestamp},${sample.hr},${sample.rrMs.joinToString(";")}," +
                            "${timestampFormat.format(Date(sample.timestamp))}\n")
                    }
                }
            } ?: error("Could not open HR CSV")
            AppUtils.infoMsg(this, "Wrote ${document.lastPathSegment}")
        } catch (error: Exception) {
            AppUtils.excMsg(this, "Error writing HR CSV", error)
        }
    }

    private fun saveAccelerometerData(filename: String) {
        val prefs = getPreferences(MODE_PRIVATE)
        val treeUriStr = prefs.getString(PREF_TREE_URI, null)
        if (treeUriStr == null) {
            AppUtils.errMsg(this, "There is no data directory set")
            return
        }
        val duration = elapsedSeconds.toDouble()
        val format = "yyyy-MM-dd_HH-mm"
        val df = SimpleDateFormat(format, Locale.US)
        val fileName = filename + "-Accelerometer-" + df.format(stopTime!!) + ".csv"
        try {
            val treeUri = Uri.parse(treeUriStr)
            val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
            val docTreeUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                treeDocumentId
            )
            val resolver = this.contentResolver
            val docUri = DocumentsContract.createDocument(
                resolver, docTreeUri,
                "text/csv", fileName
            )
            val pfd = contentResolver.openFileDescriptor(docUri!!, "w")
            FileWriter(pfd!!.fileDescriptor).use { writer ->
                PrintWriter(writer).use { out ->
                    val sampleCount = accXList.size
                    val durationString = java.lang.String.format(
                        Locale.US, "%.1f sec",
                        duration
                    )
                    out.write(
                        ("application=" + "SamplingApp Version: "
                                + AppUtils.getVersion(this)) + "\n"
                    )
                    out.write("datatype=Accelerometer\n")
                    out.write("stoptime=${stopTime.toString()}\n")
                    out.write("duration=$durationString\n")
                    out.write("samplescount=$sampleCount\n")
                    out.write("samplingrate=25.0\n")
                    out.write("samplingrate_requested=25.0\n")
                    out.write("timestamp_unit=unix_ms\n")
                    out.write("watch_transfer_complete=${watchSession.isComplete}\n")
                    out.write("x,y,z,timestamp,formatted_time\n")

                    // Write samples
                    for (i in 0 until sampleCount) {
                        out.write(
                            String.format(
                                Locale.US, "%.4f,%.4f,%.4f,%d,%s\n",
                                accXList[i],
                                accYList[i],
                                accZList[i],
                                accTimestampList[i],
                                timestampFormat.format(Date(accTimestampList[i]))
                            )
                        )
                    }
                    out.flush()
                    val msg = "Wrote " + docUri.lastPathSegment
                    Log.d(TAG, msg)
                    AppUtils.infoMsg(this, msg)
                }
            }
            pfd.close()
        } catch (ex: java.lang.Exception) {
            val msg = "Error writing Accelerometer CSV file"
            Log.e(TAG, msg)
            Log.e(TAG, Log.getStackTraceString(ex))
            AppUtils.excMsg(this, msg, ex)
        }
    }

//    /**
//     * Finishes the saveData.
//     *
//     * @param saveType The saveType (either DEVICE_HR or QRS_HR).
//     */
//    private fun saveSessionData(saveType: SaveType) {
//        val prefs = getPreferences(MODE_PRIVATE)
//        val treeUriStr = prefs.getString(PREF_TREE_URI, null)
//        if (treeUriStr == null) {
//            AppUtils.errMsg(this, "There is no data directory set")
//            return
//        }
//        var msg: String
//        val format = "yyyy-MM-dd_HH-mm"
//        val df = SimpleDateFormat(format, Locale.US)
//        val fileName: String
//        val dataList: List<HrPlotter.HrRrSessionData>
//        try {
////            if (saveType == SaveType.DEVICE_HR) {
////                fileName = "PolarECG-DeviceHR-" + df.format(stopTime) + ".csv"
////                dataList = hrPlotter!!.hrRrList1
////            } else if (saveType == SaveType.QRS_HR) {
////                fileName = "PolarECG-QRSHR-" + df.format(stopTime) + ".csv"
////                dataList = hrPlotter!!.hrRrList2
////            } else {
////                AppUtils.errMsg(
////                    this,
////                    "Invalid saveType (" + saveType + "0 in " +
////                            "doSaveSessionData"
////                )
////                return
////            }
//            when (saveType) {
//                SaveType.DEVICE_HR -> {
//                    fileName = "PolarECG-DeviceHR-" + df.format(stopTime!!) + ".csv"
//                    dataList = hrPlotter!!.hrRrList1
//                }
//                SaveType.QRS_HR -> {
//                    fileName = "PolarECG-QRSHR-" + df.format(stopTime!!) + ".csv"
//                    dataList = hrPlotter!!.hrRrList2
//                }
//                else -> {
//                    AppUtils.errMsg(
//                        this,
//                        "Invalid saveType (" + saveType + "0 in " +
//                                "doSaveSessionData"
//                    )
//                    return
//                }
//            }
//            val treeUri = Uri.parse(treeUriStr)
//            val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
//            val docTreeUri = DocumentsContract.buildDocumentUriUsingTree(
//                treeUri,
//                treeDocumentId
//            )
//            val resolver = this.contentResolver
//            val pfd: ParcelFileDescriptor?
//            val docUri = DocumentsContract.createDocument(
//                resolver, docTreeUri,
//                "text/csv", fileName
//            )
//            pfd = contentResolver.openFileDescriptor((docUri)!!, "w")
//            FileWriter(pfd!!.fileDescriptor).use { writer ->
//                PrintWriter((writer)).use { out ->
//                    // Write header (None)
//                    // Write samples
//                    for (data: HrPlotter.HrRrSessionData in dataList) {
//                        out.write(data.csvString + "\n")
//                    }
//                    out.flush()
//                    msg = "Wrote " + docUri.lastPathSegment
//                    AppUtils.infoMsg(this, msg)
//                    Log.d(TAG, "    Wrote " + dataList.size + " items")
//                }
//            }
//            pfd.close()
//        } catch (ex: java.lang.Exception) {
//            msg = "Error writing $saveType CSV file"
//            Log.e(TAG, msg)
//            Log.e(TAG, Log.getStackTraceString(ex))
//            AppUtils.excMsg(this, msg, ex)
//        }
//    }

    /**
     * Brings up a system file chooser to get the data directory
     */
    private fun chooseDataDirectory() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        openDocumentTreeLauncher.launch(intent)
    }

    /**
     * Gets ECG and Peaks and converts them into doubles. This relies on the
     * ecg arrays in EcgPlotter and QRSPlotter being the same and also the
     * respective DataIndex's. The ones in QRSPlotter are used. It also has
     * the peak values in form of binary value of `0` or `1`,
     * and also has timestamp taken from Polar device in `Long` type.
     *
     * @return PlotArrays with the ECG values, binary values that shows
     * whether it is a peak or not, and the timestamp.
     */
    private fun getEcgPlotArrays(): EcgPlotArrays {
        val arrays: EcgPlotArrays
        // Remove any out-of-range values peak values
        qrsPlotter!!.removeOutOfRangePlotPeakValues()
        val ecgValues: LinkedList<Number> = qrsPlotter!!.seriesDataEcg.getyVals()
        val peakValues: LinkedList<Number> = qrsPlotter!!.seriesDataPeaks.getyVals()
        val peakXValues: LinkedList<Number> = qrsPlotter!!.seriesDataPeaks.getxVals()
        val timestampValues: LinkedList<Number> = qrsPlotter!!.seriesTimestamp.getyVals()
        val ecgLength = ecgValues.size
        val peaksLength = peakValues.size
        val ecg = DoubleArray(ecgLength)
        val peaks = BooleanArray(ecgLength)
        val timestamp = LongArray(ecgLength)
        for ((i, value) in ecgValues.withIndex()) {
            ecg[i] = value.toDouble()
            peaks[i] = false
            timestamp[i] = timestampValues[i].toLong()
        }
//        // The peak values correspond to an index related to when they came in.
//        // This is different from the index in the arrays, which goes from 0
//        // to no more than N_TOTAL_VISIBLE_POINTS.
//        var offset = 0
//        if (qrsPlotter!!.dataIndex > N_TOTAL_VISIBLE_POINTS) {
//            offset = (-(qrsPlotter!!.dataIndex - N_TOTAL_VISIBLE_POINTS)).toInt()
//        }

        var index: Int
        for (j in 0 until peaksLength) {
            index = peakXValues[j].toInt()
//            + offset
//            Log.d(TAG, String.format("j=%d indx=%d xval=%d",
//            j, indx, peakxvals.get(j).intValue()));
            peaks[index] = true
        }
        arrays = EcgPlotArrays(ecg
            , peaks
            , timestamp)
        return arrays
    }

    /**
     * Gets PPG and timestamp of a given PpgPlotter as plot arrays.
     * This relies on the 'data' series and timestamp series
     * in the given PpgPlotter having DataIndex
     * that correspond with each other.
     *
     * @param ppgPlotter A PpgPlotter object
     * @return PlotArrays with the Ppg values and the timestamp.
     */
    private fun getPpgPlotArrays(ppgPlotter: PpgPlotter): PpgPlotArrays {
        val arrays: PpgPlotArrays
        val ppgValues: LinkedList<Number> = ppgPlotter.getDataSeries().getyVals()
        val timestampValues: LinkedList<Number> = ppgPlotter.getTimestampSeries().getyVals()
        val length = ppgValues.size
        val ppg = IntArray(length)
        val timestamp = LongArray(length)
        val indices = (0 until length).sortedBy { timestampValues[it].toLong() }
        for ((i, source) in indices.withIndex()) {
            ppg[i] = ppgValues[source].toInt()
            timestamp[i] = timestampValues[source].toLong()
        }
        arrays = PpgPlotArrays(ppg, timestamp)
        return arrays
    }

    /**
     * Toggles the tracker for *all* PPG types.
     * If a PPG type is given, toggles the tracker
     * for *only* the given type.
     *
     * This function reads current `isRecording` status
     * when toggling *all* PPG tracker.
     * Turns streaming off when not recording.
     * Turns streaming on when it is currently recording.
     *
     * When a PPG type is given, toggling uses that PPG
     * specific status to determine whether to on or off.
     *
     * @param ppgType Optional. The PPG type to toggle the tracker.
     * @see PpgType
     */
    private fun togglePpgTracker(ppgType: PpgType? = null) {
        if (ppgType != null && (!isRecording || isPaused)) {
            AppUtils.warnMsg(this, "Start or resume the recording before changing PPG channels")
            return
        }
        Log.d(
            TAG, this.javaClass.simpleName + " togglePpgTracker:"
                    + " connectedNode=" + connectedNode
        )
        if (ppgType != null && connectedNode.isNullOrEmpty()) {
            Toast.makeText(this, "Galaxy Watch unavailable; Polar recording can continue", Toast.LENGTH_LONG).show()
            return
        }

        // conditional toggling when ppg type is given
        // return early if condition met
        when (ppgType) {
            PpgType.PPG_GREEN -> {
                val message = Message(NAME, ActivityCode.START_ACTIVITY, TOGGLE_ACTIVITY)
                sendMessage(message, MessagePath.DATA_PPG_GREEN)
                toggleState(PpgType.PPG_GREEN)
                return
            }
            PpgType.PPG_IR -> {
                val message = Message(NAME, ActivityCode.START_ACTIVITY, TOGGLE_ACTIVITY)
                sendMessage(message, MessagePath.DATA_PPG_IR)
                toggleState(PpgType.PPG_IR)
                return
            }
            PpgType.PPG_RED -> {
                val message = Message(NAME, ActivityCode.START_ACTIVITY, TOGGLE_ACTIVITY)
                sendMessage(message, MessagePath.DATA_PPG_RED)
                toggleState(PpgType.PPG_RED)
                return
            }
            else -> {}
        }

        requestWatchControl(if (isRecording) ActivityCode.START_ACTIVITY else ActivityCode.STOP_ACTIVITY)
    }

    private fun stopEcgStream() {
        ecgRetryJob?.cancel()
        ecgRetryJob = null
        if (ecgStream.isActive) ecgStoppedAtMs = SystemClock.elapsedRealtime()
        val pending = ecgStream.stop()
        isEcgRunning = false
        qrsDetector = null
        if (pending) disconnectPendingEcg()
        if (::textEcgStatus.isInitialized) textEcgStatus.text = getString(R.string.ecg_status,
            getString(if (isPaused) R.string.status_paused else R.string.status_stopped))
    }

    private fun disconnectPendingEcg() {
        // SDK 4.0 attaches its stop doFinally after the start handshake. Closing the
        // connection cancels a start that might already have reached the sensor.
        val wasConnected = isPolarDeviceConnected
        isEcgReady = false
        isPolarDeviceConnected = false
        if (!wasConnected) return
        try {
            polarApi?.disconnectFromDevice(deviceId)
        } catch (error: Exception) {
            Log.w(TAG, "Pending ECG disconnect failed", error)
        }
    }

    private fun startEcgStream() {
        val api = polarApi ?: return
        if (destroying || ecgStream.isActive || !isPolarDeviceConnected || !isEcgReady ||
            !isRecording || isPaused || ecgStream.failures > 2) return
        // Polar sends stopMeasurement asynchronously on dispose; rapid Resume must wait.
        val waitMs = 1_000L - (SystemClock.elapsedRealtime() - ecgStoppedAtMs)
        if (waitMs > 0L) {
            ecgRetryJob?.cancel()
            ecgRetryJob = lifecycleScope.launch { delay(waitMs); startEcgStream() }
            return
        }
        val identifier = deviceId
        TimestampHelper.resetEcgTimestamps()
        textEcgStatus.text = getString(R.string.ecg_status, "Starting ECG")
        ecgStream.start({
            api.requestStreamSettings(identifier, DeviceStreamingFeature.ECG)
                .toFlowable()
                .flatMap { setting ->
                    val selected = setting.maxSettings()
                    require(selected.settings[PolarSensorSetting.SettingType.SAMPLE_RATE] == setOf(130)) {
                        "QRS processing requires the H10 ECG rate of 130 Hz"
                    }
                    api.startEcgStreaming(identifier, selected)
                }
                .timeout(10L, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
        }, { data ->
                if (polarApi === api && identifier == deviceId && isRecording && !isPaused && data.samples.isNotEmpty()) {
                    isEcgRunning = true
                    textEcgStatus.text = getString(R.string.ecg_status, getString(R.string.status_measuring))
                    if (qrsDetector == null) qrsDetector = QrsDetector(this)
                    qrsDetector?.process(data)
                }
            }, { error, pending ->
                if (destroying || polarApi !== api || identifier != deviceId) return@start
                isEcgRunning = false
                qrsDetector = null
                ecgStoppedAtMs = SystemClock.elapsedRealtime()
                if (pending) disconnectPendingEcg()
                Log.e(TAG, "ECG streaming failed", error)
                val retry = ecgStream.retryAfter(error)
                if (isRecording && !isPaused && retry) {
                    textEcgStatus.text = getString(R.string.ecg_status, "Retry ${ecgStream.failures}/2: ${error.message}")
                    ecgRetryJob = lifecycleScope.launch {
                        delay(1_000L)
                        if (polarApi === api && isRecording && !isPaused) {
                            if (!isPolarDeviceConnected) connectPolarDevice() else startEcgStream()
                        }
                    }
                } else {
                    textEcgStatus.text = getString(R.string.ecg_status, "Error: ${error.message}")
                    AppUtils.excMsg(this, "ECG streaming failed; check H10 contact/connection, then Resume or restart Polar", error)
                }
            }, {
                isEcgRunning = false
                qrsDetector = null
                textEcgStatus.text = getString(R.string.ecg_status, "ECG stream ended")
            })
    }

    private fun onDataArrived(dataEvent: DataEvent) {
        if (dataEvent.type != DataEvent.TYPE_CHANGED) return
        // DataEventBuffer is released after its callback: copy before posting.
        val uri = dataEvent.dataItem.uri
        val bytes = dataEvent.dataItem.data?.copyOf() ?: return
        runOnUiThread { ingestWatchBatch(uri, bytes) }
    }

    private fun ingestWatchBatch(uri: Uri, bytes: ByteArray) {
        val path = "/" + uri.path.orEmpty().trim('/').substringBefore('/')
        try {
            when (path) {
                MessagePath.DATA_PPG_GREEN, MessagePath.DATA_PPG_IR, MessagePath.DATA_PPG_RED -> {
                    val batch = Gson().fromJson(String(bytes, Charsets.UTF_8), PpgData::class.java) ?: return
                    val type = when (path) {
                        MessagePath.DATA_PPG_GREEN -> PpgType.PPG_GREEN
                        MessagePath.DATA_PPG_IR -> PpgType.PPG_IR
                        else -> PpgType.PPG_RED
                    }
                    if (batch.ppgType != type || !batch.isValid()) return
                    val plotter = when (type) {
                        PpgType.PPG_GREEN -> ppgGreenPlotter
                        PpgType.PPG_IR -> ppgIrPlotter
                        PpgType.PPG_RED -> ppgRedPlotter
                    } ?: return
                    if (watchSession.acceptBatch(batch.sessionId, uri.toString(), type.ordinal, batch.size)) {
                        for (i in 0 until batch.size) plotter.addValues(batch.ppgValues[i], batch.timestamps[i])
                        when (type) {
                            PpgType.PPG_GREEN -> ppgGreenValueNumber += batch.size
                            PpgType.PPG_IR -> ppgIrValueNumber += batch.size
                            PpgType.PPG_RED -> ppgRedValueNumber += batch.size
                        }
                        if (isRecording && !isPaused) {
                            val status = when (type) {
                                PpgType.PPG_GREEN -> textPpgGreenStatus
                                PpgType.PPG_IR -> textPpgIrStatus
                                PpgType.PPG_RED -> textPpgRedStatus
                            }
                            status.text = "${type.name}: ${getString(R.string.status_measuring)}"
                        }
                    }
                }
                MessagePath.DATA_ACCELEROMETER -> {
                    val batch = Gson().fromJson(String(bytes, Charsets.UTF_8), AccelerometerData::class.java) ?: return
                    if (!batch.isValid()) return
                    if (watchSession.acceptBatch(batch.sessionId, uri.toString(), 3, batch.size)) {
                        for (i in 0 until batch.size) {
                            accXList.add(batch.xValues[i])
                            accYList.add(batch.yValues[i])
                            accZList.add(batch.zValues[i])
                            accTimestampList.add(batch.timestamps[i])
                        }
                        accValueNumber += batch.size
                    }
                }
                else -> return
            }
            // Consumed/old unique batches can be removed; retries cannot double count them.
            Wearable.DataApi.deleteDataItems(client, uri)
            if (!isRecording) invalidateOptionsMenu()
        } catch (error: RuntimeException) {
            Log.w(TAG, "Rejected malformed Watch batch", error)
        }
    }

    private fun onMessageArrived(messagePath: String) {
        if (destroying) return
        val message = wearMessage ?: return
        if (message.sessionId != 0L && message.sessionId != watchSession.sessionId) return
        if (messagePath == MessagePath.INFO && desiredWatchCommand != null) {
            // An old acknowledgement must not overwrite a newer Pause/End request.
            if (message.commandRevision < watchControl.revision) return
            if (!watchControl.matches(message)) {
                if (desiredWatchCommand?.code == ActivityCode.START_ACTIVITY &&
                    message.code == ActivityCode.STOP_ACTIVITY && message.commandRevision >= watchControl.revision) {
                    textPpgGreenStatus.text = "Watch session ended; End and Start a new session"
                    textPpgIrStatus.text = textPpgGreenStatus.text
                    textPpgRedStatus.text = textPpgGreenStatus.text
                } else showWatchControlPending()
                return
            }
            watchAcknowledged = true
            if (message.code != ActivityCode.START_ACTIVITY) controlHandler.removeCallbacks(watchRetry)
        }
        if (messagePath == MessagePath.INFO &&
            message.code == ActivityCode.STOP_ACTIVITY && message.sensorsStopped == true && message.sampleCounts != null) {
            watchSession.reportStopped(message.sessionId, message.sampleCounts!!)
        }
        when (messagePath) {
            MessagePath.COMMAND -> {
                if (message.code == ActivityCode.STOP_ACTIVITY) {
                    toggleState(0)
                }
            }
            MessagePath.REQUEST -> {
                Log.d(TAG, "Received REQUEST message from wear: ${message.content}")
            }
            MessagePath.INFO -> {
                when (message.code) {
                    ActivityCode.START_ACTIVITY -> toggleState(1)
                    ActivityCode.STOP_ACTIVITY -> toggleState(0)
                    ActivityCode.PAUSE_ACTIVITY -> toggleState(ActivityCode.PAUSE_ACTIVITY)
                    ActivityCode.DO_NOTHING -> toggleState(0)
                    else -> Log.d(TAG, "Unknown activity code: ${message.code}")
                }
                message.extraCode?.let { mask ->
                    isPpgGreenRunning = mask and PpgType.PPG_GREEN.mask != 0
                    isPpgIrRunning = mask and PpgType.PPG_IR.mask != 0
                    isPpgRedRunning = mask and PpgType.PPG_RED.mask != 0
                    if (message.code == ActivityCode.START_ACTIVITY) invalidatePpgState()
                }
                invalidateOptionsMenu()
            }
            else -> {
                Log.d(TAG, "Unknown message path: $messagePath")
            }
        }
    }

    private fun sendMessage(message: Message, path: String) {
        if (!client.isConnected) {
            if (path == MessagePath.COMMAND) showWatchControlPending()
            return
        }
        if (path != MessagePath.COMMAND) message.sessionId = watchSession.sessionId
        val bytes = Gson().toJson(message).toByteArray(Charsets.UTF_8)
        Wearable.NodeApi.getConnectedNodes(client).setResultCallback { result ->
            runOnUiThread {
                if (destroying || !client.isConnected) return@runOnUiThread
                connectedNode = if (result.status.isSuccess) result.nodes else emptyList()
                // Node lookup is asynchronous; discard obsolete requests before sending.
                if (path == MessagePath.COMMAND && message !== desiredWatchCommand) return@runOnUiThread
                if (connectedNode.isNullOrEmpty() && path == MessagePath.COMMAND && !watchAcknowledged) showWatchControlPending()
                connectedNode?.forEach { node ->
                    Wearable.MessageApi.sendMessage(client, node.id, path, bytes).setResultCallback { sent ->
                        if (!sent.status.isSuccess) Log.w(TAG, "Watch command delivery failed: ${sent.status.statusCode}")
                    }
                }
            }
        }
    }

    private fun requestWatchControl(code: Int) {
        val id = watchSession.sessionId
        if (!watchControl.accept(id, watchControl.revision + 1L, code, SystemClock.elapsedRealtime())) return
        desiredWatchCommand = Message(NAME, "Session control", code, null, id,
            commandRevision = watchControl.revision)
        watchAcknowledged = false
        showWatchControlPending()
        controlHandler.removeCallbacks(watchRetry)
        watchRetry.run()
    }

    private fun showWatchControlPending() {
        if (destroying) return
        watchAcknowledged = false
        val action = when (desiredWatchCommand?.code) {
            ActivityCode.PAUSE_ACTIVITY -> "Pause"
            ActivityCode.STOP_ACTIVITY -> "End"
            else -> "Start/Resume"
        }
        val status = "Watch $action awaiting confirmation; reconnect both apps if offline"
        textPpgGreenStatus.text = status
        textPpgIrStatus.text = status
        textPpgRedStatus.text = status
    }

    private fun setMessageCode(message: Message, forceCode: Int = 99) {
        if (forceCode != 99) {
            message.code = forceCode
        }
        else if (appState == 0) {
            message.code = ActivityCode.START_ACTIVITY
        }
        else if (appState == 1) {
            message.code = ActivityCode.STOP_ACTIVITY
        }
    }

    /**
     * Toggles the running state of *all* PPG Tracker
     *
     * Pass a parameter to force the state
     * to a certain value.
     *
     * @param forceState Optional. A value to be used as the state
     * if present.
     */
    private fun toggleState(forceState: Boolean? = null) {
        if (forceState != null) {
            isPpgGreenRunning = forceState
            isPpgIrRunning = forceState
            isPpgRedRunning = forceState
            invalidatePpgState()
            return
        }
        isPpgGreenRunning = !isPpgGreenRunning
        isPpgIrRunning = !isPpgIrRunning
        isPpgRedRunning = !isPpgRedRunning
        invalidatePpgState()
    }

    /**
     * Toggles the running state of PPG Tracker of
     * the given PPG type.
     *
     * Pass a second parameter to force the state
     * to a certain value.
     *
     * @param ppgType The type of PPG to toggle the state
     * @param forceState Optional. A value to be used as the state
     * if present.
     * @see PpgType
     */
    private fun toggleState(ppgType: PpgType, forceState: Boolean? = null) {
        when (ppgType) {
            PpgType.PPG_GREEN -> {
                if (forceState != null) {
                    isPpgGreenRunning = forceState
                    return
                }
                isPpgGreenRunning = !isPpgGreenRunning

                if (isPpgGreenRunning) {
                    runOnUiThread {
                        textPpgGreenStatus.text = getString(R.string.ppg_green_status,
                            getString(R.string.status_running))
                    }
                } else {
                    runOnUiThread {
                        textPpgGreenStatus.text = getString(R.string.ppg_green_status,
                            getString(R.string.status_stopped))
                    }
                }
            }
            PpgType.PPG_IR -> {
                if (forceState != null) {
                    isPpgIrRunning = forceState
                    return
                }
                isPpgIrRunning = !isPpgIrRunning

                if (isPpgIrRunning) {
                    runOnUiThread {
                        textPpgIrStatus.text = getString(R.string.ppg_ir_status,
                            getString(R.string.status_running))
                    }
                } else {
                    runOnUiThread {
                        textPpgIrStatus.text = getString(R.string.ppg_ir_status,
                            getString(R.string.status_stopped))
                    }
                }
            }
            PpgType.PPG_RED -> {
                if (forceState != null) {
                    isPpgRedRunning = forceState
                    return
                }
                isPpgRedRunning = !isPpgRedRunning

                if (isPpgRedRunning) {
                    runOnUiThread {
                        textPpgRedStatus.text = getString(R.string.ppg_red_status,
                            getString(R.string.status_running))
                    }
                } else {
                    runOnUiThread {
                        textPpgRedStatus.text = getString(R.string.ppg_red_status,
                            getString(R.string.status_stopped))
                    }
                }
            }
        }
    }

    private fun toggleState(forceCode: Int = 99) {
        if (forceCode != 99) {
            appState = forceCode
        }
        else if (appState == 0) {
            appState = 1
        }
        else if (appState == 1) {
            appState = 0
        }
        runOnUiThread {
            when (appState) {
                0, ActivityCode.STOP_ACTIVITY -> {
                    textPpgGreenStatus.text = getString(R.string.ppg_green_status,
                        getString(R.string.status_stopped))
                    textPpgIrStatus.text = getString(R.string.ppg_ir_status,
                        getString(R.string.status_stopped))
                    textPpgRedStatus.text = getString(R.string.ppg_red_status,
                        getString(R.string.status_stopped))
                }
                1 -> {
                    textPpgGreenStatus.text = getString(R.string.ppg_green_status,
                        getString(R.string.status_running))
                    textPpgIrStatus.text = getString(R.string.ppg_ir_status,
                        getString(R.string.status_running))
                    textPpgRedStatus.text = getString(R.string.ppg_red_status,
                        getString(R.string.status_running))
                }
                ActivityCode.PAUSE_ACTIVITY -> {
                    textPpgGreenStatus.text = getString(R.string.ppg_green_status,
                        getString(R.string.status_paused))
                    textPpgIrStatus.text = getString(R.string.ppg_ir_status,
                        getString(R.string.status_paused))
                    textPpgRedStatus.text = getString(R.string.ppg_red_status,
                        getString(R.string.status_paused))
                }
            }
        }
        Log.d(TAG,"stateNum changed to: $appState")
    }

    private fun displayPolarInfo() {
        val msg = StringBuilder()
        msg.append("Polar Device Name: ").append(deviceName).append("\n")
        msg.append("Polar Device Id: ").append(deviceId).append("\n")
        msg.append("Polar Device Address: ").append(deviceAddress).append("\n")
        msg.append("Polar Device Firmware: ").append(deviceFirmware).append("\n")
        msg.append("Polar Device Battery Level: ").append(deviceBatteryLevel).append("\n")
        msg.append("Polar API Connected: ").append(polarApi != null).append("\n")
        msg.append("Polar Device Connected: ").append(isPolarDeviceConnected).append("\n")
        msg.append("Recording: ").append(isRecording).append("\n")
        msg.append("Receiving ECG: ").append(ecgStream.isActive)
            .append("\n")
        if (ecgPlotter != null && ecgPlotter!!.getVisibleSeries() != null && ecgPlotter!!.getVisibleSeries()
                .getyVals() != null
        ) {
            val elapsed: Double = ecgPlotter!!.getDataIndex() / ECG_SAMPLE_RATE
            msg.append("Elapsed Time: ")
                .append(getString(R.string.elapsed_time, elapsed))
                .append("\n")
            msg.append("Points plotted: ")
                .append(ecgPlotter!!.getVisibleSeries().getyVals().size)
                .append("\n")
        }
        msg.append("ECG-App Version: ").append(AppUtils.getVersion(this)).append("\n")
        msg.append("Polar BLE API Version: ").append(versionInfo()).append("\n")
        msg.append(UriUtils.getRequestedPermissionsInfo(this))
        val prefs = getPreferences(MODE_PRIVATE)
        val treeUriStr = prefs.getString(PREF_TREE_URI, null)
        if (treeUriStr == null) {
            msg.append("Data Directory: Not set")
        } else {
            val treeUri = Uri.parse(treeUriStr)
            msg.append("Data Directory: ").append(treeUri.path)
        }
        AppUtils.infoMsg(this, msg.toString())
    }

    companion object {
        private const val TAG = "Mobile.MainActivity"
        private const val NAME = PHONE_APP
        private val shortDateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        private val timestampFormat = SimpleDateFormat("dd MMM yyyy HH:mm:ss:SSS Z")
        // Currently the sampling rate for ECG is fixed at 130
//        private const val MAX_DEVICES = 3
        const val REQUEST_CODE_PERMISSIONS = 10
        const val REQUEST_CODE_BACKGROUND_PERMISSIONS = 11
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                    add(Manifest.permission.BLUETOOTH)
                    add(Manifest.permission.BLUETOOTH_ADMIN)
                }
            }.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(Manifest.permission.BLUETOOTH_SCAN)
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }.toTypedArray()
        
        // Background location must be requested separately after foreground permissions
        private val BACKGROUND_PERMISSIONS =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                emptyArray()
            }
    }

    private fun logEpochInfo(timeZoneString: String) {
        val sdf = SimpleDateFormat("dd:MM:yyyy HH:mm:ss", Locale.US)
        val sdf2 = SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US)
        /// Set the timezone
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        sdf2.timeZone = TimeZone.getTimeZone("UTC")
        try {
            val epoch = sdf.parse("01:01:2000 00:00:00")
            val epoch1 = sdf.parse("01:01:2019 00:00:00")
            if (epoch != null && epoch1 != null) {
                Log.d(
                    TAG, "epoch=" + sdf2.format(epoch)
                            + " epoch1=" + sdf2.format(epoch1)
                            + " " + epoch.time
                            + " " + epoch1.time
                            + " " + (epoch1.time - epoch.time)
                            + timeZoneString
                )
            } else {
                Log.d(TAG, "epoch=null and/or epoch2=null")
            }
        } catch (ex: java.lang.Exception) {
            Log.e(TAG, "Error parsing date", ex)
        }
    }

    private fun timestampInfo(ts: Long, name: String?): String? {
        val sdf = SimpleDateFormat("MMM dd yyyy HH:mm:ss zzz", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val sdf1 = SimpleDateFormat("MMM dd yyyy HH:mm:ss zzz", Locale.US)
        val date = Date(ts)
        return String.format(
            Locale.US, "%15d %-8s %.2f years  %s %s",
            ts, name, msToYears(ts),
            sdf.format(date), sdf1.format(date)
        )
    }

    private fun msToYears(longVal: Long): Double {
        return longVal / (1000.0 * 60.0 * 60.0 * 24.0 * 365.0)
    }
}
