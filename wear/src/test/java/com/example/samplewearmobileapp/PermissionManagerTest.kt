package com.example.samplewearmobileapp

import android.content.pm.PackageManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.times
import androidx.fragment.app.FragmentActivity

/**
 * Unit tests for [PermissionManager].
 *
 * These tests validate the two-step Android 13 permission flow:
 *  - Step 1 (STEP_FOREGROUND): BODY_SENSORS + ACTIVITY_RECOGNITION
 *  - Step 2 (STEP_BACKGROUND): BODY_SENSORS_BACKGROUND (API 33+ only)
 *
 * We test the result-routing logic in [PermissionManager.onPermissionResult]
 * by simulating granted / denied result arrays and asserting the correct
 * [PermissionManager.PermissionCallback] method is invoked.
 *
 * Note: Android framework classes (Activity, ContextCompat) are not available
 * in pure JVM unit tests. We therefore test [PermissionManager] through its
 * public [onPermissionResult] entry point using mocked callbacks.
 */
class PermissionManagerTest {

    private lateinit var callback: PermissionManager.PermissionCallback

    // Result arrays — reused across tests
    private val granted = intArrayOf(PackageManager.PERMISSION_GRANTED)
    private val denied  = intArrayOf(PackageManager.PERMISSION_DENIED)
    private val allForegroundGranted = intArrayOf(
        PackageManager.PERMISSION_GRANTED,
        PackageManager.PERMISSION_GRANTED
    )
    private val anyForegroundDenied = intArrayOf(
        PackageManager.PERMISSION_GRANTED,
        PackageManager.PERMISSION_DENIED
    )

    private val foregroundPermissions = arrayOf(
        android.Manifest.permission.BODY_SENSORS,
        android.Manifest.permission.ACTIVITY_RECOGNITION
    )

    @Before
    fun setUp() {
        callback = mock(PermissionManager.PermissionCallback::class.java)
    }

    // -------------------------------------------------------------------------
    // onPermissionResult — request code routing
    // -------------------------------------------------------------------------

    @Test
    fun `onPermissionResult returns false for unknown request code`() {
        // Create a real PermissionManager; Activity is not needed for this test
        // because we call onPermissionResult directly (no startFlow)
        val pm = buildPermissionManager()
        val handled = pm.onPermissionResult(99, emptyArray(), intArrayOf())
        assertFalse("Unknown request code should not be handled", handled)
    }

    @Test
    fun `onPermissionResult returns true for STEP_FOREGROUND code`() {
        val pm = buildPermissionManager()
        val handled = pm.onPermissionResult(
            PermissionManager.REQUEST_CODE_FOREGROUND,
            foregroundPermissions,
            anyForegroundDenied
        )
        assertTrue("FOREGROUND request code should be handled", handled)
    }

    @Test
    fun `onPermissionResult returns true for STEP_BACKGROUND code`() {
        val pm = buildPermissionManager()
        val handled = pm.onPermissionResult(
            PermissionManager.REQUEST_CODE_BACKGROUND,
            arrayOf(android.Manifest.permission.BODY_SENSORS_BACKGROUND),
            granted
        )
        assertTrue("BACKGROUND request code should be handled", handled)
    }

    // -------------------------------------------------------------------------
    // Step 2 (background) result handling
    // -------------------------------------------------------------------------

    @Test
    fun `background permission granted invokes onAllPermissionsGranted`() {
        val pm = buildPermissionManager()
        pm.onPermissionResult(
            PermissionManager.REQUEST_CODE_BACKGROUND,
            arrayOf(android.Manifest.permission.BODY_SENSORS_BACKGROUND),
            granted
        )
        verify(callback, times(1)).onAllPermissionsGranted()
        verify(callback, never()).onPermissionsPermanentlyDenied()
        verify(callback, never()).onPermissionsDenied()
    }

    @Test
    fun `background permission denied invokes onPermissionsPermanentlyDenied`() {
        val pm = buildPermissionManager()
        pm.onPermissionResult(
            PermissionManager.REQUEST_CODE_BACKGROUND,
            arrayOf(android.Manifest.permission.BODY_SENSORS_BACKGROUND),
            denied
        )
        verify(callback, times(1)).onPermissionsPermanentlyDenied()
        verify(callback, never()).onAllPermissionsGranted()
    }

    @Test
    fun `empty grantResults for background permission invokes onPermissionsPermanentlyDenied`() {
        val pm = buildPermissionManager()
        pm.onPermissionResult(
            PermissionManager.REQUEST_CODE_BACKGROUND,
            arrayOf(android.Manifest.permission.BODY_SENSORS_BACKGROUND),
            intArrayOf()  // empty — treated as denied
        )
        verify(callback, times(1)).onPermissionsPermanentlyDenied()
        verify(callback, never()).onAllPermissionsGranted()
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * Builds a [PermissionManager] with a mocked [FragmentActivity].
     * Note: [PermissionManager.startFlow] must NOT be called in unit tests
     * because it would invoke [ContextCompat.checkSelfPermission] which
     * requires the Android framework.
     */
    private fun buildPermissionManager(): PermissionManager {
        val activity = mock(FragmentActivity::class.java)
        return PermissionManager(activity, callback)
    }
}
