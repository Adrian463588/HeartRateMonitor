package com.example.samplewearmobileapp

import com.example.samplewearmobileapp.models.PpgType

class PpgRecording() {
    val values = mutableListOf<Int>()
    val timestamps = mutableListOf<Long>()
    lateinit var ppgType: PpgType

    constructor(type: PpgType): this() {
        this.ppgType = type
    }

    constructor(values: List<Int>, timestamps: List<Long>, type: PpgType): this() {
        for (value in values) {
            this.values.add(value)
        }
        for (timestamp in timestamps) {
            this.timestamps.add(timestamp)
        }
        this.ppgType = type
    }

    fun add(value: Int, timestamp: Long) {
        values.add(value)
        timestamps.add(timestamp)
    }

    fun clearFromStartUntil(count: Int) {
        val toRemove = minOf(count, values.size, timestamps.size)
        repeat(toRemove) {
            if (values.isNotEmpty()) values.removeAt(0)
            if (timestamps.isNotEmpty()) timestamps.removeAt(0)
        }
    }

    fun clear() {
        values.clear()
        timestamps.clear()
    }

    fun getSize(): Int? {
        if (values.size != timestamps.size) {
            return null
        }
        return values.size
    }
}
