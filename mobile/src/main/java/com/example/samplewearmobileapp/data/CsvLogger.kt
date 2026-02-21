package com.example.samplewearmobileapp.data

import android.os.Environment
import android.util.Log
import com.example.samplewearmobileapp.sensor.EcgSample
import com.example.samplewearmobileapp.sensor.PpgChannel
import com.example.samplewearmobileapp.sensor.PpgSample
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Streaming CSV Logger that writes sensor data line-by-line
 * as it arrives, preventing data loss on unexpected termination.
 *
 * Each recording session creates a timestamped directory under
 * Documents/HeartMonitor/{sessionId}/ with separate CSV files
 * for each data channel (ECG, PPG Green, PPG IR, PPG Red).
 *
 * Thread-safe: all writes go through Dispatchers.IO.
 */
class CsvLogger(
    private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "CsvLogger"
        private const val BASE_DIR = "HeartMonitor"
        private val TIMESTAMP_FORMAT = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSS", Locale.US
        )
    }

    private var sessionDir: File? = null
    private var ecgWriter: BufferedWriter? = null
    private var ppgGreenWriter: BufferedWriter? = null
    private var ppgIrWriter: BufferedWriter? = null
    private var ppgRedWriter: BufferedWriter? = null

    // Sample counters for data integrity
    var ecgSampleCount: Long = 0L; private set
    var ppgGreenSampleCount: Long = 0L; private set
    var ppgIrSampleCount: Long = 0L; private set
    var ppgRedSampleCount: Long = 0L; private set

    /**
     * Initializes a new recording session.
     * Creates the session directory and opens all CSV files with headers.
     *
     * @param sessionId Unique identifier for this session (ISO timestamp).
     * @param participantId Researcher-assigned participant identifier.
     * @param conditionLabel Current study condition (e.g., "Baseline", "Stress").
     * @param deviceId Polar device ID.
     * @param ecgSampleRate ECG sample rate in Hz.
     * @param ppgGreenRate PPG Green sample rate in Hz.
     * @param ppgIrRedRate PPG IR/Red sample rate in Hz.
     */
    suspend fun startSession(
        sessionId: String,
        participantId: String,
        conditionLabel: String,
        deviceId: String,
        ecgSampleRate: Double,
        ppgGreenRate: Double,
        ppgIrRedRate: Double
    ) = withContext(ioDispatcher) {
        val documentsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOCUMENTS
        )
        sessionDir = File(documentsDir, "$BASE_DIR/$sessionId").also { it.mkdirs() }

        Log.i(TAG, "Session directory created: ${sessionDir?.absolutePath}")

        // Reset counters
        ecgSampleCount = 0L
        ppgGreenSampleCount = 0L
        ppgIrSampleCount = 0L
        ppgRedSampleCount = 0L

        // ECG CSV
        ecgWriter = openCsvFile("${sessionId}_ECG.csv").also { w ->
            writeMetadataHeader(w, "ECG", participantId, conditionLabel,
                deviceId, ecgSampleRate)
            w.write("timestamp_unix_ms,timestamp_human,ecg_uv,is_peak")
            w.newLine()
            w.flush()
        }

        // PPG Green CSV
        ppgGreenWriter = openCsvFile("${sessionId}_PPG_Green.csv").also { w ->
            writeMetadataHeader(w, "PPG_Green", participantId, conditionLabel,
                deviceId, ppgGreenRate)
            w.write("timestamp_unix_ms,timestamp_human,ppg_value")
            w.newLine()
            w.flush()
        }

        // PPG IR CSV
        ppgIrWriter = openCsvFile("${sessionId}_PPG_IR.csv").also { w ->
            writeMetadataHeader(w, "PPG_IR", participantId, conditionLabel,
                deviceId, ppgIrRedRate)
            w.write("timestamp_unix_ms,timestamp_human,ppg_value")
            w.newLine()
            w.flush()
        }

        // PPG Red CSV
        ppgRedWriter = openCsvFile("${sessionId}_PPG_Red.csv").also { w ->
            writeMetadataHeader(w, "PPG_Red", participantId, conditionLabel,
                deviceId, ppgIrRedRate)
            w.write("timestamp_unix_ms,timestamp_human,ppg_value")
            w.newLine()
            w.flush()
        }

        Log.i(TAG, "All CSV files opened for session: $sessionId")
    }

    /**
     * Appends a single ECG sample to the ECG CSV file.
     */
    suspend fun writeEcgSample(sample: EcgSample) = withContext(ioDispatcher) {
        ecgWriter?.let { w ->
            w.write(String.format(
                Locale.US, "%d,%s,%.3f,%d",
                sample.timestampMs,
                TIMESTAMP_FORMAT.format(Date(sample.timestampMs)),
                sample.microVolts,
                if (sample.isPeak) 1 else 0
            ))
            w.newLine()
            ecgSampleCount++

            // Flush every 100 samples for a balance of performance vs. safety
            if (ecgSampleCount % 100 == 0L) {
                w.flush()
            }
        }
    }

    /**
     * Appends a single PPG sample to the appropriate PPG CSV file.
     */
    suspend fun writePpgSample(sample: PpgSample) = withContext(ioDispatcher) {
        val writer = when (sample.channel) {
            PpgChannel.GREEN -> ppgGreenWriter
            PpgChannel.IR -> ppgIrWriter
            PpgChannel.RED -> ppgRedWriter
        }

        writer?.let { w ->
            w.write(String.format(
                Locale.US, "%d,%s,%d",
                sample.timestampMs,
                TIMESTAMP_FORMAT.format(Date(sample.timestampMs)),
                sample.value
            ))
            w.newLine()

            when (sample.channel) {
                PpgChannel.GREEN -> ppgGreenSampleCount++
                PpgChannel.IR -> ppgIrSampleCount++
                PpgChannel.RED -> ppgRedSampleCount++
            }

            // Flush periodically
            val count = when (sample.channel) {
                PpgChannel.GREEN -> ppgGreenSampleCount
                PpgChannel.IR -> ppgIrSampleCount
                PpgChannel.RED -> ppgRedSampleCount
            }
            if (count % 100 == 0L) {
                w.flush()
            }
        }
    }

    /**
     * Flushes all open files to disk.
     * Call on pause to ensure no data loss.
     */
    suspend fun flush() = withContext(ioDispatcher) {
        try {
            ecgWriter?.flush()
            ppgGreenWriter?.flush()
            ppgIrWriter?.flush()
            ppgRedWriter?.flush()
            Log.i(TAG, "All files flushed. ECG=$ecgSampleCount, " +
                    "Green=$ppgGreenSampleCount, IR=$ppgIrSampleCount, Red=$ppgRedSampleCount")
        } catch (e: Exception) {
            Log.e(TAG, "Error flushing files", e)
        }
    }

    /**
     * Closes all CSV files and writes final metadata.
     * Call on stop recording or session end.
     *
     * @return The path to the session directory, or null if no session was active.
     */
    suspend fun closeSession(): String? = withContext(ioDispatcher) {
        val dirPath = sessionDir?.absolutePath
        try {
            closeWriter(ecgWriter, "ECG")
            closeWriter(ppgGreenWriter, "PPG_Green")
            closeWriter(ppgIrWriter, "PPG_IR")
            closeWriter(ppgRedWriter, "PPG_Red")
        } finally {
            ecgWriter = null
            ppgGreenWriter = null
            ppgIrWriter = null
            ppgRedWriter = null
        }

        Log.i(TAG, "Session closed. Total samples — " +
                "ECG: $ecgSampleCount, Green: $ppgGreenSampleCount, " +
                "IR: $ppgIrSampleCount, Red: $ppgRedSampleCount")
        Log.i(TAG, "Files saved to: $dirPath")
        dirPath
    }

    // ===== Private Helpers =====

    private fun openCsvFile(fileName: String): BufferedWriter {
        val file = File(sessionDir, fileName)
        return BufferedWriter(FileWriter(file, true))
    }

    private fun writeMetadataHeader(
        writer: BufferedWriter,
        dataType: String,
        participantId: String,
        conditionLabel: String,
        deviceId: String,
        sampleRate: Double
    ) {
        writer.write("# datatype=$dataType")
        writer.newLine()
        writer.write("# participant=$participantId")
        writer.newLine()
        writer.write("# condition=$conditionLabel")
        writer.newLine()
        writer.write("# device=$deviceId")
        writer.newLine()
        writer.write("# samplerate=$sampleRate")
        writer.newLine()
        writer.write("# starttime=${TIMESTAMP_FORMAT.format(Date())}")
        writer.newLine()
    }

    private fun closeWriter(writer: BufferedWriter?, label: String) {
        try {
            writer?.flush()
            writer?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing $label writer", e)
        }
    }
}
