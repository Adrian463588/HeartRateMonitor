package com.example.samplewearmobileapp.models

/**
 * Data class to hold arrays for plotting PPG.
 * These are arrays as opposed to the
 * LinkedList's in the respective series.
 * @property ppg The ppg values array.
 * @property timestamp The timestamp values array.
 */
data class PpgPlotArrays(var ppg: IntArray, var timestamp: LongArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PpgPlotArrays) return false
        return ppg.contentEquals(other.ppg) &&
                timestamp.contentEquals(other.timestamp)
    }

    override fun hashCode(): Int {
        var result = ppg.contentHashCode()
        result = 31 * result + timestamp.contentHashCode()
        return result
    }
}