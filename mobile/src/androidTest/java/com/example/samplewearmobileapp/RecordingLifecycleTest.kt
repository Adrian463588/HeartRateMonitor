package com.example.samplewearmobileapp

import android.content.ComponentName
import android.content.Context
import android.view.Menu
import android.widget.PopupMenu
import com.androidplot.xy.XYPlot
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Verifies Android lifecycle/control wiring; does not claim physical sensor acquisition. */
@RunWith(AndroidJUnit4::class)
class RecordingLifecycleTest {
    private class CountingPlot(context: Context) : XYPlot(context, "Redraw regression") {
        var redrawCount = 0
        var afterRedraw: (() -> Unit)? = null
        override fun redraw() {
            redrawCount++
            afterRedraw?.invoke()
        }
    }

    @Test
    fun sensorBurstRedrawsEachPlotOncePerFrame() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val rendered = CountDownLatch(1)
        lateinit var plot: CountingPlot
        lateinit var scheduler: PlotUpdateScheduler
        var before = 0
        instrumentation.runOnMainSync {
            plot = CountingPlot(instrumentation.targetContext)
            scheduler = PlotUpdateScheduler()
            before = plot.redrawCount
            plot.afterRedraw = { rendered.countDown() }
            repeat(300) { scheduler.requestRedraw(plot) }
        }
        assertTrue("A queued frame must render", rendered.await(5, TimeUnit.SECONDS))
        instrumentation.runOnMainSync {
            assertEquals(1, plot.redrawCount - before)
            scheduler.stop()
        }
    }

    @Test
    fun foregroundRecordingServiceIsDeclaredAndPrivate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = context.packageManager.getServiceInfo(ComponentName(context, MobileService::class.java), 0)
        assertFalse(service.exported)
    }

    @Test
    fun startPauseResumeStopKeepTheSessionAndStopTheService() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            lateinit var menu: Menu
            scenario.onActivity { activity ->
                menu = PopupMenu(activity, activity.findViewById(android.R.id.content)).menu
                activity.onCreateOptionsMenu(menu)
                assertEquals("Start", menu.findItem(R.id.pause).title.toString())
                assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.pause)))
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertTrue(MobileService.isServiceRunning())
            scenario.onActivity { activity ->
                activity.onCreateOptionsMenu(menu)
                assertEquals("Pause", menu.findItem(R.id.pause).title.toString())
                activity.onOptionsItemSelected(menu.findItem(R.id.pause))
                activity.onCreateOptionsMenu(menu)
                assertEquals("Resume", menu.findItem(R.id.pause).title.toString())
                activity.onOptionsItemSelected(menu.findItem(R.id.pause))
                activity.onCreateOptionsMenu(menu)
                assertEquals("Pause", menu.findItem(R.id.pause).title.toString())
                activity.onOptionsItemSelected(menu.findItem(R.id.stop_recording))
                activity.onCreateOptionsMenu(menu)
                assertEquals("Start", menu.findItem(R.id.pause).title.toString())
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertFalse(MobileService.isServiceRunning())
        }
    }
}
