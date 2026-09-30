package com.example.samplewearmobileapp.utils

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [SignalFilter.filter], the IIR/FIR digital filter utility
 * extracted from QrsDetector.
 */
class SignalFilterTest {

    // --- FIR only (y = null) ---

    @Test
    fun `FIR filter with identity coefficients returns last input`() {
        // a=[1], b=[1] → y[n] = x[n]
        val a = doubleArrayOf(1.0)
        val b = doubleArrayOf(1.0)
        val x = listOf(3.0, 5.0, 7.0)
        val result = SignalFilter.filter(a, b, x, null)
        assertEquals(7.0, result, 1e-10)
    }

    @Test
    fun `FIR filter with two b coefficients computes weighted sum`() {
        // a=[1], b=[0.5, 0.5] → y[n] = 0.5*x[n] + 0.5*x[n-1]
        val a = doubleArrayOf(1.0)
        val b = doubleArrayOf(0.5, 0.5)
        val x = listOf(2.0, 4.0, 10.0)
        val result = SignalFilter.filter(a, b, x, null)
        // 0.5 * 10 + 0.5 * 4 = 7.0
        assertEquals(7.0, result, 1e-10)
    }

    @Test
    fun `FIR filter with three b coefficients`() {
        // a=[1], b=[1, 2, 3] → y[n] = 1*x[n] + 2*x[n-1] + 3*x[n-2]
        val a = doubleArrayOf(1.0)
        val b = doubleArrayOf(1.0, 2.0, 3.0)
        val x = listOf(1.0, 2.0, 3.0)
        val result = SignalFilter.filter(a, b, x, null)
        // 1*3 + 2*2 + 3*1 = 10
        assertEquals(10.0, result, 1e-10)
    }

    // --- IIR (with feedback) ---

    @Test
    fun `IIR filter with a and b coefficients`() {
        // a=[1, 0.5], b=[1, 0]
        // suma: a[0]*y[1] + a[1]*y[0] = 1.0*1.0 + 0.5*0.0 = 1.0
        // sumb: b[0]*x[1] + b[1]*x[0] = 1.0*3.0 + 0.0*2.0 = 3.0
        // result = (3.0 - 1.0) / 1.0 = 2.0
        val a = doubleArrayOf(1.0, 0.5)
        val b = doubleArrayOf(1.0, 0.0)
        val x = listOf(2.0, 3.0)
        val y = listOf(0.0, 1.0)
        val result = SignalFilter.filter(a, b, x, y)
        assertEquals(2.0, result, 1e-10)
    }

    @Test
    fun `IIR filter with non-unity a0 scales output`() {
        // a=[2], b=[1] → y[n] = x[n] / 2
        val a = doubleArrayOf(2.0)
        val b = doubleArrayOf(1.0)
        val x = listOf(8.0)
        val result = SignalFilter.filter(a, b, x, null)
        assertEquals(4.0, result, 1e-10)
    }

    // --- Edge cases: insufficient data ---

    @Test
    fun `returns 0 when x is shorter than b`() {
        val a = doubleArrayOf(1.0)
        val b = doubleArrayOf(1.0, 1.0, 1.0)
        val x = listOf(5.0)  // len=1, but lenb=3
        val result = SignalFilter.filter(a, b, x, null)
        assertEquals(0.0, result, 1e-10)
    }

    @Test
    fun `returns 0 when y is shorter than a`() {
        val a = doubleArrayOf(1.0, 0.5, 0.5)
        val b = doubleArrayOf(1.0)
        val x = listOf(5.0)
        val y = listOf(1.0) // len=1, but lena=3
        val result = SignalFilter.filter(a, b, x, y)
        assertEquals(0.0, result, 1e-10)
    }

    @Test
    fun `returns correct value when x length equals b length exactly`() {
        val a = doubleArrayOf(1.0)
        val b = doubleArrayOf(1.0, 1.0)
        val x = listOf(3.0, 4.0) // len=2 == lenb=2
        val result = SignalFilter.filter(a, b, x, null)
        // 1*4 + 1*3 = 7
        assertEquals(7.0, result, 1e-10)
    }

    // --- Edge case: empty inputs ---

    @Test
    fun `returns 0 for empty x`() {
        val a = doubleArrayOf(1.0)
        val b = doubleArrayOf(1.0)
        val result = SignalFilter.filter(a, b, emptyList(), null)
        assertEquals(0.0, result, 1e-10)
    }

    // --- With actual derivative coefficients ---

    @Test
    fun `filter with derivative coefficients does not throw`() {
        val a = com.example.samplewearmobileapp.Constants.A_DERIVATIVE
        val b = com.example.samplewearmobileapp.Constants.B_DERIVATIVE
        // Provide enough data points
        val x = List(b.size + 5) { it.toDouble() }
        val y = List(a.size + 5) { 0.0 }
        val result = SignalFilter.filter(a, b, x, y)
        assertFalse(result.isNaN())
        assertFalse(result.isInfinite())
    }

    // --- Consistency: same input twice gives same output ---

    @Test
    fun `filter is deterministic`() {
        val a = doubleArrayOf(1.0, 0.3)
        val b = doubleArrayOf(0.7, 0.2)
        val x = listOf(1.0, 2.0, 3.0, 4.0)
        val y = listOf(0.0, 1.0, 2.0, 3.0)

        val r1 = SignalFilter.filter(a, b, x, y)
        val r2 = SignalFilter.filter(a, b, x, y)
        assertEquals(r1, r2, 0.0)
    }
}
