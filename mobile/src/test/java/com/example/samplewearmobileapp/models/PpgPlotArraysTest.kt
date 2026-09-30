package com.example.samplewearmobileapp.models

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [PpgPlotArrays] data class.
 */
class PpgPlotArraysTest {

    // --- construction ---

    @Test
    fun `constructor sets properties correctly`() {
        val ppg = intArrayOf(100, 200, 300)
        val ts = longArrayOf(10L, 20L, 30L)

        val arrays = PpgPlotArrays(ppg, ts)
        assertArrayEquals(ppg, arrays.ppg)
        assertArrayEquals(ts, arrays.timestamp)
    }

    // --- equality ---

    @Test
    fun `equal arrays produce equal objects`() {
        val a = PpgPlotArrays(intArrayOf(1, 2, 3), longArrayOf(10L, 20L, 30L))
        val b = PpgPlotArrays(intArrayOf(1, 2, 3), longArrayOf(10L, 20L, 30L))
        assertEquals(a, b)
    }

    @Test
    fun `different ppg values are not equal`() {
        val a = PpgPlotArrays(intArrayOf(1), longArrayOf(10L))
        val b = PpgPlotArrays(intArrayOf(9), longArrayOf(10L))
        assertNotEquals(a, b)
    }

    @Test
    fun `different timestamps are not equal`() {
        val a = PpgPlotArrays(intArrayOf(1), longArrayOf(10L))
        val b = PpgPlotArrays(intArrayOf(1), longArrayOf(99L))
        assertNotEquals(a, b)
    }

    // --- hashCode ---

    @Test
    fun `equal objects have same hashCode`() {
        val a = PpgPlotArrays(intArrayOf(1, 2), longArrayOf(10L, 20L))
        val b = PpgPlotArrays(intArrayOf(1, 2), longArrayOf(10L, 20L))
        assertEquals(a.hashCode(), b.hashCode())
    }

    // --- edge: empty arrays ---

    @Test
    fun `empty arrays are valid`() {
        val arrays = PpgPlotArrays(intArrayOf(), longArrayOf())
        assertEquals(0, arrays.ppg.size)
        assertEquals(0, arrays.timestamp.size)
    }

    // --- mutable ---

    @Test
    fun `properties are mutable`() {
        val arrays = PpgPlotArrays(intArrayOf(1), longArrayOf(10L))
        arrays.ppg = intArrayOf(5, 6, 7)
        assertEquals(3, arrays.ppg.size)
    }
}
