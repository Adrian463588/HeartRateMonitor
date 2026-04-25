package com.example.samplewearmobileapp

import android.graphics.Color
import android.util.Log
import android.view.View
import com.androidplot.util.PixelUtils
import com.androidplot.xy.*
import com.example.samplewearmobileapp.Constants.ECG_SAMPLE_RATE
import com.example.samplewearmobileapp.Constants.N_DOMAIN_LARGE_BOXES
import com.example.samplewearmobileapp.Constants.N_ECG_PLOT_POINTS
import com.example.samplewearmobileapp.Constants.N_LARGE
import com.example.samplewearmobileapp.Constants.N_TOTAL_VISIBLE_ECG_POINTS
import com.example.samplewearmobileapp.utils.AppUtils

/**
 * Plots QRS detection output (ECG, derivative, integration score, R-peaks).
 *
 * **Stuttering fix:**
 * `plot.redraw()` is now routed through [PlotUpdateScheduler], coalescing multiple
 * per-sample calls into one redraw per vsync frame. The existing every-73-sample
 * modulo gate has been removed in favour of the scheduler's frame-aligned throttle.
 *
 * **Data integrity:** all series receive data at full ECG rate (130 Hz).
 */
class QrsPlotter : PlotterListener {
    private lateinit var parentActivity: MainActivity
    private var plot: XYPlot
    private lateinit var scheduler: PlotUpdateScheduler

    // --- Series declarations (public for CSV export access) ---

    private lateinit var formatterEcg: XYSeriesFormatter<XYRegionFormatter>
    /** Visible ECG display series (capped at [N_TOTAL_VISIBLE_ECG_POINTS]). */
    lateinit var seriesPlotEcg: SimpleXYSeries
    /** Full-resolution ECG data series for CSV export. */
    lateinit var seriesDataEcg: SimpleXYSeries

    private lateinit var formatterSquares: XYSeriesFormatter<XYRegionFormatter>
    lateinit var seriesPlotSquares: SimpleXYSeries
    lateinit var seriesDataSquares: SimpleXYSeries

    private lateinit var formatterScores: XYSeriesFormatter<XYRegionFormatter>
    lateinit var seriesPlotScores: SimpleXYSeries
    lateinit var seriesDataScores: SimpleXYSeries

    private lateinit var formatterPeaks: XYSeriesFormatter<XYRegionFormatter>
    /** Visible R-peak markers. */
    lateinit var seriesPlotPeaks: SimpleXYSeries
    /** Full R-peak series for CSV export. */
    lateinit var seriesDataPeaks: SimpleXYSeries

    /** Timestamps aligned with [seriesDataEcg]. */
    lateinit var seriesTimestamp: SimpleXYSeries

    var dataIndex: Long = 0

    /** Simplified constructor — for getNewInstance() use only. */
    constructor(plot: XYPlot) {
        this.plot = plot
    }

    /**
     * Full constructor.
     *
     * @param activity Parent activity needed for resource access.
     * @param plot     The XYPlot view.
     * @param scheduler Rate-limited redraw coordinator.
     */
    constructor(activity: MainActivity, plot: XYPlot, scheduler: PlotUpdateScheduler) {
        Log.d(TAG, "QrsPlotter constructor")
        this.parentActivity = activity
        this.plot = plot
        this.scheduler = scheduler
        dataIndex = 0

        formatterEcg = LineAndPointFormatter(Color.rgb(0, 153, 255), null, null, null)
        formatterEcg.isLegendIconEnabled = false
        seriesPlotEcg = SimpleXYSeries("ECG")
        seriesDataEcg = SimpleXYSeries("ECG")

        formatterSquares = LineAndPointFormatter(Color.rgb(255, 216, 0), null, null, null)
        formatterSquares.isLegendIconEnabled = false
        seriesPlotSquares = SimpleXYSeries("Derivative")
        seriesDataSquares = SimpleXYSeries("Derivative")

        formatterScores = LineAndPointFormatter(Color.rgb(50, 205, 50), null, null, null)
        formatterScores.isLegendIconEnabled = false
        seriesPlotScores = SimpleXYSeries("Square")
        seriesDataScores = SimpleXYSeries("Square")

        formatterPeaks = LineAndPointFormatter(null, Color.RED, null, null)
        formatterPeaks.isLegendIconEnabled = false
        seriesPlotPeaks = SimpleXYSeries("Peaks")
        seriesDataPeaks = SimpleXYSeries("Peaks")

        seriesTimestamp = SimpleXYSeries("Timestamp")

        plot.addSeries(seriesPlotSquares, formatterSquares)
        plot.addSeries(seriesPlotScores, formatterScores)
        plot.addSeries(seriesPlotPeaks, formatterPeaks)
        plot.addSeries(seriesPlotEcg, formatterEcg)
        setupPlot()
    }

    fun setupPlot() {
        Log.d(TAG, "setupPlot")
        if (plot.visibility == View.GONE) return
        try {
            val gridRect = plot.graph.gridRect ?: run {
                Log.d(TAG, "setupPlot: gridRect is null, thread=${Thread.currentThread().name}")
                return
            }
            val rMax = .25 * N_DOMAIN_LARGE_BOXES * gridRect.height() / gridRect.width()

            plot.setRangeBoundaries(-rMax, rMax, BoundaryMode.FIXED)
            val color = plot.graph.rangeGridLinePaint.color
            plot.graph.rangeOriginLinePaint.color = color
            plot.graph.rangeOriginLinePaint.strokeWidth = PixelUtils.dpToPix(1.5f)
            plot.setRangeStep(StepMode.INCREMENT_BY_VAL, .5)
            plot.linesPerRangeLabel = 5
            plot.setUserRangeOrigin(0.0)

            updateDomainBoundaries()
            plot.setDomainStep(StepMode.INCREMENT_BY_VAL, N_LARGE.toDouble())

            scheduler.scheduleRedraw(plot)
        } catch (ex: Exception) {
            val msg = """Error in QrsPlotter.setupPlot:
                |isLaidOut=${plot.isLaidOut}
                |width=${plot.width}
                |height=${plot.height}""".trimMargin()
            AppUtils.excMsg(parentActivity, msg, ex)
            Log.e(TAG, msg, ex)
        }
    }

    /**
     * Appends one ECG sample together with its derived signals.
     * All four series receive data every call. A single frame-coalesced redraw
     * is scheduled after the update.
     */
    fun addValues(ecg: Number?, square: Number?, score: Number?, timestamp: Long?) {
        if (ecg != null) {
            if (seriesPlotEcg.size() >= N_TOTAL_VISIBLE_ECG_POINTS) seriesPlotEcg.removeFirst()
            seriesPlotEcg.addLast(dataIndex, ecg)
            seriesDataEcg.addLast(dataIndex, ecg)
        }
        if (square != null) {
            if (seriesPlotSquares.size() >= N_TOTAL_VISIBLE_ECG_POINTS) seriesPlotSquares.removeFirst()
            seriesPlotSquares.addLast(dataIndex, square)
            seriesDataSquares.addLast(dataIndex, square)
        }
        if (score != null) {
            if (seriesPlotScores.size() >= N_TOTAL_VISIBLE_ECG_POINTS) seriesPlotScores.removeFirst()
            seriesPlotScores.addLast(dataIndex, score)
            seriesDataScores.addLast(dataIndex, score)
        }
        if (timestamp != null) {
            seriesTimestamp.addLast(dataIndex, timestamp)
        }
        dataIndex++
        updateDomainBoundaries()
        scheduler.scheduleRedraw(plot)
    }

    fun addPeakValue(sample: Int, ecg: Double) {
        removeOutOfRangePlotPeakValues()
        seriesPlotPeaks.addLast(sample, ecg)
        seriesDataPeaks.addLast(sample, ecg)
    }

    fun replaceLastPeakValue(sample: Int, ecg: Double) {
        removeOutOfRangePlotPeakValues()
        seriesPlotPeaks.removeLast()
        seriesDataPeaks.removeLast()
        seriesPlotPeaks.addLast(sample, ecg)
        seriesDataPeaks.addLast(sample, ecg)
    }

    fun removeOutOfRangePlotPeakValues() {
        val xMin = dataIndex - N_TOTAL_VISIBLE_ECG_POINTS
        while (seriesPlotPeaks.size() > 0 &&
            seriesPlotPeaks.getxVals().first.toInt() < xMin
        ) {
            seriesPlotPeaks.removeFirst()
        }
    }

    private fun updateDomainBoundaries() {
        if (plot.visibility == View.GONE) return
        plot.setDomainBoundaries(dataIndex - N_ECG_PLOT_POINTS, dataIndex, BoundaryMode.FIXED)
    }

    /** Implements [PlotterListener] — schedules a throttled redraw. */
    override fun update() {
        if (plot.visibility == View.GONE) return
        scheduler.scheduleRedraw(plot)
    }

    fun setPanning(on: Boolean) {
        if (on) PanZoom.attach(plot, PanZoom.Pan.HORIZONTAL, PanZoom.Zoom.NONE)
        else PanZoom.attach(plot, PanZoom.Pan.NONE, PanZoom.Zoom.NONE)
    }

    fun clear() {
        dataIndex = 0
        seriesPlotEcg.clear(); seriesDataEcg.clear()
        seriesPlotSquares.clear(); seriesDataSquares.clear()
        seriesPlotScores.clear(); seriesDataScores.clear()
        seriesPlotPeaks.clear(); seriesDataPeaks.clear()
        scheduler.scheduleRedraw(plot)
    }

    companion object {
        private const val TAG = "QrsPlotter"
    }
}