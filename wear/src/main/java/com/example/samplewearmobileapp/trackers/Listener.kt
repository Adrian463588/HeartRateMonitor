package com.example.samplewearmobileapp.trackers

import android.os.Handler
import android.util.Log
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTracker.TrackerEventListener

/**
 * Base class for all Samsung Health sensor listeners.
 *
 * Manages the lifecycle of a [HealthTracker] by coordinating a
 * [Handler] (on the main looper) and a [TrackerEventListener].
 *
 * **Usage pattern:**
 * 1. Call [setHealthTracker] after obtaining one from [ConnectionManager].
 * 2. Call [setHandler] with a main-thread [Handler].
 * 3. Call [setTrackerEventListener] from the concrete subclass `init {}`.
 * 4. Call [startTracker] / [stopTracker] in response to UX events.
 *
 * **SOLID compliance:**
 * - SRP: handles only lifecycle; data parsing is in each concrete subclass.
 * - OCP: subclasses extend behaviour via [setTrackerEventListener].
 */
open class Listener {

    private val tag = "Listener"

    private var handler: Handler? = null
    private var healthTracker: HealthTracker? = null
    private var trackerEventListener: TrackerEventListener? = null

    /** True while the handler has been posted and the tracker is active. */
    private var isHandlerRunning = false

    /** Public read-only tracking state. */
    private var isTracking = false

    // -------------------------------------------------------------------------
    // Configuration setters (called once, by ConnectionManager / subclass init)
    // -------------------------------------------------------------------------

    fun setHealthTracker(tracker: HealthTracker) {
        healthTracker = tracker
    }

    fun setHandler(handler: Handler) {
        this.handler = handler
    }

    fun setHandlerRunning(handlerRunning: Boolean) {
        isHandlerRunning = handlerRunning
    }

    fun setTrackerEventListener(listener: TrackerEventListener) {
        trackerEventListener = listener
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Starts the tracker by posting the [healthTracker.setEventListener] call
     * to the main-thread [Handler].
     *
     * Guards against starting twice and against calling before the tracker and
     * handler have been configured.
     */
    fun startTracker() {
        val tracker = healthTracker
        val h = handler
        val listener = trackerEventListener

        if (tracker == null || h == null || listener == null) {
            Log.e(tag, "startTracker: tracker, handler, or listener not initialised — aborting")
            return
        }

        if (isHandlerRunning) {
            Log.w(tag, "startTracker: already running, ignoring duplicate call")
            return
        }

        h.post {
            tracker.setEventListener(listener)
            setHandlerRunning(true)
        }
        isTracking = true
        Log.i(tag, "startTracker: started successfully")
    }

    /**
     * Stops the tracker and removes all pending handler callbacks.
     *
     * Safe to call even if the tracker was never started.
     */
    fun stopTracker() {
        val tracker = healthTracker
        val h = handler

        if (!isHandlerRunning) {
            Log.d(tag, "stopTracker: not running, nothing to stop")
            return
        }

        tracker?.unsetEventListener()
        h?.removeCallbacksAndMessages(null)
        setHandlerRunning(false)
        isTracking = false
        Log.i(tag, "stopTracker: stopped successfully")
    }

    /** Returns `true` while sensor data is actively being delivered. */
    fun isTracking(): Boolean = isTracking
}