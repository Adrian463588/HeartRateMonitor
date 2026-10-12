package com.example.samplewearmobileapp

import com.polar.sdk.api.model.PolarEcgData
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.processors.PublishProcessor
import io.reactivex.rxjava3.schedulers.TestScheduler
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Deterministic subscription fixtures; no physical ECG acceptance or invented measurements. */
class EcgStreamControllerTest {
    private val packet = PolarEcgData(listOf(PolarEcgData.PolarEcgDataSample(1_000_000L, 1)), 1_000_000L)

    @Test fun `transient errors permit two retries and then require manual recovery`() {
        val controller = EcgStreamController()
        assertTrue(controller.retryAfter(IllegalStateException("Busy")))
        assertTrue(controller.retryAfter(IllegalStateException("Busy")))
        assertFalse(controller.retryAfter(IllegalStateException("Busy")))
        controller.resetFailures()
        assertTrue(controller.retryAfter(IllegalStateException("Busy")))
    }

    @Test fun `unsupported settings and permission errors are not retried`() {
        val controller = EcgStreamController()
        assertFalse(controller.retryAfter(IllegalArgumentException("Unsupported rate")))
        controller.resetFailures()
        assertFalse(controller.retryAfter(SecurityException("Permission denied")))
        controller.resetFailures()
        assertFalse(controller.retryAfter(UnsupportedOperationException("No ECG")))
    }

    @Test fun `packet fixture resets recovery attempts but Stop alone does not`() {
        val controller = EcgStreamController()
        controller.retryAfter(IllegalStateException("Busy"))
        controller.start({ Flowable.never() }, {}, { _, _ -> fail() }, {})
        controller.stop()
        assertEquals(1, controller.failures)
        controller.start({ Flowable.just(packet) }, {}, { _, _ -> fail() }, {})
        assertEquals(0, controller.failures)
    }

    @Test fun `synchronous settings failure clears ownership and permits a new attempt`() {
        val controller = EcgStreamController()
        var failures = 0
        controller.start({ Flowable.error(IllegalStateException("Settings not ready")) }, {},
            { _, pending -> failures++; assertTrue(pending) }, {})
        assertFalse(controller.isActive)
        controller.start({ Flowable.never() }, {}, { _, _ -> fail() }, {})
        assertTrue(controller.isActive)
        assertTrue(controller.stop())
        assertEquals(1, failures)
    }

    @Test fun `Pause cancels pending settings so late settings cannot start ECG`() {
        val settings = PublishProcessor.create<Int>()
        val controller = EcgStreamController()
        var starts = 0
        controller.start({ settings.flatMap { starts++; Flowable.never<PolarEcgData>() } }, {}, { _, _ -> fail() }, {})
        assertTrue(controller.stop())
        settings.onNext(130)
        assertEquals(0, starts)
        assertFalse(settings.hasSubscribers())
    }

    @Test fun `duplicate Start does not subscribe twice`() {
        val controller = EcgStreamController()
        var starts = 0
        val factory = { starts++; Flowable.never<PolarEcgData>() }
        controller.start(factory, {}, { _, _ -> fail() }, {})
        controller.start(factory, {}, { _, _ -> fail() }, {})
        assertEquals(1, starts)
        controller.stop()
    }

    @Test fun `End cancels queued data error and timeout before Resume`() {
        val scheduler = TestScheduler()
        val source = PublishProcessor.create<PolarEcgData>()
        val controller = EcgStreamController()
        var callbacks = 0
        controller.start({ source.timeout(10, TimeUnit.SECONDS, scheduler).observeOn(scheduler) },
            { callbacks++ }, { _, _ -> callbacks++ }, { callbacks++ })
        source.onNext(packet)
        controller.stop()
        scheduler.advanceTimeBy(20, TimeUnit.SECONDS)
        assertEquals(0, callbacks)
        assertFalse(controller.isActive)
    }

    @Test fun `Resume owns only the new stream and running Stop disposes it`() {
        val old = PublishProcessor.create<PolarEcgData>()
        val resumed = PublishProcessor.create<PolarEcgData>()
        val controller = EcgStreamController()
        var received = 0
        controller.start({ old }, { received++ }, { _, _ -> fail() }, {})
        old.onNext(packet)
        assertFalse(controller.stop())
        controller.start({ resumed }, { received++ }, { _, _ -> fail() }, {})
        old.onNext(packet)
        resumed.onNext(packet)
        assertEquals(2, received)
        assertFalse(old.hasSubscribers())
        assertFalse(controller.stop())
        assertFalse(resumed.hasSubscribers())
    }

    @Test fun `a thrown SDK call becomes a recoverable error rather than crashing`() {
        val controller = EcgStreamController()
        var failure: Throwable? = null
        controller.start({ throw IllegalArgumentException("Invalid device") }, {}, { error, _ -> failure = error }, {})
        assertTrue(failure is IllegalArgumentException)
        assertFalse(controller.isActive)
    }

    @Test fun `completion clears the subscription even before subscribe returns`() {
        val controller = EcgStreamController()
        var completed = false
        controller.start({ Flowable.empty() }, {}, { _, _ -> fail() }, { completed = true })
        assertTrue(completed)
        assertFalse(controller.isActive)
        assertFalse(controller.stop())
    }

    @Test fun `a stalled startup times out once and leaves no stale active handle`() {
        val scheduler = TestScheduler()
        val controller = EcgStreamController()
        var failures = 0
        controller.start({ Flowable.never<PolarEcgData>().timeout(10, TimeUnit.SECONDS, scheduler) }, {},
            { _, pending -> failures++; assertTrue(pending) }, {})
        scheduler.advanceTimeBy(10, TimeUnit.SECONDS)
        assertEquals(1, failures)
        assertFalse(controller.isActive)
    }
}
