package com.example.samplewearmobileapp.constants

import org.junit.Assert.*
import org.junit.Test

class MessagePathTest {

    @Test
    fun `all paths start with slash`() {
        val paths = listOf(
            MessagePath.COMMAND,
            MessagePath.REQUEST,
            MessagePath.INFO,
            MessagePath.DATA_HR,
            MessagePath.DATA_PPG_GREEN,
            MessagePath.DATA_PPG_IR,
            MessagePath.DATA_PPG_RED
        )
        paths.forEach { path ->
            assertTrue("Path '$path' should start with '/'", path.startsWith("/"))
        }
    }

    @Test
    fun `all paths are unique`() {
        val paths = listOf(
            MessagePath.COMMAND,
            MessagePath.REQUEST,
            MessagePath.INFO,
            MessagePath.DATA_HR,
            MessagePath.DATA_PPG_GREEN,
            MessagePath.DATA_PPG_IR,
            MessagePath.DATA_PPG_RED
        )
        assertEquals("All paths should be unique", paths.size, paths.toSet().size)
    }

    @Test
    fun `COMMAND path value is correct`() {
        assertEquals("/command", MessagePath.COMMAND)
    }

    @Test
    fun `REQUEST path value is correct`() {
        assertEquals("/request", MessagePath.REQUEST)
    }

    @Test
    fun `INFO path value is correct`() {
        assertEquals("/info", MessagePath.INFO)
    }

    @Test
    fun `data paths contain expected identifiers`() {
        assertTrue(MessagePath.DATA_HR.contains("hr"))
        assertTrue(MessagePath.DATA_PPG_GREEN.contains("green"))
        assertTrue(MessagePath.DATA_PPG_IR.contains("ir"))
        assertTrue(MessagePath.DATA_PPG_RED.contains("red"))
    }
}
