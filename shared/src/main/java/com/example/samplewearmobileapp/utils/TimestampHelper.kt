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

    private var polarAnchorNanos: Long? = null
    private var phoneAnchorMs = 0L

    /** Reset on every new ECG subscription, including resume/reconnect. */
    fun resetEcgTimestamps() {
        polarAnchorNanos = null
        phoneAnchorMs = 0L
    }

    /**
     * Anchor the LAST sample of the first nonempty packet to receipt time.
     * Later packets retain the device clock, including missing samples and gaps.
     * Absolute alignment remains an approximation affected by BLE latency.
     */
    fun anchorPolarTimestamp(lastSampleNanos: Long, receiptMs: Long) {
        require(lastSampleNanos >= 0L && receiptMs > 0L)
        if (polarAnchorNanos == null) {
            polarAnchorNanos = lastSampleNanos
            phoneAnchorMs = receiptMs
        }
    }

    fun polarNanosToAnchoredUnixMillis(polarNanos: Long): Long {
        require(polarNanos >= 0L)
        val anchor = checkNotNull(polarAnchorNanos) { "ECG clock must be anchored first" }
        // floorDiv also preserves ordering for samples earlier than the packet anchor.
        return phoneAnchorMs + Math.floorDiv(polarNanos - anchor, 1_000_000L)
    }

    /** Android SensorEvent.timestamp shares the elapsed-realtime nanosecond clock. */
    fun elapsedNanosToUnixMillis(eventNanos: Long, elapsedAnchorNanos: Long, unixAnchorMs: Long): Long {
        return unixAnchorMs + Math.floorDiv(eventNanos - elapsedAnchorNanos, 1_000_000L)
    }

    /** RR intervals are already milliseconds in PolarHrData.rrsMs. Last beat uses packet receipt time. */
    fun rrTimestampsFromReceipt(receiptMs: Long, rrsMs: List<Int>): LongArray {
        require(receiptMs > 0L && rrsMs.all { it > 0 })
        var timestamp = receiptMs - rrsMs.sumOf { it.toLong() }
        return LongArray(rrsMs.size) { index ->
            timestamp += rrsMs[index]
            timestamp
        }
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
