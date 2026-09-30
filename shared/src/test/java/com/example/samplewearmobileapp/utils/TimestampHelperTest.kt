package com.example.samplewearmobileapp.utils

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TimestampHelperTest {

    @Before
    fun setUp() {
        // Reset state between tests
        TimestampHelper.resetEcgTimestamps()
    }

    // --- POLAR_EPOCH_OFFSET_MS ---

    @Test
    fun `POLAR_EPOCH_OFFSET_MS is approximately 30 years in milliseconds`() {
        // Jan 1 2000 UTC = 946684800000 ms from Unix epoch
        val expected = 946684800000L
        assertEquals(expected, TimestampHelper.POLAR_EPOCH_OFFSET_MS)
    }

    // --- polarNanosToUnixMillis ---

    @Test
    fun `polarNanosToUnixMillis converts zero to epoch offset`() {
        val result = TimestampHelper.polarNanosToUnixMillis(0L)
        assertEquals(TimestampHelper.POLAR_EPOCH_OFFSET_MS, result)
    }

    @Test
    fun `polarNanosToUnixMillis converts 1 second in nanos correctly`() {
        val oneSecondNanos = 1_000_000_000L // 1 second
        val result = TimestampHelper.polarNanosToUnixMillis(oneSecondNanos)
        assertEquals(TimestampHelper.POLAR_EPOCH_OFFSET_MS + 1000L, result)
    }

    @Test
    fun `polarNanosToUnixMillis converts arbitrary timestamp`() {
        val polarNanos = 5_000_000_000L // 5 seconds since Polar epoch
        val result = TimestampHelper.polarNanosToUnixMillis(polarNanos)
        assertEquals(TimestampHelper.POLAR_EPOCH_OFFSET_MS + 5000L, result)
    }

    // --- nextEcgTimestamp with setEcgAnchor ---

    @Test
    fun `nextEcgTimestamp returns anchor time for first sample`() {
        val anchor = 1000000L
        TimestampHelper.setEcgAnchor(anchor)
        val ts = TimestampHelper.nextEcgTimestamp(130.0)
        assertEquals(anchor, ts)
    }

    @Test
    fun `nextEcgTimestamp produces sequential timestamps at correct interval`() {
        val anchor = 1000000L
        val sampleRate = 130.0
        TimestampHelper.setEcgAnchor(anchor)

        val ts0 = TimestampHelper.nextEcgTimestamp(sampleRate) // index 0
        val ts1 = TimestampHelper.nextEcgTimestamp(sampleRate) // index 1
        val ts2 = TimestampHelper.nextEcgTimestamp(sampleRate) // index 2

        assertEquals(anchor, ts0)
        // ts1 = anchor + (1 * 1000 / 130) = anchor + 7
        val expectedInterval = (1000.0 / sampleRate).toLong()
        assertEquals(anchor + expectedInterval, ts1)
        assertEquals(anchor + (2 * 1000.0 / sampleRate).toLong(), ts2)
    }

    @Test
    fun `nextEcgTimestamp without anchor uses system time`() {
        // Explicitly clear any pre-set anchor from previous tests
        TimestampHelper.setEcgAnchor(0L)
        TimestampHelper.resetEcgTimestamps()
        val beforeMs = System.currentTimeMillis()
        val ts = TimestampHelper.nextEcgTimestamp(130.0)
        val afterMs = System.currentTimeMillis()
        // Allow small tolerance for GC/scheduling jitter
        assertTrue("Timestamp $ts should be near system time [$beforeMs..$afterMs]",
            ts >= beforeMs - 10 && ts <= afterMs + 10)
    }

    // --- resetEcgTimestamps ---

    @Test
    fun `resetEcgTimestamps restarts sample index`() {
        val anchor = 5000L
        TimestampHelper.setEcgAnchor(anchor)
        TimestampHelper.nextEcgTimestamp(130.0) // index 0
        TimestampHelper.nextEcgTimestamp(130.0) // index 1
        TimestampHelper.nextEcgTimestamp(130.0) // index 2

        // After reset, the pre-set anchor should be cleared by resetEcgTimestamps
        // and the next call should re-anchor
        TimestampHelper.setEcgAnchor(anchor)
        TimestampHelper.resetEcgTimestamps()
        // The anchor was set before reset, so it's retained
        val ts = TimestampHelper.nextEcgTimestamp(130.0) // index 0 again
        assertEquals(anchor, ts)
    }

    // --- validateTimestamp ---

    @Test
    fun `validateTimestamp passes when session is null`() {
        val result = TimestampHelper.validateTimestamp(1000L, null)
        assertTrue(result.isValid)
        assertFalse(result.wasCorrected)
        assertEquals(1000L, result.timestampMs)
    }

    @Test
    fun `validateTimestamp passes when session is zero`() {
        val result = TimestampHelper.validateTimestamp(1000L, 0L)
        assertTrue(result.isValid)
        assertFalse(result.wasCorrected)
    }

    @Test
    fun `validateTimestamp passes for valid delta within threshold`() {
        val session = 2000L
        val signal = 1500L // delta = 500 ms, well within threshold
        val result = TimestampHelper.validateTimestamp(signal, session)
        assertTrue(result.isValid)
        assertFalse(result.wasCorrected)
        assertEquals(signal, result.timestampMs)
    }

    @Test
    fun `validateTimestamp detects negative delta and corrects`() {
        val session = 1000L
        val signal = 2000L // signal AFTER session → negative delta
        val result = TimestampHelper.validateTimestamp(signal, session)
        assertFalse(result.isValid)
        assertTrue(result.wasCorrected)
        // Corrected timestamp should be near current time
        assertTrue(result.timestampMs > 0)
    }

    @Test
    fun `validateTimestamp detects excessive drift and corrects`() {
        val session = 2_000_000L
        val signal = 1L // delta = 1,999,999 ms → exceeds 1,000,000 ms threshold
        val result = TimestampHelper.validateTimestamp(signal, session)
        assertFalse(result.isValid)
        assertTrue(result.wasCorrected)
    }

    @Test
    fun `validateTimestamp passes at exactly the threshold boundary`() {
        val session = 1_000_001L
        val signal = 1L // delta = 1,000,000 ms = exactly at threshold
        val result = TimestampHelper.validateTimestamp(signal, session)
        assertTrue(result.isValid)
        assertFalse(result.wasCorrected)
    }
}
