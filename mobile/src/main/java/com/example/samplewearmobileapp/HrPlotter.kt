package com.example.samplewearmobileapp

import android.graphics.Color
import android.util.Log
import com.androidplot.xy.*
import com.example.samplewearmobileapp.Constants.HR_PLOT_DOMAIN_INTERVAL
import com.example.samplewearmobileapp.models.RunningMax
import com.example.samplewearmobileapp.utils.AppUtils
import java.text.*
import java.util.*
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Plots real-time HR and RR interval data from the Polar H10 device.
 *
 * **Stuttering fix:**
 * The duplicate `fullUpdate()` extra redraw has been eliminated.
 * `plot.redraw()` is now routed through [PlotUpdateScheduler], which provides
 * a single vsync-aligned redraw per frame regardless of how many `update/fullUpdate`
 * calls arrive simultaneously.
 */
class HrPlotter : PlotterListener {
    private lateinit var parentActivity: MainActivity
    private var plot: XYPlot
    private lateinit var scheduler: PlotUpdateScheduler

    private val plotHr1 = true
    private val plotRr1 = true
    private val plotHr2 = true
    private val plotRr2 = true

    private var lastTime = Double.NaN
    private var startTime = Double.NaN
    private var startRrTime = Double.NEGATIVE_INFINITY

    private var runningMax1: RunningMax = RunningMax(50)
    private var runningMax2: RunningMax = RunningMax(50)

    private var lastRrTime = 0.0
    private var lastUpdateTime = 0.0
    private var totalRrTime = 0.0

    private var hrFormatter1: XYSeriesFormatter<XYRegionFormatter>? = null
    private var rrFormatter1: XYSeriesFormatter<XYRegionFormatter>? = null
    var hrSeries1: SimpleXYSeries? = null
    var rrSeries1: SimpleXYSeries? = null

    private var hrFormatter2: XYSeriesFormatter<XYRegionFormatter>? = null
    private var rrFormatter2: XYSeriesFormatter<XYRegionFormatter>? = null
    var hrSeries2: SimpleXYSeries? = null
    var rrSeries2: SimpleXYSeries? = null

    var hrRrList1: MutableList<HrRrSessionData> = ArrayList()
    var hrRrList2: MutableList<HrRrSessionData> = ArrayList()

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
     */
    constructor(activity: MainActivity, plot: XYPlot, scheduler: PlotUpdateScheduler) {
        Log.d(TAG, "HrPlotter constructor")
        parentActivity = activity
        this.plot = plot
        this.scheduler = scheduler

        if (plotHr1) {
            hrFormatter1 = LineAndPointFormatter(Color.RED, null, null, null)
            (hrFormatter1 as LineAndPointFormatter).isLegendIconEnabled = false
            hrSeries1 = SimpleXYSeries("HR1")
        }
        if (plotRr1) {
            rrFormatter1 = LineAndPointFormatter(Color.rgb(0, 0x99, 0xFF), null, null, null)
            (rrFormatter1 as LineAndPointFormatter).isLegendIconEnabled = false
            rrSeries1 = SimpleXYSeries("RR1")
        }
        if (plotHr2) {
            hrFormatter2 = LineAndPointFormatter(Color.rgb(0xFF, 0x88, 0xAA), null, null, null)
            (hrFormatter2 as LineAndPointFormatter).isLegendIconEnabled = false
            hrSeries2 = SimpleXYSeries("HR2")
        }
        if (plotRr2) {
            rrFormatter2 = LineAndPointFormatter(Color.rgb(0, 0xBF, 0xFF), null, null, null)
            (rrFormatter2 as LineAndPointFormatter).isLegendIconEnabled = false
            rrSeries2 = SimpleXYSeries("RR2")
        }

        plot.addSeries(hrSeries1, hrFormatter1)
        plot.addSeries(rrSeries1, rrFormatter1)
        plot.addSeries(hrSeries2, hrFormatter2)
        plot.addSeries(rrSeries2, rrFormatter2)
        setupPlot()
    }

    fun getNewInstance(plot: XYPlot): HrPlotter {
        val p = HrPlotter(plot)
        p.parentActivity = parentActivity
        p.scheduler = scheduler
        p.lastTime = lastTime
        p.startTime = startTime
        p.startRrTime = startRrTime
        p.runningMax1 = runningMax1
        p.runningMax2 = runningMax2
        p.hrRrList1 = hrRrList1
        p.hrRrList2 = hrRrList2
        p.lastRrTime = lastRrTime
        p.lastUpdateTime = lastUpdateTime
        p.totalRrTime = totalRrTime
        p.hrFormatter1 = hrFormatter1; p.hrSeries1 = hrSeries1
        p.rrFormatter1 = rrFormatter1; p.rrSeries1 = rrSeries1
        p.hrFormatter2 = hrFormatter2; p.hrSeries2 = hrSeries2
        p.rrFormatter2 = rrFormatter2; p.rrSeries2 = rrSeries2
        p.plot.addSeries(hrSeries1, hrFormatter1)
        p.plot.addSeries(rrSeries1, rrFormatter1)
        p.plot.addSeries(hrSeries2, hrFormatter2)
        p.plot.addSeries(rrSeries2, rrFormatter2)
        p.setupPlot()
        return p
    }

    fun setupPlot() {
        Log.d(TAG, "setupPlot")
        try {
            updateDomainRangeBoundaries()
            plot.setRangeStep(StepMode.INCREMENT_BY_VAL, 40.0)
            plot.setDomainStep(StepMode.SUBDIVIDE, 5.0)
            plot.graph.setLineLabelEdges(XYGraphWidget.Edge.BOTTOM, XYGraphWidget.Edge.LEFT)
            plot.graph.getLineLabelStyle(XYGraphWidget.Edge.LEFT).format = DecimalFormat("#")
            plot.graph.getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).format = object : Format() {
                private val fmt: SimpleDateFormat = X_AXIS_DATE_FORMAT
                override fun format(obj: Any, toAppendTo: StringBuffer, pos: FieldPosition): StringBuffer =
                    fmt.format((obj as Number).toDouble().roundToInt(), toAppendTo, pos)
                override fun parseObject(source: String, pos: ParsePosition): Any? = null
            }
            scheduler.scheduleRedraw(plot)
        } catch (ex: Exception) {
            val msg = """Error in HrPlotter.setupPlot:
                |isLaidOut=${plot.isLaidOut}
                |width=${plot.width}
                |height=${plot.height}""".trimMargin()
            AppUtils.excMsg(parentActivity, msg, ex)
            Log.e(TAG, msg, ex)
        }
    }

    fun addValues1(time: Double, hr: Double, rrsMs: List<Int>) {
        if (plotHr1 || plotRr1) {
            hrRrList1.add(HrRrSessionData(time, hr, rrsMs))
            if (startTime.isNaN()) startTime = time
            if (lastTime.isNaN() || time > lastTime) lastTime = time
        }
        if (plotHr1) {
            runningMax1.add(hr)
            hrSeries1!!.addLast(time, hr)
        }
        val count = rrsMs.size
        if (plotRr1 && count > 0) {
            val tValues = DoubleArray(count)
            var totalRr = 0.0
            rrsMs.forEach { totalRr += it }

            if (startRrTime.isInfinite()) {
                lastUpdateTime = time - totalRr
                lastRrTime = lastUpdateTime
                startRrTime = lastRrTime
                totalRrTime = 0.0
            }
            totalRrTime += totalRr
            var t = lastRrTime
            for (i in 0 until count) { t += rrsMs[i]; tValues[i] = t }

            if (tValues[0] < lastUpdateTime) {
                val delta = lastUpdateTime
                t += delta
                for (i in 0 until count) tValues[i] += delta
            }
            if (t > time) {
                val delta = t - time
                for (i in 0 until count) tValues[i] -= delta
            }
            for (i in 0 until count) {
                val rr = RR_SCALE * rrsMs[i]
                runningMax1.add(rr)
                rrSeries1!!.addLast(tValues[i], rr)
                lastRrTime = tValues[i]
            }
            lastUpdateTime = time
        }
        // Update domain/range and schedule ONE redraw covering HR + RR data
        updateDomainRangeBoundaries()
        scheduler.scheduleRedraw(plot)
    }

    fun addValues2(time: Double, hr: Double, rr: Double) {
        if (plotHr2 || plotRr2) {
            hrRrList2.add(HrRrSessionData(time, hr, rr))
            if (startTime.isNaN()) startTime = time
            if (lastTime.isNaN() || time > lastTime) lastTime = time
        }
        if (plotHr2) { runningMax2.add(hr); hrSeries2!!.addLast(time, hr) }
        if (plotRr2) { runningMax2.add(RR_SCALE * rr); rrSeries2!!.addLast(time, RR_SCALE * rr) }
        updateDomainRangeBoundaries()
        scheduler.scheduleRedraw(plot)
    }

    private fun updateDomainRangeBoundaries() {
        var max = maxOf(runningMax1.max(), runningMax2.max())
        if (max.isNaN() || max < 60) max = 60.0
        if (!lastTime.isNaN() && !startTime.isNaN()) {
            if (lastTime - startTime > HR_PLOT_DOMAIN_INTERVAL) {
                plot.setDomainBoundaries(lastTime - HR_PLOT_DOMAIN_INTERVAL, lastTime, BoundaryMode.FIXED)
            } else {
                plot.setDomainBoundaries(startTime, startTime + HR_PLOT_DOMAIN_INTERVAL, BoundaryMode.FIXED)
            }
        } else {
            val t0 = Date().time
            plot.setDomainBoundaries(t0, t0 + HR_PLOT_DOMAIN_INTERVAL, BoundaryMode.FIXED)
        }
        plot.setRangeBoundaries(0, ceil(max + 10).coerceAtMost(200.0), BoundaryMode.FIXED)
    }

    /** Implements [PlotterListener] — schedules a throttled redraw. */
    override fun update() {
        scheduler.scheduleRedraw(plot)
    }

    /**
     * Updates boundaries and schedules a single redraw.
     * Previously caused a *second* extra `runOnUiThread { redraw() }` on top of
     * the per-add call; now both are coalesced into one frame by the scheduler.
     */
    fun fullUpdate() {
        updateDomainRangeBoundaries()
        scheduler.scheduleRedraw(plot)
    }

    fun setPanning(on: Boolean) {
        if (on) PanZoom.attach(plot, PanZoom.Pan.HORIZONTAL, PanZoom.Zoom.NONE)
        else PanZoom.attach(plot, PanZoom.Pan.NONE, PanZoom.Zoom.NONE)
    }

    fun clear() {
        hrSeries1?.clear(); rrSeries1?.clear()
        hrSeries2?.clear(); rrSeries2?.clear()
        lastTime = Double.NaN; startTime = Double.NaN
        runningMax1 = RunningMax(50); runningMax2 = RunningMax(50)
        val t0 = Date().time
        plot.setDomainBoundaries(t0, t0 + HR_PLOT_DOMAIN_INTERVAL, BoundaryMode.FIXED)
        scheduler.scheduleRedraw(plot)
    }

    // -------------------------------------------------------------------------
    // Inner data class
    // -------------------------------------------------------------------------

    class HrRrSessionData {
        private val time: String
        private val hr: String
        private val rr: String

        constructor(time: Double, hr: Double, rrsMs: List<Int>) {
            this.time = sdf.format(Date(time.roundToLong()))
            this.hr = String.format(Locale.US, "%.0f", hr)
            val sb = StringBuilder()
            for (r in rrsMs) sb.append((1.024 * r).roundToInt()).append(" ")
            this.rr = sb.toString().trim()
        }

        constructor(time: Double, hr: Double, rr: Double) {
            this.time = sdf.format(Date(time.roundToLong()))
            this.hr = String.format(Locale.US, "%.0f", hr)
            this.rr = String.format(Locale.US, "%d", (1.024 * rr).roundToInt())
        }

        val csvString: String get() = "$time,$hr,$rr"

        companion object {
            private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        }
    }

    companion object {
        private const val TAG = "HrPlotter"
        private const val RR_SCALE = .1
        private val X_AXIS_DATE_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.US)
    }
}