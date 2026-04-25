package com.example.samplewearmobileapp

import android.util.Log
import com.example.samplewearmobileapp.models.PpgData
import com.example.samplewearmobileapp.models.PpgType

/**
 * Processes incoming PPG data batches and routes values to the correct [PpgPlotter].
 *
 * **DRY fix:** Eliminates the 3-way duplicated `for (i in 0 until data.size)` loop
 * that existed for Green / IR / Red channels in `MainActivity.onDataArrived()`.
 *
 * **SRP:** Owns *only* the "decode batch and forward to plotter" concern.
 * UI status updates remain in [UiStateManager]; scheduling remains in [PlotUpdateScheduler].
 */
class PpgDataHandler {

    /**
     * Processes all samples in [data] and appends them to [plotter].
     *
     * Validates that `ppgValues.size == timestamps.size` before processing;
     * logs a warning and returns early on mismatch to prevent index-out-of-bounds.
     *
     * @param type    PPG channel — used only for logging.
     * @param data    Incoming batch from the Wearable Data Layer.
     * @param plotter Target plotter. If null, the batch is silently dropped
     *                (plotter may not be initialized yet).
     * @return Number of samples successfully appended, or -1 if validation failed.
     */
    fun process(type: PpgType, data: PpgData, plotter: PpgPlotter?): Int {
        if (plotter == null) {
            Log.d(TAG, "process($type): plotter is null — batch dropped (${data.size} samples)")
            return 0
        }
        if (data.size <= 0) {
            Log.d(TAG, "process($type): empty batch, nothing to process")
            return 0
        }
        if (data.ppgValues.size != data.size || data.timestamps.size != data.size) {
            Log.w(
                TAG,
                "process($type): size mismatch — " +
                    "declared=${data.size}, " +
                    "ppgValues=${data.ppgValues.size}, " +
                    "timestamps=${data.timestamps.size}. Skipping batch."
            )
            return -1
        }

        Log.d(TAG, "process($type): batch size=${data.size}")
        for (i in 0 until data.size) {
            plotter.addValues(data.ppgValues[i], data.timestamps[i])
        }
        return data.size
    }

    companion object {
        private const val TAG = "PpgDataHandler"
    }
}
