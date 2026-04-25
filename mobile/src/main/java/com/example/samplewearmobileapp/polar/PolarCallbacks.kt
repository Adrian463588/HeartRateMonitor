package com.example.samplewearmobileapp.polar

import android.util.Log
import android.widget.Toast
import com.example.samplewearmobileapp.MainActivity
import com.example.samplewearmobileapp.PpgUiState
import com.example.samplewearmobileapp.R
import com.polar.sdk.api.PolarBleApi.DeviceStreamingFeature
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHrData
import java.util.Date
import java.util.UUID

/**
 * Named implementation of [PolarBleApiCallback] for the mobile app.
 *
 * **SRP:** Owns the single responsibility of translating raw Polar SDK events
 * into meaningful state updates on [MainActivity]. All business logic remains
 * in [MainActivity]; this class only bridges SDK callbacks to activity state.
 *
 * **Thread contract:** The Polar SDK fires all callbacks on its internal BLE
 * worker thread — NOT the Android main (UI) thread.
 * Rule: state-field mutations are fine on any thread (they are atomic or
 * primitive writes); every UI-touching operation MUST be wrapped in
 * [android.app.Activity.runOnUiThread].
 *
 * **OCP:** Adding a new callback (e.g. `sdkModeFeatureAvailable`) requires
 * only a new `override` here — no changes to [MainActivity] needed.
 *
 * @param activity The host activity. Holds all state fields and UI references.
 */
internal class PolarCallbacks(
    private val activity: MainActivity
) : PolarBleApiCallback() {

    // -------------------------------------------------------------------------
    // BLE power
    // -------------------------------------------------------------------------

    override fun blePowerStateChanged(powered: Boolean) {
        Log.d(TAG, "BLE power state: $powered")
    }

    // -------------------------------------------------------------------------
    // Connection lifecycle
    // -------------------------------------------------------------------------

    override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
        Log.d(TAG, "Connected: ${polarDeviceInfo.deviceId}")

        // State fields — atomic writes, safe on any thread
        activity.isPolarDeviceConnected = true
        activity.deviceId      = polarDeviceInfo.deviceId
        activity.deviceName    = polarDeviceInfo.name
        activity.deviceAddress = polarDeviceInfo.address

        // UI updates — must be on main thread
        activity.runOnUiThread {
            activity.uiStateManager.setEcgState(PpgUiState.Connected)
            Toast.makeText(
                activity,
                "${activity.getString(R.string.connecting)} ${polarDeviceInfo.deviceId}",
                Toast.LENGTH_SHORT
            ).show()
            activity.invalidateOptionsMenu()
        }
    }

    override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
        Log.d(TAG, "Connecting: ${polarDeviceInfo.deviceId}")
    }

    override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
        Log.d(TAG, "Disconnected: ${polarDeviceInfo.deviceId}")

        // State field — safe on any thread
        activity.isPolarDeviceConnected = false

        // UI — main thread
        activity.runOnUiThread {
            activity.uiStateManager.setEcgState(PpgUiState.Disconnected)
            activity.invalidateOptionsMenu()
        }
    }

    // -------------------------------------------------------------------------
    // Streaming features
    // -------------------------------------------------------------------------

    override fun streamingFeaturesReady(
        identifier: String,
        features: Set<DeviceStreamingFeature>
    ) {
        Log.d(TAG, "Streaming features ready for $identifier: $features")
        // Future: auto-start ECG stream here if desired
    }

    override fun sdkModeFeatureAvailable(identifier: String) {
        Log.d(TAG, "SDK mode feature available: $identifier")
    }

    override fun hrFeatureReady(identifier: String) {
        Log.d(TAG, "HR feature ready: $identifier")
    }

    // -------------------------------------------------------------------------
    // Device information (requires FEATURE_DEVICE_INFO)
    // -------------------------------------------------------------------------

    /**
     * Firmware version UUID as defined by Bluetooth SIG DIS spec (0x2A26).
     * Only this UUID carries the firmware version string we need for metadata.
     */
    override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
        Log.d(TAG, "DIS info: uuid=$uuid value=$value")
        if (uuid == FIRMWARE_VERSION_UUID) {
            activity.deviceFirmware = value
        }
    }

    // -------------------------------------------------------------------------
    // Battery (requires FEATURE_BATTERY_INFO)
    // -------------------------------------------------------------------------

    override fun batteryLevelReceived(identifier: String, level: Int) {
        Log.d(TAG, "Battery: $level%")
        activity.deviceBatteryLevel = level.toString()
    }

    // -------------------------------------------------------------------------
    // Heart rate (requires FEATURE_HR)
    // -------------------------------------------------------------------------

    /**
     * Called by the Polar SDK on its internal **BLE worker thread**.
     *
     * **Bug #2 fix:** The original anonymous callback updated [MainActivity.textEcgHr]
     * directly from this thread → [android.view.ViewRootImpl.CalledFromWrongThreadException].
     * It also called [com.example.samplewearmobileapp.HrPlotter.addValues1] which writes to
     * [com.androidplot.xy.SimpleXYSeries] — not thread-safe from a background thread.
     *
     * Fix: capture [time] here (safe on any thread) then post ALL UI and series
     * mutations to the main thread via [android.app.Activity.runOnUiThread].
     */
    override fun hrNotificationReceived(identifier: String, data: PolarHrData) {
        // Capture timestamp on this (BLE) thread — Date() is thread-safe
        val time = Date().time.toDouble()

        // All UI + series mutations must be on the main thread
        activity.runOnUiThread {
            // Guard: ECG must be actively streaming (flag set before toggleEcgStream())
            if (!activity.isEcgRunning) return@runOnUiThread
            activity.textEcgHr.text = data.hr.toString()
            activity.hrPlotter?.addValues1(time, data.hr.toDouble(), data.rrsMs)
        }
    }

    // -------------------------------------------------------------------------
    // File transfer (requires FEATURE_POLAR_FILE_TRANSFER)
    // -------------------------------------------------------------------------

    override fun polarFtpFeatureReady(identifier: String) {
        Log.d(TAG, "FTP feature ready: $identifier")
    }

    // -------------------------------------------------------------------------
    // Companion
    // -------------------------------------------------------------------------

    companion object {
        private const val TAG = "Mobile.PolarCallbacks"

        /**
         * Bluetooth DIS "Firmware Revision String" characteristic UUID (0x2A26).
         * Declared as a constant so [disInformationReceived] is DRY and testable.
         */
        val FIRMWARE_VERSION_UUID: UUID =
            UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
    }
}
