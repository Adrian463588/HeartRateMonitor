package com.example.samplewearmobileapp.models

/**
 * Accelerometer Data class for transportation between modules.
 *
 * Each object represents a batch of 3-axis accelerometer readings.
 * @param xValues FloatArray of X-axis acceleration values (m/s^2)
 * @param yValues FloatArray of Y-axis acceleration values (m/s^2)
 * @param zValues FloatArray of Z-axis acceleration values (m/s^2)
 * @param timestamps LongArray of timestamps for each corresponding reading
 * @param windowSize The maximum capacity of the batch arrays
 * @param size The actual number of readings currently stored in this batch
 */
data class AccelerometerData(
    var xValues: FloatArray,
    var yValues: FloatArray,
    var zValues: FloatArray,
    var timestamps: LongArray,
    var windowSize: Int,
    var size: Int = 0
) {
    constructor(windowSize: Int) : this(
        FloatArray(windowSize),
        FloatArray(windowSize),
        FloatArray(windowSize),
        LongArray(windowSize),
        windowSize,
        0
    )

    /**
     * Resets array contents and size counter.
     */
    fun clear() {
        for (i in 0 until windowSize) {
            xValues[i] = 0f
            yValues[i] = 0f
            zValues[i] = 0f
            timestamps[i] = 0L
        }
        size = 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AccelerometerData

        if (!xValues.contentEquals(other.xValues)) return false
        if (!yValues.contentEquals(other.yValues)) return false
        if (!zValues.contentEquals(other.zValues)) return false
        if (!timestamps.contentEquals(other.timestamps)) return false
        if (windowSize != other.windowSize) return false
        if (size != other.size) return false

        return true
    }

    override fun hashCode(): Int {
        var result = xValues.contentHashCode()
        result = 31 * result + yValues.contentHashCode()
        result = 31 * result + zValues.contentHashCode()
        result = 31 * result + timestamps.contentHashCode()
        result = 31 * result + windowSize
        result = 31 * result + size
        return result
    }

    override fun toString(): String {
        return "AccelerometerData(windowSize=$windowSize, size=$size)"
    }
}
