package com.example.samplewearmobileapp.models

/**
 * Tracks the running maximum and minimum over a sliding window
 * of the most recent [windowSize] values.
 *
 * @param windowSize The maximum window size.
 */
class RunningMax(private val windowSize: Int) {
    private val values: ArrayDeque<Double> = ArrayDeque(windowSize)

    /**
     * Adds a value to the sliding window.
     * If the window is full, the oldest value is evicted first.
     *
     * @param value The value to add.
     */
    fun add(value: Double) {
        if (values.size >= windowSize) {
            values.removeFirst()
        }
        values.addLast(value)
    }

    /**
     * @return The maximum value currently in the window,
     *         or [Double.NEGATIVE_INFINITY] if the window is empty.
     */
    fun max(): Double {
        var max = -Double.MAX_VALUE
        for (value in values) {
            if (value > max) max = value
        }
        return max
    }

    /**
     * @return The minimum value currently in the window,
     *         or [Double.MAX_VALUE] if the window is empty.
     */
    fun min(): Double {
        var min = Double.MAX_VALUE
        for (value in values) {
            if (value < min) min = value
        }
        return min
    }

    /**
     * @return The current number of values in the window.
     */
    fun size(): Int = values.size
}