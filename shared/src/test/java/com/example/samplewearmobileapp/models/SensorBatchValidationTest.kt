package com.example.samplewearmobileapp.models

import org.junit.Assert.*
import org.junit.Test

/** Transport fixtures only; these values do not represent acquired physiological data. */
class SensorBatchValidationTest {
    @Test
    fun `identical array capacities cannot equate different sessions or sample counts`() {
        assertNotEquals(ppg(), ppg().copy(sessionId = 2000L))
        assertNotEquals(ppg(), ppg().copy(size = 1))
        assertNotEquals(acc(), acc().copy(sessionId = 2000L))
    }

    private fun ppg() = PpgData(intArrayOf(12, 13), longArrayOf(1000, 1040), PpgType.PPG_IR, 2, 2, 1000)
    private fun acc() = AccelerometerData(floatArrayOf(1f, 2f), floatArrayOf(3f, 4f),
        floatArrayOf(5f, 6f), longArrayOf(1000, 1040), 2, 2, 1000)

    @Test
    fun `complete real-shaped batches pass for every PPG channel`() {
        for (type in PpgType.values()) assertTrue(ppg().copy(ppgType = type).isValid())
        assertTrue(acc().isValid())
    }

    @Test
    fun `array capacity never creates samples in an empty batch`() {
        assertFalse(PpgData(300, PpgType.PPG_GREEN).isValid())
        assertFalse(AccelerometerData(100).isValid())
    }

    @Test
    fun `partial final batch validates only its declared samples`() {
        assertTrue(PpgData(intArrayOf(12, 0), longArrayOf(1000, 0), PpgType.PPG_GREEN, 2, 1).isValid())
        assertTrue(acc().copy(size = 1).isValid())
    }

    @Test
    fun `oversize and mismatched arrays cannot cause receiver indexing crashes`() {
        assertFalse(ppg().copy(size = 3).isValid())
        assertFalse(ppg().copy(timestamps = longArrayOf(1000)).isValid())
        assertFalse(acc().copy(xValues = floatArrayOf(1f)).isValid())
        assertFalse(acc().copy(size = 3).isValid())
    }

    @Test
    fun `invalid clock order and synthetic zero timestamps are rejected`() {
        assertFalse(ppg().copy(timestamps = longArrayOf(1000, 1000)).isValid())
        assertFalse(ppg().copy(timestamps = longArrayOf(1040, 1000)).isValid())
        assertFalse(acc().copy(timestamps = longArrayOf(0, 1040)).isValid())
    }

    @Test
    fun `nonfinite accelerometer values are rejected without zero replacement`() {
        assertFalse(acc().copy(zValues = floatArrayOf(Float.NaN, 6f)).isValid())
        assertFalse(acc().copy(yValues = floatArrayOf(Float.POSITIVE_INFINITY, 4f)).isValid())
    }
}
