package com.example.samplewearmobileapp.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter

/**
 * Production implementation of [FileRepository] using the Storage Access Framework (SAF).
 *
 * All file operations run on [Dispatchers.IO] — never blocks the main thread.
 *
 * **Why SAF?** On Android 11+ (API 30+), direct filesystem access via [java.io.File]
 * to external storage is restricted. SAF [DocumentFile] + [ContentResolver] is the
 * only correct approach for user-visible files on modern Android.
 *
 * @param context Application context for [ContentResolver] access.
 */
class FileRepositoryImpl(private val context: Context) : FileRepository {

    private val tag = "FileRepository"

    override suspend fun saveCsv(
        fileName: String,
        header: String,
        rows: List<String>,
        treeUri: Uri
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(fileName.isNotBlank()) { "fileName must not be blank" }
            require(rows.isNotEmpty()) { "rows must not be empty" }

            val treeDocument = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@runCatching Result.failure<Unit>(
                    IllegalStateException("Cannot open document tree at $treeUri")
                )

            val safeFileName = "${fileName.replace("[^a-zA-Z0-9_\\-.]".toRegex(), "_")}.csv"
            val csvFile = treeDocument.createFile("text/csv", safeFileName)
                ?: return@runCatching Result.failure<Unit>(
                    IllegalStateException("Failed to create file '$safeFileName'")
                )

            context.contentResolver.openOutputStream(csvFile.uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).buffered().use { writer ->
                    writer.write(header)
                    writer.newLine()
                    rows.forEach { row ->
                        writer.write(row)
                        writer.newLine()
                    }
                }
            } ?: return@runCatching Result.failure<Unit>(
                IllegalStateException("Cannot open output stream for '${csvFile.uri}'")
            )

            Log.i(tag, "Saved $safeFileName (${rows.size} rows)")
            Result.success(Unit)
        }.getOrElse { throwable ->
            Log.e(tag, "saveCsv failed for '$fileName': ${throwable.message}", throwable)
            Result.failure(throwable)
        }
    }
}
