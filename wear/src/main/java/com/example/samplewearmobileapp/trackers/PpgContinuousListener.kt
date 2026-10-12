package com.example.samplewearmobileapp.trackers

import com.example.samplewearmobileapp.R
import com.example.samplewearmobileapp.models.PpgType
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.ValueKey

/** One Samsung continuous tracker, three real channels at the SDK's 25 Hz. */
class PpgContinuousListener(
    private val onSample: (PpgType, Int, Long) -> Unit,
    private val onFailure: (Int) -> Unit
) : Listener() {
    val channels = mutableSetOf<PpgType>()

    override fun onRegistrationFailed(error: RuntimeException) {
        onFailure(R.string.ConnectionError)
    }

    init {
        setTrackerEventListener(object : HealthTracker.TrackerEventListener {
            override fun onDataReceived(points: List<DataPoint>) {
                postTrackerData {
                    for (point in points) {
                        if (point.timestamp <= 0L) continue
                        if (PpgType.PPG_GREEN in channels && point.getValue(ValueKey.PpgSet.GREEN_STATUS) == 0) {
                            onSample(PpgType.PPG_GREEN, point.getValue(ValueKey.PpgSet.PPG_GREEN), point.timestamp)
                        }
                        if (PpgType.PPG_IR in channels && point.getValue(ValueKey.PpgSet.IR_STATUS) == 0) {
                            onSample(PpgType.PPG_IR, point.getValue(ValueKey.PpgSet.PPG_IR), point.timestamp)
                        }
                        if (PpgType.PPG_RED in channels && point.getValue(ValueKey.PpgSet.RED_STATUS) == 0) {
                            onSample(PpgType.PPG_RED, point.getValue(ValueKey.PpgSet.PPG_RED), point.timestamp)
                        }
                    }
                }
            }

            override fun onFlushCompleted() = Unit

            override fun onError(error: HealthTracker.TrackerError) {
                postTrackerData {
                    stopTracker()
                    onFailure(if (error == HealthTracker.TrackerError.PERMISSION_ERROR) {
                        R.string.NoPermission
                    } else if (error == HealthTracker.TrackerError.SDK_POLICY_ERROR) {
                        R.string.SdkPolicyError
                    } else {
                        R.string.ConnectionError
                    })
                }
            }
        })
    }
}
