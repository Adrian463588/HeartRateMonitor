package com.example.samplewearmobileapp

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.util.Log
import com.example.samplewearmobileapp.Constants.ECG_SAMPLE_RATE
import com.example.samplewearmobileapp.Constants.PPG_GREEN_SAMPLE_RATE
import com.example.samplewearmobileapp.Constants.PPG_IR_RED_SAMPLE_RATE
import com.example.samplewearmobileapp.models.EcgPlotArrays
import com.example.samplewearmobileapp.models.PpgPlotArrays
import com.example.samplewearmobileapp.models.PpgType
import com.example.samplewearmobileapp.utils.AppUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Handles all Storage Access Framework (SAF) CSV file operations for
 * ECG and PPG data export.
 *
 * **SRP:** This class owns *only* file-I/O concerns — it never touches the UI.
 * All UI feedback (Toast / AlertDialog) is the responsibility of the caller.
 *
 * **DRY:** The shared [openSafDocument] helper is the single point of truth
 * for SAF document creation; [writeEcgHeader] / [writePpgHeader] are the
 * single points of truth for CSV header writing.
 *
 * **Return contract:** Every public `suspend` function returns a [SaveResult]
 * describing success or failure so callers can decide how to surface it.
 *
 * **Thread-safety:** All I/O runs on [Dispatchers.IO]; callers need not
 * manage threads.
 *
 * **Usage:**
 * ```kotlin
 * lifecycleScope.launch {
 *     val result = dataSaver.saveEcgData("session1", arrays, metadata)
 *     showSaveResult(result)
 * }
 * ```
 */
class DataSaver(private val context: Context) {

    // =========================================================================
    // Public result type (SRP: I/O result only — no UI dependencies)
    // =========================================================================

    /**
     * Outcome of a single file-save operation.
     *
     * **OCP:** Callers switch on subtypes; adding new outcomes (e.g. `Skipped`)
     * does not require changing existing success/failure handling.
     */
    sealed class SaveResult {
        /**
         * The file was written successfully.
         *
         * @param fileName The final filename written to storage (including the
         *                 date suffix), useful for display in a confirmation message.
         */
        data class Success(val fileName: String) : SaveResult()

        /**
         * The file could not be written.
         *
         * @param fileName The filename that was attempted.
         * @param cause    The exception that caused the failure, or null if
         *                 the failure was a precondition (e.g. no tree URI set).
         */
        data class Failure(val fileName: String, val cause: Throwable? = null) : SaveResult()
    }

    // =========================================================================
    // Session metadata carrier
    // =========================================================================

    /**
     * Immutable snapshot of session metadata captured at recording stop time.
     */
    data class SessionMetadata(
        val stopTime: Date,
        val startTime: Date,
        val deviceId: String,
        val deviceName: String,
        val deviceFirmware: String,
        val deviceBatteryLevel: String,
        val deviceStopHr: String,
        val calculatedStopHr: String,
        val peakCount: Int,
        val appVersion: String
    ) {
        val durationMs: Long   get() = stopTime.time - startTime.time
        val durationSec: Double get() = durationMs / 1000.0
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Saves ECG data (values + timestamps + QRS peaks) to a CSV file in the
     * SAF-granted directory.
     *
     * @param filename  Base name from user input (e.g. "session1").
     * @param arrays    ECG samples, peak flags, and timestamps.
     * @param metadata  Recording session info.
     * @return [SaveResult.Success] with the written filename, or
     *         [SaveResult.Failure] describing the error.
     */
    suspend fun saveEcgData(
        filename: String,
        arrays: EcgPlotArrays,
        metadata: SessionMetadata
    ): SaveResult = withContext(Dispatchers.IO) {
        val treeUriStr = getTreeUriStr()
            ?: return@withContext SaveResult.Failure(
                fileName = "$filename-ECG",
                cause    = null
            )

        val df = SimpleDateFormat(FILE_DATE_FORMAT, Locale.US)
        val fileName = "${filename}_ECG_${df.format(metadata.stopTime)}.csv"
        val pfd = openSafDocument(treeUriStr, "text/csv", fileName)
            ?: return@withContext SaveResult.Failure(fileName)

        val sessionStartMs = metadata.startTime.time
        return@withContext try {
            FileWriter(pfd.fileDescriptor).use { fw ->
                PrintWriter(fw).use { out ->
                    out.writeEcgHeader(metadata, arrays.ecg.size)
                    for (i in arrays.ecg.indices) {
                        val elapsed = arrays.timestamp[i] - sessionStartMs
                        out.write(
                            String.format(
                                Locale.US, "%.3f,%d,%d,%s,%d,%d,%d\n",
                                arrays.ecg[i],
                                if (arrays.peaks[i]) 1 else 0,
                                arrays.timestamp[i],
                                TIMESTAMP_FORMAT.format(Date(arrays.timestamp[i])),
                                (elapsed / 60000L).toInt(),
                                ((elapsed % 60000L) / 1000L).toInt(),
                                (elapsed % 1000L).toInt()
                            )
                        )
                    }
                    out.flush()
                }
            }
            pfd.close()
            Log.d(TAG, "Wrote ECG: $fileName")
            SaveResult.Success(fileName)
        } catch (ex: Exception) {
            runCatching { pfd.close() }
            Log.e(TAG, "Error saving ECG: $fileName", ex)
            SaveResult.Failure(fileName, ex)
        }
    }

    /**
     * Saves PPG data (values + timestamps) to a CSV file.
     *
     * @param filename   Base name from user input.
     * @param arrays     PPG samples and timestamps.
     * @param ppgType    Channel identifier (Green / IR / Red).
     * @param metadata   Recording session info.
     * @return [SaveResult.Success] with the written filename, or
     *         [SaveResult.Failure] describing the error.
     */
    suspend fun savePpgData(
        filename: String,
        arrays: PpgPlotArrays,
        ppgType: PpgType,
        metadata: SessionMetadata
    ): SaveResult = withContext(Dispatchers.IO) {
        val treeUriStr = getTreeUriStr()
            ?: return@withContext SaveResult.Failure(
                fileName = "$filename-${ppgType.label()}",
                cause    = null
            )

        val df = SimpleDateFormat(FILE_DATE_FORMAT, Locale.US)
        val typeLabel  = ppgType.label()
        val sampleRate = ppgType.sampleRate()
        val fileName   = "$filename-$typeLabel-${df.format(metadata.stopTime)}.csv"
        val pfd = openSafDocument(treeUriStr, "text/csv", fileName)
            ?: return@withContext SaveResult.Failure(fileName)

        val sessionStartMs = metadata.startTime.time
        return@withContext try {
            FileWriter(pfd.fileDescriptor).use { fw ->
                PrintWriter(fw).use { out ->
                    out.writePpgHeader(metadata, typeLabel, sampleRate, arrays.ppg.size)
                    for (i in arrays.ppg.indices) {
                        val elapsed = arrays.timestamp[i] - sessionStartMs
                        out.write(
                            String.format(
                                Locale.US, "%d,%d,%s,%d,%d,%d\n",
                                arrays.ppg[i],
                                arrays.timestamp[i],
                                TIMESTAMP_FORMAT.format(Date(arrays.timestamp[i])),
                                (elapsed / 60000L).toInt(),
                                ((elapsed % 60000L) / 1000L).toInt(),
                                (elapsed % 1000L).toInt()
                            )
                        )
                    }
                    out.flush()
                }
            }
            pfd.close()
            Log.d(TAG, "Wrote $typeLabel: $fileName")
            SaveResult.Success(fileName)
        } catch (ex: Exception) {
            runCatching { pfd.close() }
            Log.e(TAG, "Error saving $typeLabel: $fileName", ex)
            SaveResult.Failure(fileName, ex)
        }
    }

    // =========================================================================
    // Package-private helpers (internal to DataSaver)
    // =========================================================================

    /**
     * Returns the SAF tree URI string stored in private preferences, or null
     * if none has been set.
     *
     * **SRP note:** No UI side-effects — the caller is responsible for
     * informing the user when the return value is null.
     */
    internal fun getTreeUriStr(): String? {
        val prefs = context.getSharedPreferences(
            context.packageName + "_preferences",
            Context.MODE_PRIVATE
        ).let {
            // Primary: app private prefs (written by SAF picker result)
            it.getString(Constants.PREF_TREE_URI, null)
        } ?: run {
            // Fallback: activity-scoped prefs (legacy path)
            (context as? android.app.Activity)
                ?.getPreferences(Context.MODE_PRIVATE)
                ?.getString(Constants.PREF_TREE_URI, null)
        }
        return prefs
    }

    /**
     * Single shared SAF document-creation helper.
     * DRY: eliminates the ~30-line boilerplate previously duplicated in every
     * save function.
     *
     * @return An open [ParcelFileDescriptor] for writing, or null on error.
     */
    private fun openSafDocument(
        treeUriStr: String,
        mimeType: String,
        fileName: String
    ): ParcelFileDescriptor? = try {
        val treeUri    = Uri.parse(treeUriStr)
        val docTreeUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        val docUri = DocumentsContract.createDocument(
            context.contentResolver, docTreeUri, mimeType, fileName
        ) ?: run {
            Log.e(TAG, "createDocument returned null for $fileName")
            return null
        }
        context.contentResolver.openFileDescriptor(docUri, "w")
    } catch (ex: Exception) {
        Log.e(TAG, "openSafDocument failed for $fileName", ex)
        null
    }

    // =========================================================================
    // PrintWriter extension helpers (DRY)
    // =========================================================================

    private fun PrintWriter.writeEcgHeader(meta: SessionMetadata, sampleCount: Int) {
        write("application=SamplingApp Version: ${meta.appVersion}\n")
        write("datatype=ECG\n")
        write("stoptime=${meta.stopTime}\n")
        write("duration=${String.format(Locale.US, "%.1f sec", meta.durationSec)}\n")
        write("samplescount=$sampleCount\n")
        write("samplingrate=$ECG_SAMPLE_RATE\n")
        write("stopdevicehr=${meta.deviceStopHr}\n")
        write("stopcalculatedhr=${meta.calculatedStopHr}\n")
        write("peakscount=${meta.peakCount}\n")
        write("devicename=${meta.deviceName}\n")
        write("deviceid=${meta.deviceId}\n")
        write("battery=${meta.deviceBatteryLevel}\n")
        write("firmware=${meta.deviceFirmware}\n")
    }

    private fun PrintWriter.writePpgHeader(
        meta: SessionMetadata,
        typeLabel: String,
        sampleRate: Double,
        sampleCount: Int
    ) {
        write("application=SamplingApp Version: ${meta.appVersion}\n")
        write("datatype=$typeLabel\n")
        write("stoptime=${meta.stopTime}\n")
        write("duration=${String.format(Locale.US, "%.1f sec", meta.durationSec)}\n")
        write("samplescount=$sampleCount\n")
        write("samplingrate=$sampleRate\n")
        write("stopcalculatedhr=${meta.calculatedStopHr}\n")
    }

    // =========================================================================
    // Extension helpers on PpgType (DRY)
    // =========================================================================

    private fun PpgType.label(): String = when (this) {
        PpgType.PPG_GREEN -> "PPG Green"
        PpgType.PPG_IR    -> "PPG IR"
        PpgType.PPG_RED   -> "PPG Red"
    }

    private fun PpgType.sampleRate(): Double = when (this) {
        PpgType.PPG_GREEN               -> PPG_GREEN_SAMPLE_RATE.toDouble()
        PpgType.PPG_IR, PpgType.PPG_RED -> PPG_IR_RED_SAMPLE_RATE.toDouble()
    }

    companion object {
        private const val TAG              = "DataSaver"
        private const val FILE_DATE_FORMAT = "yyyy-MM-dd_HH-mm"
        private val TIMESTAMP_FORMAT = SimpleDateFormat("dd MMM yyyy HH:mm:ss:SSS Z", Locale.US)
    }
}
