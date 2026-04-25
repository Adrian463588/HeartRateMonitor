package com.example.samplewearmobileapp

import android.content.Context
import android.util.Log
import com.example.samplewearmobileapp.models.PpgType

/**
 * Manages all PPG and ECG status [android.widget.TextView] updates on the main thread.
 *
 * **DRY fix:** Eliminates the three overloaded `toggleState(Int)`, `toggleState(Boolean)`,
 * `toggleState(PpgType)` functions in `MainActivity`, each of which contained duplicated
 * `runOnUiThread { textPpgX.text = ... }` blocks.
 *
 * **SRP:** Owns *only* the "what text should these status views show" concern.
 *
 * All public functions must be called from the **main thread**.
 */
class UiStateManager(
    private val context: Context,
    private val views: StatusViews
) {

    /**
     * Data class carrying references to the four status TextViews.
     * Constructed once in [MainActivity.onCreate] to avoid repeated `findViewById` calls.
     */
    data class StatusViews(
        val ppgGreen: android.widget.TextView,
        val ppgIr: android.widget.TextView,
        val ppgRed: android.widget.TextView,
        val ecg: android.widget.TextView
    )

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Sets all three PPG status views to the same [state]. */
    fun setAllPpgState(state: PpgUiState) {
        setPpgGreenState(state)
        setPpgIrState(state)
        setPpgRedState(state)
    }

    /** Sets the status view for a single PPG [type]. */
    fun setSinglePpgState(type: PpgType, state: PpgUiState) {
        when (type) {
            PpgType.PPG_GREEN -> setPpgGreenState(state)
            PpgType.PPG_IR    -> setPpgIrState(state)
            PpgType.PPG_RED   -> setPpgRedState(state)
        }
    }

    /** Sets the ECG status view. */
    fun setEcgState(state: PpgUiState) {
        views.ecg.text = context.getString(R.string.ecg_status, state.label(context))
        Log.d(TAG, "ECG state → ${state::class.simpleName}")
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun setPpgGreenState(state: PpgUiState) {
        views.ppgGreen.text = context.getString(R.string.ppg_green_status, state.label(context))
        Log.d(TAG, "PPG_GREEN state → ${state::class.simpleName}")
    }

    private fun setPpgIrState(state: PpgUiState) {
        views.ppgIr.text = context.getString(R.string.ppg_ir_status, state.label(context))
        Log.d(TAG, "PPG_IR state → ${state::class.simpleName}")
    }

    private fun setPpgRedState(state: PpgUiState) {
        views.ppgRed.text = context.getString(R.string.ppg_red_status, state.label(context))
        Log.d(TAG, "PPG_RED state → ${state::class.simpleName}")
    }

    companion object {
        private const val TAG = "UiStateManager"
    }
}

/**
 * Sealed class representing all possible UI display states for a sensor channel.
 * Replaces the magic `Int` sentinel `99` and the ad-hoc `appState: Int` field.
 */
sealed class PpgUiState {
    object Default     : PpgUiState()
    object Running     : PpgUiState()
    object Stopped     : PpgUiState()
    object Paused      : PpgUiState()
    object Measuring   : PpgUiState()
    object Connected   : PpgUiState()
    object Connecting  : PpgUiState()
    object Disconnected: PpgUiState()

    /** Returns the localised string for this state from [R.string]. */
    fun label(context: Context): String = when (this) {
        is Default      -> context.getString(R.string.status_default)
        is Running      -> context.getString(R.string.status_running)
        is Stopped      -> context.getString(R.string.status_stopped)
        is Paused       -> context.getString(R.string.status_paused)
        is Measuring    -> context.getString(R.string.status_measuring)
        is Connected    -> context.getString(R.string.status_connected)
        is Connecting   -> context.getString(R.string.status_connecting)
        is Disconnected -> context.getString(R.string.status_disconnected)
    }
}
