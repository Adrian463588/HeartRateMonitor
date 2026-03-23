package com.example.samplewearmobileapp.constants.codes

import org.junit.Assert.*
import org.junit.Test

class ActivityCodeTest {

    @Test
    fun `all codes are unique`() {
        val codes = listOf(
            ActivityCode.DO_NOTHING,
            ActivityCode.START_ACTIVITY,
            ActivityCode.STOP_ACTIVITY,
            ActivityCode.PAUSE_ACTIVITY
        )
        assertEquals("All codes should be unique", codes.size, codes.toSet().size)
    }

    @Test
    fun `DO_NOTHING is 0`() {
        assertEquals(0, ActivityCode.DO_NOTHING)
    }

    @Test
    fun `START_ACTIVITY is 1`() {
        assertEquals(1, ActivityCode.START_ACTIVITY)
    }

    @Test
    fun `STOP_ACTIVITY is 2`() {
        assertEquals(2, ActivityCode.STOP_ACTIVITY)
    }

    @Test
    fun `PAUSE_ACTIVITY is 3`() {
        assertEquals(3, ActivityCode.PAUSE_ACTIVITY)
    }

    @Test
    fun `codes are sequential from 0`() {
        assertEquals(ActivityCode.DO_NOTHING + 1, ActivityCode.START_ACTIVITY)
        assertEquals(ActivityCode.START_ACTIVITY + 1, ActivityCode.STOP_ACTIVITY)
        assertEquals(ActivityCode.STOP_ACTIVITY + 1, ActivityCode.PAUSE_ACTIVITY)
    }
}
