package com.example.samplewearmobileapp

import android.view.Choreographer

/**
 * A central scheduler that decouples high-frequency data ingestion from UI rendering.
 *
 * Uses Android's [Choreographer] to sync plot updates with the physical screen refresh
 * rate (typically 60 FPS). This prevents the UI thread's message queue from being
 * flooded by redraw requests from sensors that emit data at high frequencies (like ECG
 * at 130 Hz).
 */
class PlotUpdateScheduler : Choreographer.FrameCallback {
    private val plotters = mutableListOf<PlotterListener>()
    private var isRunning = false
    private val choreographer = Choreographer.getInstance()

    /**
     * Registers a plotter to be updated on every vsync frame.
     */
    fun registerPlotter(plotter: PlotterListener) {
        if (!plotters.contains(plotter)) {
            plotters.add(plotter)
        }
    }

    /**
     * Unregisters a plotter.
     */
    fun unregisterPlotter(plotter: PlotterListener) {
        plotters.remove(plotter)
    }

    /**
     * Unregisters all plotters.
     */
    fun clearPlotters() {
        plotters.clear()
    }

    /**
     * Starts the rendering loop. Should be called when the activity resumes.
     */
    fun start() {
        if (!isRunning) {
            isRunning = true
            choreographer.postFrameCallback(this)
        }
    }

    /**
     * Stops the rendering loop. Should be called when the activity pauses to save battery.
     */
    fun stop() {
        if (isRunning) {
            isRunning = false
            choreographer.removeFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isRunning) return

        // Iterate through all registered plotters and trigger their render methods
        // on the UI thread exactly once per frame.
        for (plotter in plotters) {
            plotter.update()
        }

        // Schedule the next frame
        choreographer.postFrameCallback(this)
    }
}
