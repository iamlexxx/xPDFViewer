package com.xpdfviewer.scanner

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.util.Locale
import java.util.UUID

data class DocumentScanPage(
    val id: String = UUID.randomUUID().toString(),
    val originalBitmap: Bitmap,
    val uncroppedBitmap: Bitmap? = null,
    val rotationDegrees: Int = 0,
    val filterMode: ScanFilterMode = ScanFilterMode.ORIGINAL,
    val isEnhanced: Boolean = false,
    val isCleaned: Boolean = false
) {
    fun getProcessedBitmap(): Bitmap {
        val rotated = DocumentImageProcessor.rotateBitmap(originalBitmap, rotationDegrees.toFloat())
        return DocumentImageProcessor.applyFilter(rotated, filterMode, isEnhanced, isCleaned)
    }
}

object ScannedPdfCreator {

    fun generatePdfToDestination(
        context: Context,
        pages: List<DocumentScanPage>,
        destinationUri: Uri,
        fileName: String
    ): ScannedDocumentResult? {
        if (pages.isEmpty()) return null

        try {
            PDFBoxResourceLoader.init(context.applicationContext)
            val doc = PDDocument()

            for (scanPage in pages) {
                val processedBitmap = scanPage.getProcessedBitmap()
                val pdImage = JPEGFactory.createFromImage(doc, processedBitmap, 0.88f)

                val imgWidth = pdImage.width.toFloat()
                val imgHeight = pdImage.height.toFloat()
                val pageRect = PDRectangle(imgWidth, imgHeight)
                val page = PDPage(pageRect)
                doc.addPage(page)

                PDPageContentStream(doc, page).use { contentStream ->
                    contentStream.drawImage(pdImage, 0f, 0f, imgWidth, imgHeight)
                }
            }

            context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                doc.save(output)
            }
            doc.close()

            var bytesWritten = 0L
            try {
                context.contentResolver.openFileDescriptor(destinationUri, "r")?.use { pfd ->
                    bytesWritten = pfd.statSize
                }
            } catch (ignored: Exception) {}

            val sizeMb = bytesWritten / (1024f * 1024f)
            val sizeFormatted = String.format(Locale.US, "%.1f MB", if (sizeMb > 0.05f) sizeMb else 0.1f)

            return ScannedDocumentResult(
                uri = destinationUri,
                fileName = fileName,
                pageCount = pages.size,
                fileSizeFormatted = sizeFormatted
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
