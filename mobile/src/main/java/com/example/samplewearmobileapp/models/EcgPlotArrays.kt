package com.example.samplewearmobileapp.models

/**
 * Data class to hold arrays for plotting ECG.
 * These are arrays as opposed to the
 * LinkedList's in the respective series.
 * @property ecg The ecg array is the ECG values.
 * @property peaks The peaks arrays is the same length as ecg array
 * and is true or false depending on if the ECG value corresponds to a peak.
 * @property timestamp The timestamp as taken from the Polar device.
 */
data class EcgPlotArrays(
    var ecg: DoubleArray,
    var peaks: BooleanArray,
    var timestamp: LongArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EcgPlotArrays) return false
        return ecg.contentEquals(other.ecg) &&
                peaks.contentEquals(other.peaks) &&
                timestamp.contentEquals(other.timestamp)
    }

    override fun hashCode(): Int {
        var result = ecg.contentHashCode()
        result = 31 * result + peaks.contentHashCode()
        result = 31 * result + timestamp.contentHashCode()
        return result
    }
}