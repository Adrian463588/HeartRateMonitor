package com.example.samplewearmobileapp.data

import android.util.Log
import com.example.samplewearmobileapp.sensor.EcgSample
import com.example.samplewearmobileapp.sensor.PpgSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Recording session state machine.
 */
enum class SessionState {
    IDLE,
    RECORDING,
    PAUSED,
    STOPPED
}

/**
 * Metadata for a recording session, entered by the researcher
 * before starting data collection.
 */
data class SessionConfig(
    val participantId: String,
    val conditionLabel: String,
    val polarDeviceId: String,
    val notes: String = ""
)

/**
 * Summary of a completed session, returned after stopping.
 */
data class SessionSummary(
    val sessionId: String,
    val startTime: Date,
    val stopTime: Date,
    val durationSeconds: Long,
    val ecgSampleCount: Long,
    val ppgGreenSampleCount: Long,
    val ppgIrSampleCount: Long,
    val ppgRedSampleCount: Long,
    val outputDirectory: String?
)

/**
 * Manages the lifecycle of a recording session.
 *
 * Coordinates the [CsvLogger] with session state transitions
 * (IDLE → RECORDING → PAUSED → STOPPED) and provides
 * researcher-facing metadata management.
 *
 * This class does NOT manage sensor connections directly;
 * the ViewModel orchestrates sensors + session manager together.
 */
class SessionManager(
    private val csvLogger: CsvLogger
) {
    companion object {
        private const val TAG = "SessionManager"
        private val SESSION_ID_FORMAT = SimpleDateFormat(
            "yyyy-MM-dd_HH-mm-ss", Locale.US
        )
    }

    private val _state = MutableStateFlow(SessionState.IDLE)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    var currentSessionId: String? = null; private set
    var currentConfig: SessionConfig? = null; private set
    private var startTime: Date? = null
    private var stopTime: Date? = null

    /**
     * Starts a new recording session.
     * Opens CSV files and transitions state to RECORDING.
     *
     * @param config Session configuration from the researcher.
     * @param ecgSampleRate ECG sample rate in Hz.
     * @param ppgGreenRate PPG Green sample rate in Hz.
     * @param ppgIrRedRate PPG IR/Red sample rate in Hz.
     */
    suspend fun startSession(
        config: SessionConfig,
        ecgSampleRate: Double = 130.0,
        ppgGreenRate: Double = 25.0,
        ppgIrRedRate: Double = 100.0
    ) {
        if (_state.value == SessionState.RECORDING) {
            Log.w(TAG, "Session already recording. Ignoring start request.")
            return
        }

        currentConfig = config
        currentSessionId = SESSION_ID_FORMAT.format(Date())
        startTime = Date()
        _elapsedSeconds.value = 0L

        csvLogger.startSession(
            sessionId = currentSessionId!!,
            participantId = config.participantId,
            conditionLabel = config.conditionLabel,
            deviceId = config.polarDeviceId,
            ecgSampleRate = ecgSampleRate,
            ppgGreenRate = ppgGreenRate,
            ppgIrRedRate = ppgIrRedRate
        )

        _state.value = SessionState.RECORDING
        Log.i(TAG, "Session started: $currentSessionId " +
                "(participant=${config.participantId}, condition=${config.conditionLabel})")
    }

    /**
     * Pauses the current recording session.
     * Flushes CSV files to disk.
     */
    suspend fun pauseSession() {
        if (_state.value != SessionState.RECORDING) {
            Log.w(TAG, "Cannot pause — not recording. Current state: ${_state.value}")
            return
        }
        csvLogger.flush()
        _state.value = SessionState.PAUSED
        Log.i(TAG, "Session paused at ${_elapsedSeconds.value}s")
    }

    /**
     * Resumes a paused session.
     */
    fun resumeSession() {
        if (_state.value != SessionState.PAUSED) {
            Log.w(TAG, "Cannot resume — not paused. Current state: ${_state.value}")
            return
        }
        _state.value = SessionState.RECORDING
        Log.i(TAG, "Session resumed at ${_elapsedSeconds.value}s")
    }

    /**
     * Stops the recording session and closes all CSV files.
     *
     * @return A [SessionSummary] with data counts and output path.
     */
    suspend fun stopSession(): SessionSummary {
        stopTime = Date()
        val outputDir = csvLogger.closeSession()
        _state.value = SessionState.STOPPED

        val summary = SessionSummary(
            sessionId = currentSessionId ?: "unknown",
            startTime = startTime ?: Date(),
            stopTime = stopTime!!,
            durationSeconds = _elapsedSeconds.value,
            ecgSampleCount = csvLogger.ecgSampleCount,
            ppgGreenSampleCount = csvLogger.ppgGreenSampleCount,
            ppgIrSampleCount = csvLogger.ppgIrSampleCount,
            ppgRedSampleCount = csvLogger.ppgRedSampleCount,
            outputDirectory = outputDir
        )

        Log.i(TAG, "Session stopped: $summary")
        return summary
    }

    /**
     * Writes an ECG sample if the session is actively recording.
     */
    suspend fun recordEcgSample(sample: EcgSample) {
        if (_state.value == SessionState.RECORDING) {
            csvLogger.writeEcgSample(sample)
        }
    }

    /**
     * Writes a PPG sample if the session is actively recording.
     */
    suspend fun recordPpgSample(sample: PpgSample) {
        if (_state.value == SessionState.RECORDING) {
            csvLogger.writePpgSample(sample)
        }
    }

    /**
     * Increments the elapsed second counter.
     * Called by the ViewModel's timer coroutine.
     */
    fun tickElapsedTime() {
        _elapsedSeconds.value++
    }

    /**
     * Resets the session manager to IDLE state for a new session.
     */
    fun reset() {
        _state.value = SessionState.IDLE
        _elapsedSeconds.value = 0L
        currentSessionId = null
        currentConfig = null
        startTime = null
        stopTime = null
    }
}
