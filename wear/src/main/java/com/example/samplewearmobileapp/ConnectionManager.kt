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

class ConnectionManager(observer: ConnectionObserver) {
    private val tag = "Connection Manager"
    private var connectionObserver : ConnectionObserver = observer
    private lateinit var healthTrackingService: HealthTrackingService
    private val connectionListener: ConnectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            Log.i(tag,"Connected")
            connectionObserver.onConnectionResult(R.string.ConnectedToHs)
            if (!isTrackerAvailable(healthTrackingService, HealthTrackerType.HEART_RATE)) {
                Log.i(tag, "Device does not support Heart Rate tracking")
            }
            if (!isTrackerAvailable(healthTrackingService, HealthTrackerType.PPG_GREEN)) {
                Log.i(tag, "Device does not support PPG Green tracking")
                connectionObserver.onConnectionResult(R.string.NoPpgGreenSupport)
            }
            if (!isTrackerAvailable(healthTrackingService, HealthTrackerType.PPG_IR)) {
                Log.i(tag, "Device does not support PPG InfraRed tracking")
                connectionObserver.onConnectionResult(R.string.NoPpgIrSupport)
            }
            if (!isTrackerAvailable(healthTrackingService, HealthTrackerType.PPG_RED)) {
                Log.i(tag, "Device does not support PPG Red tracking")
                connectionObserver.onConnectionResult(R.string.NoPpgRedSupport)
            }
        }

        override fun onConnectionEnded() {
            Log.i(tag, "Disconnected")
        }

        override fun onConnectionFailed(e: HealthTrackerException?) {
            connectionObserver.onError(e)
        }
    }

    fun connect(context: Context?) {
        healthTrackingService = HealthTrackingService(connectionListener, context)
        healthTrackingService.connectService()
    }

    fun disconnect() {
        healthTrackingService.disconnectService()
    }


    fun initPpgGreen(ppgGreenListener: PpgGreenListener) {
        val healthTracker = healthTrackingService.getHealthTracker(HealthTrackerType.PPG_GREEN)
        ppgGreenListener.setHealthTracker(healthTracker)
        setHandlerForListener(ppgGreenListener)
    }

    fun initPpgIr(ppgIrListener: PpgIrListener) {
        val healthTracker = healthTrackingService.getHealthTracker(HealthTrackerType.PPG_IR)
        ppgIrListener.setHealthTracker(healthTracker)
        setHandlerForListener(ppgIrListener)
    }

    fun initPpgRed(ppgRedListener: PpgRedListener) {
        val healthTracker = healthTrackingService.getHealthTracker(HealthTrackerType.PPG_RED)
        ppgRedListener.setHealthTracker(healthTracker)
        setHandlerForListener(ppgRedListener)
    }

    private fun setHandlerForListener(listener: Listener) {
        listener.setHandler(Handler(Looper.getMainLooper()))
    }

    private fun isTrackerAvailable(
        healthTrackingService: HealthTrackingService,
        type: HealthTrackerType
    ): Boolean {
        val availableTrackers = healthTrackingService.trackingCapability.supportHealthTrackerTypes
        return availableTrackers.contains(type)
    }
}