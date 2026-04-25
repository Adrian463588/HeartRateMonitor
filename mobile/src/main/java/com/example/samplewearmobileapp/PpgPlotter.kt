package com.example.samplewearmobileapp

import android.util.Log
import com.androidplot.xy.*
import com.example.samplewearmobileapp.Constants.N_PPG_GREEN_PLOT_POINTS
import com.example.samplewearmobileapp.Constants.N_PPG_IR_RED_PLOT_POINTS
import com.example.samplewearmobileapp.models.PpgType
import com.example.samplewearmobileapp.models.PpgType.*
import com.example.samplewearmobileapp.models.RunningMax
import com.example.samplewearmobileapp.utils.AppUtils
import java.text.DecimalFormat
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Plots live PPG data received from the Samsung watch.
 *
 * **Stuttering fix:**
 * `plot.redraw()` is now routed through [PlotUpdateScheduler], which coalesces
 * calls within the same vsync frame into one redraw per plot per frame (≤30 fps).
 * Previously, redraw was called per-sample (25–52 Hz per channel = 100+ Hz total).
 *
 * **Boundary throttle:**
 * [updateDomainRangeBoundaries] is called at most once every [BOUNDARY_UPDATE_INTERVAL]
 * samples, avoiding redundant `ceil/floor` calculations and `setRangeBoundaries` calls
 * per-sample. Data is still written every sample.
 *
 * **Data integrity:**
 * [seriesAll] and [seriesTimestamp] are written every sample at full sensor rate.
 * Only the *display refresh* and *boundary* calculation are throttled.
 */
class PpgPlotter : PlotterListener {
    private lateinit var parentActivity: MainActivity
    private var plot: XYPlot
    private lateinit var scheduler: PlotUpdateScheduler
    private lateinit var formatter: XYSeriesFormatter<XYRegionFormatter>
    private lateinit var ppgType: PpgType
    private var visiblePointLimit: Int = 0

    private lateinit var seriesVisible: SimpleXYSeries
    private lateinit var seriesAll: SimpleXYSeries
    private lateinit var seriesTimestamp: SimpleXYSeries

    private var dataIndex: Long = 0
    private var runningMax: RunningMax = RunningMax(N_PPG_IR_RED_PLOT_POINTS)

    /** Simplified constructor — for getNewInstance() use only. */
    constructor(plot: XYPlot) {
        this.plot = plot
        visiblePointLimit = N_PPG_IR_RED_PLOT_POINTS
    }

    /**
     * Full constructor.
     *
     * @param activity Parent activity.
     * @param plot     The XYPlot view.
     * @param ppgType  Channel type (Green, IR, Red).
     * @param scheduler Rate-limited redraw coordinator.
     * @param title    Series title.
     * @param lineColor Line color as ARGB int.
     * @param showVertices Whether to show point vertices.
     */
    constructor(
        activity: MainActivity,
        plot: XYPlot,
        ppgType: PpgType,
        scheduler: PlotUpdateScheduler,
        title: String?,
        lineColor: Int?,
        showVertices: Boolean
    ) {
        Log.d(TAG, "PpgPlotter constructor: $ppgType")
        this.parentActivity = activity
        this.plot = plot
        this.ppgType = ppgType
        this.scheduler = scheduler
        this.dataIndex = 0
        this.visiblePointLimit = when (ppgType) {
            PPG_GREEN -> N_PPG_GREEN_PLOT_POINTS
            PPG_IR, PPG_RED -> N_PPG_IR_RED_PLOT_POINTS
        }
        formatter = LineAndPointFormatter(
            lineColor,
            if (showVertices) lineColor else null, null, null
        )
        formatter.isLegendIconEnabled = false
        seriesVisible = SimpleXYSeries(title)
        seriesAll = SimpleXYSeries(title)
        seriesTimestamp = SimpleXYSeries("Ppg-Timestamp-${ppgType.name}")
        plot.addSeries(seriesVisible, formatter)
        setupPlot()
    }

    /** Copies this plotter onto a new [XYPlot] view (e.g. after layout reinflation). */
    fun getNewInstance(plot: XYPlot): PpgPlotter {
        val p = PpgPlotter(plot)
        p.parentActivity = parentActivity
        p.ppgType = ppgType
        p.scheduler = scheduler
        p.dataIndex = dataIndex
        p.visiblePointLimit = visiblePointLimit
        p.formatter = formatter
        p.seriesVisible = seriesVisible
        p.seriesAll = seriesAll
        p.seriesTimestamp = seriesTimestamp
        p.plot.addSeries(seriesVisible, formatter)
        p.setupPlot()
        return p
    }

    fun setupPlot() {
        Log.d(TAG, "setupPlot: $ppgType")
        try {
            updateDomainRangeBoundaries()
            plot.setRangeStep(StepMode.SUBDIVIDE, 8.0)
            plot.setDomainStep(StepMode.INCREMENT_BY_VAL, visiblePointLimit * .25)
            scheduler.scheduleRedraw(plot)
        } catch (ex: Exception) {
            val msg = """Error in PpgPlotter.setupPlot:
                |isLaidOut=${plot.isLaidOut}
                |width=${plot.width}
                |height=${plot.height}""".trimMargin()
            AppUtils.excMsg(parentActivity, msg, ex)
            Log.e(TAG, msg, ex)
        }
    }

    /**
     * Appends a single PPG sample.
     *
     * The [seriesAll] and [seriesTimestamp] series receive every sample at full
     * sensor rate for lossless CSV export. [updateDomainRangeBoundaries] is
     * throttled to every [BOUNDARY_UPDATE_INTERVAL] samples to reduce CPU load.
     * A single frame-coalesced redraw is scheduled via [PlotUpdateScheduler].
     */
    fun addValues(ppgValue: Int, timestamp: Long) {
        if (seriesVisible.size() >= visiblePointLimit) {
            seriesVisible.removeFirst()
        }
        runningMax.add(ppgValue.toDouble())
        seriesVisible.addLast(dataIndex, ppgValue)
        seriesAll.addLast(dataIndex, ppgValue)
        seriesTimestamp.addLast(dataIndex, timestamp)
        dataIndex++

        // Throttle boundary recalculation — domain/range math is expensive;
        // updating every 5 samples (≤0.2s at 25Hz) is imperceptible to users.
        if (dataIndex % BOUNDARY_UPDATE_INTERVAL == 0L) {
            updateDomainRangeBoundaries()
        }

        // One Choreographer-coalesced redraw per incoming sample slot.
        // If multiple channels call this simultaneously, each has its own
        // pending set entry → still only one redraw per plot per frame.
        scheduler.scheduleRedraw(plot)
    }

    private fun updateDomainRangeBoundaries() {
        val max: Double = runningMax.max().coerceAtLeast(60.0)
        val min: Double = runningMax.min().coerceAtMost(0.0)
        val upper: Number = ceil(max + 0.1 * max)
        val lower: Number = floor(min - 0.1 * min).coerceAtLeast(0.0)
        plot.setRangeBoundaries(lower, upper, BoundaryMode.FIXED)
        plot.setDomainBoundaries(dataIndex - visiblePointLimit, dataIndex, BoundaryMode.FIXED)
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
    fun getPpgType(): PpgType = ppgType

    fun getCompiledDataSeries(): SimpleXYSeries = SimpleXYSeries(
        seriesAll.getyVals().toMutableList(),
        seriesTimestamp.getyVals().toMutableList(),
        "Complete-PPG-${ppgType.name}"
    )

    fun clear() {
        dataIndex = 0
        seriesVisible.clear()
        seriesAll.clear()
        seriesTimestamp.clear()
        runningMax = RunningMax(visiblePointLimit)
        scheduler.scheduleRedraw(plot)
    }

    companion object {
        private const val TAG = "PpgPlotter"

        /**
         * Boundary recalculation is throttled to every N samples.
         * At 25 Hz (PPG IR/Red) this means ≤200ms between updates —
         * imperceptible to the user, but saves significant main-thread CPU.
         */
        private const val BOUNDARY_UPDATE_INTERVAL = 5L
    }
}