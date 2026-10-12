package com.example.samplewearmobileapp.models

/** Tracks delivered batches and the Watch's final counts, without discarding late flushes. */
class WatchSession {
    var sessionId = 0L
        private set
    private val received = mutableSetOf<String>()
    private val counts = IntArray(4) // Green, IR, Red, accelerometer.
    private var expected: IntArray? = null
    private var stopRequested = false

    val isComplete: Boolean get() = !stopRequested || expected?.contentEquals(counts) == true

    fun start(id: Long) {
        require(id > 0L)
        sessionId = id
        received.clear()
        counts.fill(0)
        expected = null
        stopRequested = false
    }

    fun requestStop() {
        stopRequested = true
        expected = null
    }

    fun reportStopped(id: Long, samples: IntArray) {
        if (id == sessionId && samples.size == counts.size && samples.all { it >= 0 }) {
            expected = samples.copyOf()
        }
    }

    fun acceptBatch(id: Long, key: String, channel: Int, size: Int): Boolean {
        if (sessionId == 0L || id != sessionId || channel !in counts.indices || size <= 0 ||
            key in received) return false
        received.add(key)
        counts[channel] += size
        return true
    }
}
