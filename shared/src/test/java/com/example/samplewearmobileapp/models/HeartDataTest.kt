package com.example.samplewearmobileapp.models

import org.junit.Assert.*
import org.junit.Test

class HeartDataTest {

    @Test
    fun `primary constructor sets all fields`() {
        val data = HeartData(72, 800, "2024-01-01T00:00:00")
        assertEquals(72, data.hr)
        assertEquals(800, data.ibi)
        assertEquals("2024-01-01T00:00:00", data.timestamp)
    }

    @Test
    fun `default constructor sets hr and ibi to zero`() {
        val data = HeartData()
        assertEquals(0, data.hr)
        assertEquals(0, data.ibi)
        assertNotNull(data.timestamp)
        assertTrue(data.timestamp.isNotEmpty())
    }

    @Test
    fun `default constructor generates ISO timestamp`() {
        val data = HeartData()
        // ISO_DATE_TIME format contains 'T' separator
        assertTrue("Timestamp should contain 'T': ${data.timestamp}",
            data.timestamp.contains("T"))
    }

    @Test
    fun `data class equality works for same values`() {
        val a = HeartData(60, 1000, "ts1")
        val b = HeartData(60, 1000, "ts1")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `data class equality detects different hr`() {
        val a = HeartData(60, 1000, "ts1")
        val b = HeartData(80, 1000, "ts1")
        assertNotEquals(a, b)
    }

    @Test
    fun `copy creates modified instance`() {
        val original = HeartData(70, 850, "ts")
        val copy = original.copy(hr = 90)
        assertEquals(90, copy.hr)
        assertEquals(850, copy.ibi)
    }

    @Test
    fun `fields are mutable`() {
        val data = HeartData()
        data.hr = 120
        data.ibi = 500
        data.timestamp = "custom"
        assertEquals(120, data.hr)
        assertEquals(500, data.ibi)
        assertEquals("custom", data.timestamp)
    }
}
