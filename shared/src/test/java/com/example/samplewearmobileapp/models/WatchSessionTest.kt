package com.example.samplewearmobileapp.models

import org.junit.Assert.*
import org.junit.Test

class WatchSessionTest {
    @Test
    fun `late 42 sample flush remains accepted after stop`() {
        val session = WatchSession()
        session.start(1000L)
        session.requestStop()
        session.reportStopped(1000L, intArrayOf(0, 0, 0, 42))
        assertFalse(session.isComplete)
        assertTrue(session.acceptBatch(1000L, "/dataaccelerometer/1000/0", 3, 42))
        assertTrue(session.isComplete)
    }

    @Test
    fun `stop counts can arrive after the final batch`() {
        val session = WatchSession()
        session.start(1000L)
        session.requestStop()
        session.acceptBatch(1000L, "batch", 3, 42)
        assertFalse(session.isComplete)
        session.reportStopped(1000L, intArrayOf(0, 0, 0, 42))
        assertTrue(session.isComplete)
    }

    @Test
    fun `pause flush and resumed batches belong to the same session`() {
        val session = WatchSession()
        session.start(1000L)
        assertTrue(session.acceptBatch(1000L, "pause-tail", 0, 42))
        assertTrue(session.acceptBatch(1000L, "resumed-batch", 0, 300))
        session.requestStop()
        session.reportStopped(1000L, intArrayOf(342, 0, 0, 0))
        assertTrue(session.isComplete)
    }

    @Test
    fun `replayed batch cannot satisfy missing data twice`() {
        val session = WatchSession()
        session.start(1000L)
        session.requestStop()
        session.reportStopped(1000L, intArrayOf(0, 0, 0, 84))
        assertTrue(session.acceptBatch(1000L, "same-batch", 3, 42))
        assertFalse(session.acceptBatch(1000L, "same-batch", 3, 42))
        assertFalse(session.isComplete)
    }

    @Test
    fun `fresh session rejects previous recording and stop receipts`() {
        val session = WatchSession()
        session.start(1000L)
        session.acceptBatch(1000L, "old", 3, 42)
        session.start(2000L)
        session.requestStop()
        session.reportStopped(1000L, intArrayOf(0, 0, 0, 42))
        assertFalse(session.acceptBatch(1000L, "late-old", 3, 42))
        assertFalse(session.isComplete)
        session.reportStopped(2000L, intArrayOf(0, 0, 0, 0))
        assertTrue(session.isComplete)
    }

    @Test
    fun `all four channels must be delivered before completion`() {
        val session = WatchSession()
        session.start(1000L)
        session.requestStop()
        session.reportStopped(1000L, intArrayOf(300, 300, 300, 100))
        for (channel in 0..2) {
            session.acceptBatch(1000L, "ppg-$channel", channel, 300)
            assertFalse(session.isComplete)
        }
        session.acceptBatch(1000L, "acc", 3, 100)
        assertTrue(session.isComplete)
    }

    @Test
    fun `invalid counts and unknown sessions cannot claim completion`() {
        val session = WatchSession()
        assertFalse(session.acceptBatch(0L, "idle", 0, 1))
        session.start(1000L)
        session.requestStop()
        session.reportStopped(1000L, intArrayOf(-1, 0, 0, 0))
        session.reportStopped(1000L, intArrayOf(0))
        assertFalse(session.acceptBatch(1000L, "invalid-channel", 4, 1))
        assertFalse(session.acceptBatch(1000L, "empty", 0, 0))
        assertFalse(session.isComplete)
    }
}
