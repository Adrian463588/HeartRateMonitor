package com.example.samplewearmobileapp

import com.example.samplewearmobileapp.models.PpgType

/**
 * Buffers PPG data points (value + timestamp) for batch transmission
 * to the mobile module.
 *
 * @property ppgType The type of PPG data (Green, IR, or Red).
 */
class PpgRecording() {
    val values = mutableListOf<Int>()
    val timestamps = mutableListOf<Long>()
    lateinit var ppgType: PpgType

    constructor(type: PpgType) : this() {
        this.ppgType = type
    }

    constructor(values: List<Int>, timestamps: List<Long>, type: PpgType) : this() {
        this.values.addAll(values)
        this.timestamps.addAll(timestamps)
        this.ppgType = type
    }

    /** Appends a single PPG value and its timestamp to the buffer. */
    fun add(value: Int, timestamp: Long) {
        values.add(value)
        timestamps.add(timestamp)
    }

    /**
     * Removes the first [count] elements from both buffers.
     *
     * @param count Number of elements to remove from the front.
     */
    fun clearFromStartUntil(count: Int) {
        for (i in 0 until count) {
            values.removeFirst()
            timestamps.removeFirst()
        }
    }

    /**
     * Returns the current buffer size, or null if values and timestamps
     * have become desynchronized (indicating a bug).
     */
    fun getSize(): Int? {
        if (values.size != timestamps.size) {
            return null
        }
        return values.size
    }
}