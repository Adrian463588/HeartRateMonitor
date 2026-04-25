package com.example.samplewearmobileapp

import com.example.samplewearmobileapp.trackers.Listener
import com.samsung.android.service.health.tracking.HealthTracker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

/**
 * Unit tests for [Listener] base class.
 *
 * Validates lifecycle state management and null-safety guards added to
 * prevent NPE crashes on Galaxy Watch 5 / One UI 6.
 */
class ListenerTest {

    private lateinit var listener: Listener

    @Before
    fun setUp() {
        listener = Listener()
    }

    @Test
    fun `isTracking returns false on fresh instance`() {
        assertFalse(listener.isTracking())
    }

    @Test
    fun `startTracker without healthTracker set does not crash and stays false`() {
        // Should log an error and return cleanly — no NPE
        listener.startTracker()
        assertFalse(listener.isTracking())
    }

    @Test
    fun `stopTracker when not running does not crash`() {
        // Safe to call before start — should be a no-op
        listener.stopTracker()
        assertFalse(listener.isTracking())
    }

    @Test
    fun `stopTracker when healthTracker not set does not crash`() {
        // Simulates calling stopTracker before ConnectionManager.initPpg* completes
        listener.setHandlerRunning(true)   // force handler-running state
        listener.stopTracker()             // healthTracker is null → must not NPE
        assertFalse(listener.isTracking())
    }

    @Test
    fun `setHandlerRunning reflects in subsequent stopTracker guard`() {
        listener.setHandlerRunning(false)
        // stopTracker early-returns when handler is not running
        listener.stopTracker()
        assertFalse(listener.isTracking())
    }
}
