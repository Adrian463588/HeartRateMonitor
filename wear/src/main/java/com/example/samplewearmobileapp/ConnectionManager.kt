package com.example.samplewearmobileapp

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.samplewearmobileapp.trackers.Listener
import com.example.samplewearmobileapp.trackers.ppggreen.PpgGreenListener
import com.example.samplewearmobileapp.trackers.ppgir.PpgIrListener
import com.example.samplewearmobileapp.trackers.ppgred.PpgRedListener
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.HealthTrackerType

/**
 * Manages the connection to the Samsung Health Tracking Service and
 * the initialisation of individual sensor trackers.
 *
 * **SOLID compliance:**
 * - SRP: only responsible for connecting to and disconnecting from the
 *   Samsung HealthTrackingService, and provisioning trackers.
 * - DIP: depends on the [ConnectionObserver] abstraction, not the Activity.
 */
class ConnectionManager(private val observer: ConnectionObserver) {

    private val tag = "ConnectionManager"
    private lateinit var healthTrackingService: HealthTrackingService

    // -------------------------------------------------------------------------
    // Internal Samsung connection listener
    // -------------------------------------------------------------------------

    private val connectionListener = object : ConnectionListener {

        override fun onConnectionSuccess() {
            Log.i(tag, "Samsung Health Service connected")
            observer.onConnectionResult(R.string.ConnectedToHs)
            logUnsupportedTrackers()
        }

        override fun onConnectionEnded() {
            Log.i(tag, "Samsung Health Service disconnected")
        }

        override fun onConnectionFailed(e: HealthTrackerException?) {
            Log.e(tag, "Samsung Health Service connection failed: ${e?.message}")
            observer.onError(e)
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Initiates an async connection to the Samsung Health Tracking Service. */
    fun connect(context: Context) {
        healthTrackingService = HealthTrackingService(connectionListener, context)
        healthTrackingService.connectService()
    }

    /** Disconnects from the Samsung Health Tracking Service. */
    fun disconnect() {
        if (::healthTrackingService.isInitialized) {
            healthTrackingService.disconnectService()
        }
    }

    /**
     * Initialises and provisions a [PpgGreenListener] with the PPG_GREEN tracker.
     * Must be called only after [onConnectionSuccess].
     */
    fun initPpgGreen(listener: PpgGreenListener) {
        initListener(listener, HealthTrackerType.PPG_GREEN)
    }

    /**
     * Initialises and provisions a [PpgIrListener] with the PPG_IR tracker.
     * Must be called only after [onConnectionSuccess].
     */
    fun initPpgIr(listener: PpgIrListener) {
        initListener(listener, HealthTrackerType.PPG_IR)
    }

    /**
     * Initialises and provisions a [PpgRedListener] with the PPG_RED tracker.
     * Must be called only after [onConnectionSuccess].
     */
    fun initPpgRed(listener: PpgRedListener) {
        initListener(listener, HealthTrackerType.PPG_RED)
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Generic helper that obtains a tracker of [type] from the service and
     * configures the given [listener] with it and a main-thread [Handler].
     *
     * DRY: all three initPpg*() calls delegate here instead of repeating
     * the same two-line pattern.
     */
    private fun initListener(listener: Listener, type: HealthTrackerType) {
        val tracker = healthTrackingService.getHealthTracker(type)
        listener.setHealthTracker(tracker)
        listener.setHandler(Handler(Looper.getMainLooper()))
    }

    /**
     * Logs a warning for every tracker type that is not available on this
     * device, and notifies the observer so the UI can inform the user.
     */
    private fun logUnsupportedTrackers() {
        val supported = healthTrackingService.trackingCapability.supportHealthTrackerTypes

        val checks = mapOf(
            HealthTrackerType.PPG_GREEN to R.string.NoPpgGreenSupport,
            HealthTrackerType.PPG_IR    to R.string.NoPpgIrSupport,
            HealthTrackerType.PPG_RED   to R.string.NoPpgRedSupport,
        )

        checks.forEach { (type, errorRes) ->
            if (!supported.contains(type)) {
                Log.w(tag, "Device does not support $type tracking")
                observer.onConnectionResult(errorRes)
            }
        }
    }
}