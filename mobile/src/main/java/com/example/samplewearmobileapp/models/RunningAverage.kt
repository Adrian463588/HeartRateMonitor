package com.example.samplewearmobileapp.models

import java.util.*

/**
 * Computes a running (sliding-window) average over the
 * most recent [maxItems] values.
 *
 * @param maxItems The maximum window size.
 */
class RunningAverage(private val maxItems: Int) {
    private val list = LinkedList<Double>()
    private var sum = 0.0

    /**
     * Adds a value to the running window.
     * If the window is full, the oldest value is evicted first.
     *
     * @param value The value to add.
     */
    fun add(value: Double) {
        if (list.size == maxItems) {
            sum -= list.first
            list.removeFirst()
        }
        list.add(value)
        sum += value
    }

    /**
     * @return The average of all values currently in the window,
     *         or 0.0 if the window is empty.
     */
    fun average(): Double {
        return if (list.size == 0) 0.0 else sum / list.size
    }

    /**
     * @return The running sum of all values in the window.
     */
    fun sum(): Double = sum

    /**
     * @return The current number of values in the window.
     */
    fun size(): Int = list.size
}