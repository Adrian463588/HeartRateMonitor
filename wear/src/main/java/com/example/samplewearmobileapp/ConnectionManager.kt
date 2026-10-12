package com.example.samplewearmobileapp

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.samplewearmobileapp.trackers.Listener
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.PpgType

class ConnectionManager(private val observer: ConnectionObserver) {
    private lateinit var service: HealthTrackingService

    fun connect(context: Context) {
        service = HealthTrackingService(object : ConnectionListener {
            override fun onConnectionSuccess() {
                observer.onConnectionResult(R.string.ConnectedToHs)
            }

            override fun onConnectionEnded() {
                observer.onConnectionResult(R.string.ConnectionError)
            }

            override fun onConnectionFailed(error: HealthTrackerException?) {
                observer.onError(error)
            }
        }, context)
        service.connectService()
    }

    fun initPpg(listener: Listener): Boolean {
        if (HealthTrackerType.PPG_CONTINUOUS !in service.trackingCapability.supportHealthTrackerTypes) {
            return false
        }
        // IR/Red on-demand trackers cannot run together as continuous recorders.
        listener.setHealthTracker(service.getHealthTracker(
            HealthTrackerType.PPG_CONTINUOUS, setOf(PpgType.GREEN, PpgType.IR, PpgType.RED)
        ))
        listener.setHandler(Handler(Looper.getMainLooper()))
        return true
    }

    fun disconnect() {
        if (::service.isInitialized) service.disconnectService()
    }
}
