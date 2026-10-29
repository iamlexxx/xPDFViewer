package com.xpdfviewer.pdf

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import java.io.FileOutputStream

/**
 * Streams an existing PDF document to Android's native PrintManager.
 * Runs with low memory footprint using chunked byte streaming.
 */
class PdfPrintAdapter(
    private val context: Context,
    private val uri: Uri,
    private val documentName: String,
    private val pageCount: Int = PrintDocumentInfo.PAGE_COUNT_UNKNOWN
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }

        val sanitizedName = documentName.ifBlank { "Document" }.removeSuffix(".pdf")
        val info = PrintDocumentInfo.Builder("$sanitizedName.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(if (pageCount > 0) pageCount else PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()

        callback?.onLayoutFinished(info, newAttributes != oldAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        if (destination == null) {
            callback?.onWriteFailed("Missing destination file descriptor")
            return
        }

        if (cancellationSignal?.isCanceled == true) {
            callback?.onWriteCancelled()
            return
        }

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(destination.fileDescriptor).use { outputStream ->
                    val buffer = ByteArray(16384)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (cancellationSignal?.isCanceled == true) {
                            callback?.onWriteCancelled()
                            return
                        }
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    outputStream.flush()
                }
            } ?: run {
                callback?.onWriteFailed("Unable to open PDF input stream")
                return
            }

            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            e.printStackTrace()
            callback?.onWriteFailed("Error writing print stream: ${e.message}")
        }
    }
}
