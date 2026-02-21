package com.example.samplewearmobileapp.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized permission handler for the HeartRate Monitor app.
 *
 * Manages all Android 13+ runtime permissions required for
 * BLE connections, sensor access, foreground services, and notifications.
 *
 * Exposes a [StateFlow] of [PermissionState] for UI observation.
 */
class PermissionManager {

    companion object {
        private const val TAG = "PermissionManager"
        const val REQUEST_CODE_ALL_PERMISSIONS = 100
        const val REQUEST_CODE_BACKGROUND_LOCATION = 101

        /**
         * Core permissions needed before recording can start.
         * Background location is requested separately after foreground permissions are granted.
         */
        val REQUIRED_PERMISSIONS: Array<String>
            get() {
                val perms = mutableListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.BODY_SENSORS
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    perms.add(Manifest.permission.BLUETOOTH_SCAN)
                    perms.add(Manifest.permission.BLUETOOTH_CONNECT)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                return perms.toTypedArray()
            }
    }

    /**
     * Represents the overall permission state of the app.
     */
    data class PermissionState(
        val bluetoothGranted: Boolean = false,
        val locationGranted: Boolean = false,
        val bodySensorsGranted: Boolean = false,
        val notificationsGranted: Boolean = false,
        val backgroundLocationGranted: Boolean = false,
        val allCoreGranted: Boolean = false
    )

    private val _state = MutableStateFlow(PermissionState())
    val state: StateFlow<PermissionState> = _state.asStateFlow()

    /**
     * Checks and updates the current permission state.
     * Call this in onResume() or after returning from permission requests.
     */
    fun refreshState(context: Context) {
        val bluetoothGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT) &&
                    hasPermission(context, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            true // Legacy Bluetooth permissions granted via manifest
        }

        val locationGranted =
            hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)

        val bodySensorsGranted =
            hasPermission(context, Manifest.permission.BODY_SENSORS)

        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true // Not required pre-Android 13
        }

        val backgroundLocationGranted =
            hasPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

        val allCoreGranted = bluetoothGranted && locationGranted &&
                bodySensorsGranted && notificationsGranted

        _state.value = PermissionState(
            bluetoothGranted = bluetoothGranted,
            locationGranted = locationGranted,
            bodySensorsGranted = bodySensorsGranted,
            notificationsGranted = notificationsGranted,
            backgroundLocationGranted = backgroundLocationGranted,
            allCoreGranted = allCoreGranted
        )

        Log.d(TAG, "Permission state refreshed: ${_state.value}")
    }

    /**
     * Requests all required core permissions.
     * Call this when the user taps "Start" and permissions are missing.
     */
    fun requestCorePermissions(activity: Activity) {
        val missingPerms = REQUIRED_PERMISSIONS.filter {
            !hasPermission(activity, it)
        }.toTypedArray()

        if (missingPerms.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: ${missingPerms.joinToString()}")
            ActivityCompat.requestPermissions(
                activity,
                missingPerms,
                REQUEST_CODE_ALL_PERMISSIONS
            )
        }
    }

    /**
     * Requests background location permission separately.
     * Must be called AFTER foreground location is granted (Android 11+ requirement).
     */
    fun requestBackgroundLocation(activity: Activity) {
        if (!hasPermission(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                REQUEST_CODE_BACKGROUND_LOCATION
            )
        }
    }

    /**
     * Checks if the user has permanently denied a permission.
     * If so, opens app settings for manual grant.
     *
     * @return true if the user was directed to settings.
     */
    fun handlePermanentlyDenied(activity: Activity, permission: String): Boolean {
        if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) &&
            !hasPermission(activity, permission)
        ) {
            Log.w(TAG, "Permission permanently denied: $permission. Opening settings.")
            openAppSettings(activity)
            return true
        }
        return false
    }

    /**
     * Whether all core permissions are granted and recording can proceed.
     */
    fun areAllCorePermissionsGranted(context: Context): Boolean {
        return REQUIRED_PERMISSIONS.all { hasPermission(context, it) }
    }

    // ===== Private Helpers =====

    private fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun openAppSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
        activity.startActivity(intent)
    }
}
