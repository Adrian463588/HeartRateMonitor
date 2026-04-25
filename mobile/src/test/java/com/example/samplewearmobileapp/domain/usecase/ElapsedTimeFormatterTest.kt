package com.example.samplewearmobileapp.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class ElapsedTimeFormatterTest {

    private lateinit var formatter: ElapsedTimeFormatter

    @Before
    fun setUp() {
        formatter = ElapsedTimeFormatter()
    }

    // -----------------------------------------------------------------------
    // decompose()
    // -----------------------------------------------------------------------

    @Test
    fun `decompose zero returns all zeros`() {
        val result = formatter.decompose(0L)
        assertEquals(ElapsedTimeFormatter.ElapsedTime(0, 0, 0), result)
    }

    @Test
    fun `decompose 1000ms returns 0 min 1 sec 0 ms`() {
        val result = formatter.decompose(1_000L)
        assertEquals(ElapsedTimeFormatter.ElapsedTime(0, 1, 0), result)
    }

    @Test
    fun `decompose 61450ms returns 1 min 1 sec 450 ms`() {
        val result = formatter.decompose(61_450L)
        assertEquals(ElapsedTimeFormatter.ElapsedTime(1, 1, 450), result)
    }

    @Test
    fun `decompose 3600000ms returns 60 min 0 sec 0 ms`() {
        val result = formatter.decompose(3_600_000L)
        assertEquals(ElapsedTimeFormatter.ElapsedTime(60, 0, 0), result)
    }

    @Test
    fun `decompose negative throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            formatter.decompose(-1L)
        }
    }

    // -----------------------------------------------------------------------
    // format()
    // -----------------------------------------------------------------------

    @Test
    fun `format zero returns 00 00 000`() {
        assertEquals("00:00.000", formatter.format(0L))
    }

    @Test
    fun `format 5450ms returns 00 05 450`() {
        assertEquals("00:05.450", formatter.format(5_450L))
    }

    @Test
    fun `format 61450ms returns 01 01 450`() {
        assertEquals("01:01.450", formatter.format(61_450L))
    }

    @Test
    fun `format 3600000ms returns 60 00 000`() {
        assertEquals("60:00.000", formatter.format(3_600_000L))
    }

    // -----------------------------------------------------------------------
    // getElapsedMs()
    // -----------------------------------------------------------------------

    @Test
    fun `getElapsedMs with zero accumulated returns delta from start`() {
        val startEpochMs = 1_000L
        val nowEpochMs = 3_500L
        val result = formatter.getElapsedMs(
            startEpochMs = startEpochMs,
            accumulatedMs = 0L,
            nowEpochMs = nowEpochMs
        )
        assertEquals(2_500L, result)
    }

    @Test
    fun `getElapsedMs with accumulated adds correctly`() {
        val result = formatter.getElapsedMs(
            startEpochMs = 1_000L,
            accumulatedMs = 5_000L,
            nowEpochMs = 3_000L
        )
        assertEquals(7_000L, result)
    }

    @Test
    fun `getElapsedMs with same start and now returns accumulated only`() {
        val result = formatter.getElapsedMs(
            startEpochMs = 1_000L,
            accumulatedMs = 3_000L,
            nowEpochMs = 1_000L
        )
        assertEquals(3_000L, result)
    }
}
