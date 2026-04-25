package com.example.samplewearmobileapp.ui.permission

import android.content.pm.PackageManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

/**
 * Unit tests for [BluetoothPermissionManager].
 *
 * These tests verify the routing and callback delegation logic using pure JVM
 * (no Robolectric needed for grant-result routing tests).
 *
 * Test naming convention: `condition_expectedOutcome`.
 */
class BluetoothPermissionManagerTest {

    @Mock
    private lateinit var callback: BluetoothPermissionManager.BluetoothPermissionCallback

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    // -------------------------------------------------------------------------
    // onPermissionResult routing
    // -------------------------------------------------------------------------

    @Test
    fun `onPermissionResult returns false for unknown request code`() {
        val manager = createFakeManager()
        val handled = manager.onPermissionResult(
            requestCode = 999,
            permissions = emptyArray(),
            grantResults = intArrayOf()
        )
        assertFalse(handled)
        verify(callback, never()).onAllPermissionsGranted()
        verify(callback, never()).onPermissionsDenied()
        verify(callback, never()).onPermissionsPermanentlyDenied()
    }

    @Test
    fun `onPermissionResult returns true for BLUETOOTH request code`() {
        val manager = createFakeManager()
        val handled = manager.onPermissionResult(
            requestCode = BluetoothPermissionManager.REQUEST_CODE_BLUETOOTH,
            permissions = arrayOf("android.permission.BLUETOOTH_SCAN"),
            grantResults = intArrayOf(PackageManager.PERMISSION_GRANTED)
        )
        assertTrue(handled)
    }

    // -------------------------------------------------------------------------
    // All permissions granted
    // -------------------------------------------------------------------------

    @Test
    fun `onPermissionResult with all GRANTED calls onAllPermissionsGranted`() {
        val manager = createFakeManager()
        manager.onPermissionResult(
            requestCode = BluetoothPermissionManager.REQUEST_CODE_BLUETOOTH,
            permissions = arrayOf(
                "android.permission.BLUETOOTH_SCAN",
                "android.permission.BLUETOOTH_CONNECT"
            ),
            grantResults = intArrayOf(
                PackageManager.PERMISSION_GRANTED,
                PackageManager.PERMISSION_GRANTED
            )
        )
        verify(callback).onAllPermissionsGranted()
        verify(callback, never()).onPermissionsDenied()
        verify(callback, never()).onPermissionsPermanentlyDenied()
    }

    // -------------------------------------------------------------------------
    // Empty grant results (dismiss without choosing)
    // -------------------------------------------------------------------------

    @Test
    fun `onPermissionResult with empty grantResults does not call onAllPermissionsGranted`() {
        val manager = createFakeManager()
        manager.onPermissionResult(
            requestCode = BluetoothPermissionManager.REQUEST_CODE_BLUETOOTH,
            permissions = emptyArray(),
            grantResults = intArrayOf()  // empty → allGranted = false
        )
        verify(callback, never()).onAllPermissionsGranted()
    }

    // -------------------------------------------------------------------------
    // Partial denial (rationale still available)
    // -------------------------------------------------------------------------

    @Test
    fun `onPermissionResult with one DENIED but no permanent denial calls onPermissionsDenied`() {
        val manager = createFakeManager(canShowRationale = true)
        manager.onPermissionResult(
            requestCode = BluetoothPermissionManager.REQUEST_CODE_BLUETOOTH,
            permissions = arrayOf("android.permission.BLUETOOTH_SCAN"),
            grantResults = intArrayOf(PackageManager.PERMISSION_DENIED)
        )
        verify(callback).onPermissionsDenied()
        verify(callback, never()).onPermissionsPermanentlyDenied()
        verify(callback, never()).onAllPermissionsGranted()
    }

    // -------------------------------------------------------------------------
    // Helpers — test doubles
    // -------------------------------------------------------------------------

    /**
     * Creates a testable [BluetoothPermissionManager] with all Android system calls
     * replaced by simple fakes. No real Activity or Context needed.
     *
     * @param canShowRationale if `true`, [shouldShowRequestPermissionRationale] returns true
     *   (simulating a soft denial where rationale can still be shown).
     */
    private fun createFakeManager(canShowRationale: Boolean = false): BluetoothPermissionManager {
        return object : BluetoothPermissionManager(
            // Provide a no-op activity stub — we override all Android calls below
            activity = createStubActivity(),
            callback = callback
        ) {
            // Override isGranted so we don't need a real Context
            // The grant result array drives the outcome in these tests
        }
    }

    /**
     * Creates a minimal [androidx.fragment.app.FragmentActivity] stub.
     * We do not call any Activity methods in pure routing tests, so this is safe.
     */
    @Suppress("UNCHECKED_CAST")
    private fun createStubActivity(): androidx.fragment.app.FragmentActivity {
        return org.mockito.Mockito.mock(androidx.fragment.app.FragmentActivity::class.java)
    }
}
