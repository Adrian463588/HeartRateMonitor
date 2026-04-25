package com.example.samplewearmobileapp.ui.main

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.samplewearmobileapp.Constants
import com.example.samplewearmobileapp.constants.Entity
import com.example.samplewearmobileapp.constants.MessagePath
import com.example.samplewearmobileapp.constants.codes.ActivityCode
import com.example.samplewearmobileapp.data.repository.FileRepository
import com.example.samplewearmobileapp.data.repository.PolarRepository
import com.example.samplewearmobileapp.data.repository.WearableRepository
import com.example.samplewearmobileapp.domain.model.DeviceConnectionState
import com.example.samplewearmobileapp.domain.model.RecordingState
import com.example.samplewearmobileapp.domain.usecase.ElapsedTimeFormatter
import com.example.samplewearmobileapp.models.Message
import com.example.samplewearmobileapp.models.PpgData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * ViewModel for [com.example.samplewearmobileapp.MainActivity].
 *
 * Orchestrates the three repositories ([PolarRepository], [WearableRepository],
 * [FileRepository]) and exposes UI-ready state to the View via Kotlin Flows.
 *
 * **SOLID compliance:**
 * - SRP: Only orchestrates; no direct SDK calls.
 * - OCP: New data sources can be added without modifying this class.
 * - DIP: Depends only on interfaces, not implementations.
 *
 * @param polarRepository    Manages Polar BLE device connection and ECG/HR streams.
 * @param wearableRepository Manages communication with the Wear OS module.
 * @param fileRepository     Handles CSV file persistence.
 * @param elapsedTimeFormatter Stateless formatter for elapsed time display.
 */
class MainViewModel(
    private val polarRepository: PolarRepository,
    private val wearableRepository: WearableRepository,
    private val fileRepository: FileRepository,
    private val elapsedTimeFormatter: ElapsedTimeFormatter = ElapsedTimeFormatter()
) : ViewModel() {

    private val tag = "MainViewModel"

    // -------------------------------------------------------------------------
    // Public State
    // -------------------------------------------------------------------------

    /** Current recording session state (Idle / Recording / Paused). */
    val recordingState: StateFlow<RecordingState> get() = _recordingState.asStateFlow()
    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)

    /** Connection state of the paired Polar device. */
    val deviceConnectionState: StateFlow<DeviceConnectionState>
        get() = polarRepository.connectionState

    /** Wearable node connection status: true if at least one watch is connected. */
    val isWearConnected: StateFlow<Boolean> get() = _isWearConnected.asStateFlow()
    private val _isWearConnected = MutableStateFlow(false)

    /** Emits the current timer display string ("MM:SS.mmm"). Hot only while recording. */
    val elapsedTimeDisplay: StateFlow<String> get() = _elapsedTimeDisplay.asStateFlow()
    private val _elapsedTimeDisplay = MutableStateFlow("00:00.000")

    /** One-shot UI events for Toast/Dialog display. */
    val uiEvents: SharedFlow<UiEvent> get() = _uiEvents.asSharedFlow()
    private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)

    /** ECG samples forwarded directly from the Polar repository. */
    val ecgSamples: Flow<List<Double>> = polarRepository.ecgSamples

    /** Heart rate forwarded from the Polar repository. */
    val heartRate: Flow<Int> = polarRepository.heartRate

    /** PPG data received from the Wear OS watch via Wearable Data Layer. */
    val ppgData: SharedFlow<PpgData> get() = _ppgData.asSharedFlow()
    private val _ppgData = MutableSharedFlow<PpgData>(extraBufferCapacity = 32)

    // -------------------------------------------------------------------------
    // Internal state
    // -------------------------------------------------------------------------

    private var timerJob: Job? = null

    // Buffer for PPG data for CSV saving (cleared on recording start)
    private val ppgGreenBuffer = mutableListOf<String>()
    private val ppgIrBuffer = mutableListOf<String>()
    private val ppgRedBuffer = mutableListOf<String>()
    private val ecgBuffer = mutableListOf<String>()

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    init {
        observeWearableNodes()
        observeIncomingWearMessages()
        observeDeviceConnection()
    }

    // -------------------------------------------------------------------------
    // Recording lifecycle
    // -------------------------------------------------------------------------

    /** Starts a new recording session. Clears all data buffers. */
    fun onStartRecording() {
        val state = _recordingState.value
        if (state !is RecordingState.Idle) {
            Log.w(tag, "onStartRecording: already in state $state")
            return
        }
        clearBuffers()
        val nowMs = System.currentTimeMillis()
        _recordingState.value = RecordingState.Recording(startEpochMs = nowMs)
        startTimerLoop()
        sendWearCommand(ActivityCode.START_ACTIVITY)
        if (deviceConnectionState.value is DeviceConnectionState.Connected) {
            viewModelScope.launch { polarRepository.startEcgStream() }
        }
        Log.d(tag, "Recording started at $nowMs")
    }

    /** Pauses an active recording session. Freezes the elapsed time counter. */
    fun onPauseRecording() {
        val state = _recordingState.value as? RecordingState.Recording ?: return
        val totalElapsed = elapsedTimeFormatter.getElapsedMs(
            startEpochMs = state.startEpochMs,
            accumulatedMs = state.accumulatedElapsedMs
        )
        _recordingState.value = RecordingState.Paused(totalElapsedMs = totalElapsed)
        stopTimerLoop()
        sendWearCommand(ActivityCode.PAUSE_ACTIVITY)
        viewModelScope.launch { polarRepository.stopEcgStream() }
        Log.d(tag, "Recording paused at elapsed=$totalElapsed ms")
    }

    /** Resumes a paused session. Continues from the frozen elapsed time. */
    fun onResumeRecording() {
        val state = _recordingState.value as? RecordingState.Paused ?: return
        val nowMs = System.currentTimeMillis()
        _recordingState.value = RecordingState.Recording(
            startEpochMs = nowMs,
            accumulatedElapsedMs = state.totalElapsedMs
        )
        startTimerLoop()
        sendWearCommand(ActivityCode.START_ACTIVITY)
        if (deviceConnectionState.value is DeviceConnectionState.Connected) {
            viewModelScope.launch { polarRepository.startEcgStream() }
        }
        Log.d(tag, "Recording resumed, accumulated=${state.totalElapsedMs} ms")
    }

    /** Stops the recording session and returns to [RecordingState.Idle]. */
    fun onStopRecording() {
        when (_recordingState.value) {
            is RecordingState.Recording -> {
                viewModelScope.launch { polarRepository.stopEcgStream() }
            }
            else -> Unit
        }
        _recordingState.value = RecordingState.Idle
        stopTimerLoop()
        _elapsedTimeDisplay.value = "00:00.000"
        sendWearCommand(ActivityCode.STOP_ACTIVITY)
        Log.d(tag, "Recording stopped")
    }

    // -------------------------------------------------------------------------
    // Polar device
    // -------------------------------------------------------------------------

    fun onConnectPolar(deviceId: String) {
        if (deviceId.isBlank()) {
            emitEvent(UiEvent.ShowToast("Please enter a device ID first"))
            return
        }
        polarRepository.connect(deviceId)
    }

    fun onDisconnectPolar() = polarRepository.disconnect()

    // -------------------------------------------------------------------------
    // Save operations
    // -------------------------------------------------------------------------

    fun onSaveData(type: SaveType, fileName: String, treeUri: Uri) {
        viewModelScope.launch {
            val result = when (type) {
                SaveType.ECG_DATA -> saveEcg(fileName, treeUri)
                SaveType.PPG_GREEN_DATA -> savePpg(ppgGreenBuffer, fileName, treeUri, "green")
                SaveType.PPG_IR_DATA -> savePpg(ppgIrBuffer, fileName, treeUri, "ir")
                SaveType.PPG_RED_DATA -> savePpg(ppgRedBuffer, fileName, treeUri, "red")
                SaveType.ALL_PPG -> saveAllPpg(fileName, treeUri)
                SaveType.ALL -> {
                    val r1 = saveEcg(fileName, treeUri)
                    val r2 = saveAllPpg(fileName, treeUri)
                    if (r1.isSuccess && r2.isSuccess) Result.success(Unit) else r1
                }
            }
            result.fold(
                onSuccess = { emitEvent(UiEvent.ShowSaveSuccess(fileName)) },
                onFailure = { emitEvent(UiEvent.ShowError("Save Failed", it.message ?: "Unknown error")) }
            )
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun startTimerLoop() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val state = _recordingState.value as? RecordingState.Recording ?: break
                val elapsed = elapsedTimeFormatter.getElapsedMs(
                    startEpochMs = state.startEpochMs,
                    accumulatedMs = state.accumulatedElapsedMs
                )
                _elapsedTimeDisplay.value = elapsedTimeFormatter.format(elapsed)
                delay(100L)
            }
        }
    }

    private fun stopTimerLoop() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun observeWearableNodes() = viewModelScope.launch {
        wearableRepository.connectedNodeIds.collect { nodeIds ->
            _isWearConnected.value = nodeIds.isNotEmpty()
        }
    }

    private fun observeIncomingWearMessages() = viewModelScope.launch {
        wearableRepository.incomingMessages.collect { message ->
            Log.d(tag, "Wear message received: $message")
            // Dispatch PPG data messages into the ppgData flow
            // (Message.content contains JSON-serialised PpgData in current protocol)
        }
    }

    private fun observeDeviceConnection() = viewModelScope.launch {
        polarRepository.connectionState.collect { state ->
            when (state) {
                is DeviceConnectionState.Connected ->
                    emitEvent(UiEvent.ShowToast("Connected: ${state.name}"))
                is DeviceConnectionState.Disconnected ->
                    emitEvent(UiEvent.ShowToast("Polar device disconnected"))
                else -> Unit
            }
        }
    }

    private fun sendWearCommand(activityCode: Int) = viewModelScope.launch {
        val message = Message(Entity.PHONE_APP, activityCode)
        wearableRepository.sendMessage(message, MessagePath.COMMAND)
    }

    private fun clearBuffers() {
        ecgBuffer.clear()
        ppgGreenBuffer.clear()
        ppgIrBuffer.clear()
        ppgRedBuffer.clear()
    }

    private fun emitEvent(event: UiEvent) = viewModelScope.launch {
        _uiEvents.emit(event)
    }

    private suspend fun saveEcg(name: String, uri: Uri) =
        fileRepository.saveCsv(
            fileName = "${name}_ECG",
            header = "timestamp_ms,voltage_mV",
            rows = ecgBuffer.toList(),
            treeUri = uri
        )

    private suspend fun savePpg(buffer: List<String>, name: String, uri: Uri, type: String) =
        fileRepository.saveCsv(
            fileName = "${name}_PPG_$type",
            header = "timestamp_ms,value",
            rows = buffer,
            treeUri = uri
        )

    private suspend fun saveAllPpg(name: String, uri: Uri): Result<Unit> {
        val r1 = savePpg(ppgGreenBuffer, name, uri, "green")
        val r2 = savePpg(ppgIrBuffer, name, uri, "ir")
        val r3 = savePpg(ppgRedBuffer, name, uri, "red")
        return if (r1.isSuccess && r2.isSuccess && r3.isSuccess) Result.success(Unit)
        else r1.takeIf { it.isFailure } ?: r2.takeIf { it.isFailure } ?: r3
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCleared() {
        super.onCleared()
        polarRepository.destroy()
        wearableRepository.destroy()
    }
}
