package com.example.samplewearmobileapp.constants.codes

import org.junit.Assert.*
import org.junit.Test

class ExtraCodeTest {

    @Test
    fun `PPG Green codes use 0x0X range`() {
        assertEquals(0x01, ExtraCode.STOP_PPG_GREEN)
        assertEquals(0x02, ExtraCode.START_PPG_GREEN)
        assertEquals(0x03, ExtraCode.RESTART_PPG_GREEN)
        assertEquals(0x04, ExtraCode.DATA_PPG_GREEN)
    }

    @Test
    fun `PPG IR codes use 0x1X range`() {
        assertEquals(0x11, ExtraCode.STOP_PPG_IR)
        assertEquals(0x12, ExtraCode.START_PPG_IR)
        assertEquals(0x13, ExtraCode.RESTART_PPG_IR)
        assertEquals(0x14, ExtraCode.DATA_PPG_IR)
    }

    @Test
    fun `PPG Red codes use 0x2X range`() {
        assertEquals(0x21, ExtraCode.STOP_PPG_RED)
        assertEquals(0x22, ExtraCode.START_PPG_RED)
        assertEquals(0x23, ExtraCode.RESTART_PPG_RED)
        assertEquals(0x24, ExtraCode.DATA_PPG_RED)
    }

    @Test
    fun `TOGGLE_ACTIVITY uses 0x3X range`() {
        assertEquals(0x31, ExtraCode.TOGGLE_ACTIVITY)
    }

    @Test
    fun `all codes are unique`() {
        val codes = listOf(
            ExtraCode.STOP_PPG_GREEN, ExtraCode.START_PPG_GREEN,
            ExtraCode.RESTART_PPG_GREEN, ExtraCode.DATA_PPG_GREEN,
            ExtraCode.STOP_PPG_IR, ExtraCode.START_PPG_IR,
            ExtraCode.RESTART_PPG_IR, ExtraCode.DATA_PPG_IR,
            ExtraCode.STOP_PPG_RED, ExtraCode.START_PPG_RED,
            ExtraCode.RESTART_PPG_RED, ExtraCode.DATA_PPG_RED,
            ExtraCode.TOGGLE_ACTIVITY
        )
        assertEquals("All extra codes should be unique",
            codes.size, codes.toSet().size)
    }

    @Test
    fun `each group follows STOP-START-RESTART-DATA pattern`() {
        // Green: 0x01, 0x02, 0x03, 0x04
        assertEquals(ExtraCode.STOP_PPG_GREEN + 1, ExtraCode.START_PPG_GREEN)
        assertEquals(ExtraCode.START_PPG_GREEN + 1, ExtraCode.RESTART_PPG_GREEN)
        assertEquals(ExtraCode.RESTART_PPG_GREEN + 1, ExtraCode.DATA_PPG_GREEN)

        // IR: 0x11, 0x12, 0x13, 0x14
        assertEquals(ExtraCode.STOP_PPG_IR + 1, ExtraCode.START_PPG_IR)
        assertEquals(ExtraCode.START_PPG_IR + 1, ExtraCode.RESTART_PPG_IR)
        assertEquals(ExtraCode.RESTART_PPG_IR + 1, ExtraCode.DATA_PPG_IR)

        // Red: 0x21, 0x22, 0x23, 0x24
        assertEquals(ExtraCode.STOP_PPG_RED + 1, ExtraCode.START_PPG_RED)
        assertEquals(ExtraCode.START_PPG_RED + 1, ExtraCode.RESTART_PPG_RED)
        assertEquals(ExtraCode.RESTART_PPG_RED + 1, ExtraCode.DATA_PPG_RED)
    }
}
