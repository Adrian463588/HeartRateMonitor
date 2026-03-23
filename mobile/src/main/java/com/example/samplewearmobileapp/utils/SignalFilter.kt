package com.example.samplewearmobileapp.utils

/**
 * Utility object for digital signal processing filters.
 * Extracted from [com.example.samplewearmobileapp.QrsDetector] for testability.
 */
object SignalFilter {

    /**
     * Calculates a result for a generalized IIR filter with coefficients [a] and [b].
     *
     * The transfer function is:
     * ```
     * y[n] = (1 / a[0]) * (sumb - suma)
     * suma = sum from j=1..q of a[j] * y[n-j],  q = len(a)
     * sumb = sum from j=0..p of b[j] * x[n-j],  p = len(b)
     * ```
     * Uses the values at the ends (most recent) of [x] and [y].
     *
     * @param a The A (feedback) filter coefficients.
     * @param b The B (feedforward) filter coefficients.
     * @param x Input signal values.
     * @param y Previous output values (may be null for FIR-only filters).
     * @return The new filtered value, or 0.0 if inputs are too short.
     */
    fun filter(
        a: DoubleArray, b: DoubleArray, x: List<Double>,
        y: List<Double>?
    ): Double {
        val lena = a.size
        val lenb = b.size
        val lenx = x.size
        if (lenx < lenb) return 0.0

        var suma = 0.0
        if (y != null) {
            val leny = y.size
            if (leny < lena) return 0.0
            for (i in 0 until lena) {
                suma += a[i] * y[leny - i - 1]
            }
        }

        var sumb = 0.0
        for (i in 0 until lenb) {
            sumb += b[i] * x[lenx - i - 1]
        }
        return (sumb - suma) / a[0]
    }
}
