package com.example.samplewearmobileapp.utils

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Tests for the pure (non-Android-dependent) utility functions
 * in [AppUtils]. Methods that require a Context or AlertDialog
 * must be tested via instrumented (androidTest) tests.
 */
class AppUtilsTest {

    // --- getExtension ---

    @Test
    fun `getExtension returns extension without dot`() {
        assertEquals("txt", AppUtils.getExtension(File("file.txt")))
    }

    @Test
    fun `getExtension returns last extension for double-dotted filename`() {
        assertEquals("gz", AppUtils.getExtension(File("archive.tar.gz")))
    }

    @Test
    fun `getExtension returns null for no extension`() {
        assertNull(AppUtils.getExtension(File("Makefile")))
    }

    @Test
    fun `getExtension returns extension in lowercase`() {
        assertEquals("jpg", AppUtils.getExtension(File("Photo.JPG")))
    }

    @Test
    fun `getExtension returns null for dot at end`() {
        // lastIndexOf('.') returns last index, but i < s.length - 1 fails
        assertNull(AppUtils.getExtension(File("file.")))
    }

    @Test
    fun `getExtension returns null for dotfile without further extension`() {
        // ".gitignore" → lastIndexOf('.') == 0, but condition is i > 0
        assertNull(AppUtils.getExtension(File(".gitignore")))
    }

    // --- getStackTraceString ---

    @Test
    fun `getStackTraceString contains exception message`() {
        val ex = RuntimeException("test error")
        val trace = AppUtils.getStackTraceString(ex)
        assertNotNull(trace)
        assertTrue(trace!!.contains("test error"))
    }

    @Test
    fun `getStackTraceString contains exception class name`() {
        val ex = IllegalArgumentException("bad arg")
        val trace = AppUtils.getStackTraceString(ex)
        assertNotNull(trace)
        assertTrue(trace!!.contains("IllegalArgumentException"))
    }

    @Test
    fun `getStackTraceString contains stack frames`() {
        val ex = RuntimeException("test")
        val trace = AppUtils.getStackTraceString(ex)
        assertNotNull(trace)
        assertTrue(trace!!.contains("at "))
    }

    // --- getHashCode ---

    @Test
    fun `getHashCode returns consistent value for same object`() {
        val obj = "test string"
        val hash1 = AppUtils.getHashCode(obj)
        val hash2 = AppUtils.getHashCode(obj)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `getHashCode returns null string for null input`() {
        assertEquals("null", AppUtils.getHashCode(null))
    }

    @Test
    fun `getHashCode formats as 8-char uppercase hex string`() {
        val hash = AppUtils.getHashCode("test")
        assertNotNull(hash)
        assertEquals(8, hash!!.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'A'..'F' })
    }

    // --- extended edge cases ---

    @Test
    fun `getExtension with path separators`() {
        assertEquals("txt", AppUtils.getExtension(File("/path/to/file.txt")))
    }

    @Test
    fun `getExtension with windows path separators`() {
        assertEquals("csv", AppUtils.getExtension(File("C:\\data\\output.csv")))
    }

    @Test
    fun `getHashCode with integer`() {
        val hash = AppUtils.getHashCode(42)
        assertNotNull(hash)
        assertEquals(8, hash!!.length)
    }

    @Test
    fun `getHashCode with empty string`() {
        val hash = AppUtils.getHashCode("")
        assertNotNull(hash)
        assertEquals(8, hash!!.length)
    }

    @Test
    fun `getStackTraceString with nested cause`() {
        val cause = IllegalStateException("root cause")
        val ex = RuntimeException("wrapper", cause)
        val trace = AppUtils.getStackTraceString(ex)
        assertNotNull(trace)
        assertTrue(trace!!.contains("root cause"))
        assertTrue(trace.contains("wrapper"))
    }
}
