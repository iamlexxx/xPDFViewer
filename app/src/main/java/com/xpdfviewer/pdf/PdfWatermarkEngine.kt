package com.xpdfviewer.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import com.xpdfviewer.data.PaginationAnchor
import com.xpdfviewer.data.PaginationConfig
import com.xpdfviewer.data.PaginationFormat
import com.xpdfviewer.data.WatermarkConfig
import com.xpdfviewer.data.WatermarkLayer
import com.xpdfviewer.data.WatermarkPageScope
import com.xpdfviewer.data.WatermarkResult
import com.xpdfviewer.data.WatermarkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

object PdfWatermarkEngine {

    suspend fun getDocumentMetadata(
        context: Context,
        uri: Uri
    ): Triple<String, Long, Int> = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val fileName = PdfSecurityManager.resolveFileName(context, uri)
        val fileSize = PdfSecurityManager.queryFileSize(context, uri)
        var pages = 0
        try {
            context.contentResolver.openInputStream(uri)?.use { inStream ->
                PDDocument.load(inStream).use { doc ->
                    pages = doc.numberOfPages
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Triple(fileName, fileSize, pages)
    }

    fun parsePageIndices(
        scope: WatermarkPageScope,
        customRangeText: String,
        totalPages: Int
    ): Set<Int> {
        if (totalPages <= 0) return emptySet()
        return when (scope) {
            WatermarkPageScope.ALL_PAGES -> (0 until totalPages).toSet()
            WatermarkPageScope.SKIP_FIRST -> (1 until totalPages).toSet()
            WatermarkPageScope.CUSTOM_RANGE -> {
                val result = mutableSetOf<Int>()
                val parts = customRangeText.split(",", ";", " ")
                for (rawPart in parts) {
                    val part = rawPart.trim()
                    if (part.isEmpty()) continue
                    if (part.contains("-")) {
                        val rangeParts = part.split("-").map { it.trim().toIntOrNull() }
                        if (rangeParts.size == 2 && rangeParts[0] != null && rangeParts[1] != null) {
                            val start = (rangeParts[0]!! - 1).coerceAtLeast(0)
                            val end = (rangeParts[1]!! - 1).coerceAtMost(totalPages - 1)
                            if (start <= end) {
                                for (p in start..end) result.add(p)
                            }
                        }
                    } else {
                        val singlePage = part.toIntOrNull()
                        if (singlePage != null) {
                            val idx = singlePage - 1
                            if (idx in 0 until totalPages) {
                                result.add(idx)
                            }
                        }
                    }
                }
                result
            }
        }
    }

    suspend fun applyWatermarkAndPagination(
        context: Context,
        sourceUri: Uri,
        destinationUri: Uri,
        outputFileName: String,
        watermarkConfig: WatermarkConfig,
        paginationConfig: PaginationConfig,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): WatermarkResult = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)

        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        var doc: PDDocument? = null
        val tempFile = File(context.cacheDir, "watermarked_temp_${System.currentTimeMillis()}.pdf")

        try {
            inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: throw IllegalArgumentException("Cannot open input PDF stream")
            doc = PDDocument.load(inputStream)
            val totalPages = doc.numberOfPages
            if (totalPages == 0) throw IllegalStateException("Document has 0 pages")

            val watermarkPages = parsePageIndices(
                watermarkConfig.pageScope,
                watermarkConfig.customRangeText,
                totalPages
            )

            val paginationPages = if (paginationConfig.isEnabled) {
                parsePageIndices(
                    paginationConfig.pageScope,
                    paginationConfig.customRangeText,
                    totalPages
                )
            } else {
                emptySet()
            }

            var watermarkBitmap: Bitmap? = null
            if (watermarkConfig.type == WatermarkType.IMAGE && watermarkConfig.imageUri != null) {
                try {
                    context.contentResolver.openInputStream(watermarkConfig.imageUri)?.use { imgIn ->
                        watermarkBitmap = BitmapFactory.decodeStream(imgIn)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val font = PDType1Font.HELVETICA_BOLD
            val paginationFont = PDType1Font.HELVETICA

            for (pageIdx in 0 until totalPages) {
                val page = doc.getPage(pageIdx)
                val cropBox = page.cropBox ?: page.mediaBox
                val boxWidth = cropBox.width
                val boxHeight = cropBox.height

                // 1. Watermark Pass
                if (pageIdx in watermarkPages) {
                    val appendMode = if (watermarkConfig.layer == WatermarkLayer.UNDER_CONTENT) {
                        PDPageContentStream.AppendMode.PREPEND
                    } else {
                        PDPageContentStream.AppendMode.APPEND
                    }

                    if (watermarkConfig.type == WatermarkType.TEXT && watermarkConfig.text.isNotBlank()) {
                        val text = watermarkConfig.text.trim()
                        val fontSize = watermarkConfig.fontSize
                        val textWidth = (font.getStringWidth(text) / 1000f) * fontSize
                        val textHeight = fontSize * 0.75f

                        val cx = cropBox.lowerLeftX + (boxWidth / 2f)
                        val cy = cropBox.lowerLeftY + (boxHeight / 2f)

                        val rad = Math.toRadians(watermarkConfig.rotation.degrees.toDouble())
                        val cos = Math.cos(rad).toFloat()
                        val sin = Math.sin(rad).toFloat()

                        // Calculate translation so (cx, cy) is text center
                        val tx = cx - (textWidth / 2f * cos - textHeight / 2f * sin)
                        val ty = cy - (textWidth / 2f * sin + textHeight / 2f * cos)

                        PDPageContentStream(doc, page, appendMode, true, true).use { cs ->
                            val gs = PDExtendedGraphicsState().apply {
                                nonStrokingAlphaConstant = watermarkConfig.opacity
                                strokingAlphaConstant = watermarkConfig.opacity
                            }
                            cs.setGraphicsStateParameters(gs)
                            cs.setNonStrokingColor(
                                watermarkConfig.color.r,
                                watermarkConfig.color.g,
                                watermarkConfig.color.b
                            )

                            cs.beginText()
                            cs.setFont(font, fontSize)
                            cs.setTextMatrix(Matrix(cos, sin, -sin, cos, tx, ty))
                            cs.showText(text)
                            cs.endText()
                        }
                    } else if (watermarkConfig.type == WatermarkType.IMAGE && watermarkBitmap != null) {
                        val bm = watermarkBitmap!!
                        val targetWidth = boxWidth * 0.45f
                        val targetHeight = (targetWidth / bm.width.toFloat()) * bm.height.toFloat()
                        val imgX = cropBox.lowerLeftX + (boxWidth - targetWidth) / 2f
                        val imgY = cropBox.lowerLeftY + (boxHeight - targetHeight) / 2f

                        val pdImage = LosslessFactory.createFromImage(doc, bm)
                        PDPageContentStream(doc, page, appendMode, true, true).use { cs ->
                            val gs = PDExtendedGraphicsState().apply {
                                nonStrokingAlphaConstant = watermarkConfig.opacity
                            }
                            cs.setGraphicsStateParameters(gs)
                            cs.drawImage(pdImage, imgX, imgY, targetWidth, targetHeight)
                        }
                    }
                }

                // 2. Pagination Pass
                if (paginationConfig.isEnabled && pageIdx in paginationPages) {
                    val pageNumber = paginationConfig.startNumber + pageIdx
                    val pageStr = formatPaginationText(
                        format = paginationConfig.format,
                        page = pageNumber,
                        total = totalPages
                    )

                    val fontSize = paginationConfig.fontSize
                    val textWidth = (paginationFont.getStringWidth(pageStr) / 1000f) * fontSize
                    val margin = paginationConfig.marginPadding

                    val anchor = paginationConfig.anchor
                    val posX = when {
                        anchor.isLeft -> cropBox.lowerLeftX + margin
                        anchor.isCenter -> cropBox.lowerLeftX + (boxWidth - textWidth) / 2f
                        else -> cropBox.upperRightX - margin - textWidth
                    }

                    val posY = if (anchor.isTop) {
                        cropBox.upperRightY - margin - fontSize
                    } else {
                        cropBox.lowerLeftY + margin
                    }

                    PDPageContentStream(
                        doc,
                        page,
                        PDPageContentStream.AppendMode.APPEND,
                        true,
                        true
                    ).use { cs ->
                        cs.setNonStrokingColor(
                            paginationConfig.color.r,
                            paginationConfig.color.g,
                            paginationConfig.color.b
                        )
                        cs.beginText()
                        cs.setFont(paginationFont, fontSize)
                        cs.newLineAtOffset(posX, posY)
                        cs.showText(pageStr)
                        cs.endText()
                    }
                }

                onProgress(pageIdx + 1, totalPages)
            }

            // Save to temp file first to prevent corruption
            FileOutputStream(tempFile).use { fos ->
                doc.save(fos)
            }

            // Copy to final SAF destination
            outputStream = context.contentResolver.openOutputStream(destinationUri)
                ?: throw IllegalArgumentException("Cannot open destination output stream")
            tempFile.inputStream().use { fileIn ->
                fileIn.copyTo(outputStream)
            }

            WatermarkResult(
                success = true,
                outputUri = destinationUri,
                outputFileName = outputFileName,
                processedPages = totalPages
            )
        } catch (e: Exception) {
            e.printStackTrace()
            WatermarkResult(
                success = false,
                errorMessage = e.localizedMessage ?: "Unknown error occurred"
            )
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
            try { doc?.close() } catch (_: Exception) {}
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun formatPaginationText(
        format: PaginationFormat,
        page: Int,
        total: Int
    ): String {
        return when (format) {
            PaginationFormat.PAGE_OF_TOTAL -> "Page $page of $total"
            PaginationFormat.PAGE_ONLY -> "$page"
            PaginationFormat.PAGE_PREFIX -> "Page $page"
            PaginationFormat.DASHES -> "- $page -"
            PaginationFormat.BATES -> String.format(Locale.US, "DOC-%05d", page)
        }
    }
}

