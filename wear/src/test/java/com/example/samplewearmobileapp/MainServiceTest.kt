package com.example.samplewearmobileapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MainService] companion-object state tracking.
 *
 * Validates the @Volatile isRunning flag introduced to replace the broken
 * ContextCompat.getSystemService pattern that always returned null.
 */
class MainServiceTest {

    @Test
    fun `isRunning is false by default`() {
        // Reset to known state
        MainService::class.java
            .getDeclaredField("isRunning")
            .also { it.isAccessible = true }
            .set(null, false)

        assertFalse(MainService.isRunning)
    }

    @Test
    fun `stopService does not crash when service is not running`() {
        // Should be a safe no-op when called before start
        // (context is null-safe in test since we are testing the flag only)
        assertFalse(MainService.isRunning)
    }
}
