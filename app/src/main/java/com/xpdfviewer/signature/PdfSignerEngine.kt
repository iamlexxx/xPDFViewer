package com.xpdfviewer.signature

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

object PdfSignerEngine {

    suspend fun signPdfDocument(
        context: Context,
        sourceUri: Uri,
        destinationUri: Uri,
        outputFileName: String,
        stamps: List<SignatureStamp>,
        password: String? = null
    ): SignResult = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)

        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        var doc: PDDocument? = null

        try {
            inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: throw IllegalArgumentException("Failed to open source PDF stream")
            doc = PDDocument.load(inputStream)

            val pageCount = doc.numberOfPages
            val stampsByPage = stamps.groupBy { it.pageIndex }

            for ((pageIdx, pageStamps) in stampsByPage) {
                if (pageIdx !in 0 until pageCount) continue

                val page = doc.getPage(pageIdx)
                val cropBox = page.cropBox ?: page.mediaBox
                val pageWidth = cropBox.width
                val pageHeight = cropBox.height

                // AppendMode.APPEND ensures existing PDF vector content, text, and graphics remain intact
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    for (stamp in pageStamps) {
                        val stampPdfW = stamp.normalizedWidth * pageWidth
                        val stampPdfH = stamp.normalizedHeight * pageHeight
                        val stampPdfX = cropBox.lowerLeftX + (stamp.normalizedX * pageWidth)
                        // PDF coordinate system has origin (0,0) at lower-left, while UI has (0,0) at top-left
                        val stampPdfY = cropBox.lowerLeftY + ((1.0f - stamp.normalizedY - stamp.normalizedHeight) * pageHeight)

                        val pdImage = LosslessFactory.createFromImage(doc, stamp.bitmap)
                        cs.drawImage(pdImage, stampPdfX, stampPdfY, stampPdfW, stampPdfH)
                    }
                }
            }

            // Optional password protection & encryption
            val isEncrypted = !password.isNullOrBlank()
            if (isEncrypted) {
                val accessPermission = AccessPermission().apply {
                    setCanPrint(true)
                    setCanExtractContent(true)
                }
                val policy = StandardProtectionPolicy(password, password, accessPermission).apply {
                    encryptionKeyLength = 128
                }
                doc.protect(policy)
            }

            outputStream = context.contentResolver.openOutputStream(destinationUri)
                ?: throw IllegalArgumentException("Failed to open destination output stream")
            doc.save(outputStream)

            val fileSizeBytes = queryFileSize(context, destinationUri)

            SignResult(
                outputUri = destinationUri,
                fileName = outputFileName,
                fileSizeBytes = fileSizeBytes,
                pageCount = pageCount,
                signaturesCount = stamps.size,
                isEncrypted = isEncrypted
            )
        } finally {
            try { doc?.close() } catch (ignored: Exception) {}
            try { inputStream?.close() } catch (ignored: Exception) {}
            try { outputStream?.close() } catch (ignored: Exception) {}
        }
    }

    private fun queryFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    cursor.getLong(sizeIndex)
                } else 0L
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
