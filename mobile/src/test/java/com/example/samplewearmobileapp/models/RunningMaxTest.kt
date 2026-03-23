package com.example.samplewearmobileapp.models

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RunningMaxTest {

    private lateinit var rm: RunningMax

    @Before
    fun setUp() {
        rm = RunningMax(3)
    }

    // --- size ---

    @Test
    fun `size is zero initially`() {
        assertEquals(0, rm.size())
    }

    @Test
    fun `size increases with add`() {
        rm.add(1.0)
        rm.add(2.0)
        assertEquals(2, rm.size())
    }

    @Test
    fun `size does not exceed window size`() {
        rm.add(1.0)
        rm.add(2.0)
        rm.add(3.0)
        rm.add(4.0)
        assertEquals(3, rm.size())
    }

    // --- max ---

    @Test
    fun `max returns the largest value in window`() {
        rm.add(10.0)
        rm.add(30.0)
        rm.add(20.0)
        assertEquals(30.0, rm.max(), 0.0001)
    }

    @Test
    fun `max updates after overflow evicts previous max`() {
        rm.add(100.0)
        rm.add(20.0)
        rm.add(30.0)
        rm.add(40.0) // evicts 100.0
        assertEquals(40.0, rm.max(), 0.0001) // window: [20, 30, 40]
    }

    @Test
    fun `max of single element returns that element`() {
        rm.add(42.0)
        assertEquals(42.0, rm.max(), 0.0001)
    }

    // --- min ---

    @Test
    fun `min returns the smallest value in window`() {
        rm.add(10.0)
        rm.add(30.0)
        rm.add(20.0)
        assertEquals(10.0, rm.min(), 0.0001)
    }

    @Test
    fun `min updates after overflow evicts previous min`() {
        rm.add(5.0)
        rm.add(20.0)
        rm.add(30.0)
        rm.add(40.0) // evicts 5.0
        assertEquals(20.0, rm.min(), 0.0001) // window: [20, 30, 40]
    }

    @Test
    fun `min of single element returns that element`() {
        rm.add(42.0)
        assertEquals(42.0, rm.min(), 0.0001)
    }

    // --- negative values ---

    @Test
    fun `handles negative values for max and min`() {
        rm.add(-10.0)
        rm.add(-30.0)
        rm.add(-20.0)
        assertEquals(-10.0, rm.max(), 0.0001)
        assertEquals(-30.0, rm.min(), 0.0001)
    }

    // --- edge: window size 1 ---

    @Test
    fun `window of size 1 always returns latest value`() {
        val single = RunningMax(1)
        single.add(5.0)
        assertEquals(5.0, single.max(), 0.0001)
        assertEquals(5.0, single.min(), 0.0001)
        single.add(15.0)
        assertEquals(15.0, single.max(), 0.0001)
        assertEquals(15.0, single.min(), 0.0001)
    }

    // --- extended edge cases ---

    @Test
    fun `all identical values`() {
        rm.add(42.0)
        rm.add(42.0)
        rm.add(42.0)
        assertEquals(42.0, rm.max(), 0.0001)
        assertEquals(42.0, rm.min(), 0.0001)
    }

    @Test
    fun `zero values`() {
        rm.add(0.0)
        rm.add(0.0)
        assertEquals(0.0, rm.max(), 0.0001)
        assertEquals(0.0, rm.min(), 0.0001)
    }

    @Test
    fun `alternating high low pattern`() {
        rm.add(100.0)
        rm.add(1.0)
        rm.add(100.0)
        assertEquals(100.0, rm.max(), 0.0001)
        assertEquals(1.0, rm.min(), 0.0001)
    }

    @Test
    fun `large number of adds maintains correctness`() {
        val bigRm = RunningMax(5)
        for (i in 1..50) bigRm.add(i.toDouble())
        // Window: [46, 47, 48, 49, 50]
        assertEquals(50.0, bigRm.max(), 0.0001)
        assertEquals(46.0, bigRm.min(), 0.0001)
        assertEquals(5, bigRm.size())
    }
}
