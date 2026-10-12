package com.example.samplewearmobileapp

import com.polar.sdk.api.model.PolarEcgData
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.SerialDisposable

/** Owns pending startup as well as running ECG; callbacks from disposed attempts are ignored. */
internal class EcgStreamController {
    private var subscription: SerialDisposable? = null
    private var receivedData = false
    val isActive: Boolean get() = subscription != null
    var failures = 0
        private set

    fun resetFailures() { failures = 0 }

    fun retryAfter(error: Throwable): Boolean {
        failures++
        if (error is IllegalArgumentException || error is SecurityException || error is UnsupportedOperationException) {
            failures = 3
        }
        return failures <= 2
    }

    fun start(factory: () -> Flowable<PolarEcgData>, onData: (PolarEcgData) -> Unit,
              onError: (Throwable, Boolean) -> Unit, onComplete: () -> Unit) {
        if (isActive) return
        val owner = SerialDisposable()
        subscription = owner // Set before subscribe: a synchronous error must not leave a stale handle.
        receivedData = false
        owner.set(Flowable.defer(factory).subscribe({ data ->
            if (subscription === owner) {
                if (data.samples.isNotEmpty()) {
                    receivedData = true
                    failures = 0
                }
                onData(data)
            }
        }, { error ->
            if (subscription === owner) {
                val pending = !receivedData
                subscription = null
                owner.dispose()
                onError(error, pending)
            }
        }, {
            if (subscription === owner) {
                subscription = null
                owner.dispose()
                onComplete()
            }
        }))
    }

    /** SDK 4.0 cannot guarantee a stop command when canceled during startMeasurement. */
    fun stop(): Boolean {
        val pending = isActive && !receivedData
        val owner = subscription
        subscription = null
        owner?.dispose()
        return pending
    }
}
