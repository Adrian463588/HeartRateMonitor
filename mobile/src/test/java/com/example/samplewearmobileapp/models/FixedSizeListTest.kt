package com.example.samplewearmobileapp.models

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FixedSizeListTest {

    private lateinit var list: FixedSizeList<Int>

    @Before
    fun setUp() {
        list = FixedSizeList(3)
    }

    // --- maxSize ---

    @Test
    fun `maxSize returns the configured maximum size`() {
        assertEquals(3, list.maxSize())
    }

    // --- add ---

    @Test
    fun `add appends element to the end`() {
        list.add(10)
        list.add(20)
        assertEquals(listOf(10, 20), list.toList())
    }

    @Test
    fun `add evicts oldest element when at capacity`() {
        list.add(1)
        list.add(2)
        list.add(3)
        list.add(4)
        assertEquals(3, list.size)
        assertEquals(listOf(2, 3, 4), list.toList())
    }

    @Test
    fun `add keeps evicting on repeated overflow`() {
        for (i in 1..10) list.add(i)
        assertEquals(3, list.size)
        assertEquals(listOf(8, 9, 10), list.toList())
    }

    // --- addFirst ---

    @Test
    fun `addFirst prepends element`() {
        list.addFirst(10)
        list.addFirst(20)
        assertEquals(listOf(20, 10), list.toList())
    }

    @Test
    fun `addFirst evicts oldest when at capacity`() {
        list.add(1)
        list.add(2)
        list.add(3)
        list.addFirst(0)
        assertEquals(3, list.size)
        // removeFirst() removes head=1, then 0 is prepended
        assertEquals(listOf(0, 2, 3), list.toList())
    }

    // --- addLast ---

    @Test
    fun `addLast appends element to the end`() {
        list.addLast(10)
        list.addLast(20)
        assertEquals(listOf(10, 20), list.toList())
    }

    @Test
    fun `addLast evicts oldest when at capacity`() {
        list.addLast(1)
        list.addLast(2)
        list.addLast(3)
        list.addLast(4)
        assertEquals(3, list.size)
        assertEquals(listOf(2, 3, 4), list.toList())
    }

    // --- setLast ---

    @Test
    fun `setLast replaces the last element`() {
        list.add(1)
        list.add(2)
        list.add(3)
        list.setLast(99)
        assertEquals(listOf(1, 2, 99), list.toList())
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun `setLast throws on empty list`() {
        list.setLast(42)
    }

    // --- edge cases ---

    @Test
    fun `list with size 1 always holds only the latest element`() {
        val single = FixedSizeList<String>(1)
        single.add("a")
        single.add("b")
        single.add("c")
        assertEquals(1, single.size)
        assertEquals("c", single.first)
    }

    @Test
    fun `empty list has size zero`() {
        assertEquals(0, list.size)
        assertTrue(list.isEmpty())
    }

    // --- extended edge cases ---

    @Test
    fun `setLast on single element replaces it`() {
        list.add(10)
        list.setLast(99)
        assertEquals(listOf(99), list.toList())
    }

    @Test
    fun `iteration order is maintained`() {
        list.add(1)
        list.add(2)
        list.add(3)
        val collected = mutableListOf<Int>()
        for (item in list) collected.add(item)
        assertEquals(listOf(1, 2, 3), collected)
    }

    @Test
    fun `first and last return correct values`() {
        list.add(10)
        list.add(20)
        list.add(30)
        assertEquals(10, list.first)
        assertEquals(30, list.last)
    }
}
