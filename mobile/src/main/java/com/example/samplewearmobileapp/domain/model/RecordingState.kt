package com.example.samplewearmobileapp.domain.model

/**
 * Represents the current state of a sensor recording session.
 *
 * Sealed class enforces exhaustive `when` expressions in the ViewModel and UI layers,
 * eliminating the bug-prone dual-boolean pattern (`isRecording && isPaused`).
 *
 * Transition rules:
 *   [Idle] → [Recording]   via `onStartRecording()`
 *   [Recording] → [Paused] via `onPauseRecording()`
 *   [Paused] → [Recording] via `onResumeRecording()`
 *   [Recording|Paused] → [Idle] via `onStopRecording()`
 */
sealed class RecordingState {

    /** No active session. Initial state. */
    data object Idle : RecordingState()

    /**
     * Session is actively recording.
     * @param startEpochMs Wall-clock time (Unix ms) when the current segment started.
     * @param accumulatedElapsedMs Total elapsed ms from all previous segments (pause/resume cycles).
     */
    data class Recording(
        val startEpochMs: Long,
        val accumulatedElapsedMs: Long = 0L
    ) : RecordingState()

    /**
     * Session is paused.
     * @param totalElapsedMs Total elapsed ms at the point of pausing. Preserved across resumes.
     */
    data class Paused(val totalElapsedMs: Long) : RecordingState()
}
