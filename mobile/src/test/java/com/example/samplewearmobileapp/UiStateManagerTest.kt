package com.example.samplewearmobileapp

import android.content.Context
import android.widget.TextView
import com.example.samplewearmobileapp.models.PpgType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner

/**
 * Unit tests for [UiStateManager] and [PpgUiState].
 *
 * [UiStateManager] internally calls [android.widget.TextView.setText] via the Kotlin property
 * setter `.text =`. In JVM tests (no Android framework) we verify that
 * [Context.getString] is called with the correct resource ID and format argument
 * rather than asserting on the TextView text directly — since TextView is a
 * framework object that would require Robolectric to function.
 *
 * What is covered:
 *  1. [UiStateManager.setAllPpgState] — calls getString for all 3 channels
 *  2. [UiStateManager.setSinglePpgState] — routes to exactly one channel
 *  3. [UiStateManager.setEcgState]     — calls getString for ECG only
 *  4. [PpgUiState.label]               — returns the correct localised string
 */
@RunWith(MockitoJUnitRunner::class)
class UiStateManagerTest {

    @Mock private lateinit var context: Context
    @Mock private lateinit var tvGreen: TextView
    @Mock private lateinit var tvIr: TextView
    @Mock private lateinit var tvRed: TextView
    @Mock private lateinit var tvEcg: TextView

    private lateinit var manager: UiStateManager

    @Before
    fun setUp() {
        // Stub state label strings
        `when`(context.getString(R.string.status_running)).thenReturn("Running")
        `when`(context.getString(R.string.status_stopped)).thenReturn("Stopped")
        `when`(context.getString(R.string.status_paused)).thenReturn("Paused")
        `when`(context.getString(R.string.status_measuring)).thenReturn("Measuring")
        `when`(context.getString(R.string.status_connected)).thenReturn("Connected")
        `when`(context.getString(R.string.status_connecting)).thenReturn("Connecting")
        `when`(context.getString(R.string.status_disconnected)).thenReturn("Disconnected")
        `when`(context.getString(R.string.status_default)).thenReturn("—")

        // Stub format strings (returns a composite but we don't assert on the exact text, only call count)
        `when`(context.getString(eq(R.string.ppg_green_status), any())).thenReturn("Green: ?")
        `when`(context.getString(eq(R.string.ppg_ir_status),    any())).thenReturn("IR: ?")
        `when`(context.getString(eq(R.string.ppg_red_status),   any())).thenReturn("Red: ?")
        `when`(context.getString(eq(R.string.ecg_status),       any())).thenReturn("ECG: ?")

        manager = UiStateManager(
            context,
            UiStateManager.StatusViews(tvGreen, tvIr, tvRed, tvEcg)
        )
    }

    // -------------------------------------------------------------------------
    // setAllPpgState — must touch all three PPG views
    // -------------------------------------------------------------------------

    @Test
    fun `setAllPpgState Running calls getString for all three PPG channels`() {
        manager.setAllPpgState(PpgUiState.Running)
        verify(context, times(1)).getString(eq(R.string.ppg_green_status), any())
        verify(context, times(1)).getString(eq(R.string.ppg_ir_status),    any())
        verify(context, times(1)).getString(eq(R.string.ppg_red_status),   any())
        verify(context, never()).getString(eq(R.string.ecg_status), any())
    }

    @Test
    fun `setAllPpgState Stopped calls getString for all three PPG channels`() {
        manager.setAllPpgState(PpgUiState.Stopped)
        verify(context, times(1)).getString(eq(R.string.ppg_green_status), any())
        verify(context, times(1)).getString(eq(R.string.ppg_ir_status),    any())
        verify(context, times(1)).getString(eq(R.string.ppg_red_status),   any())
    }

    @Test
    fun `setAllPpgState Paused calls getString for all three PPG channels`() {
        manager.setAllPpgState(PpgUiState.Paused)
        verify(context, times(1)).getString(eq(R.string.ppg_green_status), any())
        verify(context, times(1)).getString(eq(R.string.ppg_ir_status),    any())
        verify(context, times(1)).getString(eq(R.string.ppg_red_status),   any())
    }

    // -------------------------------------------------------------------------
    // setSinglePpgState — must touch exactly one PPG view
    // -------------------------------------------------------------------------

    @Test
    fun `setSinglePpgState PPG_GREEN calls getString only for green`() {
        manager.setSinglePpgState(PpgType.PPG_GREEN, PpgUiState.Measuring)
        verify(context, times(1)).getString(eq(R.string.ppg_green_status), any())
        verify(context, never()).getString(eq(R.string.ppg_ir_status),  any())
        verify(context, never()).getString(eq(R.string.ppg_red_status), any())
        verify(context, never()).getString(eq(R.string.ecg_status),     any())
    }

    @Test
    fun `setSinglePpgState PPG_IR calls getString only for IR`() {
        manager.setSinglePpgState(PpgType.PPG_IR, PpgUiState.Connected)
        verify(context, never()).getString( eq(R.string.ppg_green_status), any())
        verify(context, times(1)).getString(eq(R.string.ppg_ir_status),    any())
        verify(context, never()).getString( eq(R.string.ppg_red_status),   any())
    }

    @Test
    fun `setSinglePpgState PPG_RED calls getString only for red`() {
        manager.setSinglePpgState(PpgType.PPG_RED, PpgUiState.Disconnected)
        verify(context, never()).getString( eq(R.string.ppg_green_status), any())
        verify(context, never()).getString( eq(R.string.ppg_ir_status),    any())
        verify(context, times(1)).getString(eq(R.string.ppg_red_status),   any())
    }

    // -------------------------------------------------------------------------
    // setEcgState — must touch only the ECG view
    // -------------------------------------------------------------------------

    @Test
    fun `setEcgState Connecting calls getString only for ECG`() {
        manager.setEcgState(PpgUiState.Connecting)
        verify(context, times(1)).getString(eq(R.string.ecg_status), any())
        verify(context, never()).getString(eq(R.string.ppg_green_status), any())
        verify(context, never()).getString(eq(R.string.ppg_ir_status),    any())
        verify(context, never()).getString(eq(R.string.ppg_red_status),   any())
    }

    @Test
    fun `setEcgState Disconnected calls getString for ECG`() {
        manager.setEcgState(PpgUiState.Disconnected)
        verify(context, times(1)).getString(eq(R.string.ecg_status), any())
    }

    // -------------------------------------------------------------------------
    // PpgUiState.label — all 8 states return correct value
    // -------------------------------------------------------------------------

    @Test
    fun `PpgUiState Default label returns dash`() {
        assertEquals("—", PpgUiState.Default.label(context))
    }

    @Test
    fun `PpgUiState Running label returns Running`() {
        assertEquals("Running", PpgUiState.Running.label(context))
    }

    @Test
    fun `PpgUiState Stopped label returns Stopped`() {
        assertEquals("Stopped", PpgUiState.Stopped.label(context))
    }

    @Test
    fun `PpgUiState Paused label returns Paused`() {
        assertEquals("Paused", PpgUiState.Paused.label(context))
    }

    @Test
    fun `PpgUiState Measuring label returns Measuring`() {
        assertEquals("Measuring", PpgUiState.Measuring.label(context))
    }

    @Test
    fun `PpgUiState Connected label returns Connected`() {
        assertEquals("Connected", PpgUiState.Connected.label(context))
    }

    @Test
    fun `PpgUiState Connecting label returns Connecting`() {
        assertEquals("Connecting", PpgUiState.Connecting.label(context))
    }

    @Test
    fun `PpgUiState Disconnected label returns Disconnected`() {
        assertEquals("Disconnected", PpgUiState.Disconnected.label(context))
    }
}
