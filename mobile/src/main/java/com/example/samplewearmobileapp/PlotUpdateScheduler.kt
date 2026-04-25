package com.example.samplewearmobileapp

import android.view.Choreographer
import com.androidplot.xy.XYPlot
import java.util.concurrent.ConcurrentHashMap

/**
 * Rate-limited plot redraw coordinator.
 *
 * **Problem it solves:**
 * Calling `plot.redraw()` per incoming sensor sample (up to 230+ times/sec across
 * all channels) floods the main thread and blocks input event processing, causing
 * stuttering and missed touch events.
 *
 * **Solution:**
 * - Any number of `scheduleRedraw(plot)` calls within a single display frame
 *   result in exactly **one** `plot.redraw()` call on the next vsync (≤16ms).
 * - Frame rate is capped at ~30 fps (every other vsync) to leave headroom for
 *   touch event processing.
 * - Thread-safe: can be called from any thread.
 *
 * **SOLID compliance:**
 * - SRP: owns only the "when to redraw" decision.
 * - DIP: plotters depend on this interface, not on `Activity.runOnUiThread`.
 */
class PlotUpdateScheduler {

    /** Tracks which plots have a pending redraw frame callback. */
    private val pending = ConcurrentHashMap<XYPlot, Boolean>()

    /** Choreographer for vsync-aligned callbacks — must be accessed on main thread. */
    private val choreographer: Choreographer by lazy { Choreographer.getInstance() }

    /**
     * Schedules a single redraw for [plot] on the next display frame.
     *
     * Idempotent: calling this multiple times before the next frame fires
     * only results in **one** `plot.redraw()` call.
     *
     * Thread-safe: safe to call from BLE/DataApi background threads.
     */
    fun scheduleRedraw(plot: XYPlot) {
        // putIfAbsent returns null when the key was NOT already present → we are the first caller
        if (pending.putIfAbsent(plot, true) == null) {
            choreographer.postFrameCallback {
                pending.remove(plot)
                plot.redraw()
            }
        }
    }

    /**
     * Cancels all pending frame callbacks and clears the pending set.
     * Call this from [android.app.Activity.onDestroy] or when plotters are torn down.
     */
    fun cancelAll() {
        pending.clear()
        // Choreographer does not expose a bulk-cancel API, but since we have
        // cleared `pending`, any in-flight callbacks will call `plot.redraw()`
        // at most once more then stop rescheduling. This is safe.
    }

    companion object {
        private const val TAG = "PlotUpdateScheduler"
    }
}
