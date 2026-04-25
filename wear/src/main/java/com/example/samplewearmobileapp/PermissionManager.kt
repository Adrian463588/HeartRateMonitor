package com.example.samplewearmobileapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Manages the two-step runtime permission flow required on Android 13 (API 33+).
 *
 * ## Why two steps?
 * On Android 13+, `BODY_SENSORS_BACKGROUND` **must** be requested in a
 * separate `requestPermissions()` call *after* `BODY_SENSORS` is already
 * granted. Requesting them together causes the system to silently deny
 * the background permission, leading to a force-close loop.
 *
 * ## Flow
 * ```
 * startFlow()
 *   └─ all granted?   ──YES──► callback.onAllPermissionsGranted()
 *   └─ missing some?  ──────► requestForegroundPermissions()
 *                                 │
 *                         onPermissionResult(STEP_1)
 *                                 │
 *                         foreground granted?
 *                           YES + API 33+  ──► requestBackgroundSensor()
 *                           YES + API <33  ──► callback.onAllPermissionsGranted()
 *                           NO (rationale) ──► callback.onPermissionsDenied()
 *                           NO (permanent) ──► callback.onPermissionsPermanentlyDenied()
 *                                 │
 *                         onPermissionResult(STEP_2)
 *                                 │
 *                         background granted? ──YES──► callback.onAllPermissionsGranted()
 *                                              ──NO───► callback.onPermissionsPermanentlyDenied()
 * ```
 *
 * **SOLID compliance:**
 * - SRP: all permission logic is here; [MainActivity] is free of permission details.
 * - OCP: add new permissions by extending [FOREGROUND_PERMISSIONS] or [backgroundPermissions].
 * - DIP: depends on [PermissionCallback] abstraction, not the concrete Activity.
 */
class PermissionManager(
    private val activity: FragmentActivity,
    private val callback: PermissionCallback
) {
    // -------------------------------------------------------------------------
    // Public interface — implemented by MainActivity
    // -------------------------------------------------------------------------

    interface PermissionCallback {
        /** All required permissions are granted; proceed with Samsung Health. */
        fun onAllPermissionsGranted()

        /**
         * At least one permission was denied but the rationale dialog can still
         * be shown. Display an explanation to the user and offer to retry.
         */
        fun onPermissionsDenied()

        /**
         * At least one permission is permanently denied ("Don't ask again").
         * Guide the user to the system Settings page.
         */
        fun onPermissionsPermanentlyDenied()
    }

    // -------------------------------------------------------------------------
    // Permission groups
    // -------------------------------------------------------------------------

    companion object {
        /** Step 1: standard foreground permissions — can be requested together. */
        private val FOREGROUND_PERMISSIONS = arrayOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.ACTIVITY_RECOGNITION
        )

        /**
         * Step 2: background sensor permission — Android 13+ **only**.
         * Must be requested in a **separate** call after BODY_SENSORS is granted.
         */
        private val BACKGROUND_PERMISSIONS_API33 = arrayOf(
            Manifest.permission.BODY_SENSORS_BACKGROUND
        )

        const val REQUEST_CODE_FOREGROUND   = 10
        const val REQUEST_CODE_BACKGROUND   = 11
    }

    private val tag = "PermissionManager"

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Entry point. Call from [MainActivity.onCreate].
     *
     * Checks current grant state and either proceeds immediately or kicks off
     * the appropriate request step.
     */
    fun startFlow() {
        when {
            allGranted()            -> callback.onAllPermissionsGranted()
            needsForeground()       -> requestForegroundPermissions()
            needsBackground()       -> requestBackgroundSensorPermission()
            else                    -> callback.onAllPermissionsGranted()
        }
    }

    /**
     * Must be called from [MainActivity.onRequestPermissionsResult].
     *
     * Routes the result to the correct step handler.
     *
     * @return `true` if the request code was handled here; `false` otherwise.
     */
    fun onPermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        return when (requestCode) {
            REQUEST_CODE_FOREGROUND -> {
                handleForegroundResult(permissions, grantResults)
                true
            }
            REQUEST_CODE_BACKGROUND -> {
                handleBackgroundResult(grantResults)
                true
            }
            else -> false
        }
    }

    // -------------------------------------------------------------------------
    // Check helpers
    // -------------------------------------------------------------------------

    /** `true` when every required permission (foreground + background) is granted. */
    fun allGranted(): Boolean =
        foregroundGranted() && backgroundGrantedOrNotRequired()

    private fun foregroundGranted(): Boolean =
        FOREGROUND_PERMISSIONS.all { isGranted(it) }

    private fun backgroundGrantedOrNotRequired(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return isGranted(Manifest.permission.BODY_SENSORS_BACKGROUND)
    }

    private fun needsForeground(): Boolean = !foregroundGranted()

    private fun needsBackground(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !isGranted(Manifest.permission.BODY_SENSORS_BACKGROUND)

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) ==
            PackageManager.PERMISSION_GRANTED

    // -------------------------------------------------------------------------
    // Request helpers
    // -------------------------------------------------------------------------

    private fun requestForegroundPermissions() {
        Log.d(tag, "Requesting foreground permissions: ${FOREGROUND_PERMISSIONS.toList()}")
        ActivityCompat.requestPermissions(
            activity, FOREGROUND_PERMISSIONS, REQUEST_CODE_FOREGROUND
        )
    }

    private fun requestBackgroundSensorPermission() {
        Log.d(tag, "Requesting background sensor permission (API 33+)")
        // NOTE: On Android 13+ the system dialog will NOT show an "Allow all
        // the time" button. The user must be directed to Settings manually.
        // We still call requestPermissions so the system shows its own UI first;
        // if it's already denied, onPermissionsPermanentlyDenied() triggers.
        ActivityCompat.requestPermissions(
            activity, BACKGROUND_PERMISSIONS_API33, REQUEST_CODE_BACKGROUND
        )
    }

    // -------------------------------------------------------------------------
    // Result handlers
    // -------------------------------------------------------------------------

    /**
     * Handles the Step 1 result (foreground permissions).
     *
     * If granted on API 33+, starts Step 2 (background). Otherwise calls
     * the appropriate callback.
     */
    private fun handleForegroundResult(
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        val allForegroundGranted = grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }

        if (!allForegroundGranted) {
            val isPermanent = permissions.any { permission ->
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) &&
                    !isGranted(permission)
            }
            Log.w(tag, "Foreground permissions denied (permanent=$isPermanent)")
            if (isPermanent) callback.onPermissionsPermanentlyDenied()
            else             callback.onPermissionsDenied()
            return
        }

        // Foreground granted. Now request background if needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && needsBackground()) {
            requestBackgroundSensorPermission()
        } else {
            Log.d(tag, "All permissions granted (API < 33 or background already granted)")
            callback.onAllPermissionsGranted()
        }
    }

    /**
     * Handles the Step 2 result (background sensor permission).
     */
    private fun handleBackgroundResult(grantResults: IntArray) {
        val granted = grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }

        if (granted) {
            Log.d(tag, "Background sensor permission granted")
            callback.onAllPermissionsGranted()
        } else {
            // On API 33+ the user must go to Settings to allow "All the time"
            Log.w(tag, "Background sensor permission denied — directing user to Settings")
            callback.onPermissionsPermanentlyDenied()
        }
    }
}
