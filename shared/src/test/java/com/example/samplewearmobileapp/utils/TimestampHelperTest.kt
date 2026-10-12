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

    // Deterministic clock fixtures below are regression inputs, not sensor recordings.
    @Test
    fun `first packet is anchored at its last sample`() {
        TimestampHelper.anchorPolarTimestamp(1_000_000_000L, 10_000L)
        assertEquals(10_000L, TimestampHelper.polarNanosToAnchoredUnixMillis(1_000_000_000L))
        assertEquals(9_992L, TimestampHelper.polarNanosToAnchoredUnixMillis(992_307_692L))
    }

    @Test
    fun `130 Hz samples span one second without cumulative rounding drift`() {
        TimestampHelper.anchorPolarTimestamp(0L, 10_000L)
        val timestamps = (0..130).map {
            TimestampHelper.polarNanosToAnchoredUnixMillis(it * 1_000_000_000L / 130)
        }
        assertEquals(1000L, timestamps.last() - timestamps.first())
        assertTrue(timestamps.zipWithNext().all { (a, b) -> b - a in 7L..8L })
    }

    @Test
    fun `missing samples retain their device time gap`() {
        TimestampHelper.anchorPolarTimestamp(1_000_000_000L, 10_000L)
        assertEquals(15_000L, TimestampHelper.polarNanosToAnchoredUnixMillis(6_000_000_000L))
    }

    @Test
    fun `packet delay cannot change an established clock anchor`() {
        TimestampHelper.anchorPolarTimestamp(1_000_000_000L, 10_000L)
        TimestampHelper.anchorPolarTimestamp(2_000_000_000L, 30_000L)
        assertEquals(11_000L, TimestampHelper.polarNanosToAnchoredUnixMillis(2_000_000_000L))
    }

    @Test
    fun `resume clears the old anchor and retains the wall clock pause`() {
        TimestampHelper.anchorPolarTimestamp(1_000_000_000L, 10_000L)
        TimestampHelper.resetEcgTimestamps()
        TimestampHelper.anchorPolarTimestamp(20_000_000_000L, 29_000L)
        assertEquals(29_000L, TimestampHelper.polarNanosToAnchoredUnixMillis(20_000_000_000L))
    }

    @Test(expected = IllegalStateException::class)
    fun `unanchored ECG cannot silently invent a timestamp`() {
        TimestampHelper.polarNanosToAnchoredUnixMillis(1L)
    }

    @Test
    fun `accelerometer timestamp uses event time rather than delayed receipt time`() {
        assertEquals(5000L, TimestampHelper.elapsedNanosToUnixMillis(2_000_000_000L, 3_000_000_000L, 6000L))
    }

    // --- validateTimestamp ---

    @Test
    fun `RR timestamps preserve millisecond intervals and packet receipt anchor`() {
        assertArrayEquals(longArrayOf(9100L, 10000L), TimestampHelper.rrTimestampsFromReceipt(10000L, listOf(800, 900)))
    }

    @Test
    fun `RR timestamps cannot double the epoch or collapse a notification pause`() {
        val epoch = 1_792_000_000_000L
        val before = TimestampHelper.rrTimestampsFromReceipt(epoch, listOf(1000)).single()
        val after = TimestampHelper.rrTimestampsFromReceipt(epoch + 10000L, listOf(1000)).single()
        assertEquals(10000L, after - before)
        assertEquals(epoch + 10000L, after)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid RR intervals cannot generate fabricated beat timing`() {
        TimestampHelper.rrTimestampsFromReceipt(10000L, listOf(0))
    }

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
