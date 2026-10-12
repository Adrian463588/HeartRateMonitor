package com.example.samplewearmobileapp

import android.view.Choreographer
import com.androidplot.xy.XYPlot

/** Coalesces repeated sensor-triggered redraws into one redraw per plot per frame. */
class PlotUpdateScheduler : Choreographer.FrameCallback {
    private val choreographer = Choreographer.getInstance()
    private val dirtyPlots = linkedSetOf<XYPlot>()
    private var framePending = false

    fun requestRedraw(plot: XYPlot) {
        dirtyPlots.add(plot)
        if (!framePending) {
            framePending = true
            choreographer.postFrameCallback(this)
        }
    }

    fun stop() {
        choreographer.removeFrameCallback(this)
        dirtyPlots.clear()
        framePending = false
    }

    override fun doFrame(frameTimeNanos: Long) {
        framePending = false
        val plots = dirtyPlots.toList()
        dirtyPlots.clear()
        plots.forEach { it.redraw() }
    }
}
