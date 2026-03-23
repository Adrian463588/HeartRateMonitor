package com.example.samplewearmobileapp.models

import org.junit.Assert.*
import org.junit.Test

class PpgDataTest {

    // --- constructor ---

    @Test
    fun `constructor creates arrays of correct window size`() {
        val data = PpgData(10, PpgType.PPG_GREEN)
        assertEquals(10, data.ppgValues.size)
        assertEquals(10, data.timestamps.size)
        assertEquals(10, data.windowSize)
        assertEquals(PpgType.PPG_GREEN, data.ppgType)
        assertEquals(0, data.size)
    }

    // --- clear ---

    @Test
    fun `clear resets all values and timestamps to zero`() {
        val data = PpgData(3, PpgType.PPG_IR)
        data.ppgValues[0] = 100
        data.ppgValues[1] = 200
        data.timestamps[0] = 999L
        data.timestamps[1] = 888L
        data.size = 2

        data.clear()

        assertEquals(0, data.size)
        assertArrayEquals(intArrayOf(0, 0, 0), data.ppgValues)
        assertArrayEquals(longArrayOf(0, 0, 0), data.timestamps)
    }

    @Test
    fun `clear on already empty data has no effect`() {
        val data = PpgData(2, PpgType.PPG_RED)
        data.clear()
        assertEquals(0, data.size)
    }

    // --- equals ---

    @Test
    fun `equals returns true for identical data`() {
        val a = PpgData(intArrayOf(1, 2), longArrayOf(10L, 20L),
            PpgType.PPG_GREEN, 2, 2)
        val b = PpgData(intArrayOf(1, 2), longArrayOf(10L, 20L),
            PpgType.PPG_GREEN, 2, 2)
        assertEquals(a, b)
    }

    @Test
    fun `equals returns false for different ppg values`() {
        val a = PpgData(intArrayOf(1, 2), longArrayOf(10L, 20L),
            PpgType.PPG_GREEN, 2, 2)
        val b = PpgData(intArrayOf(3, 4), longArrayOf(10L, 20L),
            PpgType.PPG_GREEN, 2, 2)
        assertNotEquals(a, b)
    }

    @Test
    fun `equals returns false for different ppg type`() {
        val a = PpgData(intArrayOf(1), longArrayOf(10L),
            PpgType.PPG_GREEN, 1, 1)
        val b = PpgData(intArrayOf(1), longArrayOf(10L),
            PpgType.PPG_IR, 1, 1)
        assertNotEquals(a, b)
    }

    @Test
    fun `equals returns false for different timestamps`() {
        val a = PpgData(intArrayOf(1), longArrayOf(10L),
            PpgType.PPG_GREEN, 1, 1)
        val b = PpgData(intArrayOf(1), longArrayOf(99L),
            PpgType.PPG_GREEN, 1, 1)
        assertNotEquals(a, b)
    }

    @Test
    fun `equals with same instance returns true`() {
        val a = PpgData(2, PpgType.PPG_RED)
        assertEquals(a, a)
    }

    @Test
    fun `equals with null returns false`() {
        val a = PpgData(2, PpgType.PPG_RED)
        assertNotEquals(a, null)
    }

    // --- hashCode ---

    @Test
    fun `hashCode is consistent for equal objects`() {
        val a = PpgData(intArrayOf(1, 2), longArrayOf(10L, 20L),
            PpgType.PPG_GREEN, 2, 2)
        val b = PpgData(intArrayOf(1, 2), longArrayOf(10L, 20L),
            PpgType.PPG_GREEN, 2, 2)
        assertEquals(a.hashCode(), b.hashCode())
    }

    // --- toString ---

    @Test
    fun `toString contains ppg type and window size`() {
        val data = PpgData(5, PpgType.PPG_IR)
        data.size = 3
        val str = data.toString()
        assertTrue(str.contains("PPG_IR"))
        assertTrue(str.contains("5"))
        assertTrue(str.contains("3"))
    }
}
