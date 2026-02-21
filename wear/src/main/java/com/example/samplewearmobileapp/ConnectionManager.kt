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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the connection to Samsung Health Tracking Service
 * on the Galaxy Watch, with retry logic for resilient operation.
 *
 * @param observer Callback for connection events.
 */
class ConnectionManager(observer: ConnectionObserver) {
    companion object {
        private const val TAG = "ConnectionManager"
        private const val MAX_RETRY_COUNT = 5
        private const val INITIAL_RETRY_DELAY_MS = 2000L
    }

    private var connectionObserver: ConnectionObserver = observer
    private lateinit var healthTrackingService: HealthTrackingService

    private var retryCount = 0
    private var retryHandler = Handler(Looper.getMainLooper())
    private var context: Context? = null

    /** Observable connection state */
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val connectionListener: ConnectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            Log.i(TAG, "Connected to Samsung Health Tracking Service")
            retryCount = 0 // Reset retry count on success
            _isConnected.value = true
            connectionObserver.onConnectionResult(R.string.ConnectedToHs)

            // Check tracker availability
            checkTrackerAvailability()
        }

        override fun onConnectionEnded() {
            Log.i(TAG, "Disconnected from Samsung Health Tracking Service")
            _isConnected.value = false

            // Attempt reconnection if not intentionally disconnected
            scheduleRetry()
        }

        override fun onConnectionFailed(e: HealthTrackerException?) {
            Log.e(TAG, "Connection failed: ${e?.message}")
            _isConnected.value = false
            connectionObserver.onError(e)

            // Attempt reconnection
            scheduleRetry()
        }
    }

    /**
     * Connect to Samsung Health Tracking Service.
     */
    fun connect(context: Context?) {
        this.context = context
        retryCount = 0
        try {
            healthTrackingService = HealthTrackingService(connectionListener, context)
            healthTrackingService.connectService()
            Log.i(TAG, "Connecting to Health Tracking Service...")
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating connection", e)
            scheduleRetry()
        }
    }

    /**
     * Disconnect from Samsung Health Tracking Service.
     * Cancels any pending retry attempts.
     */
    fun disconnect() {
        retryHandler.removeCallbacksAndMessages(null)
        retryCount = MAX_RETRY_COUNT // Prevent further retries
        try {
            if (::healthTrackingService.isInitialized) {
                healthTrackingService.disconnectService()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting", e)
        }
        _isConnected.value = false
    }

    // === Tracker Initialization ===

    fun initPpgGreen(ppgGreenListener: PpgGreenListener) {
        try {
            val healthTracker = healthTrackingService.getHealthTracker(HealthTrackerType.PPG_GREEN)
            ppgGreenListener.setHealthTracker(healthTracker)
            setHandlerForListener(ppgGreenListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init PPG Green tracker", e)
        }
    }

    fun initPpgIr(ppgIrListener: PpgIrListener) {
        try {
            val healthTracker = healthTrackingService.getHealthTracker(HealthTrackerType.PPG_IR)
            ppgIrListener.setHealthTracker(healthTracker)
            setHandlerForListener(ppgIrListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init PPG IR tracker", e)
        }
    }

    fun initPpgRed(ppgRedListener: PpgRedListener) {
        try {
            val healthTracker = healthTrackingService.getHealthTracker(HealthTrackerType.PPG_RED)
            ppgRedListener.setHealthTracker(healthTracker)
            setHandlerForListener(ppgRedListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init PPG Red tracker", e)
        }
    }

    // === Private Helpers ===

    private fun setHandlerForListener(listener: Listener) {
        listener.setHandler(Handler(Looper.getMainLooper()))
    }

    /**
     * Schedules a reconnection attempt with exponential backoff.
     * Max delay caps at ~32 seconds after 5 retries.
     */
    private fun scheduleRetry() {
        if (retryCount >= MAX_RETRY_COUNT) {
            Log.e(TAG, "Max retry attempts ($MAX_RETRY_COUNT) reached. Giving up.")
            return
        }

        val delayMs = INITIAL_RETRY_DELAY_MS * (1L shl retryCount) // Exponential backoff
        retryCount++
        Log.w(TAG, "Scheduling retry #$retryCount in ${delayMs}ms")

        retryHandler.postDelayed({
            context?.let { ctx ->
                Log.i(TAG, "Retry #$retryCount: reconnecting...")
                try {
                    healthTrackingService = HealthTrackingService(connectionListener, ctx)
                    healthTrackingService.connectService()
                } catch (e: Exception) {
                    Log.e(TAG, "Retry #$retryCount failed", e)
                    scheduleRetry()
                }
            }
        }, delayMs)
    }

    private fun checkTrackerAvailability() {
        try {
            val availableTrackers = healthTrackingService.trackingCapability.supportHealthTrackerTypes

            if (!availableTrackers.contains(HealthTrackerType.PPG_GREEN)) {
                Log.w(TAG, "Device does not support PPG Green tracking")
                connectionObserver.onConnectionResult(R.string.NoPpgGreenSupport)
            }
            if (!availableTrackers.contains(HealthTrackerType.PPG_IR)) {
                Log.w(TAG, "Device does not support PPG InfraRed tracking")
                connectionObserver.onConnectionResult(R.string.NoPpgIrSupport)
            }
            if (!availableTrackers.contains(HealthTrackerType.PPG_RED)) {
                Log.w(TAG, "Device does not support PPG Red tracking")
                connectionObserver.onConnectionResult(R.string.NoPpgRedSupport)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking tracker availability", e)
        }
    }
}