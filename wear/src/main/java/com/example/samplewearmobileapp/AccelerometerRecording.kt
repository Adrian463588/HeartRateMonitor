package com.example.samplewearmobileapp

import android.util.Log

class AccelerometerRecording {
    val xValues = mutableListOf<Float>()
    val yValues = mutableListOf<Float>()
    val zValues = mutableListOf<Float>()
    val timestamps = mutableListOf<Long>()

    fun add(x: Float, y: Float, z: Float, timestamp: Long) {
        xValues.add(x)
        yValues.add(y)
        zValues.add(z)
        timestamps.add(timestamp)
    }

    fun clearFromStartUntil(count: Int) {
        val toRemove = minOf(count, xValues.size, yValues.size, zValues.size, timestamps.size)
        repeat(toRemove) {
            if (xValues.isNotEmpty()) xValues.removeAt(0)
            if (yValues.isNotEmpty()) yValues.removeAt(0)
            if (zValues.isNotEmpty()) zValues.removeAt(0)
            if (timestamps.isNotEmpty()) timestamps.removeAt(0)
        }
        Log.i("Wear: AccRecording", "Cleared $toRemove items. Remaining size: ${xValues.size}")
    }

    fun clear() {
        xValues.clear()
        yValues.clear()
        zValues.clear()
        timestamps.clear()
    }

    fun getSize(): Int = xValues.size
}
