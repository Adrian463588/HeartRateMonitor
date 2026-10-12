package com.example.samplewearmobileapp.models

import org.junit.Assert.*
import org.junit.Test

class AccelerometerDataTest {

    @Test
    fun `constructor creates arrays of correct window size`() {
        val data = AccelerometerData(10)
        assertEquals(10, data.xValues.size)
        assertEquals(10, data.yValues.size)
        assertEquals(10, data.zValues.size)
        assertEquals(10, data.timestamps.size)
        assertEquals(10, data.windowSize)
        assertEquals(0, data.size)
    }

    @Test
    fun `clear resets all values and timestamps to zero`() {
        val data = AccelerometerData(3)
        data.xValues[0] = 1.0f
        data.yValues[0] = 2.0f
        data.zValues[0] = 3.0f
        data.timestamps[0] = 12345L
        data.size = 1

        data.clear()

        assertEquals(0, data.size)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), data.xValues, 0.0001f)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), data.yValues, 0.0001f)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), data.zValues, 0.0001f)
        assertArrayEquals(longArrayOf(0L, 0L, 0L), data.timestamps)
    }

    @Test
    fun `equals and hashCode return true for identical data`() {
        val a = AccelerometerData(
            floatArrayOf(1.0f, 2.0f),
            floatArrayOf(3.0f, 4.0f),
            floatArrayOf(5.0f, 6.0f),
            longArrayOf(100L, 200L),
            2,
            2
        )
        val b = AccelerometerData(
            floatArrayOf(1.0f, 2.0f),
            floatArrayOf(3.0f, 4.0f),
            floatArrayOf(5.0f, 6.0f),
            longArrayOf(100L, 200L),
            2,
            2
        )
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `equals returns false for different x values`() {
        val a = AccelerometerData(
            floatArrayOf(1.0f), floatArrayOf(2.0f), floatArrayOf(3.0f),
            longArrayOf(100L), 1, 1
        )
        val b = AccelerometerData(
            floatArrayOf(9.0f), floatArrayOf(2.0f), floatArrayOf(3.0f),
            longArrayOf(100L), 1, 1
        )
        assertNotEquals(a, b)
    }

    @Test
    fun `toString contains windowSize and size`() {
        val data = AccelerometerData(50)
        data.size = 25
        val str = data.toString()
        assertTrue(str.contains("windowSize=50"))
        assertTrue(str.contains("size=25"))
    }
}
