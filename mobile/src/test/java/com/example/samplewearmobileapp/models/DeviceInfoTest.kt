package com.example.samplewearmobileapp.models

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [DeviceInfo] data class.
 */
class DeviceInfoTest {

    // --- construction ---

    @Test
    fun `constructor sets properties correctly`() {
        val info = DeviceInfo("Polar H10", "12345ABC")
        assertEquals("Polar H10", info.name)
        assertEquals("12345ABC", info.id)
    }

    // --- data class equality ---

    @Test
    fun `equal objects have same content`() {
        val a = DeviceInfo("Polar H10", "123")
        val b = DeviceInfo("Polar H10", "123")
        assertEquals(a, b)
    }

    @Test
    fun `different name means not equal`() {
        val a = DeviceInfo("Polar H10", "123")
        val b = DeviceInfo("Polar OH1", "123")
        assertNotEquals(a, b)
    }

    @Test
    fun `different id means not equal`() {
        val a = DeviceInfo("Polar H10", "123")
        val b = DeviceInfo("Polar H10", "456")
        assertNotEquals(a, b)
    }

    // --- hashCode ---

    @Test
    fun `equal objects have same hashCode`() {
        val a = DeviceInfo("Polar H10", "123")
        val b = DeviceInfo("Polar H10", "123")
        assertEquals(a.hashCode(), b.hashCode())
    }

    // --- copy ---

    @Test
    fun `copy creates independent instance with same values`() {
        val original = DeviceInfo("Polar H10", "123")
        val copy = original.copy()
        assertEquals(original, copy)
        assertNotSame(original, copy)
    }

    @Test
    fun `copy with modified field creates correct instance`() {
        val original = DeviceInfo("Polar H10", "123")
        val modified = original.copy(name = "Polar OH1")
        assertEquals("Polar OH1", modified.name)
        assertEquals("123", modified.id)
    }

    // --- toString ---

    @Test
    fun `toString contains class name and properties`() {
        val info = DeviceInfo("Polar H10", "ABC")
        val str = info.toString()
        assertTrue(str.contains("DeviceInfo"))
        assertTrue(str.contains("Polar H10"))
        assertTrue(str.contains("ABC"))
    }

    // --- mutable properties ---

    @Test
    fun `properties are mutable`() {
        val info = DeviceInfo("Old", "111")
        info.name = "New"
        info.id = "222"
        assertEquals("New", info.name)
        assertEquals("222", info.id)
    }

    // --- edge case: empty strings ---

    @Test
    fun `empty name and id are valid`() {
        val info = DeviceInfo("", "")
        assertEquals("", info.name)
        assertEquals("", info.id)
    }
}
