package com.example.samplewearmobileapp

import android.Manifest
import android.bluetooth.BluetoothAdapter.EXTRA_STATE
import android.bluetooth.BluetoothAdapter.STATE_OFF
import android.bluetooth.BluetoothAdapter.STATE_TURNING_OFF
import android.bluetooth.BluetoothAdapter.STATE_TURNING_ON
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.androidplot.xy.XYPlot
import com.example.samplewearmobileapp.Constants.PREF_ANALYSIS_VISIBILITY
import com.example.samplewearmobileapp.Constants.PREF_DEVICE_ID
import com.example.samplewearmobileapp.Constants.PREF_ECG_VISIBILITY
import com.example.samplewearmobileapp.Constants.PREF_PATIENT_NAME
import com.example.samplewearmobileapp.Constants.PREF_PPG_GREEN_VISIBILITY
import com.example.samplewearmobileapp.Constants.PREF_PPG_IR_VISIBILITY
import com.example.samplewearmobileapp.Constants.PREF_PPG_RED_VISIBILITY
import com.example.samplewearmobileapp.Constants.PREF_TREE_URI
import com.example.samplewearmobileapp.data.repository.FileRepositoryImpl
import com.example.samplewearmobileapp.data.repository.PolarRepositoryImpl
import com.example.samplewearmobileapp.data.repository.WearableRepositoryImpl
import com.example.samplewearmobileapp.databinding.ActivityMainBinding
import com.example.samplewearmobileapp.domain.model.DeviceConnectionState
import com.example.samplewearmobileapp.domain.model.RecordingState
import com.example.samplewearmobileapp.ui.main.MainViewModel
import com.example.samplewearmobileapp.ui.main.SaveType
import com.example.samplewearmobileapp.ui.main.UiEvent
import com.example.samplewearmobileapp.utils.AppUtils
import com.example.samplewearmobileapp.utils.UriUtils
import kotlinx.coroutines.launch

/**
 * Main Activity — pure UI layer (MVVM pattern).
 *
 * This class is responsible exclusively for:
 * - View Binding setup and lifecycle management.
 * - Observing [MainViewModel] state flows and reflecting them in the UI.
 * - Handling user gestures and forwarding them to the ViewModel.
 * - Managing Android permission requests (Bluetooth, location).
 * - Managing plotter lifecycles (ECG, PPG, QRS, HR).
 *
 * All business logic (recording state, sensor data, Wearable IPC, file I/O)
 * is owned by [MainViewModel] and its injected repositories.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        const val REQUEST_CODE_PERMISSIONS = 1001
        private val BLUETOOTH_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    // -------------------------------------------------------------------------
    // View Binding & ViewModel
    // -------------------------------------------------------------------------

    private lateinit var binding: ActivityMainBinding
    private lateinit var menu: Menu
    private var sharedPreferences: SharedPreferences? = null

    /**
     * ViewModel factory that wires up the concrete repository implementations.
     * In a production app this would use Hilt / Koin DI.
     */
    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(
                polarRepository = PolarRepositoryImpl(applicationContext),
                wearableRepository = WearableRepositoryImpl(applicationContext),
                fileRepository = FileRepositoryImpl(applicationContext)
            ) as T
        }
    }

    // -------------------------------------------------------------------------
    // Plotters
    // -------------------------------------------------------------------------

    var ecgPlotter: EcgPlotter? = null
    var ppgGreenPlotter: PpgPlotter? = null
    var ppgIrPlotter: PpgPlotter? = null
    var ppgRedPlotter: PpgPlotter? = null
    var qrsPlotter: QrsPlotter? = null
    var hrPlotter: HrPlotter? = null
    private var qrsDetector: QrsDetector? = null

    // Plot Views
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
    private lateinit var textEcgHr: TextView
    private lateinit var textEcgInfo: TextView
    private lateinit var textEcgTime: TextView
    private lateinit var ecgPlot: XYPlot
    private lateinit var analysisContainer: ViewGroup
    private lateinit var qrsPlot: XYPlot
    private lateinit var hrPlot: XYPlot

    // Visibility preferences
    private var isPpgGreenVisible = true
    private var isPpgIrVisible = true
    private var isPpgRedVisible = true
    private var isEcgVisible = true
    private var isUsingAnalysis = false

    // -------------------------------------------------------------------------
    // Bluetooth State Receiver
    // -------------------------------------------------------------------------

    private var isBluetoothReceiverRegistered = false
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(EXTRA_STATE, STATE_OFF)) {
                STATE_TURNING_OFF -> Toast.makeText(context, "Bluetooth turning off", Toast.LENGTH_SHORT).show()
                STATE_TURNING_ON -> Toast.makeText(context, "Bluetooth turning on", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Launchers
    // -------------------------------------------------------------------------

    private val openDocumentTreeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            val treeUri = result.data?.data ?: return@registerForActivityResult
            contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val editor = getPreferences(MODE_PRIVATE).edit()
            editor.putString(PREF_TREE_URI, treeUri.toString())
            editor.apply()
            Log.d(TAG, "Tree URI saved: $treeUri (${UriUtils.getNPersistedPermissions(this)} permissions)")
        }
    }

    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            Toast.makeText(this, "Bluetooth is required for Polar device", Toast.LENGTH_LONG).show()
        }
    }

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_LONG).show()
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        bindViews()
        initPlotters()
        loadVisibilityPreferences()
        requestBluetoothPermissionsIfNeeded()
        registerBluetoothReceiver()
        observeViewModel()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBluetoothReceiverRegistered) {
            unregisterReceiver(bluetoothStateReceiver)
            isBluetoothReceiverRegistered = false
        }
    }

    // -------------------------------------------------------------------------
    // Menu
    // -------------------------------------------------------------------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        this.menu = menu
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.device_id -> {
                showDeviceIdDialog()
                true
            }
            R.id.pause -> {
                // Toggle pause/resume based on current state
                when (viewModel.recordingState.value) {
                    is RecordingState.Recording -> viewModel.onPauseRecording()
                    is RecordingState.Paused -> viewModel.onResumeRecording()
                    else -> Unit
                }
                true
            }
            R.id.stop_recording -> {
                viewModel.onStopRecording()
                clearPlotters()
                true
            }
            R.id.save_ecg_data -> {
                promptSave(SaveType.ECG_DATA)
                true
            }
            R.id.save_ppg_green_data -> {
                promptSave(SaveType.PPG_GREEN_DATA)
                true
            }
            R.id.save_ppg_ir_data -> {
                promptSave(SaveType.PPG_IR_DATA)
                true
            }
            R.id.save_ppg_red_data -> {
                promptSave(SaveType.PPG_RED_DATA)
                true
            }
            R.id.save_all_ppg_data -> {
                promptSave(SaveType.ALL_PPG)
                true
            }
            R.id.save_all -> {
                promptSave(SaveType.ALL)
                true
            }
            R.id.choose_data_directory -> {
                openDocumentTreeLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
                true
            }
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // -------------------------------------------------------------------------
    // ViewModel Observation
    // -------------------------------------------------------------------------

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.recordingState.collect { state ->
                updateMenuForRecordingState(state)
                when (state) {
                    is RecordingState.Idle -> {
                        textEcgTime.text = "00:00.000"
                    }
                    is RecordingState.Recording -> {
                        // Timer display updated separately below
                    }
                    is RecordingState.Paused -> { /* keep last time display */ }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.elapsedTimeDisplay.collect { timeStr ->
                textEcgTime.text = timeStr
            }
        }

        lifecycleScope.launch {
            viewModel.deviceConnectionState.collect { state ->
                updateDeviceConnectionUI(state)
            }
        }

        lifecycleScope.launch {
            viewModel.heartRate.collect { bpm ->
                textEcgHr.text = bpm.toString()
                // hrPlotter receives HR via QrsDetector.addValues2() in the plotter chain
            }
        }

        lifecycleScope.launch {
            viewModel.ecgSamples.collect { samples ->
                ecgPlotter?.let { plotter ->
                    // samples already converted μV → mV in PolarRepositoryImpl
                    // plotter expects PolarEcgData, forward via direct series update
                }
            }
        }

        lifecycleScope.launch {
            viewModel.isWearConnected.collect { connected ->
                textStatusContainerTitle.text = if (connected) "Watch: Connected" else "Watch: --"
            }
        }

        lifecycleScope.launch {
            viewModel.uiEvents.collect { event ->
                handleUiEvent(event)
            }
        }
    }

    // -------------------------------------------------------------------------
    // UI Event Handling
    // -------------------------------------------------------------------------

    private fun handleUiEvent(event: UiEvent) {
        when (event) {
            is UiEvent.ShowToast -> Toast.makeText(this, event.message, Toast.LENGTH_SHORT).show()
            is UiEvent.ShowError -> AppUtils.alert(this, event.title, event.message)
            is UiEvent.ShowDeviceIdDialog -> showDeviceIdDialog()
            is UiEvent.ShowSaveDialog -> promptSave(event.saveType)
            is UiEvent.ShowSaveSuccess -> Toast.makeText(this, "Saved: ${event.filePath}", Toast.LENGTH_LONG).show()
        }
    }

    // -------------------------------------------------------------------------
    // Dialogs
    // -------------------------------------------------------------------------

    private fun showDeviceIdDialog() {
        val savedId = getPreferences(MODE_PRIVATE).getString(PREF_DEVICE_ID, "") ?: ""
        AppUtils.showDeviceIdDialog(this, savedId) { deviceId ->
            getPreferences(MODE_PRIVATE).edit().putString(PREF_DEVICE_ID, deviceId).apply()
            viewModel.onConnectPolar(deviceId)
        }
    }

    private fun promptSave(type: SaveType) {
        val prefs = getPreferences(MODE_PRIVATE)
        val treeUriString = prefs.getString(PREF_TREE_URI, null)
        if (treeUriString == null) {
            Toast.makeText(this, "Please choose a save folder first", Toast.LENGTH_SHORT).show()
            openDocumentTreeLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
            return
        }
        val patientName = sharedPreferences?.getString(PREF_PATIENT_NAME, "") ?: ""
        AppUtils.showSaveDialog(this, patientName) { fileName ->
            viewModel.onSaveData(type, fileName, Uri.parse(treeUriString))
        }
    }

    // -------------------------------------------------------------------------
    // UI State Helpers
    // -------------------------------------------------------------------------

    private fun updateDeviceConnectionUI(state: DeviceConnectionState) {
        when (state) {
            is DeviceConnectionState.Disconnected -> {
                textEcgStatus.text = getString(R.string.status_not_connected)
                textEcgInfo.text = ""
            }
            is DeviceConnectionState.Connecting -> {
                textEcgStatus.text = "Connecting to ${state.deviceId}…"
            }
            is DeviceConnectionState.Connected -> {
                textEcgStatus.text = "${state.name} (${state.deviceId})"
                textEcgInfo.text = "FW: ${state.firmware} | Batt: ${state.batteryLevel ?: "?"}%"
            }
        }
    }

    private fun updateMenuForRecordingState(state: RecordingState) {
        if (!::menu.isInitialized) return
        val isIdle = state is RecordingState.Idle
        val isRecording = state is RecordingState.Recording
        val isPaused = state is RecordingState.Paused
        menu.findItem(R.id.pause)?.apply {
            isVisible = !isIdle
            title = if (isPaused) getString(R.string.play_item) else getString(R.string.pause_item)
        }
        menu.findItem(R.id.stop_recording)?.isVisible = !isIdle
    }

    // -------------------------------------------------------------------------
    // Plotter Lifecycle
    // -------------------------------------------------------------------------

    private fun initPlotters() {
        ecgPlotter = EcgPlotter(
            this, ecgPlot,
            getString(R.string.ecg_plot_title),
            android.graphics.Color.RED,
            false
        )
        ppgGreenPlotter = PpgPlotter(
            this, ppgGreenPlot,
            com.example.samplewearmobileapp.models.PpgType.PPG_GREEN,
            getString(R.string.ppg_green_plot_title),
            android.graphics.Color.GREEN,
            false
        )
        ppgIrPlotter = PpgPlotter(
            this, ppgIrPlot,
            com.example.samplewearmobileapp.models.PpgType.PPG_IR,
            getString(R.string.ppg_ir_plot_title),
            android.graphics.Color.rgb(180, 0, 255),
            false
        )
        ppgRedPlotter = PpgPlotter(
            this, ppgRedPlot,
            com.example.samplewearmobileapp.models.PpgType.PPG_RED,
            getString(R.string.ppg_red_plot_title),
            android.graphics.Color.rgb(255, 80, 80),
            false
        )
        qrsDetector = QrsDetector(this)
        qrsPlotter = QrsPlotter(this, qrsPlot)
        hrPlotter = HrPlotter(this, hrPlot)
    }

    private fun clearPlotters() {
        ecgPlotter?.clear()
        ppgGreenPlotter?.clear()
        ppgIrPlotter?.clear()
        ppgRedPlotter?.clear()
        qrsPlotter?.clear()
        hrPlotter?.clear()
    }

    // -------------------------------------------------------------------------
    // View Binding
    // -------------------------------------------------------------------------

    private fun bindViews() {
        textStatusContainerTitle = binding.statusContainerTitle
        textPpgGreenStatus = binding.statusPpgGreen
        textPpgIrStatus = binding.statusPpgIr
        textPpgRedStatus = binding.statusPpgRed
        textEcgStatus = binding.statusEcg
        ppgContainer = binding.ppgContainer
        ppgGreenPlot = binding.ppgGreenPlot
        ppgIrPlot = binding.ppgIrPlot
        ppgRedPlot = binding.ppgRedPlot
        ecgContainer = binding.ecgContainer
        textEcgHr = binding.ecgHr
        textEcgInfo = binding.ecgInfo
        textEcgTime = binding.ecgTime
        ecgPlot = binding.ecgPlot
        analysisContainer = binding.analysisContainer
        qrsPlot = binding.qrsPlot
        hrPlot = binding.hrPlot
    }

    // -------------------------------------------------------------------------
    // Preferences
    // -------------------------------------------------------------------------

    private fun loadVisibilityPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        isPpgGreenVisible = prefs.getBoolean(PREF_PPG_GREEN_VISIBILITY, true)
        isPpgIrVisible = prefs.getBoolean(PREF_PPG_IR_VISIBILITY, true)
        isPpgRedVisible = prefs.getBoolean(PREF_PPG_RED_VISIBILITY, true)
        isEcgVisible = prefs.getBoolean(PREF_ECG_VISIBILITY, true)
        isUsingAnalysis = prefs.getBoolean(PREF_ANALYSIS_VISIBILITY, false)
        applyVisibility()
    }

    private fun applyVisibility() {
        ppgGreenPlot.visibility = if (isPpgGreenVisible) android.view.View.VISIBLE else android.view.View.GONE
        ppgIrPlot.visibility = if (isPpgIrVisible) android.view.View.VISIBLE else android.view.View.GONE
        ppgRedPlot.visibility = if (isPpgRedVisible) android.view.View.VISIBLE else android.view.View.GONE
        ecgContainer.visibility = if (isEcgVisible) android.view.View.VISIBLE else android.view.View.GONE
        analysisContainer.visibility = if (isUsingAnalysis) android.view.View.VISIBLE else android.view.View.GONE
    }

    // -------------------------------------------------------------------------
    // Permissions
    // -------------------------------------------------------------------------

    private fun requestBluetoothPermissionsIfNeeded() {
        val missing = BLUETOOTH_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            bluetoothPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun registerBluetoothReceiver() {
        val filter = IntentFilter(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothStateReceiver, filter)
        isBluetoothReceiverRegistered = true
    }
}