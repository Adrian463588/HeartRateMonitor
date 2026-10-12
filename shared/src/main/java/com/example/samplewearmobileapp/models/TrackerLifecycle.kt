package com.example.samplewearmobileapp.models

/** Registration ownership survives an SDK error until unregistration succeeds. */
class TrackerLifecycle {
    @Volatile var requested = false
        private set
    @Volatile var registered = false
        private set
    @Volatile var generation = 0L
        private set

    fun start(register: () -> Unit): Boolean {
        if (requested) return true
        check(!registered) { "Release the previous tracker before restarting" }
        generation++
        requested = true
        registered = true // Even partial registration failure must be cleaned up.
        try {
            register()
        } catch (error: RuntimeException) {
            requested = false
            throw error
        }
        return true
    }

    fun stop(unregister: () -> Unit): Boolean {
        generation++
        requested = false
        if (registered) {
            unregister() // Keep ownership if this fails so the next Stop can retry.
            registered = false
        }
        return true
    }

    fun accepts(callbackGeneration: Long): Boolean = requested && generation == callbackGeneration
}
