package com.example.samplewearmobileapp.data.repository

import android.net.Uri

/**
 * Contract for all file I/O operations (CSV saving).
 *
 * Separates persistence concerns from the ViewModel and UI, allowing
 * the implementation to be swapped and the interface to be mocked in tests.
 */
interface FileRepository {

    /**
     * Saves sensor data to a CSV file in the user's chosen document tree.
     *
     * @param fileName  Desired file name without extension (e.g. "ECG_2026-04-25_13-00").
     * @param header    CSV header row (e.g. `"timestamp_ms,voltage_mV"`).
     * @param rows      Ordered list of CSV data rows to append after the header.
     * @param treeUri   The persisted [Uri] of the user-chosen folder (from Storage Access Framework).
     * @return [Result.success] on successful write, [Result.failure] on any IO error.
     */
    suspend fun saveCsv(
        fileName: String,
        header: String,
        rows: List<String>,
        treeUri: Uri
    ): Result<Unit>
}
