package com.example.samplewearmobileapp.models

import org.junit.Assert.*
import org.junit.Test

class TrackerLifecycleTest {
    @Test fun `Pause releases once and invalidates queued callbacks before Resume`() {
        val tracker = TrackerLifecycle()
        var starts = 0
        var stops = 0
        tracker.start { starts++ }
        val oldCallback = tracker.generation
        tracker.start { starts++ }
        tracker.stop { stops++ }
        tracker.stop { stops++ }
        assertFalse(tracker.accepts(oldCallback))
        tracker.start { starts++ }
        assertFalse(tracker.accepts(oldCallback))
        assertTrue(tracker.accepts(tracker.generation))
        assertEquals(2, starts)
        assertEquals(1, stops)
    }

    @Test fun `failed unregister preserves ownership for a successful retry`() {
        val tracker = TrackerLifecycle()
        tracker.start {}
        try {
            tracker.stop { throw IllegalStateException("SDK busy") }
            fail("Expected release failure")
        } catch (_: IllegalStateException) { }
        assertFalse(tracker.requested)
        assertTrue(tracker.registered)
        assertFalse(tracker.accepts(tracker.generation))
        tracker.stop {}
        assertFalse(tracker.registered)
    }

    @Test fun `partial registration failure still requires cleanup`() {
        val tracker = TrackerLifecycle()
        try {
            tracker.start { throw IllegalStateException("Registration failed") }
            fail("Expected registration failure")
        } catch (_: IllegalStateException) { }
        assertFalse(tracker.requested)
        assertTrue(tracker.registered)
        var released = false
        tracker.stop { released = true }
        assertTrue(released)
        assertFalse(tracker.registered)
    }

    @Test fun `Start cannot overlap a tracker whose release failed`() {
        val tracker = TrackerLifecycle()
        tracker.start {}
        try { tracker.stop { throw IllegalStateException("SDK busy") } } catch (_: IllegalStateException) { }
        var started = false
        try {
            tracker.start { started = true }
            fail("Unreleased tracker must block Start")
        } catch (_: IllegalStateException) { }
        assertFalse(started)
    }

    @Test fun `Stop without prior registration is safe`() {
        val tracker = TrackerLifecycle()
        var called = false
        tracker.stop { called = true }
        assertFalse(called)
        assertFalse(tracker.registered)
        assertFalse(tracker.requested)
    }
}
