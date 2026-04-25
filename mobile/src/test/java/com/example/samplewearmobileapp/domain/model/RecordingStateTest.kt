package com.example.samplewearmobileapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RecordingStateTest {

    @Test
    fun `Idle is distinct from Recording and Paused`() {
        val idle = RecordingState.Idle
        val recording = RecordingState.Recording(startEpochMs = 1000L)
        val paused = RecordingState.Paused(totalElapsedMs = 5000L)
        assertNotEquals(idle, recording)
        assertNotEquals(idle, paused)
        assertNotEquals(recording, paused)
    }

    @Test
    fun `Recording default accumulatedElapsedMs is zero`() {
        val state = RecordingState.Recording(startEpochMs = 12345L)
        assertEquals(0L, state.accumulatedElapsedMs)
    }

    @Test
    fun `Recording stores startEpochMs correctly`() {
        val expected = 9_999_999L
        val state = RecordingState.Recording(startEpochMs = expected)
        assertEquals(expected, state.startEpochMs)
    }

    @Test
    fun `Paused stores totalElapsedMs correctly`() {
        val expected = 30_000L
        val state = RecordingState.Paused(totalElapsedMs = expected)
        assertEquals(expected, state.totalElapsedMs)
    }

    @Test
    fun `Recording equality by value`() {
        val a = RecordingState.Recording(startEpochMs = 1000L, accumulatedElapsedMs = 500L)
        val b = RecordingState.Recording(startEpochMs = 1000L, accumulatedElapsedMs = 500L)
        assertEquals(a, b)
    }

    @Test
    fun `Recording inequality when startEpochMs differs`() {
        val a = RecordingState.Recording(startEpochMs = 1000L)
        val b = RecordingState.Recording(startEpochMs = 2000L)
        assertNotEquals(a, b)
    }

    @Test
    fun `when expression is exhaustive over all states`() {
        // If this compiles, the sealed class is exhaustive
        fun describe(state: RecordingState): String = when (state) {
            is RecordingState.Idle -> "idle"
            is RecordingState.Recording -> "recording"
            is RecordingState.Paused -> "paused"
        }
        assertEquals("idle", describe(RecordingState.Idle))
        assertEquals("recording", describe(RecordingState.Recording(0L)))
        assertEquals("paused", describe(RecordingState.Paused(0L)))
    }
}
