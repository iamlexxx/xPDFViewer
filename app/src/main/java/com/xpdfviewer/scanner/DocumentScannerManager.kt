package com.xpdfviewer.scanner

import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import java.util.Locale

data class ScannedDocumentResult(
    val uri: Uri,
    val fileName: String,
    val pageCount: Int,
    val fileSizeFormatted: String
)

object DocumentScannerManager {

    fun startScan(
        activity: Activity,
        onSuccess: (IntentSender) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setGalleryImportAllowed(true)
            .setPageLimit(100)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
            )
            .build()

        val scanner = GmsDocumentScanning.getClient(options)

        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                onSuccess(intentSender)
            }
            .addOnFailureListener { exception ->
                onError(exception)
            }
    }

    /**
     * Saves the scanned document from temporary scanner cache directly into the user's chosen destination URI.
     * No automatic files are created beforehand.
     */
    fun saveScanToDestination(
        context: Context,
        sourceUri: Uri,
        destinationUri: Uri,
        fileName: String,
        pageCount: Int
    ): ScannedDocumentResult? {
        try {
            var bytesCopied = 0L
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesCopied += bytesRead
                    }
                    output.flush()
                }
            }

            val sizeMb = bytesCopied / (1024f * 1024f)
            val sizeFormatted = String.format(Locale.US, "%.1f MB", sizeMb)

            return ScannedDocumentResult(
                uri = destinationUri,
                fileName = fileName,
                pageCount = pageCount,
                fileSizeFormatted = sizeFormatted
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
