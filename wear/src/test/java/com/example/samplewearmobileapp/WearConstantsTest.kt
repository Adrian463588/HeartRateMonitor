package com.example.samplewearmobileapp

import org.junit.Assert.*
import org.junit.Test

class WearConstantsTest {

    @Test
    fun `MS_TO_SEC converts correctly`() {
        assertEquals(0.001, Constants.MS_TO_SEC, 1e-10)
    }

    @Test
    fun `PPG_GREEN_SAMPLE_RATE is 25 Hz`() {
        assertEquals(25.0, Constants.PPG_GREEN_SAMPLE_RATE, 1e-10)
    }

    @Test
    fun `PPG_IR_RED_SAMPLE_RATE is 100 Hz`() {
        assertEquals(100.0, Constants.PPG_IR_RED_SAMPLE_RATE, 1e-10)
    }

    @Test
    fun `PPG_GREEN_BATCH_SIZE equals rate times tick duration`() {
        // 25 Hz * 12s = 300
        val expected = (Constants.PPG_GREEN_SAMPLE_RATE *
                Constants.PPG_GREEN_TICK_RATE * Constants.MS_TO_SEC).toInt()
        assertEquals(expected, Constants.PPG_GREEN_BATCH_SIZE)
        assertEquals(300, Constants.PPG_GREEN_BATCH_SIZE)
    }

    @Test
    fun `PPG_IR_RED_BATCH_SIZE equals rate times batch tick`() {
        // 100 Hz * 3s = 300
        val expected = (Constants.PPG_IR_RED_SAMPLE_RATE *
                Constants.PPG_IR_RED_BATCH_TICK_RATE * Constants.MS_TO_SEC).toInt()
        assertEquals(expected, Constants.PPG_IR_RED_BATCH_SIZE)
        assertEquals(300, Constants.PPG_IR_RED_BATCH_SIZE)
    }

    @Test
    fun `BATCHING_DURATION is 12 seconds`() {
        assertEquals(12000L, Constants.BATCHING_DURATION)
    }

    @Test
    fun `PPG_GREEN_TICK_RATE is 12 seconds`() {
        assertEquals(12000L, Constants.PPG_GREEN_TICK_RATE)
    }

    @Test
    fun `PPG_IR_RED_TICK_RATE is 10ms`() {
        // 1000 / 100 = 10
        assertEquals(10L, Constants.PPG_IR_RED_TICK_RATE)
    }

    @Test
    fun `PPG_IR_RED_BATCH_TICK_RATE is 3 seconds`() {
        assertEquals(3000L, Constants.PPG_IR_RED_BATCH_TICK_RATE)
    }

    @Test
    fun `batch sizes are consistent across Green and IR-Red`() {
        // Both should produce 300 data points per batch
        assertEquals(Constants.PPG_GREEN_BATCH_SIZE, Constants.PPG_IR_RED_BATCH_SIZE)
    }
}
