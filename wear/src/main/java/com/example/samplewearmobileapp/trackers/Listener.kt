package com.example.samplewearmobileapp.trackers

import android.os.Handler
import android.util.Log
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTracker.TrackerEventListener

open class Listener {
    private val tag = "Listener Base Class"

    private lateinit var handler: Handler
    private lateinit var healthTracker: HealthTracker
    private var isHandlerRunning = false
    private var isTracking = false
    private var generation = 0L

    private lateinit var trackerEventListener: TrackerEventListener

    fun setHealthTracker(tracker: HealthTracker) {
        healthTracker = tracker
    }

    fun setHandler(handler: Handler) {
        this.handler = handler
    }

    fun setHandlerRunning(handlerRunning: Boolean) {
        isHandlerRunning = handlerRunning
        if (!handlerRunning) isTracking = false
    }

    fun setTrackerEventListener(tracker: TrackerEventListener) {
        trackerEventListener = tracker
    }

    fun startTracker(): Boolean {
        if (!::healthTracker.isInitialized || !::handler.isInitialized ||
            !::trackerEventListener.isInitialized) return false
        if (!isTracking) {
            generation++
            isTracking = true
            handler.post {
                if (isTracking) {
                    try {
                        healthTracker.setEventListener(trackerEventListener)
                        setHandlerRunning(true)
                    } catch (error: RuntimeException) {
                        setHandlerRunning(false)
                        Log.e(tag, "Tracker registration failed", error)
                        onRegistrationFailed(error)
                    }
                }
            }
        }
        return true
    }

    fun stopTracker() {
        generation++
        isTracking = false
        if (::handler.isInitialized) handler.removeCallbacksAndMessages(null)
        if (isHandlerRunning) {
            try {
                healthTracker.unsetEventListener()
            } catch (error: RuntimeException) {
                Log.w(tag, "Tracker already unavailable", error)
            }
            setHandlerRunning(false)
        }
    }

    protected fun postTrackerData(block: () -> Unit) {
        if (!::handler.isInitialized) return
        val callbackGeneration = generation
        handler.post {
            if (isTracking && generation == callbackGeneration) block()
        }
    }

    protected open fun onRegistrationFailed(error: RuntimeException) = Unit

    fun isTracking(): Boolean {
        return isTracking
    }
}
