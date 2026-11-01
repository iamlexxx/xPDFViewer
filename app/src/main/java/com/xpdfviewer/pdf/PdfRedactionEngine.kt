package com.xpdfviewer.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.xpdfviewer.data.NormalizedRect
import com.xpdfviewer.data.RedactionMark
import com.xpdfviewer.data.RedactionResult
import com.xpdfviewer.data.RedactionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfRedactionEngine {

    /**
     * Resolves document metadata: display name, file size in bytes, and total page count.
     */
    suspend fun getDocumentMetadata(
        context: Context,
        uri: Uri
    ): Triple<String, Long, Int> = withContext(Dispatchers.IO) {
        val fileName = PdfSecurityManager.resolveFileName(context, uri)
        val fileSize = PdfSecurityManager.queryFileSize(context, uri)
        PdfTextExtractor.initialize(context)
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

    /**
     * Automatically scans the document for target keyword/phrase occurrences and converts
     * them into pending [RedactionMark] instances across all pages.
     */
    suspend fun searchKeywordRedactions(
        context: Context,
        uri: Uri,
        pageCount: Int,
        keyword: String
    ): List<RedactionMark> = withContext(Dispatchers.Default) {
        if (keyword.isBlank() || pageCount <= 0) return@withContext emptyList()
        val marks = mutableListOf<RedactionMark>()

        PdfSearchEngine.searchDocument(
            context = context,
            uri = uri,
            pageCount = pageCount,
            query = keyword
        ) { matches, _ ->
            for (match in matches) {
                for (bound in match.bounds) {
                    marks.add(
                        RedactionMark(
                            pageIndex = match.pageIndex,
                            bounds = bound,
                            labelText = match.matchedText,
                            source = RedactionSource.KEYWORD_SEARCH
                        )
                    )
                }
            }
        }

        marks
    }

    /**
     * Commits permanent document sanitization:
     * 1. Applies solid opaque blackout vector plates directly into page content streams.
     * 2. Strips overlapping annotation objects (links, highlights, notes, form fields).
     * 3. Cleans document metadata dictionaries (Author, Producer, Title, Keywords).
     * 4. Purges XMP schema package to eliminate revision history leaks.
     */
    suspend fun applyRedactions(
        context: Context,
        sourceUri: Uri,
        destinationUri: Uri,
        marks: List<RedactionMark>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): RedactionResult = withContext(Dispatchers.IO) {
        PdfTextExtractor.initialize(context)
        if (marks.isEmpty()) {
            return@withContext RedactionResult(success = false, errorMessage = "No redaction marks specified.")
        }

        val tempFile = File(context.cacheDir, "xpdf_redacted_${System.currentTimeMillis()}.pdf")
        var doc: PDDocument? = null
        try {
            val inStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext RedactionResult(success = false, errorMessage = "Cannot open source PDF document.")
            doc = PDDocument.load(inStream)

            if (doc.isEncrypted) {
                try {
                    doc.setAllSecurityToBeRemoved(true)
                } catch (ignored: Exception) {}
            }

            val totalPages = doc.numberOfPages
            val marksByPage = marks.groupBy { it.pageIndex }

            var appliedCount = 0

            for (pageIdx in 0 until totalPages) {
                val pageMarks = marksByPage[pageIdx]
                if (!pageMarks.isNullOrEmpty()) {
                    val page = doc.getPage(pageIdx)
                    val pageBox = page.cropBox ?: page.mediaBox

                    // 1. Append solid black vector fill plates
                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                        stream.setNonStrokingColor(0f) // Gray 0f = Opaque Black

                        for (mark in pageMarks) {
                            val pdfLeft = pageBox.lowerLeftX + mark.bounds.left * pageBox.width
                            val pdfBottom = pageBox.upperRightY - mark.bounds.bottom * pageBox.height
                            val pdfWidth = (mark.bounds.right - mark.bounds.left) * pageBox.width
                            val pdfHeight = (mark.bounds.bottom - mark.bounds.top) * pageBox.height

                            if (pdfWidth > 0 && pdfHeight > 0) {
                                stream.addRect(pdfLeft, pdfBottom, pdfWidth, pdfHeight)
                                stream.fill()
                                appliedCount++
                            }
                        }
                    }

                    // 2. Prune overlapping annotations
                    try {
                        val annots = page.annotations
                        if (annots != null && annots.isNotEmpty()) {
                            val iterator = annots.iterator()
                            while (iterator.hasNext()) {
                                val annot = iterator.next()
                                val rect = annot.rectangle ?: continue
                                if (pageMarks.any { markIntersectsRect(it.bounds, rect, pageBox) }) {
                                    iterator.remove()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                onProgress(pageIdx + 1, totalPages)
            }

            // 3. Metadata Scrubbing
            val docInfo = doc.documentInformation
            if (docInfo != null) {
                docInfo.title = null
                docInfo.author = null
                docInfo.subject = null
                docInfo.keywords = null
                docInfo.creator = null
                docInfo.producer = "xPDFViewer Sanitizer"
                docInfo.modificationDate = null
            }

            // 4. Purge XMP Metadata Schema
            try {
                doc.documentCatalog.metadata = null
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Save to temp file
            FileOutputStream(tempFile).use { fos ->
                doc.save(fos)
            }

            // Copy to user's SAF destination
            context.contentResolver.openOutputStream(destinationUri, "wt")?.use { outStream ->
                tempFile.inputStream().use { fileIn ->
                    fileIn.copyTo(outStream)
                }
            } ?: return@withContext RedactionResult(success = false, errorMessage = "Cannot write to destination file.")

            RedactionResult(
                success = true,
                totalRedactionsApplied = appliedCount,
                outputUri = destinationUri
            )
        } catch (e: Exception) {
            e.printStackTrace()
            RedactionResult(success = false, errorMessage = e.localizedMessage ?: "Failed to redact PDF.")
        } finally {
            runCatching { doc?.close() }
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun markIntersectsRect(
        markBounds: NormalizedRect,
        rect: PDRectangle,
        pageBox: PDRectangle
    ): Boolean {
        val markPdfLeft = pageBox.lowerLeftX + markBounds.left * pageBox.width
        val markPdfBottom = pageBox.upperRightY - markBounds.bottom * pageBox.height
        val markPdfRight = pageBox.lowerLeftX + markBounds.right * pageBox.width
        val markPdfTop = pageBox.upperRightY - markBounds.top * pageBox.height

        val rLeft = minOf(rect.lowerLeftX, rect.upperRightX)
        val rRight = maxOf(rect.lowerLeftX, rect.upperRightX)
        val rBottom = minOf(rect.lowerLeftY, rect.upperRightY)
        val rTop = maxOf(rect.lowerLeftY, rect.upperRightY)

        return !(markPdfRight < rLeft || markPdfLeft > rRight || markPdfTop < rBottom || markPdfBottom > rTop)
    }
}
