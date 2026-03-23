package com.example.samplewearmobileapp.models

import com.example.samplewearmobileapp.constants.codes.ActivityCode
import org.junit.Assert.*
import org.junit.Test

class MessageTest {

    // --- empty constructor ---

    @Test
    fun `empty constructor sets default values`() {
        val msg = Message()
        assertEquals("Dum dum", msg.sender)
        assertEquals("Dummy message", msg.content)
        assertEquals(ActivityCode.DO_NOTHING, msg.code)
        assertNull(msg.extraCode)
    }

    // --- sender-only constructor ---

    @Test
    fun `sender-only constructor sets sender and defaults`() {
        val msg = Message("PhoneApp")
        assertEquals("PhoneApp", msg.sender)
        assertNull(msg.content)
        assertEquals(ActivityCode.DO_NOTHING, msg.code)
        assertNull(msg.extraCode)
    }

    // --- sender + activityCode constructor ---

    @Test
    fun `sender and code constructor sets both fields`() {
        val msg = Message("PhoneApp", ActivityCode.START_ACTIVITY)
        assertEquals("PhoneApp", msg.sender)
        assertEquals(ActivityCode.START_ACTIVITY, msg.code)
        assertNull(msg.content)
        assertNull(msg.extraCode)
    }

    @Test
    fun `sender and STOP code works correctly`() {
        val msg = Message("WearApp", ActivityCode.STOP_ACTIVITY)
        assertEquals(ActivityCode.STOP_ACTIVITY, msg.code)
    }

    // --- sender + activityCode + extraCode constructor ---

    @Test
    fun `full constructor sets all fields`() {
        val msg = Message("PhoneApp", ActivityCode.PAUSE_ACTIVITY, 42)
        assertEquals("PhoneApp", msg.sender)
        assertEquals(ActivityCode.PAUSE_ACTIVITY, msg.code)
        assertEquals(42, msg.extraCode)
        assertNull(msg.content)
    }

    // --- primary constructor (all fields) ---

    @Test
    fun `primary constructor sets all fields including content`() {
        val msg = Message("WearApp", "Hello", ActivityCode.START_ACTIVITY, 7)
        assertEquals("WearApp", msg.sender)
        assertEquals("Hello", msg.content)
        assertEquals(ActivityCode.START_ACTIVITY, msg.code)
        assertEquals(7, msg.extraCode)
    }

    // --- data class equality ---

    @Test
    fun `data class equals works for identical messages`() {
        val a = Message("A", "content", 1, 2)
        val b = Message("A", "content", 1, 2)
        assertEquals(a, b)
    }

    @Test
    fun `data class equals detects different sender`() {
        val a = Message("A", "content", 1, 2)
        val b = Message("B", "content", 1, 2)
        assertNotEquals(a, b)
    }

    // --- mutability ---

    @Test
    fun `fields are mutable`() {
        val msg = Message("Sender")
        msg.code = ActivityCode.STOP_ACTIVITY
        msg.content = "Updated"
        msg.extraCode = 99
        assertEquals(ActivityCode.STOP_ACTIVITY, msg.code)
        assertEquals("Updated", msg.content)
        assertEquals(99, msg.extraCode)
    }
}
