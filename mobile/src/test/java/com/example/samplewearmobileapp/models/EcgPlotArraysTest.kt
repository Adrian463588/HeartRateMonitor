package com.example.samplewearmobileapp.models

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [EcgPlotArrays] data class.
 */
class EcgPlotArraysTest {

    // --- construction ---

    @Test
    fun `constructor sets properties correctly`() {
        val ecg = doubleArrayOf(1.0, 2.0, 3.0)
        val peaks = booleanArrayOf(false, true, false)
        val ts = longArrayOf(100L, 200L, 300L)

        val arrays = EcgPlotArrays(ecg, peaks, ts)
        assertArrayEquals(ecg, arrays.ecg, 0.0)
        assertArrayEquals(peaks, arrays.peaks)
        assertArrayEquals(ts, arrays.timestamp)
    }

    // --- equality ---

    @Test
    fun `equal arrays produce equal objects`() {
        val a = EcgPlotArrays(
            doubleArrayOf(1.0, 2.0),
            booleanArrayOf(true, false),
            longArrayOf(10L, 20L)
        )
        val b = EcgPlotArrays(
            doubleArrayOf(1.0, 2.0),
            booleanArrayOf(true, false),
            longArrayOf(10L, 20L)
        )
        assertEquals(a, b)
    }

    @Test
    fun `different ecg values are not equal`() {
        val a = EcgPlotArrays(
            doubleArrayOf(1.0), booleanArrayOf(false), longArrayOf(10L)
        )
        val b = EcgPlotArrays(
            doubleArrayOf(9.0), booleanArrayOf(false), longArrayOf(10L)
        )
        assertNotEquals(a, b)
    }

    @Test
    fun `different peaks are not equal`() {
        val a = EcgPlotArrays(
            doubleArrayOf(1.0), booleanArrayOf(true), longArrayOf(10L)
        )
        val b = EcgPlotArrays(
            doubleArrayOf(1.0), booleanArrayOf(false), longArrayOf(10L)
        )
        assertNotEquals(a, b)
    }

    @Test
    fun `different timestamps are not equal`() {
        val a = EcgPlotArrays(
            doubleArrayOf(1.0), booleanArrayOf(false), longArrayOf(10L)
        )
        val b = EcgPlotArrays(
            doubleArrayOf(1.0), booleanArrayOf(false), longArrayOf(99L)
        )
        assertNotEquals(a, b)
    }

    // --- hashCode ---

    @Test
    fun `equal objects have same hashCode`() {
        val a = EcgPlotArrays(
            doubleArrayOf(1.0, 2.0),
            booleanArrayOf(true, false),
            longArrayOf(10L, 20L)
        )
        val b = EcgPlotArrays(
            doubleArrayOf(1.0, 2.0),
            booleanArrayOf(true, false),
            longArrayOf(10L, 20L)
        )
        assertEquals(a.hashCode(), b.hashCode())
    }

    // --- edge: empty arrays ---

    @Test
    fun `empty arrays are valid`() {
        val arrays = EcgPlotArrays(
            doubleArrayOf(), booleanArrayOf(), longArrayOf()
        )
        assertEquals(0, arrays.ecg.size)
        assertEquals(0, arrays.peaks.size)
        assertEquals(0, arrays.timestamp.size)
    }

    // --- mutable properties ---

    @Test
    fun `properties are mutable`() {
        val arrays = EcgPlotArrays(
            doubleArrayOf(1.0), booleanArrayOf(false), longArrayOf(10L)
        )
        arrays.ecg = doubleArrayOf(9.0)
        assertArrayEquals(doubleArrayOf(9.0), arrays.ecg, 0.0)
    }
}
