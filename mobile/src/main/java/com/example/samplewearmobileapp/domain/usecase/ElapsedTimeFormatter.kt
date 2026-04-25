package com.example.samplewearmobileapp.domain.usecase

/**
 * Encapsulates elapsed-time decomposition logic, making it independently testable.
 *
 * Extracted from the monolithic [MainActivity] to respect SRP.
 *
 * @see ElapsedTimeFormatterTest
 */
class ElapsedTimeFormatter {

    /**
     * Decomposes total elapsed milliseconds into human-readable components.
     *
     * @param elapsedMs Non-negative elapsed time in milliseconds.
     * @return [ElapsedTime] with minutes, seconds, and sub-second milliseconds.
     */
    fun decompose(elapsedMs: Long): ElapsedTime {
        require(elapsedMs >= 0) { "elapsedMs must be non-negative, got $elapsedMs" }
        val totalSeconds = elapsedMs / 1000L
        val ms = (elapsedMs % 1000L).toInt()
        val sec = (totalSeconds % 60L).toInt()
        val min = (totalSeconds / 60L).toInt()
        return ElapsedTime(min, sec, ms)
    }

    /**
     * Formats elapsed milliseconds as a `MM:SS.mmm` string for display.
     *
     * @param elapsedMs Non-negative elapsed time in milliseconds.
     * @return Formatted string, e.g. `"05:03.450"`.
     */
    fun format(elapsedMs: Long): String {
        val (min, sec, ms) = decompose(elapsedMs)
        return "%02d:%02d.%03d".format(min, sec, ms)
    }

    /**
     * Calculates total elapsed ms for a [Recording] state, including
     * time accumulated from previous pause/resume cycles.
     *
     * @param startEpochMs Wall-clock Unix ms when current segment started.
     * @param accumulatedMs Elapsed ms accumulated from previous completed segments.
     * @param nowEpochMs Current Unix time (ms). Defaults to [System.currentTimeMillis].
     * @return Total elapsed ms since recording began.
     */
    fun getElapsedMs(
        startEpochMs: Long,
        accumulatedMs: Long,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Long = accumulatedMs + (nowEpochMs - startEpochMs)

    /**
     * Immutable value object for a decomposed elapsed time.
     */
    data class ElapsedTime(
        val minutes: Int,
        val seconds: Int,
        val millis: Int
    )
}
