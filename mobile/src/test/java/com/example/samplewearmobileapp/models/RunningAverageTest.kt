package com.example.samplewearmobileapp.models

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RunningAverageTest {

    private lateinit var avg: RunningAverage

    @Before
    fun setUp() {
        avg = RunningAverage(3)
    }

    // --- empty state ---

    @Test
    fun `average of empty window is zero`() {
        assertEquals(0.0, avg.average(), 0.0001)
    }

    @Test
    fun `sum of empty window is zero`() {
        assertEquals(0.0, avg.sum(), 0.0001)
    }

    @Test
    fun `size of empty window is zero`() {
        assertEquals(0, avg.size())
    }

    // --- basic add and average ---

    @Test
    fun `average of single value equals that value`() {
        avg.add(10.0)
        assertEquals(10.0, avg.average(), 0.0001)
    }

    @Test
    fun `average of multiple values within window`() {
        avg.add(10.0)
        avg.add(20.0)
        avg.add(30.0)
        assertEquals(20.0, avg.average(), 0.0001)
    }

    @Test
    fun `sum tracks all values in window`() {
        avg.add(10.0)
        avg.add(20.0)
        assertEquals(30.0, avg.sum(), 0.0001)
    }

    @Test
    fun `size reflects number of added values`() {
        avg.add(1.0)
        avg.add(2.0)
        assertEquals(2, avg.size())
    }

    // --- window overflow ---

    @Test
    fun `overflow evicts oldest value from average`() {
        avg.add(10.0)
        avg.add(20.0)
        avg.add(30.0)
        avg.add(40.0) // evicts 10.0
        assertEquals(30.0, avg.average(), 0.0001) // (20+30+40)/3
    }

    @Test
    fun `overflow keeps size at max`() {
        avg.add(1.0)
        avg.add(2.0)
        avg.add(3.0)
        avg.add(4.0)
        avg.add(5.0)
        assertEquals(3, avg.size())
    }

    @Test
    fun `sum is correct after overflow`() {
        avg.add(10.0)
        avg.add(20.0)
        avg.add(30.0)
        avg.add(40.0) // evicts 10
        assertEquals(90.0, avg.sum(), 0.0001) // 20+30+40
    }

    // --- edge: window size 1 ---

    @Test
    fun `window of size 1 always returns latest value as average`() {
        val single = RunningAverage(1)
        single.add(5.0)
        assertEquals(5.0, single.average(), 0.0001)
        single.add(15.0)
        assertEquals(15.0, single.average(), 0.0001)
    }

    // --- negative values ---

    @Test
    fun `handles negative values correctly`() {
        avg.add(-10.0)
        avg.add(-20.0)
        avg.add(-30.0)
        assertEquals(-20.0, avg.average(), 0.0001)
    }
}
