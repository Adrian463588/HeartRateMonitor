package com.example.samplewearmobileapp.models

import com.example.samplewearmobileapp.constants.codes.ActivityCode.DO_NOTHING
import com.example.samplewearmobileapp.constants.codes.ActivityCode.START_ACTIVITY
import com.example.samplewearmobileapp.constants.codes.ActivityCode.PAUSE_ACTIVITY
import com.example.samplewearmobileapp.constants.codes.ActivityCode.STOP_ACTIVITY
import org.junit.Assert.*
import org.junit.Test

class WatchControlTest {
    @Test fun `End cannot be undone by delayed Start or a later Start in the same session`() {
        val control = WatchControl()
        assertTrue(control.accept(10, 1, START_ACTIVITY, 0))
        assertTrue(control.accept(10, 3, STOP_ACTIVITY, 1))
        assertFalse(control.accept(10, 1, START_ACTIVITY, 2))
        assertFalse(control.accept(10, 4, START_ACTIVITY, 3))
        assertEquals(STOP_ACTIVITY, control.code)
        assertTrue(control.accept(11, 1, START_ACTIVITY, 4))
    }

    @Test fun `Pause and Resume preserve identity and reject reordered Pause`() {
        val control = WatchControl()
        control.accept(10, 1, START_ACTIVITY, 0)
        assertTrue(control.accept(10, 2, PAUSE_ACTIVITY, 1))
        assertTrue(control.accept(10, 3, START_ACTIVITY, 2))
        assertFalse(control.accept(10, 2, PAUSE_ACTIVITY, 3))
        assertEquals(10L, control.sessionId)
        assertEquals(3L, control.revision)
    }

    @Test fun `duplicate End stays stopped and can be acknowledged again`() {
        val control = WatchControl()
        assertTrue(control.accept(10, 2, STOP_ACTIVITY, 0)) // Start may have been lost.
        assertTrue(control.accept(10, 2, STOP_ACTIVITY, 1))
        assertFalse(control.accept(10, 2, PAUSE_ACTIVITY, 2))
        assertEquals(STOP_ACTIVITY, control.code)
    }

    @Test fun `an older session cannot stop or start the current one`() {
        val control = WatchControl()
        control.accept(20, 1, START_ACTIVITY, 0)
        assertFalse(control.accept(10, 99, STOP_ACTIVITY, 1))
        assertFalse(control.accept(10, 99, START_ACTIVITY, 1))
        assertEquals(20L, control.sessionId)
    }

    @Test fun `missing controller expires at fifteen seconds and heartbeat renews lease`() {
        val control = WatchControl()
        control.accept(10, 1, START_ACTIVITY, 1_000)
        assertFalse(control.isExpired(15_999))
        assertTrue(control.isExpired(16_000))
        assertTrue(control.accept(10, 1, START_ACTIVITY, 16_100))
        assertFalse(control.isExpired(16_101))
    }

    @Test fun `stale commands cannot keep sensors alive`() {
        val control = WatchControl()
        control.accept(10, 3, START_ACTIVITY, 0)
        assertFalse(control.accept(10, 1, START_ACTIVITY, 14_000))
        assertTrue(control.isExpired(15_000))
    }

    @Test fun `Pause and End have no acquisition lease`() {
        val control = WatchControl()
        control.accept(10, 1, PAUSE_ACTIVITY, 0)
        assertFalse(control.isExpired(100_000))
        control.accept(10, 2, STOP_ACTIVITY, 0)
        assertFalse(control.isExpired(100_000))
    }

    @Test fun `sensor release failure cannot acknowledge End`() {
        val control = WatchControl()
        control.accept(10, 2, STOP_ACTIVITY, 0)
        val ack = Message("Watch", null, STOP_ACTIVITY, null, 10, commandRevision = 2, sensorsStopped = false)
        assertFalse(control.matches(ack))
        ack.sensorsStopped = true
        assertTrue(control.matches(ack))
    }

    @Test fun `old acknowledgement cannot complete a newer command`() {
        val control = WatchControl()
        control.accept(10, 3, PAUSE_ACTIVITY, 0)
        val ack = Message("Watch", null, START_ACTIVITY, null, 10, commandRevision = 1)
        assertFalse(control.matches(ack))
        ack.code = PAUSE_ACTIVITY
        ack.commandRevision = 3
        ack.sensorsStopped = true
        assertTrue(control.matches(ack))
        ack.sessionId = 9
        assertFalse(control.matches(ack))
    }

    @Test fun `invalid or unversioned commands do not acquire sensors`() {
        val control = WatchControl()
        assertFalse(control.accept(0, 1, START_ACTIVITY, 0))
        assertFalse(control.accept(10, 0, START_ACTIVITY, 0))
        assertFalse(control.accept(10, 1, 99, 0))
        assertEquals(DO_NOTHING, control.code)
    }

    @Test fun `restoring the last End rejects delayed Start after Watch activity recreation`() {
        val restored = WatchControl()
        restored.accept(10, 3, STOP_ACTIVITY, 0)
        assertFalse(restored.accept(10, 1, START_ACTIVITY, 1))
        assertFalse(restored.accept(10, 4, START_ACTIVITY, 1))
        assertTrue(restored.accept(11, 1, START_ACTIVITY, 2))
    }
}
