package com.xpdfviewer.pdf

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.xpdfviewer.data.SplitResult
import com.xpdfviewer.data.SplitSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfSplitterEngine {

    /**
     * Inspects a document to resolve its display name, file size, and total page count.
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
     * Parses a free-form range string (e.g., "1-3, 5, 8-12") into a list of [SplitSegment]s.
     *
     * @param rangeStr User input text.
     * @param totalPages Total page count of the source document.
     * @return Pair of parsed segments and an optional user-facing validation warning.
     */
    fun parseRangeString(rangeStr: String, totalPages: Int): Pair<List<SplitSegment>, String?> {
        val trimmed = rangeStr.trim()
        if (trimmed.isEmpty()) {
            return emptyList<SplitSegment>() to "Enter page numbers or ranges (e.g. 1-3, 5, 8-10)."
        }

        if (totalPages <= 0) {
            return emptyList<SplitSegment>() to "Invalid document page count."
        }

        val parts = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) {
            return emptyList<SplitSegment>() to "Enter valid page numbers or ranges."
        }

        val segments = mutableListOf<SplitSegment>()
        val warnings = mutableListOf<String>()

        for (part in parts) {
            if (part.contains("-")) {
                val bounds = part.split("-").map { it.trim() }
                if (bounds.size == 2) {
                    val start = bounds[0].toIntOrNull()
                    val end = bounds[1].toIntOrNull()
                    if (start == null || end == null) {
                        warnings.add("Invalid range format: '$part'. Use e.g. 1-3.")
                        continue
                    }
                    val normalizedStart = minOf(start, end)
                    val normalizedEnd = maxOf(start, end)

                    if (normalizedStart < 1 || normalizedEnd > totalPages) {
                        warnings.add("Range $part exceeds document pages (1 to $totalPages).")
                    }

                    val clampedStart = normalizedStart.coerceIn(1, totalPages)
                    val clampedEnd = normalizedEnd.coerceIn(1, totalPages)

                    val pageIndices = (clampedStart..clampedEnd).map { it - 1 }
                    if (pageIndices.isNotEmpty()) {
                        val label = if (clampedStart == clampedEnd) "Page $clampedStart" else "Pages $clampedStart–$clampedEnd"
                        segments.add(SplitSegment(label = label, pageIndices = pageIndices))
                    }
                } else {
                    warnings.add("Invalid range segment: '$part'.")
                }
            } else {
                val page = part.toIntOrNull()
                if (page == null) {
                    warnings.add("Non-numeric page: '$part'.")
                } else if (page < 1 || page > totalPages) {
                    warnings.add("Page $page is outside document range (1 to $totalPages).")
                } else {
                    val pageIdx = page - 1
                    segments.add(SplitSegment(label = "Page $page", pageIndices = listOf(pageIdx)))
                }
            }
        }

        val warningMsg = if (segments.isEmpty()) {
            warnings.firstOrNull() ?: "No valid pages specified."
        } else if (warnings.isNotEmpty()) {
            warnings.first()
        } else {
            null
        }

        return segments to warningMsg
    }

    /**
     * Splits a document into equal chunks of [interval] pages.
     */
    fun generateFixedIntervalSegments(interval: Int, totalPages: Int): List<SplitSegment> {
        if (interval <= 0 || totalPages <= 0) return emptyList()
        val segments = mutableListOf<SplitSegment>()
        var startPage = 0
        while (startPage < totalPages) {
            val endPage = minOf(startPage + interval, totalPages)
            val pageIndices = (startPage until endPage).toList()
            val label = if (pageIndices.size == 1) {
                "Page ${startPage + 1}"
            } else {
                "Pages ${startPage + 1}–$endPage"
            }
            segments.add(SplitSegment(label = label, pageIndices = pageIndices))
            startPage = endPage
        }
        return segments
    }

    /**
     * Generates a 1-page individual segment for every page in the document.
     */
    fun generateBurstSegments(totalPages: Int): List<SplitSegment> {
        if (totalPages <= 0) return emptyList()
        return (0 until totalPages).map { pageIdx ->
            SplitSegment(
                label = "Page ${pageIdx + 1}",
                pageIndices = listOf(pageIdx)
            )
        }
    }

    /**
     * Extracts a list of page indices from [sourceUri] into a single new PDF saved at [destinationUri].
     */
    suspend fun extractPagesToSingleFile(
        context: Context,
        sourceUri: Uri,
        destinationUri: Uri,
        pageIndices: List<Int>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): SplitResult = withContext(Dispatchers.IO) {
        PdfTextExtractor.initialize(context)
        val tempFile = File(context.cacheDir, "xpdf_split_single_${System.currentTimeMillis()}.pdf")
        var srcDoc: PDDocument? = null
        try {
            val inStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext SplitResult(success = false, errorMessage = "Cannot open source PDF document.")
            srcDoc = PDDocument.load(inStream)

            if (srcDoc.isEncrypted) {
                try {
                    srcDoc.setAllSecurityToBeRemoved(true)
                } catch (ignored: Exception) {}
            }

            val validIndices = pageIndices.filter { it in 0 until srcDoc.numberOfPages }
            if (validIndices.isEmpty()) {
                return@withContext SplitResult(success = false, errorMessage = "No valid pages selected for extraction.")
            }

            val newDoc = PDDocument()
            newDoc.use { outDoc ->
                for ((count, pageIdx) in validIndices.withIndex()) {
                    val page = srcDoc.getPage(pageIdx)
                    outDoc.importPage(page)
                    onProgress(count + 1, validIndices.size)
                }

                FileOutputStream(tempFile).use { fos ->
                    outDoc.save(fos)
                }
            }

            // Copy temp file to user's destination SAF Uri
            context.contentResolver.openOutputStream(destinationUri, "wt")?.use { outStream ->
                tempFile.inputStream().use { fileIn ->
                    fileIn.copyTo(outStream)
                }
            } ?: return@withContext SplitResult(success = false, errorMessage = "Unable to write to destination file.")

            SplitResult(
                success = true,
                createdFileCount = 1,
                outputFileUri = destinationUri,
                createdFiles = listOf("Extracted_${validIndices.size}_pages.pdf")
            )
        } catch (e: Exception) {
            e.printStackTrace()
            SplitResult(success = false, errorMessage = e.localizedMessage ?: "Failed to extract PDF pages.")
        } finally {
            runCatching { srcDoc?.close() }
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    /**
     * Splits [sourceUri] into multiple PDF files saved inside the SAF directory tree chosen by user.
     *
     * @param treeUri SAF Tree URI returned by OpenDocumentTree.
     * @param segments List of segments to output.
     * @param filePrefix Base prefix for generated files (e.g. "Report_Part_").
     * @param onProgress Callback invoked per generated file: (processedCount, totalCount, currentFileName).
     */
    suspend fun splitToDirectory(
        context: Context,
        sourceUri: Uri,
        treeUri: Uri,
        segments: List<SplitSegment>,
        filePrefix: String,
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): SplitResult = withContext(Dispatchers.IO) {
        PdfTextExtractor.initialize(context)
        if (segments.isEmpty()) {
            return@withContext SplitResult(success = false, errorMessage = "No split segments defined.")
        }

        var srcDoc: PDDocument? = null
        val createdUris = mutableListOf<Uri>()
        val createdNames = mutableListOf<String>()

        try {
            val inStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext SplitResult(success = false, errorMessage = "Cannot open source PDF document.")
            srcDoc = PDDocument.load(inStream)

            if (srcDoc.isEncrypted) {
                try {
                    srcDoc.setAllSecurityToBeRemoved(true)
                } catch (ignored: Exception) {}
            }

            val parentDocUri = if (DocumentsContract.isDocumentUri(context, treeUri)) {
                treeUri
            } else {
                DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri)
                )
            }

            val totalSegments = segments.size
            val cleanPrefix = if (filePrefix.isBlank()) "document" else filePrefix.trim()

            for ((index, segment) in segments.withIndex()) {
                val fileName = "${cleanPrefix}_part_${index + 1}.pdf"
                val newFileUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    parentDocUri,
                    "application/pdf",
                    fileName
                ) ?: return@withContext SplitResult(
                    success = false,
                    createdFileCount = createdNames.size,
                    createdFiles = createdNames,
                    errorMessage = "Failed to create '$fileName' in destination folder."
                )

                val tempFile = File(context.cacheDir, "xpdf_part_${System.currentTimeMillis()}_${index}.pdf")
                try {
                    val partDoc = PDDocument()
                    partDoc.use { pDoc ->
                        for (pageIdx in segment.pageIndices) {
                            if (pageIdx in 0 until srcDoc.numberOfPages) {
                                pDoc.importPage(srcDoc.getPage(pageIdx))
                            }
                        }
                        FileOutputStream(tempFile).use { fos ->
                            pDoc.save(fos)
                        }
                    }

                    context.contentResolver.openOutputStream(newFileUri, "wt")?.use { outStream ->
                        tempFile.inputStream().use { fileIn ->
                            fileIn.copyTo(outStream)
                        }
                    } ?: return@withContext SplitResult(
                        success = false,
                        createdFileCount = createdNames.size,
                        createdFiles = createdNames,
                        errorMessage = "Failed to write data to '$fileName'."
                    )

                    createdUris.add(newFileUri)
                    createdNames.add(fileName)
                    onProgress(index + 1, totalSegments, fileName)
                } finally {
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                }
            }

            SplitResult(
                success = true,
                createdFileCount = createdNames.size,
                outputDirectoryUri = treeUri,
                outputFileUri = createdUris.firstOrNull(),
                createdFiles = createdNames
            )
        } catch (e: Exception) {
            e.printStackTrace()
            SplitResult(
                success = false,
                createdFileCount = createdNames.size,
                createdFiles = createdNames,
                errorMessage = e.localizedMessage ?: "Error during PDF split."
            )
        } finally {
            runCatching { srcDoc?.close() }
        }
    }
}
