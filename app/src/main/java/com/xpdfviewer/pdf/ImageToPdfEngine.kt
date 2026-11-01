package com.xpdfviewer.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix as AndroidMatrix
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.xpdfviewer.data.ConverterResult
import com.xpdfviewer.data.ImageToPdfConfig
import com.xpdfviewer.data.PageOrientationPreset
import com.xpdfviewer.data.PageSizePreset
import com.xpdfviewer.data.SelectedImageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object ImageToPdfEngine {

    suspend fun convertImagesToPdf(
        context: Context,
        images: List<SelectedImageItem>,
        config: ImageToPdfConfig,
        destinationUri: Uri,
        outputFileName: String,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): ConverterResult = withContext(Dispatchers.IO) {
        if (images.isEmpty()) {
            return@withContext ConverterResult(
                success = false,
                errorMessage = "No images selected to convert"
            )
        }

        PDFBoxResourceLoader.init(context.applicationContext)
        val tempFile = File(context.cacheDir, "img2pdf_temp_${System.currentTimeMillis()}.pdf")
        var doc: PDDocument? = null
        var outputStream: OutputStream? = null

        try {
            doc = PDDocument()
            val totalImages = images.size

            for ((index, item) in images.withIndex()) {
                var bitmap: Bitmap? = null
                try {
                    context.contentResolver.openInputStream(item.uri)?.use { inStream ->
                        bitmap = BitmapFactory.decodeStream(inStream)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (bitmap == null) continue

                // Apply user rotation if any
                var processedBitmap = bitmap!!
                if (item.rotationDegrees % 360 != 0) {
                    val rotMatrix = AndroidMatrix().apply {
                        postRotate(item.rotationDegrees.toFloat())
                    }
                    processedBitmap = Bitmap.createBitmap(
                        bitmap!!,
                        0,
                        0,
                        bitmap!!.width,
                        bitmap!!.height,
                        rotMatrix,
                        true
                    )
                }

                val bmpWidth = processedBitmap.width.toFloat()
                val bmpHeight = processedBitmap.height.toFloat()

                // Calculate Page Rect
                val pageRect: PDRectangle = when (config.pageSize) {
                    PageSizePreset.FIT_TO_IMAGE -> {
                        PDRectangle(bmpWidth, bmpHeight)
                    }
                    PageSizePreset.A4, PageSizePreset.US_LETTER -> {
                        var baseW = config.pageSize.widthPt
                        var baseH = config.pageSize.heightPt

                        when (config.orientation) {
                            PageOrientationPreset.AUTO -> {
                                if (bmpWidth > bmpHeight) {
                                    // Landscape image
                                    PDRectangle(maxOf(baseW, baseH), minOf(baseW, baseH))
                                } else {
                                    // Portrait image
                                    PDRectangle(minOf(baseW, baseH), maxOf(baseW, baseH))
                                }
                            }
                            PageOrientationPreset.LANDSCAPE -> {
                                PDRectangle(maxOf(baseW, baseH), minOf(baseW, baseH))
                            }
                            PageOrientationPreset.PORTRAIT -> {
                                PDRectangle(minOf(baseW, baseH), maxOf(baseW, baseH))
                            }
                        }
                    }
                }

                val page = PDPage(pageRect)
                doc.addPage(page)

                val margin = config.margin.paddingPt
                val availW = (pageRect.width - (margin * 2f)).coerceAtLeast(10f)
                val availH = (pageRect.height - (margin * 2f)).coerceAtLeast(10f)

                // Scale image to fit within available bounds while maintaining aspect ratio
                val scale = minOf(availW / bmpWidth, availH / bmpHeight)
                val targetW = bmpWidth * scale
                val targetH = bmpHeight * scale

                // Center within page margins
                val drawX = margin + (availW - targetW) / 2f
                val drawY = margin + (availH - targetH) / 2f

                val qualityFloat = (config.quality.qualityInt / 100f).coerceIn(0.1f, 1.0f)
                val pdImage = JPEGFactory.createFromImage(doc, processedBitmap, qualityFloat)

                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.OVERWRITE, true, true).use { cs ->
                    cs.drawImage(pdImage, drawX, drawY, targetW, targetH)
                }

                if (processedBitmap != bitmap) {
                    processedBitmap.recycle()
                }
                bitmap?.recycle()

                onProgress(index + 1, totalImages)
            }

            FileOutputStream(tempFile).use { fos ->
                doc.save(fos)
            }

            outputStream = context.contentResolver.openOutputStream(destinationUri)
                ?: throw IllegalArgumentException("Cannot open destination output stream")
            tempFile.inputStream().use { fileIn ->
                fileIn.copyTo(outputStream)
            }

            ConverterResult(
                success = true,
                outputUri = destinationUri,
                outputFileName = outputFileName,
                processedCount = totalImages
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ConverterResult(
                success = false,
                errorMessage = e.localizedMessage ?: "Failed to generate PDF from images"
            )
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
            try { doc?.close() } catch (_: Exception) {}
            if (tempFile.exists()) tempFile.delete()
        }
    }
}

