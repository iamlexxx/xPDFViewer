package com.xpdfviewer.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class PdfCompressionLevel(
    val title: String,
    val description: String,
    val estimatedSavings: String,
    val jpegQuality: Float,
    val maxDimension: Int
) {
    EXTREME(
        title = "Extreme Compression",
        description = "Smallest file size • Basic image quality • Best for strict email limits",
        estimatedSavings = "~70% Smaller",
        jpegQuality = 0.50f,
        maxDimension = 1100
    ),
    RECOMMENDED(
        title = "Recommended",
        description = "Optimal balance of sharpness and file size • Great for everyday documents",
        estimatedSavings = "~50% Smaller",
        jpegQuality = 0.72f,
        maxDimension = 1600
    ),
    LESS(
        title = "Less Compression",
        description = "High image clarity • Mild file size reduction • Best for photos and printing",
        estimatedSavings = "~30% Smaller",
        jpegQuality = 0.86f,
        maxDimension = 2200
    )
}

data class CompressResult(
    val success: Boolean,
    val originalSizeBytes: Long,
    val compressedSizeBytes: Long,
    val pageCount: Int,
    val errorMessage: String? = null
) {
    val savedBytes: Long get() = (originalSizeBytes - compressedSizeBytes).coerceAtLeast(0L)
    val savedPercent: Int
        get() = if (originalSizeBytes > 0) {
            (((originalSizeBytes - compressedSizeBytes).toFloat() / originalSizeBytes) * 100).toInt().coerceIn(0, 99)
        } else 0
}

object PdfCompressor {

    fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    cursor.getLong(sizeIndex)
                } else 0L
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun getFileName(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else "document.pdf"
            } ?: "document.pdf"
        } catch (e: Exception) {
            "document.pdf"
        }
    }

    fun getPageCount(context: Context, uri: Uri): Int {
        return try {
            PDFBoxResourceLoader.init(context.applicationContext)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                PDDocument.load(stream).use { doc ->
                    doc.numberOfPages
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    suspend fun compressPdf(
        context: Context,
        sourceUri: Uri,
        destinationUri: Uri,
        level: PdfCompressionLevel,
        onProgress: (currentPage: Int, totalPages: Int) -> Unit
    ): CompressResult = withContext(Dispatchers.IO) {
        val originalSize = getFileSize(context, sourceUri)
        val tempFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.pdf")

        try {
            PDFBoxResourceLoader.init(context.applicationContext)

            val doc = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                PDDocument.load(stream)
            } ?: return@withContext CompressResult(
                success = false,
                originalSizeBytes = originalSize,
                compressedSizeBytes = 0L,
                pageCount = 0,
                errorMessage = "Failed to open PDF document."
            )

            val totalPages = doc.numberOfPages

            doc.use { document ->
                for (pageIndex in 0 until totalPages) {
                    onProgress(pageIndex + 1, totalPages)
                    val page = document.getPage(pageIndex)
                    val resources = page.resources ?: continue
                    compressResources(document, resources, level.jpegQuality, level.maxDimension)
                }

                FileOutputStream(tempFile).use { out ->
                    document.save(out)
                }
            }

            val compressedSize = tempFile.length()

            // Stream to target destination URI
            context.contentResolver.openOutputStream(destinationUri, "wt")?.use { output ->
                tempFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: return@withContext CompressResult(
                success = false,
                originalSizeBytes = originalSize,
                compressedSizeBytes = 0L,
                pageCount = totalPages,
                errorMessage = "Failed to write compressed document to destination."
            )

            CompressResult(
                success = true,
                originalSizeBytes = originalSize,
                compressedSizeBytes = compressedSize,
                pageCount = totalPages
            )
        } catch (e: Exception) {
            e.printStackTrace()
            CompressResult(
                success = false,
                originalSizeBytes = originalSize,
                compressedSizeBytes = 0L,
                pageCount = 0,
                errorMessage = e.localizedMessage ?: "Unknown compression error"
            )
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun compressResources(
        doc: PDDocument,
        resources: PDResources,
        quality: Float,
        maxDimension: Int
    ) {
        try {
            val names = resources.xObjectNames ?: return
            for (name in names) {
                try {
                    val xObject = resources.getXObject(name)
                    if (xObject is PDImageXObject) {
                        val bitmap = xObject.image ?: continue
                        val (scaled, shouldRecycle) = downscaleBitmap(bitmap, maxDimension)
                        try {
                            val newXObject = JPEGFactory.createFromImage(doc, scaled, quality)
                            resources.put(name, newXObject)
                        } finally {
                            if (shouldRecycle && !scaled.isRecycled) {
                                scaled.recycle()
                            }
                        }
                    } else if (xObject is PDFormXObject) {
                        xObject.resources?.let { formRes ->
                            compressResources(doc, formRes, quality, maxDimension)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun downscaleBitmap(source: Bitmap, maxDim: Int): Pair<Bitmap, Boolean> {
        val w = source.width
        val h = source.height
        if (w <= maxDim && h <= maxDim) {
            return Pair(source, false)
        }

        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        val targetW = (w * ratio).toInt().coerceAtLeast(32)
        val targetH = (h * ratio).toInt().coerceAtLeast(32)

        val scaled = Bitmap.createScaledBitmap(source, targetW, targetH, true)
        return Pair(scaled, true)
    }
}
