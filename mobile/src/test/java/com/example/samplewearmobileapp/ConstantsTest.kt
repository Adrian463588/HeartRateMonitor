package com.example.samplewearmobileapp

import com.example.samplewearmobileapp.Constants.A_DERIVATIVE
import com.example.samplewearmobileapp.Constants.B_DERIVATIVE
import com.example.samplewearmobileapp.Constants.DATA_WINDOW
import com.example.samplewearmobileapp.Constants.ECG_SAMPLE_RATE
import com.example.samplewearmobileapp.Constants.HR_200_INTERVAL
import com.example.samplewearmobileapp.Constants.HR_PLOT_DOMAIN_INTERVAL
import com.example.samplewearmobileapp.Constants.MICRO_TO_MILLI_VOLT
import com.example.samplewearmobileapp.Constants.MOV_AVG_HR_WINDOW
import com.example.samplewearmobileapp.Constants.N_DOMAIN_LARGE_BOXES
import com.example.samplewearmobileapp.Constants.N_ECG_PLOT_POINTS
import com.example.samplewearmobileapp.Constants.N_LARGE
import com.example.samplewearmobileapp.Constants.N_PPG_GREEN_PLOT_POINTS
import com.example.samplewearmobileapp.Constants.N_PPG_IR_RED_PLOT_POINTS
import com.example.samplewearmobileapp.Constants.N_TOTAL_VISIBLE_ECG_POINTS
import com.example.samplewearmobileapp.Constants.PPG_GREEN_SAMPLE_RATE
import com.example.samplewearmobileapp.Constants.PPG_IR_RED_SAMPLE_RATE
import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies that computed constants in [Constants] are consistent
 * and hold expected relationships.
 */
class ConstantsTest {

    // --- Sampling Rates ---

    @Test
    fun `ECG sample rate is positive`() {
        assertTrue(ECG_SAMPLE_RATE > 0)
    }

    @Test
    fun `PPG Green sample rate is positive`() {
        assertTrue(PPG_GREEN_SAMPLE_RATE > 0)
    }

    @Test
    fun `PPG IR Red sample rate is positive`() {
        assertTrue(PPG_IR_RED_SAMPLE_RATE > 0)
    }

    // --- Plot Points ---

    @Test
    fun `N_ECG_PLOT_POINTS is positive`() {
        assertTrue(N_ECG_PLOT_POINTS > 0)
    }

    @Test
    fun `N_TOTAL_VISIBLE_ECG_POINTS is greater than or equal to N_ECG_PLOT_POINTS`() {
        assertTrue(N_TOTAL_VISIBLE_ECG_POINTS >= N_ECG_PLOT_POINTS)
    }

    @Test
    fun `N_PPG_GREEN_PLOT_POINTS is positive`() {
        assertTrue(N_PPG_GREEN_PLOT_POINTS > 0)
    }

    @Test
    fun `N_PPG_IR_RED_PLOT_POINTS is positive`() {
        assertTrue(N_PPG_IR_RED_PLOT_POINTS > 0)
    }

    // --- Grid Constants ---

    @Test
    fun `N_LARGE is positive`() {
        assertTrue(N_LARGE > 0)
    }

    @Test
    fun `N_DOMAIN_LARGE_BOXES is positive`() {
        assertTrue(N_DOMAIN_LARGE_BOXES > 0)
    }

    // --- Conversion Factor ---

    @Test
    fun `MICRO_TO_MILLI_VOLT is 0_001`() {
        assertEquals(0.001, MICRO_TO_MILLI_VOLT, 1e-9)
    }

    // --- Timing ---

    @Test
    fun `HR_200_INTERVAL is a fraction of sample rate`() {
        assertTrue(HR_200_INTERVAL > 0)
    }

    @Test
    fun `MOV_AVG_HR_WINDOW is positive`() {
        assertTrue(MOV_AVG_HR_WINDOW > 0)
    }

    @Test
    fun `HR_PLOT_DOMAIN_INTERVAL is positive`() {
        assertTrue(HR_PLOT_DOMAIN_INTERVAL > 0)
    }

    // --- Filter Coefficients ---

    @Test
    fun `A_DERIVATIVE coefficients array is non-empty`() {
        assertTrue(A_DERIVATIVE.isNotEmpty())
    }

    @Test
    fun `B_DERIVATIVE coefficients array is non-empty`() {
        assertTrue(B_DERIVATIVE.isNotEmpty())
    }

    @Test
    fun `A_DERIVATIVE first element is non-zero for valid filter`() {
        assertNotEquals(0.0, A_DERIVATIVE[0], 1e-15)
    }

    // --- DATA_WINDOW ---

    @Test
    fun `DATA_WINDOW is positive and reasonable`() {
        assertTrue(DATA_WINDOW > 0)
        assertTrue(DATA_WINDOW <= 10000)
    }
}
