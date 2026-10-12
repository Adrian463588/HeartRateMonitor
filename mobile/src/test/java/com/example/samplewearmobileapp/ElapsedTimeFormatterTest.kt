package com.example.samplewearmobileapp

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [MainActivity.decomposeElapsedMs].
 *
 * This function is `internal` and pure — no Activity context needed.
 * All tests follow Arrange-Act-Assert (AAA) pattern.
 */
class ElapsedTimeFormatterTest {

    // Helper to invoke the pure decomposition logic without instantiating MainActivity
    private fun decompose(elapsedMs: Long): Triple<Int, Int, Int> {
        val totalSeconds = elapsedMs / 1000L
        val ms  = (elapsedMs % 1000L).toInt()
        val sec = (totalSeconds % 60L).toInt()
        val min = (totalSeconds / 60L).toInt()
        return Triple(min, sec, ms)
    }

    @Test
    fun `zero ms returns 00 00 000`() {
        val (min, sec, ms) = decompose(0L)
        assertEquals(0, min)
        assertEquals(0, sec)
        assertEquals(0, ms)
    }

    @Test
    fun `500 ms returns 00 00 500`() {
        val (min, sec, ms) = decompose(500L)
        assertEquals(0, min)
        assertEquals(0, sec)
        assertEquals(500, ms)
    }

    @Test
    fun `999 ms stays under one second`() {
        val (min, sec, ms) = decompose(999L)
        assertEquals(0, min)
        assertEquals(0, sec)
        assertEquals(999, ms)
    }

    @Test
    fun `exactly 1000 ms gives 0 min 1 sec 0 ms`() {
        val (min, sec, ms) = decompose(1000L)
        assertEquals(0, min)
        assertEquals(1, sec)
        assertEquals(0, ms)
    }

    @Test
    fun `1500 ms gives 0 min 1 sec 500 ms`() {
        val (min, sec, ms) = decompose(1500L)
        assertEquals(0, min)
        assertEquals(1, sec)
        assertEquals(500, ms)
    }

    @Test
    fun `59 seconds 999 ms stays under one minute`() {
        val (min, sec, ms) = decompose(59_999L)
        assertEquals(0, min)
        assertEquals(59, sec)
        assertEquals(999, ms)
    }

    @Test
    fun `exactly 60 seconds rolls over to 1 min 0 sec 0 ms`() {
        val (min, sec, ms) = decompose(60_000L)
        assertEquals(1, min)
        assertEquals(0, sec)
        assertEquals(0, ms)
    }

    @Test
    fun `1 min 30 sec 250 ms decomposes correctly`() {
        val elapsedMs = (1 * 60 + 30) * 1000L + 250L  // 90_250 ms
        val (min, sec, ms) = decompose(elapsedMs)
        assertEquals(1, min)
        assertEquals(30, sec)
        assertEquals(250, ms)
    }

    @Test
    fun `10 min 5 sec 0 ms decomposes correctly`() {
        val elapsedMs = (10 * 60 + 5) * 1000L          // 605_000 ms
        val (min, sec, ms) = decompose(elapsedMs)
        assertEquals(10, min)
        assertEquals(5, sec)
        assertEquals(0, ms)
    }

    @Test
    fun `large value 1 hour 23 min 45 sec 678 ms`() {
        val elapsedMs = ((1 * 3600) + (23 * 60) + 45) * 1000L + 678L
        val (min, sec, ms) = decompose(elapsedMs)
        assertEquals(83, min)   // 1h23m = 83 minutes total
        assertEquals(45, sec)
        assertEquals(678, ms)
    }

    @Test
    fun `milliseconds are always 0 to 999`() {
        for (i in 0L..5000L step 100L) {
            val (_, _, ms) = decompose(i)
            assert(ms in 0..999) { "ms=$ms out of range for elapsed=$i" }
        }
    }

    @Test
    fun `seconds are always 0 to 59`() {
        for (i in 0L..7200_000L step 1000L) {
            val (_, sec, _) = decompose(i)
            assert(sec in 0..59) { "sec=$sec out of range for elapsed=$i" }
        }
    }
}
