package com.example.samplewearmobileapp

import android.graphics.RectF
import android.util.Log
import com.androidplot.util.PixelUtils
import com.androidplot.xy.*
import com.example.samplewearmobileapp.Constants.MICRO_TO_MILLI_VOLT
import com.example.samplewearmobileapp.Constants.ECG_SAMPLE_RATE
import com.example.samplewearmobileapp.utils.TimestampHelper
import com.example.samplewearmobileapp.Constants.N_DOMAIN_LARGE_BOXES
import com.example.samplewearmobileapp.Constants.N_ECG_PLOT_POINTS
import com.example.samplewearmobileapp.Constants.N_LARGE
import com.example.samplewearmobileapp.Constants.N_TOTAL_VISIBLE_ECG_POINTS
import com.example.samplewearmobileapp.utils.AppUtils
import com.polar.sdk.api.model.PolarEcgData
import java.util.*

/**
 * Plots live ECG data from the Polar H10 sensor.
 *
 * **Stuttering fix:**
 * All `plot.redraw()` calls are now routed through [PlotUpdateScheduler],
 * which coalesces multiple calls within the same vsync frame into a single
 * redraw (≤30 fps). This eliminates the 130 `runOnUiThread` posts/sec that
 * previously blocked touch-event processing.
 *
 * **Data integrity:**
 * [seriesAll] and [seriesTimestamp] are still written every sample at full
 * sensor rate (130 Hz). Only the *display refresh* is throttled.
 */
class EcgPlotter : PlotterListener {
    private lateinit var parentActivity: MainActivity
    private var plot: XYPlot
    private lateinit var scheduler: PlotUpdateScheduler
    private lateinit var formatter: XYSeriesFormatter<XYRegionFormatter>

    /**
     * Display-only series, capped at [N_TOTAL_VISIBLE_ECG_POINTS].
     */
    private lateinit var seriesVisible: SimpleXYSeries

    /**
     * Full-resolution series used for CSV export.
     * **Never sampled or dropped.**
     */
    private lateinit var seriesAll: SimpleXYSeries

    /**
     * Timestamps aligned to [seriesAll] — one-to-one correspondence.
     */
    private lateinit var seriesTimestamp: SimpleXYSeries

    private var dataIndex: Long = 0

    /** Simplified constructor — for getNewInstance() use only. */
    constructor(plot: XYPlot) {
        this.plot = plot
    }

    /**
     * Full constructor.
     *
     * @param activity Parent activity.
     * @param plot     The XYPlot view.
     * @param scheduler Rate-limited redraw coordinator.
     * @param title    Series title.
     * @param lineColor Line color as ARGB int.
     * @param showVertices Whether to show point vertices.
     */
    constructor(
        activity: MainActivity,
        plot: XYPlot,
        scheduler: PlotUpdateScheduler,
        title: String?,
        lineColor: Int?,
        showVertices: Boolean
    ) {
        Log.d(TAG, "EcgPlotter constructor")
        this.parentActivity = activity
        this.plot = plot
        this.scheduler = scheduler
        this.dataIndex = 0
        formatter = LineAndPointFormatter(
            lineColor,
            if (showVertices) lineColor else null, null, null
        )
        formatter.isLegendIconEnabled = false
        seriesVisible = SimpleXYSeries(title)
        seriesAll = SimpleXYSeries(title)
        seriesTimestamp = SimpleXYSeries("Ecg-Timestamp")
        plot.addSeries(seriesVisible, formatter)
        setupPlot()
    }

    /** Copies this plotter onto a new XYPlot view (e.g. after layout reinflation). */
    fun getNewInstance(plot: XYPlot): EcgPlotter {
        val p = EcgPlotter(plot)
        p.parentActivity = parentActivity
        p.scheduler = scheduler
        p.dataIndex = dataIndex
        p.formatter = formatter
        p.seriesVisible = seriesVisible
        p.seriesAll = seriesAll
        p.seriesTimestamp = seriesTimestamp
        p.plot.addSeries(seriesVisible, formatter)
        p.setupPlot()
        return p
    }

    fun setupPlot() {
        Log.d(TAG, "setupPlot")
        try {
            val rMax: Double
            val gridRect: RectF = plot.graph.gridRect ?: run {
                Log.d(TAG, "setupPlot: gridRect is null, thread=${Thread.currentThread().name}")
                return
            }
            rMax = .25 * N_DOMAIN_LARGE_BOXES * gridRect.height() / gridRect.width()

            plot.setRangeBoundaries(-rMax, rMax, BoundaryMode.FIXED)
            val color = plot.graph.rangeGridLinePaint.color
            plot.graph.rangeOriginLinePaint.color = color
            plot.graph.rangeOriginLinePaint.strokeWidth = PixelUtils.dpToPix(1.5f)
            plot.setRangeStep(StepMode.INCREMENT_BY_VAL, .1)
            plot.linesPerRangeLabel = 5
            plot.setUserRangeOrigin(0.0)

            updateDomainBoundaries()
            plot.setDomainStep(StepMode.INCREMENT_BY_VAL, .2 * N_LARGE)
            plot.linesPerDomainLabel = 5

            scheduler.scheduleRedraw(plot)
        } catch (ex: Exception) {
            val msg = """Error in EcgPlotter.setupPlot:
                |isLaidOut=${plot.isLaidOut}
                |width=${plot.width}
                |height=${plot.height}""".trimMargin()
            AppUtils.excMsg(parentActivity, msg, ex)
            Log.e(TAG, msg, ex)
        }
    }

    private var lastBatchTimestamps = LongArray(0)
    fun getLastBatchTimestamps(): LongArray = lastBatchTimestamps

    /**
     * Appends a batch of ECG samples from the Polar device.
     *
     * All samples are written to [seriesAll] and [seriesTimestamp] at full
     * 130 Hz resolution. The display series ([seriesVisible]) rolls off old
     * entries beyond the visible window. A single redraw is scheduled via
     * [PlotUpdateScheduler] for the whole batch.
     */
    fun addValues(polarEcgData: PolarEcgData) {
        val sampleCount = polarEcgData.samples.size
        if (sampleCount == 0) return

        val batchTs = LongArray(sampleCount)
        var batchIdx = 0

        for (sample in polarEcgData.samples) {
            if (seriesVisible.size() >= N_TOTAL_VISIBLE_ECG_POINTS) {
                seriesVisible.removeFirst()
            }
            val mv = MICRO_TO_MILLI_VOLT * sample.voltage
            seriesVisible.addLast(dataIndex, mv)
            seriesAll.addLast(dataIndex, mv)
            val ts = TimestampHelper.nextEcgTimestamp(ECG_SAMPLE_RATE.toDouble())
            seriesTimestamp.addLast(dataIndex, ts)
            batchTs[batchIdx++] = ts
            dataIndex++
        }
        lastBatchTimestamps = batchTs

        updateDomainBoundaries()
        // One redraw for the entire batch — not one per sample.
        scheduler.scheduleRedraw(plot)
    }

    private fun updateDomainBoundaries() {
        plot.setDomainBoundaries(dataIndex - N_ECG_PLOT_POINTS, dataIndex, BoundaryMode.FIXED)
    }

    /** Triggers a redraw via the scheduler (implements [PlotterListener]). */
    override fun update() {
        scheduler.scheduleRedraw(plot)
    }

    fun setPanning(on: Boolean) {
        if (on) PanZoom.attach(plot, PanZoom.Pan.HORIZONTAL, PanZoom.Zoom.NONE)
        else PanZoom.attach(plot, PanZoom.Pan.NONE, PanZoom.Zoom.NONE)
    }

    fun getVisibleSeries(): SimpleXYSeries = seriesVisible
    fun getDataSeries(): SimpleXYSeries = seriesAll
    fun getTimestampSeries(): SimpleXYSeries = seriesTimestamp
    fun getDataIndex(): Long = dataIndex

    fun getCompiledDataSeries(): SimpleXYSeries = SimpleXYSeries(
        seriesAll.getyVals().toMutableList(),
        seriesTimestamp.getyVals().toMutableList(),
        "Complete-ECG"
    )

    fun clear() {
        dataIndex = 0
        seriesVisible.clear()
        seriesAll.clear()
        seriesTimestamp.clear()
        scheduler.scheduleRedraw(plot)
    }

    companion object {
        private const val TAG = "EcgPlotter"
    }
}