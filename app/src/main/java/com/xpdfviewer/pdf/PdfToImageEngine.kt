package com.xpdfviewer.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.xpdfviewer.data.ConverterResult
import com.xpdfviewer.data.ImageExportFormat
import com.xpdfviewer.data.PdfToImageConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object PdfToImageEngine {

    suspend fun convertPdfToImagesZip(
        context: Context,
        sourcePdfUri: Uri,
        config: PdfToImageConfig,
        destinationZipUri: Uri,
        outputZipName: String,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): ConverterResult = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "pdf2img_source_${System.currentTimeMillis()}.pdf")
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var outputStream: OutputStream? = null
        var zipOut: ZipOutputStream? = null

        try {
            // Copy source PDF to local cache so PdfRenderer can open seekable file descriptor
            context.contentResolver.openInputStream(sourcePdfUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalArgumentException("Cannot open source PDF input stream")

            pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val totalPages = renderer.pageCount

            val targetPages = PdfWatermarkEngine.parsePageIndices(
                config.pageScope,
                config.customRangeText,
                totalPages
            ).sorted()

            if (targetPages.isEmpty()) {
                throw IllegalStateException("No pages matching selected range to export")
            }

            outputStream = context.contentResolver.openOutputStream(destinationZipUri)
                ?: throw IllegalArgumentException("Cannot open destination ZIP output stream")
            zipOut = ZipOutputStream(outputStream)

            val baseName = outputZipName.substringBeforeLast(".zip", "page")
            val scale = config.dpi.scaleFactor
            val format = if (config.format == ImageExportFormat.JPEG) {
                Bitmap.CompressFormat.JPEG
            } else {
                Bitmap.CompressFormat.PNG
            }

            for ((step, pageIdx) in targetPages.withIndex()) {
                val page = renderer.openPage(pageIdx)
                val targetW = (page.width * scale).toInt().coerceAtLeast(1)
                val targetH = (page.height * scale).toInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                // Default to white paper background
                bitmap.eraseColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val byteStream = ByteArrayOutputStream()
                val quality = if (config.format == ImageExportFormat.JPEG) 90 else 100
                bitmap.compress(format, quality, byteStream)
                bitmap.recycle()

                val entryName = "${baseName}_page_${pageIdx + 1}.${config.format.extension}"
                val entry = ZipEntry(entryName)
                zipOut.putNextEntry(entry)
                zipOut.write(byteStream.toByteArray())
                zipOut.closeEntry()

                onProgress(step + 1, targetPages.size)
            }

            zipOut.finish()

            ConverterResult(
                success = true,
                outputUri = destinationZipUri,
                outputFileName = outputZipName,
                processedCount = targetPages.size
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ConverterResult(
                success = false,
                errorMessage = e.localizedMessage ?: "Failed to export PDF to images"
            )
        } finally {
            try { zipOut?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
            if (tempFile.exists()) tempFile.delete()
        }
    }
}

