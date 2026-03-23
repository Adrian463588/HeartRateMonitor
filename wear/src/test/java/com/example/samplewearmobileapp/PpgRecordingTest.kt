package com.example.samplewearmobileapp

import com.example.samplewearmobileapp.models.PpgType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PpgRecordingTest {

    private lateinit var recording: PpgRecording

    @Before
    fun setUp() {
        recording = PpgRecording(PpgType.PPG_GREEN)
    }

    // --- Constructor Tests ---

    @Test
    fun `default constructor with type sets ppgType`() {
        assertEquals(PpgType.PPG_GREEN, recording.ppgType)
        assertTrue(recording.values.isEmpty())
        assertTrue(recording.timestamps.isEmpty())
    }

    @Test
    fun `constructor with data copies values and timestamps`() {
        val values = listOf(100, 200, 300)
        val timestamps = listOf(1000L, 2000L, 3000L)
        val rec = PpgRecording(values, timestamps, PpgType.PPG_IR)

        assertEquals(3, rec.values.size)
        assertEquals(3, rec.timestamps.size)
        assertEquals(PpgType.PPG_IR, rec.ppgType)
        assertEquals(listOf(100, 200, 300), rec.values)
        assertEquals(listOf(1000L, 2000L, 3000L), rec.timestamps)
    }

    @Test
    fun `copy constructor does not share list reference`() {
        val original = mutableListOf(1, 2, 3)
        val timestamps = mutableListOf(10L, 20L, 30L)
        val rec = PpgRecording(original, timestamps, PpgType.PPG_RED)

        original.add(4)
        assertEquals(3, rec.values.size) // should not see the added element
    }

    // --- add() Tests ---

    @Test
    fun `add appends value and timestamp`() {
        recording.add(42, 12345L)
        assertEquals(1, recording.values.size)
        assertEquals(42, recording.values[0])
        assertEquals(12345L, recording.timestamps[0])
    }

    @Test
    fun `add multiple values maintains order`() {
        recording.add(1, 100L)
        recording.add(2, 200L)
        recording.add(3, 300L)
        assertEquals(listOf(1, 2, 3), recording.values)
        assertEquals(listOf(100L, 200L, 300L), recording.timestamps)
    }

    // --- clearFromStartUntil() Tests ---

    @Test
    fun `clearFromStartUntil removes exact count from front`() {
        for (i in 1..5) recording.add(i, i.toLong() * 100)

        recording.clearFromStartUntil(3) // Remove first 3 elements
        assertEquals(2, recording.values.size)
        assertEquals(listOf(4, 5), recording.values)
        assertEquals(listOf(400L, 500L), recording.timestamps)
    }

    @Test
    fun `clearFromStartUntil with zero removes nothing`() {
        recording.add(1, 100L)
        recording.add(2, 200L)

        recording.clearFromStartUntil(0)
        assertEquals(2, recording.values.size)
    }

    @Test
    fun `clearFromStartUntil with full size empties lists`() {
        for (i in 1..5) recording.add(i, i.toLong() * 100)

        recording.clearFromStartUntil(5)
        assertTrue(recording.values.isEmpty())
        assertTrue(recording.timestamps.isEmpty())
    }

    @Test
    fun `clearFromStartUntil with 1 removes only first element`() {
        recording.add(10, 1000L)
        recording.add(20, 2000L)

        recording.clearFromStartUntil(1)
        assertEquals(1, recording.values.size)
        assertEquals(20, recording.values[0])
        assertEquals(2000L, recording.timestamps[0])
    }

    // --- getSize() Tests ---

    @Test
    fun `getSize returns zero for empty recording`() {
        assertEquals(0, recording.getSize())
    }

    @Test
    fun `getSize returns correct count after adds`() {
        recording.add(1, 100L)
        recording.add(2, 200L)
        assertEquals(2, recording.getSize())
    }

    @Test
    fun `getSize returns null when values and timestamps desync`() {
        recording.add(1, 100L)
        recording.values.add(999) // artificially desync
        assertNull(recording.getSize())
    }

    // --- Combined Operations ---

    @Test
    fun `add then clearFromStartUntil batch cycle`() {
        // Simulate batch cycle: add 300, clear 300, add 300 more
        for (i in 1..300) recording.add(i, i.toLong())
        assertEquals(300, recording.getSize())

        recording.clearFromStartUntil(300)
        assertEquals(0, recording.getSize())

        for (i in 301..600) recording.add(i, i.toLong())
        assertEquals(300, recording.getSize())
        assertEquals(301, recording.values[0])
    }
}
