package com.example.samplewearmobileapp

import com.example.samplewearmobileapp.models.PpgType
import org.junit.Assert.*
import org.junit.Test

class RecordingBufferTest {
    @Test
    fun `all PPG channels retain the sample after a full batch`() {
        for (type in PpgType.values()) {
            val buffer = PpgRecording(type)
            repeat(301) { buffer.add(it, 1000L + it * 40L) }
            buffer.clearFromStartUntil(300)
            assertEquals(1, buffer.getSize())
            assertEquals(listOf(300), buffer.values)
            assertEquals(listOf(13000L), buffer.timestamps)
        }
    }

    @Test
    fun `42 sample final PPG flush drains exactly 42 for every channel`() {
        for (type in PpgType.values()) {
            val buffer = PpgRecording(type)
            repeat(42) { buffer.add(it, 1000L + it * 40L) }
            val finalValues = buffer.values.toList()
            val finalTimestamps = buffer.timestamps.toList()
            buffer.clearFromStartUntil(42)
            assertEquals(42, finalValues.size)
            assertEquals(42, finalTimestamps.size)
            assertEquals(0, buffer.getSize())
        }
    }

    @Test
    fun `accelerometer batch leaves the next XYZ tuple intact`() {
        val buffer = AccelerometerRecording()
        repeat(101) { buffer.add(it.toFloat(), (it + 1).toFloat(), (it + 2).toFloat(), 1000L + it * 40L) }
        buffer.clearFromStartUntil(100)
        assertEquals(listOf(100f), buffer.xValues)
        assertEquals(listOf(101f), buffer.yValues)
        assertEquals(listOf(102f), buffer.zValues)
        assertEquals(listOf(5000L), buffer.timestamps)
    }

    @Test
    fun `accelerometer partial flush and fresh reset leave no padded samples`() {
        val buffer = AccelerometerRecording()
        repeat(42) { buffer.add(1f, 2f, 3f, 1000L + it * 40L) }
        buffer.clearFromStartUntil(42)
        assertEquals(0, buffer.getSize())
        assertTrue(buffer.timestamps.isEmpty())
        buffer.add(4f, 5f, 6f, 5000L)
        buffer.clear()
        assertTrue(buffer.xValues.isEmpty())
        assertTrue(buffer.yValues.isEmpty())
        assertTrue(buffer.zValues.isEmpty())
    }

}
