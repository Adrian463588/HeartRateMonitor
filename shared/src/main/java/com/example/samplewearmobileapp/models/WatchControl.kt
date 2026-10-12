package com.example.samplewearmobileapp.models

import com.example.samplewearmobileapp.constants.codes.ActivityCode

/** Ordered controller intent; End is terminal until a newer session starts. */
class WatchControl {
    var sessionId = 0L
        private set
    var revision = 0L
        private set
    var code = ActivityCode.DO_NOTHING
        private set
    private var lastContactMs = 0L

    fun accept(id: Long, sequence: Long, state: Int, nowMs: Long): Boolean {
        if (id <= 0L || sequence <= 0L || id < sessionId ||
            state !in setOf(ActivityCode.START_ACTIVITY, ActivityCode.PAUSE_ACTIVITY, ActivityCode.STOP_ACTIVITY)) return false
        if (id == sessionId && (sequence < revision ||
            (sequence == revision && state != code) ||
            (code == ActivityCode.STOP_ACTIVITY && state != ActivityCode.STOP_ACTIVITY))) return false
        sessionId = id
        revision = sequence
        code = state
        lastContactMs = nowMs
        return true // Duplicate intent renews the lease and receives another acknowledgement.
    }

    fun isExpired(nowMs: Long): Boolean = code == ActivityCode.START_ACTIVITY &&
        nowMs - lastContactMs >= TIMEOUT_MS

    fun matches(message: Message): Boolean = message.sessionId == sessionId &&
        message.commandRevision == revision && message.code == code &&
        (code == ActivityCode.START_ACTIVITY || message.sensorsStopped == true)

    companion object {
        const val HEARTBEAT_MS = 3_000L
        const val TIMEOUT_MS = 15_000L
    }
}
