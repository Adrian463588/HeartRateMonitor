package com.example.samplewearmobileapp

import com.example.samplewearmobileapp.trackers.Listener
import org.junit.Assert.*
import org.junit.Test

class ListenerReadinessTest {
    @Test
    fun `missing Samsung connection cannot crash or report recording`() {
        val listener = Listener()
        assertFalse(listener.startTracker())
        listener.stopTracker()
        listener.stopTracker()
        assertFalse(listener.isTracking())
    }
}
