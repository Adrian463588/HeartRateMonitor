package com.example.samplewearmobileapp

import org.junit.Assert.*
import org.junit.Test

class RecordingBufferTest {
    @Test
    fun `continuous PPG metadata and buffers match the SDK 25 Hz contract`() {
        assertEquals(25.0, Constants.PPG_GREEN_SAMPLE_RATE, 0.0)
        assertEquals(25.0, Constants.PPG_IR_RED_SAMPLE_RATE, 0.0)
        assertEquals(300, Constants.PPG_GREEN_BATCH_SIZE)
        assertEquals(300, Constants.PPG_IR_RED_BATCH_SIZE)
        assertEquals(100, Constants.ACCELEROMETER_BATCH_SIZE)
    }
}
