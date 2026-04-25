package com.example.samplewearmobileapp

import com.example.samplewearmobileapp.constants.MessagePath
import com.example.samplewearmobileapp.constants.codes.ActivityCode
import com.example.samplewearmobileapp.models.PpgType
import com.example.samplewearmobileapp.models.Message
import com.example.samplewearmobileapp.constants.Entity.PHONE_APP
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

/**
 * Unit tests for WearableClient-adjacent concerns:
 * - Gson singleton availability + round-trip serialisation of [Message]
 * - [MessagePath] constant non-emptiness and uniqueness
 * - [PpgType]-to-MessagePath exhaustiveness
 * - [ActivityCode] value distinctness
 *
 * Full end-to-end WearableClient tests require Robolectric or instrumentation because
 * [Wearable.getMessageClient] requires the Android framework.
 */
@RunWith(MockitoJUnitRunner::class)
class WearableClientTest {

    private val gson = Gson()

    // -------------------------------------------------------------------------
    // Gson singleton in MainActivity.companion
    // -------------------------------------------------------------------------

    @Test
    fun `Gson singleton in MainActivity companion is not null`() {
        assertNotNull(MainActivity.GSON)
    }

    // -------------------------------------------------------------------------
    // Message serialization round-trip
    // -------------------------------------------------------------------------

    @Test
    fun `Gson can serialize and deserialize Message sender and code`() {
        // Use the 2-arg constructor: Message(sender, activityCode)
        val original = Message(PHONE_APP, ActivityCode.START_ACTIVITY)
        val json = gson.toJson(original)
        val deserialized = gson.fromJson(json, Message::class.java)
        assertEquals(original.sender, deserialized.sender)
        assertEquals(original.code, deserialized.code)
    }

    @Test
    fun `Gson round-trip preserves content set via property`() {
        val original = Message(PHONE_APP, ActivityCode.STOP_ACTIVITY).also { it.content = "hello" }
        val json = gson.toJson(original)
        val deserialized = gson.fromJson(json, Message::class.java)
        assertEquals("hello", deserialized.content)
    }

    @Test
    fun `Gson round-trip preserves extraCode`() {
        val original = Message(PHONE_APP, ActivityCode.START_ACTIVITY, 42)
        val json = gson.toJson(original)
        val deserialized = gson.fromJson(json, Message::class.java)
        assertEquals(42, deserialized.extraCode)
    }

    // -------------------------------------------------------------------------
    // MessagePath constant non-emptiness
    // -------------------------------------------------------------------------

    @Test
    fun `MessagePath constants are all non-empty strings`() {
        assertTrue(MessagePath.COMMAND.isNotEmpty())
        assertTrue(MessagePath.DATA_HR.isNotEmpty())
        assertTrue(MessagePath.DATA_PPG_GREEN.isNotEmpty())
        assertTrue(MessagePath.DATA_PPG_IR.isNotEmpty())
        assertTrue(MessagePath.DATA_PPG_RED.isNotEmpty())
        assertTrue(MessagePath.INFO.isNotEmpty())
        assertTrue(MessagePath.REQUEST.isNotEmpty())
    }

    @Test
    fun `All three PPG channel paths are distinct`() {
        val paths = setOf(
            MessagePath.DATA_PPG_GREEN,
            MessagePath.DATA_PPG_IR,
            MessagePath.DATA_PPG_RED
        )
        assertEquals("PPG paths must all be unique", 3, paths.size)
    }

    // -------------------------------------------------------------------------
    // PpgType exhaustiveness — every type maps to a path
    // -------------------------------------------------------------------------

    @Test
    fun `every PpgType value maps to a non-empty MessagePath`() {
        PpgType.values().forEach { type ->
            val path = when (type) {
                PpgType.PPG_GREEN -> MessagePath.DATA_PPG_GREEN
                PpgType.PPG_IR    -> MessagePath.DATA_PPG_IR
                PpgType.PPG_RED   -> MessagePath.DATA_PPG_RED
            }
            assertTrue("$type maps to empty path", path.isNotEmpty())
        }
    }

    // -------------------------------------------------------------------------
    // ActivityCode value distinctness
    // -------------------------------------------------------------------------

    @Test
    fun `ActivityCode START and STOP are different`() {
        assertNotEquals(ActivityCode.START_ACTIVITY, ActivityCode.STOP_ACTIVITY)
    }

    @Test
    fun `ActivityCode PAUSE is distinct from START and STOP`() {
        assertNotEquals(ActivityCode.PAUSE_ACTIVITY, ActivityCode.START_ACTIVITY)
        assertNotEquals(ActivityCode.PAUSE_ACTIVITY, ActivityCode.STOP_ACTIVITY)
    }

    @Test
    fun `ActivityCode DO_NOTHING is zero`() {
        assertEquals(0, ActivityCode.DO_NOTHING)
    }

    // -------------------------------------------------------------------------
    // Message default constructor safety
    // -------------------------------------------------------------------------

    @Test
    fun `Message default (no arg) constructor does not throw`() {
        val msg = Message()
        assertNotNull(msg.sender)
    }

    @Test
    fun `Message single-arg constructor sets default code`() {
        val msg = Message("TestSender")
        assertEquals(ActivityCode.DO_NOTHING, msg.code)
    }
}
