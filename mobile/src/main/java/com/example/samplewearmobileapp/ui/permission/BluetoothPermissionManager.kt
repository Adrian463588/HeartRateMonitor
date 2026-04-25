package com.example.samplewearmobileapp.ui.permission

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Manages the two-step Bluetooth runtime permission flow for the mobile companion app.
 *
 * ## Why two steps?
 * On Android 12+ (API 31+), `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` are runtime
 * permissions that must be explicitly granted. On older devices, `BLUETOOTH` and
 * `ACCESS_FINE_LOCATION` were required instead. This manager abstracts the version
 * checks so that [MainActivity] remains free of permission logic (SRP).
 *
 * ## Flow
 * ```
 * startFlow()
 *   └─ all granted? ──YES──► callback.onAllPermissionsGranted()
 *   └─ missing?     ──────► requestPermissions()
 *                               │
 *                       onPermissionResult(REQUEST_CODE)
 *                               │
 *                       all granted? ──YES──► callback.onAllPermissionsGranted()
 *                                    ──NO (rationale)──► callback.onPermissionsDenied()
 *                                    ──NO (permanent)──► callback.onPermissionsPermanentlyDenied()
 * ```
 *
 * **SOLID compliance:**
 * - SRP: all Bluetooth permission logic is isolated here.
 * - OCP: extend [PERMISSIONS_API_31] or [PERMISSIONS_LEGACY] to add new permissions.
 * - DIP: depends on [BluetoothPermissionCallback] abstraction, not concrete Activity.
 *
 * @param activity The host [FragmentActivity] (used for permission requests & rationale check).
 * @param callback Receives permission outcomes.
 */
class BluetoothPermissionManager(
    private val activity: FragmentActivity,
    private val callback: BluetoothPermissionCallback
) {

    // -------------------------------------------------------------------------
    // Public callback interface — implemented by the caller (e.g. MainActivity)
    // -------------------------------------------------------------------------

    interface BluetoothPermissionCallback {
        /** All required Bluetooth permissions are granted. Proceed with BLE operations. */
        fun onAllPermissionsGranted()

        /**
         * One or more permissions were denied, but a rationale can still be shown.
         * Typically: show an explanation AlertDialog and re-trigger [startFlow].
         */
        fun onPermissionsDenied()

        /**
         * One or more permissions are permanently denied ("Don't ask again").
         * Guide the user to App Settings via an Intent to grant manually.
         */
        fun onPermissionsPermanentlyDenied()
    }

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    companion object {
        /**
         * Bluetooth permissions required on Android 12+ (API 31+).
         * `neverForLocation` flag is declared in the Manifest on BLUETOOTH_SCAN.
         */
        private val PERMISSIONS_API_31 = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )

        /**
         * Legacy Bluetooth + location permission required below Android 12.
         * These are capped at `maxSdkVersion="30"` in the Manifest.
         */
        private val PERMISSIONS_LEGACY = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        /** Request code used in [onPermissionResult]. */
        const val REQUEST_CODE_BLUETOOTH = 20
    }

    private val tag = "BluetoothPermMgr"

    /** The permission array applicable to the device's API level. */
    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PERMISSIONS_API_31
        } else {
            PERMISSIONS_LEGACY
        }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Entry point. Call from [MainActivity.onCreate] (after binding is set up).
     *
     * Immediately calls [BluetoothPermissionCallback.onAllPermissionsGranted] if
     * all permissions are already granted, otherwise starts the permission request.
     */
    fun startFlow() {
        if (allGranted()) {
            Log.d(tag, "All Bluetooth permissions already granted")
            callback.onAllPermissionsGranted()
        } else {
            requestPermissions()
        }
    }

    /**
     * Must be forwarded from [MainActivity.onRequestPermissionsResult].
     *
     * @return `true` if this manager handled the request code; `false` otherwise.
     */
    fun onPermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        if (requestCode != REQUEST_CODE_BLUETOOTH) return false
        handleResult(permissions, grantResults)
        return true
    }

    /** Returns `true` if all required Bluetooth permissions are currently granted. */
    fun allGranted(): Boolean = requiredPermissions.all { isGranted(it) }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun requestPermissions() {
        Log.d(tag, "Requesting Bluetooth permissions: ${requiredPermissions.toList()}")
        ActivityCompat.requestPermissions(
            activity,
            requiredPermissions,
            REQUEST_CODE_BLUETOOTH
        )
    }

    private fun handleResult(permissions: Array<out String>, grantResults: IntArray) {
        val allGranted = grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }

        if (allGranted) {
            Log.d(tag, "All Bluetooth permissions granted")
            callback.onAllPermissionsGranted()
            return
        }

        // Determine if any denial is permanent (user ticked "Don't ask again")
        val isPermanentDenial = permissions.any { permission ->
            !isGranted(permission) &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }

        Log.w(tag, "Bluetooth permissions denied (permanent=$isPermanentDenial)")
        if (isPermanentDenial) {
            callback.onPermissionsPermanentlyDenied()
        } else {
            callback.onPermissionsDenied()
        }
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) ==
            PackageManager.PERMISSION_GRANTED
}
