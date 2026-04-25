package com.example.samplewearmobileapp.ui.main

/**
 * One-shot UI events emitted by [MainViewModel] and consumed exactly once by [MainActivity].
 *
 * Using a sealed class for events (rather than State) prevents events from being
 * "replayed" on recomposition/rotation — each event fires once and is gone.
 */
sealed class UiEvent {

    /** Show a short [Toast] with [message]. */
    data class ShowToast(val message: String) : UiEvent()

    /** Show a non-fatal error [AlertDialog] with [title] and [message]. */
    data class ShowError(val title: String, val message: String) : UiEvent()

    /** Prompt the user to enter a Polar device ID. */
    data object ShowDeviceIdDialog : UiEvent()

    /** Prompt the user to enter a filename before saving. */
    data class ShowSaveDialog(val saveType: SaveType) : UiEvent()

    /** Notify the user the data was saved successfully at [filePath]. */
    data class ShowSaveSuccess(val filePath: String) : UiEvent()
}

/** The type of data the user wants to save. */
enum class SaveType {
    ECG_DATA,
    PPG_GREEN_DATA,
    PPG_IR_DATA,
    PPG_RED_DATA,
    ALL_PPG,
    ALL
}
