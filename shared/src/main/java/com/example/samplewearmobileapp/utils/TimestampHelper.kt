package com.example.samplewearmobileapp.utils

import java.util.Calendar
import java.util.TimeZone
import java.util.logging.Logger

/**
 * Centralized timestamp normalization utility for synchronizing
 * data from different sensor time domains:
 *
 * - **Samsung Galaxy Watch (PPG):** Unix Epoch millis (ms since Jan 1, 1970 UTC)
 * - **Polar H10/Verity (ECG):** Nanoseconds since Jan 1, 2000 UTC
 *
 * This helper converts Polar timestamps to Unix millis and provides
 * sanity checks to detect synchronization failures.
 */
object TimestampHelper {
    private val logger = Logger.getLogger("TimestampHelper")

    /**
     * Offset in milliseconds from Unix epoch (1970-01-01T00:00:00Z)
     * to Polar epoch (2000-01-01T00:00:00Z).
     *
     * Calculated using UTC calendar to avoid local-timezone errors.
     * Value: 946684800000 ms (30 years worth of milliseconds).
     */
    val POLAR_EPOCH_OFFSET_MS: Long = run {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(2000, Calendar.JANUARY, 1, 0, 0, 0)
        cal.timeInMillis
    }

    /** Maximum allowed delta (in ms) between signal start and session start */
    private const val MAX_DRIFT_MS = 1_000_000L // 1000 seconds

    /**
     * Converts a Polar device timestamp (nanoseconds since 2000-01-01 UTC)
     * to standard Unix epoch milliseconds (since 1970-01-01 UTC).
     *
     * Formula: Unix_Millis = (Polar_Nanos / 1,000,000) + POLAR_EPOCH_OFFSET_MS
     *
     * @param polarNanos The raw timestamp from the Polar device in nanoseconds.
     * @return The equivalent timestamp in Unix epoch milliseconds.
     */
    fun polarNanosToUnixMillis(polarNanos: Long): Long {
        val millisSince2000 = polarNanos / 1_000_000L
        return millisSince2000 + POLAR_EPOCH_OFFSET_MS
    }

    // --- Phone-Clock ECG Timestamp Generator ---
    // Bypasses unreliable Polar device timestamps entirely.
    // Uses the phone's wall clock (same time domain as PPG)
    // with Polar's precise sample rate for inter-sample intervals.

    /** Phone wall clock time when ECG streaming started */
    private var ecgStreamStartMs: Long = 0L
    /** Running sample counter since stream start */
    private var ecgSampleIndex: Long = 0L
    private var isEcgStreamStarted = false

    /**
     * Resets the ECG timestamp state so the phone-clock anchor
     * will be captured lazily when the first ECG sample actually arrives
     * (unless [setEcgAnchor] was called to pre-set the anchor).
     * Call this when ECG streaming is about to begin (before the async setup).
     */
    fun resetEcgTimestamps() {
        ecgSampleIndex = 0L
        isEcgStreamStarted = false
        // Don't clear ecgStreamStartMs — it may have been pre-set by setEcgAnchor()
        logger.info("ECG timestamps reset; anchor will be set on first sample arrival")
    }

    /**
     * Pre-sets the ECG anchor to a specific wall-clock time.
     * Use this to pin ECG T0 to the recording start moment,
     * so ECG and PPG timestamps share the same reference point.
     *
     * Must be called **before** [resetEcgTimestamps] or [nextEcgTimestamp].
     *
     * @param anchorMs Unix epoch millis to use as ECG T0 (e.g. `startTime.time`).
     */
    fun setEcgAnchor(anchorMs: Long) {
        ecgStreamStartMs = anchorMs
        ecgSampleIndex = 0L
        isEcgStreamStarted = false
        logger.info("ECG anchor pre-set to $anchorMs (recording start time)")
    }

    /**
     * Returns the next ECG sample timestamp in Unix epoch millis,
     * computed from the phone's wall clock + sample index.
     *
     * Formula: `startTime + (sampleIndex × 1000 / sampleRate)`
     *
     * This guarantees timestamps are in the same time domain as PPG
     * (phone wall clock) while preserving precise inter-sample intervals.
     *
     * @param sampleRateHz The ECG sample rate (e.g. 130.0 for Polar H10).
     * @return Timestamp in Unix epoch milliseconds.
     */
    fun nextEcgTimestamp(sampleRateHz: Double): Long {
        if (!isEcgStreamStarted) {
            if (ecgStreamStartMs == 0L) {
                // No pre-set anchor — fall back to current phone time
                ecgStreamStartMs = System.currentTimeMillis()
                logger.info("ECG phone-clock anchor set at $ecgStreamStartMs (first sample arrival, no pre-set anchor)")
            } else {
                logger.info("ECG streaming started with pre-set anchor at $ecgStreamStartMs")
            }
            ecgSampleIndex = 0L
            isEcgStreamStarted = true
        }
        val ts = ecgStreamStartMs + (ecgSampleIndex * 1000.0 / sampleRateHz).toLong()
        ecgSampleIndex++
        return ts
    }

    /**
     * @deprecated Use [nextEcgTimestamp] instead.
     * Kept for backward compatibility.
     */
    fun resetAnchor() {
        resetEcgTimestamps()
    }

    /**
     * @deprecated Use [nextEcgTimestamp] instead.
     * Kept for backward compatibility.
     */
    @Suppress("UNUSED_PARAMETER")
    fun polarNanosToAnchoredUnixMillis(polarNanos: Long): Long {
        return nextEcgTimestamp(130.0)
    }

    /**
     * Validates and optionally corrects a signal timestamp against a session
     * start time. Detects two failure modes:
     *
     * 1. **Negative delta:** Signal timestamp is AFTER session start
     *    (impossible if measurement started before the test).
     * 2. **Excessive drift:** Delta exceeds [MAX_DRIFT_MS] (1000 seconds),
     *    indicating a clock synchronization failure.
     *
     * When a failure is detected, the method falls back to
     * [System.currentTimeMillis] as the arrival-time approximation.
     *
     * @param signalTimestampMs The signal's timestamp in Unix millis.
     * @param sessionStartMs   The test session's start timestamp in Unix millis.
     *                         Pass 0 or null to skip validation.
     * @return A [ValidatedTimestamp] containing the (possibly corrected) timestamp
     *         and a flag indicating whether correction was applied.
     */
    fun validateTimestamp(
        signalTimestampMs: Long,
        sessionStartMs: Long?
    ): ValidatedTimestamp {
        if (sessionStartMs == null || sessionStartMs == 0L) {
            return ValidatedTimestamp(signalTimestampMs, isValid = true, wasCorrected = false)
        }

        val delta = sessionStartMs - signalTimestampMs

        // Negative check: signal timestamp is AFTER session start
        if (delta < 0) {
            val fallback = System.currentTimeMillis()
            logger.warning("Negative delta detected! " +
                    "Signal=$signalTimestampMs > Session=$sessionStartMs " +
                    "(delta=${delta}ms). Correcting to arrival time: $fallback")
            return ValidatedTimestamp(fallback, isValid = false, wasCorrected = true)
        }

        // Drift check: delta exceeds 1000 seconds
        if (delta > MAX_DRIFT_MS) {
            val fallback = System.currentTimeMillis()
            logger.warning("Excessive drift detected! " +
                    "Delta=${delta}ms (${delta / 1000}s) exceeds ${MAX_DRIFT_MS / 1000}s threshold. " +
                    "Signal=$signalTimestampMs, Session=$sessionStartMs. " +
                    "Falling back to arrival time: $fallback")
            return ValidatedTimestamp(fallback, isValid = false, wasCorrected = true)
        }

        return ValidatedTimestamp(signalTimestampMs, isValid = true, wasCorrected = false)
    }

    /**
     * Result of timestamp validation.
     *
     * @property timestampMs    The final (possibly corrected) timestamp in Unix millis.
     * @property isValid        Whether the original timestamp passed sanity checks.
     * @property wasCorrected   Whether the timestamp was replaced with a fallback value.
     */
    data class ValidatedTimestamp(
        val timestampMs: Long,
        val isValid: Boolean,
        val wasCorrected: Boolean
    )
}
