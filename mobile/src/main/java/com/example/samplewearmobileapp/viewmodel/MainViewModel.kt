package com.example.samplewearmobileapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.samplewearmobileapp.data.CsvLogger
import com.example.samplewearmobileapp.data.SessionConfig
import com.example.samplewearmobileapp.data.SessionManager
import com.example.samplewearmobileapp.data.SessionState
import com.example.samplewearmobileapp.data.SessionSummary
import com.example.samplewearmobileapp.di.IoDispatcher
import com.example.samplewearmobileapp.permissions.PermissionManager
import com.example.samplewearmobileapp.sensor.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State exposed to the Activity/Fragment.
 * Combines all observable states into a single data class.
 */
data class UiState(
    // Session
    val sessionState: SessionState = SessionState.IDLE,
    val elapsedSeconds: Long = 0L,
    val sessionId: String? = null,

    // Polar H10
    val polarConnectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val polarDeviceId: String = "",
    val polarDeviceName: String = "",
    val polarBatteryLevel: Int = -1,
    val polarFirmware: String = "",

    // Wear / PPG
    val wearConnectionState: ConnectionState = ConnectionState.DISCONNECTED,

    // Data quality
    val ecgSampleCount: Long = 0L,
    val ppgGreenSampleCount: Long = 0L,
    val ppgIrSampleCount: Long = 0L,
    val ppgRedSampleCount: Long = 0L,

    // Researcher metadata
    val participantId: String = "",
    val conditionLabel: String = "Baseline",

    // Last session summary (after stop)
    val lastSessionSummary: SessionSummary? = null
)

/**
 * ViewModel for the main data collection screen.
 *
 * Orchestrates [PolarEcgManager], [WearPpgManager], [SessionManager],
 * and exposes a unified [UiState] for the UI to observe.
 *
 * Data flow:
 *   Sensor Managers → ViewModel (collect Flows) → SessionManager → CsvLogger
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val permissionManager: PermissionManager,
    private val polarEcgManager: PolarEcgManager,
    private val wearPpgManager: WearPpgManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    // === Session Manager ===
    private val csvLogger = CsvLogger(ioDispatcher)
    private val sessionManager = SessionManager(csvLogger)

    // === UI State ===
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // === Permission State ===
    val permissionState = permissionManager.state

    // === Internal ===
    private var timerJob: Job? = null
    private var ecgCollectionJob: Job? = null
    private var ppgGreenCollectionJob: Job? = null
    private var ppgIrCollectionJob: Job? = null
    private var ppgRedCollectionJob: Job? = null

    init {
        // Observe session state changes
        viewModelScope.launch {
            sessionManager.state.collect { state ->
                _uiState.value = _uiState.value.copy(sessionState = state)
            }
        }
        viewModelScope.launch {
            sessionManager.elapsedSeconds.collect { elapsed ->
                _uiState.value = _uiState.value.copy(elapsedSeconds = elapsed)
            }
        }

        // Observe Polar connection state
        viewModelScope.launch {
            polarEcgManager.connectionState.collect { state ->
                _uiState.value = _uiState.value.copy(polarConnectionState = state)
            }
        }
        viewModelScope.launch {
            polarEcgManager.deviceName.collect { name ->
                _uiState.value = _uiState.value.copy(polarDeviceName = name)
            }
        }
        viewModelScope.launch {
            polarEcgManager.batteryLevel.collect { level ->
                _uiState.value = _uiState.value.copy(polarBatteryLevel = level)
            }
        }
        viewModelScope.launch {
            polarEcgManager.firmware.collect { fw ->
                _uiState.value = _uiState.value.copy(polarFirmware = fw)
            }
        }

        // Observe Wear connection state
        viewModelScope.launch {
            wearPpgManager.connectionState.collect { state ->
                _uiState.value = _uiState.value.copy(wearConnectionState = state)
            }
        }
    }

    // ===== Sensor Connection =====

    /**
     * Connects to the Polar H10 device.
     * Call when user enters a device ID and taps connect.
     */
    fun connectPolar(deviceId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(polarDeviceId = deviceId)
            polarEcgManager.connect(deviceId)
        }
    }

    /**
     * Disconnects from the Polar H10.
     */
    fun disconnectPolar() {
        viewModelScope.launch {
            polarEcgManager.disconnect()
        }
    }

    /**
     * Starts listening for PPG data from the watch.
     */
    fun connectWear() {
        wearPpgManager.startListening()
    }

    /**
     * Stops listening for PPG data.
     */
    fun disconnectWear() {
        stopPpgCollection()
        wearPpgManager.stopListening()
    }

    // ===== Session Actions =====

    /**
     * Starts a new recording session.
     * Connects to sensors, opens CSV files, starts data collection.
     */
    fun startSession(participantId: String, conditionLabel: String, polarDeviceId: String) {
        viewModelScope.launch {
            val config = SessionConfig(
                participantId = participantId,
                conditionLabel = conditionLabel,
                polarDeviceId = polarDeviceId
            )

            sessionManager.startSession(config)

            _uiState.value = _uiState.value.copy(
                participantId = participantId,
                conditionLabel = conditionLabel,
                polarDeviceId = polarDeviceId,
                sessionId = sessionManager.currentSessionId
            )

            // Start ECG collection
            startEcgCollection()

            // Send START command to watch and begin PPG collection
            wearPpgManager.sendCommand(WearCommand.StartAllTrackers)
            startPpgCollection()

            // Start timer
            startTimer()
            Log.i(TAG, "Session started: ${sessionManager.currentSessionId}")
        }
    }

    /**
     * Pauses the current recording session.
     */
    fun pauseSession() {
        viewModelScope.launch {
            stopTimer()
            polarEcgManager.stopEcgStream()
            stopPpgCollection()
            wearPpgManager.sendCommand(WearCommand.PauseAllTrackers)
            sessionManager.pauseSession()
            Log.i(TAG, "Session paused")
        }
    }

    /**
     * Resumes a paused session.
     */
    fun resumeSession() {
        viewModelScope.launch {
            sessionManager.resumeSession()
            startEcgCollection()
            wearPpgManager.sendCommand(WearCommand.StartAllTrackers)
            startPpgCollection()
            startTimer()
            Log.i(TAG, "Session resumed")
        }
    }

    /**
     * Stops the recording session and closes all CSV files.
     */
    fun stopSession() {
        viewModelScope.launch {
            stopTimer()
            polarEcgManager.stopEcgStream()
            stopEcgCollection()
            stopPpgCollection()
            wearPpgManager.sendCommand(WearCommand.StopAllTrackers)

            val summary = sessionManager.stopSession()
            _uiState.value = _uiState.value.copy(
                lastSessionSummary = summary,
                ecgSampleCount = summary.ecgSampleCount,
                ppgGreenSampleCount = summary.ppgGreenSampleCount,
                ppgIrSampleCount = summary.ppgIrSampleCount,
                ppgRedSampleCount = summary.ppgRedSampleCount
            )
            Log.i(TAG, "Session stopped. $summary")
        }
    }

    /**
     * Resets to IDLE state for a fresh session.
     */
    fun resetSession() {
        sessionManager.reset()
        _uiState.value = UiState()
    }

    // ===== Data Collection Flows =====

    private fun startEcgCollection() {
        ecgCollectionJob?.cancel()
        ecgCollectionJob = viewModelScope.launch {
            polarEcgManager.startEcgStream().collect { sample ->
                sessionManager.recordEcgSample(sample)
                _uiState.value = _uiState.value.copy(
                    ecgSampleCount = csvLogger.ecgSampleCount
                )
            }
        }
    }

    private fun stopEcgCollection() {
        ecgCollectionJob?.cancel()
        ecgCollectionJob = null
    }

    private fun startPpgCollection() {
        ppgGreenCollectionJob?.cancel()
        ppgGreenCollectionJob = viewModelScope.launch {
            wearPpgManager.ppgGreenFlow.collect { sample ->
                sessionManager.recordPpgSample(sample)
                _uiState.value = _uiState.value.copy(
                    ppgGreenSampleCount = csvLogger.ppgGreenSampleCount
                )
            }
        }

        ppgIrCollectionJob?.cancel()
        ppgIrCollectionJob = viewModelScope.launch {
            wearPpgManager.ppgIrFlow.collect { sample ->
                sessionManager.recordPpgSample(sample)
                _uiState.value = _uiState.value.copy(
                    ppgIrSampleCount = csvLogger.ppgIrSampleCount
                )
            }
        }

        ppgRedCollectionJob?.cancel()
        ppgRedCollectionJob = viewModelScope.launch {
            wearPpgManager.ppgRedFlow.collect { sample ->
                sessionManager.recordPpgSample(sample)
                _uiState.value = _uiState.value.copy(
                    ppgRedSampleCount = csvLogger.ppgRedSampleCount
                )
            }
        }
    }

    private fun stopPpgCollection() {
        ppgGreenCollectionJob?.cancel()
        ppgIrCollectionJob?.cancel()
        ppgRedCollectionJob?.cancel()
        ppgGreenCollectionJob = null
        ppgIrCollectionJob = null
        ppgRedCollectionJob = null
    }

    // ===== Timer =====

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (sessionManager.state.value == SessionState.RECORDING) {
                delay(1000L)
                if (sessionManager.state.value == SessionState.RECORDING) {
                    sessionManager.tickElapsedTime()
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onCleared() {
        timerJob?.cancel()
        ecgCollectionJob?.cancel()
        ppgGreenCollectionJob?.cancel()
        ppgIrCollectionJob?.cancel()
        ppgRedCollectionJob?.cancel()
        super.onCleared()
    }
}
