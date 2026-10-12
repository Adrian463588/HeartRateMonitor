package com.example.samplewearmobileapp.trackers

import android.os.Handler
import android.util.Log
import com.example.samplewearmobileapp.models.TrackerLifecycle
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTracker.TrackerEventListener
import com.samsung.android.service.health.tracking.data.DataPoint

open class Listener {
    private val tag = "Listener Base Class"

    private lateinit var handler: Handler
    private lateinit var healthTracker: HealthTracker
    private val lifecycle = TrackerLifecycle()

    private lateinit var trackerEventListener: TrackerEventListener

    fun setHealthTracker(tracker: HealthTracker) {
        healthTracker = tracker
    }

    fun setHandler(handler: Handler) {
        this.handler = handler
    }

    fun setHandlerRunning(handlerRunning: Boolean) {
        if (!handlerRunning) stopTracker()
    }

    fun setTrackerEventListener(tracker: TrackerEventListener) {
        trackerEventListener = tracker
    }

    fun startTracker(): Boolean {
        if (!::healthTracker.isInitialized || !::handler.isInitialized ||
            !::trackerEventListener.isInitialized) return false
        return try {
            lifecycle.start {
                val registration = lifecycle.generation
                // Bind callbacks to their registration, including callbacks delivered after Resume.
                healthTracker.setEventListener(object : TrackerEventListener {
                    override fun onDataReceived(points: List<DataPoint>) {
                        val copy = points.toList()
                        deliver(registration) { trackerEventListener.onDataReceived(copy) }
                    }
                    override fun onFlushCompleted() {
                        deliver(registration) { trackerEventListener.onFlushCompleted() }
                    }
                    override fun onError(error: HealthTracker.TrackerError) {
                        deliver(registration) { trackerEventListener.onError(error) }
                    }
                })
            }
        } catch (error: RuntimeException) {
            stopTracker()
            Log.e(tag, "Tracker registration failed", error)
            onRegistrationFailed(error)
            false
        }
    }

    fun stopTracker(): Boolean {
        if (::handler.isInitialized) handler.removeCallbacksAndMessages(null)
        return try {
            lifecycle.stop { healthTracker.unsetEventListener() }
        } catch (error: RuntimeException) {
            Log.w(tag, "Tracker release failed; Stop must retry", error)
            false
        }
    }

    protected fun postTrackerData(block: () -> Unit) {
        if (!::handler.isInitialized) return
        deliver(lifecycle.generation, block)
    }

    private fun deliver(callbackGeneration: Long, block: () -> Unit) {
        handler.post {
            if (lifecycle.accepts(callbackGeneration)) block()
        }
    }

    protected open fun onRegistrationFailed(error: RuntimeException) = Unit

    fun isTracking(): Boolean {
        return lifecycle.requested
    }

    fun isRegistered(): Boolean = lifecycle.registered
}
