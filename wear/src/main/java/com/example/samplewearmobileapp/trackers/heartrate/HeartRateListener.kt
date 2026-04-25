package com.example.samplewearmobileapp.trackers.heartrate

import android.util.Log
import com.example.samplewearmobileapp.R
import com.example.samplewearmobileapp.TrackerDataNotifier
import com.example.samplewearmobileapp.trackers.Listener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.ValueKey

/**
 * Listener for Heart Rate data from the Samsung Health Sensor SDK.
 *
 * **SDK 1.4.1 note:** The following `ValueKey` names changed from SDK 1.2:
 * - `ValueKey.HeartRateSet.STATUS`         → `ValueKey.HeartRateSet.HEART_RATE_STATUS`
 * - `ValueKey.HeartRateSet.HEART_RATE_IBI` → `ValueKey.HeartRateSet.IBI_LIST`
 *   (IBI is now returned as a List<Int> rather than a packed Int)
 */
class HeartRateListener internal constructor() : Listener() {

    private val tag = "HeartRateListener"

    init {
        val trackerEventListener = object : HealthTracker.TrackerEventListener {

            override fun onDataReceived(list: List<DataPoint>) {
                for (dataPoint in list) {
                    readValuesFromDataPoint(dataPoint)
                }
            }

            override fun onFlushCompleted() {
                Log.i(tag, "onFlushCompleted called")
            }

            override fun onError(trackerError: HealthTracker.TrackerError) {
                Log.e(tag, "onError called: $trackerError")
                setHandlerRunning(false)
                when (trackerError) {
                    HealthTracker.TrackerError.PERMISSION_ERROR ->
                        TrackerDataNotifier.instance?.notifyError(R.string.NoPermission)
                    HealthTracker.TrackerError.SDK_POLICY_ERROR ->
                        TrackerDataNotifier.instance?.notifyError(R.string.SdkPolicyError)
                    else -> Log.w(tag, "Unhandled TrackerError: $trackerError")
                }
            }
        }
        setTrackerEventListener(trackerEventListener)
    }

    fun readValuesFromDataPoint(dataPoint: DataPoint) {
        val hrData = HeartRateData()

        // SDK 1.4.1: HEART_RATE_STATUS replaces STATUS
        hrData.status = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE_STATUS)
        hrData.hr     = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE)

        // SDK 1.4.1: IBI_LIST replaces the packed-Int HEART_RATE_IBI.
        // We take the first IBI value if the list is non-empty.
        val ibiList: List<Int> = dataPoint.getValue(ValueKey.HeartRateSet.IBI_LIST)
        val rawIbi = ibiList.firstOrNull() ?: 0
        hrData.qIbi = (rawIbi shr HeartRateData.IBI_QUALITY_SHIFT) and HeartRateData.IBI_QUALITY_MASK
        hrData.ibi  = rawIbi and HeartRateData.IBI_MASK

        Log.d(tag, "HR=${hrData.hr} IBI=${hrData.ibi} status=${hrData.status}")
    }
}