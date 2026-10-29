package com.xpdfviewer.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfDocumentModifier {

    suspend fun addBlankPage(
        context: Context,
        sourceUri: Uri,
        destinationUri: Uri = sourceUri,
        afterPageIndex: Int
    ): Boolean = withContext(Dispatchers.IO) {
        PdfTextExtractor.initialize(context)
        val tempFile = File(context.cacheDir, "xpdf_edit_${System.currentTimeMillis()}.pdf")
        try {
            val doc = context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                PDDocument.load(inputStream)
            } ?: return@withContext false

            doc.use { document ->
                val currentPage = if (afterPageIndex in 0 until document.numberOfPages) {
                    document.getPage(afterPageIndex)
                } else null

                val mediaBox = currentPage?.mediaBox ?: PDRectangle.A4
                val newPage = PDPage(mediaBox)

                if (currentPage != null) {
                    document.pages.insertAfter(newPage, currentPage)
                } else {
                    document.addPage(newPage)
                }

                FileOutputStream(tempFile).use { outputStream ->
                    document.save(outputStream)
                }
            }

            // Write to destination content URI
            context.contentResolver.openOutputStream(destinationUri, "wt")?.use { outputStream ->
                tempFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return@withContext false

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    suspend fun deletePage(
        context: Context,
        sourceUri: Uri,
        destinationUri: Uri = sourceUri,
        pageIndex: Int
    ): Boolean = withContext(Dispatchers.IO) {
        PdfTextExtractor.initialize(context)
        val tempFile = File(context.cacheDir, "xpdf_edit_${System.currentTimeMillis()}.pdf")
        try {
            val doc = context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                PDDocument.load(inputStream)
            } ?: return@withContext false

            doc.use { document ->
                if (document.numberOfPages <= 1) {
                    return@withContext false
                }
                if (pageIndex !in 0 until document.numberOfPages) {
                    return@withContext false
                }

                document.removePage(pageIndex)

                FileOutputStream(tempFile).use { outputStream ->
                    document.save(outputStream)
                }
            }

            // Write to destination content URI
            context.contentResolver.openOutputStream(destinationUri, "wt")?.use { outputStream ->
                tempFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return@withContext false

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    suspend fun extractPage(
        context: Context,
        sourceUri: Uri,
        destinationUri: Uri = sourceUri,
        pageIndex: Int
    ): Boolean = withContext(Dispatchers.IO) {
        PdfTextExtractor.initialize(context)
        val tempFile = File(context.cacheDir, "xpdf_extract_${System.currentTimeMillis()}.pdf")
        var srcDoc: PDDocument? = null
        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return@withContext false
            srcDoc = PDDocument.load(inputStream)

            if (pageIndex !in 0 until srcDoc.numberOfPages) {
                return@withContext false
            }

            if (srcDoc.isEncrypted) {
                try {
                    srcDoc.setAllSecurityToBeRemoved(true)
                } catch (ignored: Exception) {}
            }

            val newDoc = PDDocument()
            newDoc.use { extractedDoc ->
                val pageToKeep = srcDoc.getPage(pageIndex)
                extractedDoc.importPage(pageToKeep)

                FileOutputStream(tempFile).use { outputStream ->
                    extractedDoc.save(outputStream)
                }
            }

            // Write to destination content URI
            context.contentResolver.openOutputStream(destinationUri, "wt")?.use { outputStream ->
                tempFile.inputStream().use { fileInput ->
                    fileInput.copyTo(outputStream)
                }
            } ?: return@withContext false

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            runCatching { srcDoc?.close() }
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    suspend fun insertPdfDocument(
        context: Context,
        sourceUri: Uri,
        insertUri: Uri,
        destinationUri: Uri = sourceUri,
        afterPageIndex: Int
    ): Boolean = withContext(Dispatchers.IO) {
        PdfTextExtractor.initialize(context)
        val tempFile = File(context.cacheDir, "xpdf_insert_${System.currentTimeMillis()}.pdf")
        val openStreams = mutableListOf<java.io.InputStream>()
        var sourceDoc: PDDocument? = null
        var insertDoc: PDDocument? = null
        try {
            val srcStream = context.contentResolver.openInputStream(sourceUri) ?: return@withContext false
            openStreams.add(srcStream)
            sourceDoc = PDDocument.load(srcStream)

            val insStream = context.contentResolver.openInputStream(insertUri) ?: return@withContext false
            openStreams.add(insStream)
            insertDoc = PDDocument.load(insStream)

            val totalInsertPages = insertDoc.numberOfPages
            if (totalInsertPages == 0) return@withContext false

            val targetPageCount = sourceDoc.numberOfPages
            var currentAnchorPage = if (afterPageIndex in 0 until targetPageCount) {
                sourceDoc.getPage(afterPageIndex)
            } else null

            for (i in 0 until totalInsertPages) {
                val pageToImport = insertDoc.getPage(i)
                val importedPage = sourceDoc.importPage(pageToImport)

                // If inserting in the middle, move importedPage after currentAnchorPage
                if (currentAnchorPage != null && afterPageIndex < targetPageCount - 1) {
                    sourceDoc.pages.remove(importedPage)
                    sourceDoc.pages.insertAfter(importedPage, currentAnchorPage)
                    currentAnchorPage = importedPage
                } else if (currentAnchorPage != null) {
                    currentAnchorPage = importedPage
                }
            }

            FileOutputStream(tempFile).use { outputStream ->
                sourceDoc.save(outputStream)
            }

            // Write to destination content URI
            context.contentResolver.openOutputStream(destinationUri, "wt")?.use { outputStream ->
                tempFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return@withContext false

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            runCatching { sourceDoc?.close() }
            runCatching { insertDoc?.close() }
            openStreams.forEach { runCatching { it.close() } }
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    /**
     * Merges multiple PDF documents from [sourceUris] in order into a single cached PDF file.
     * Returns Result containing the merged File and the total page count.
     */
    suspend fun mergePdfDocumentsToCache(
        context: Context,
        sourceUris: List<Uri>,
        cacheFileName: String = "merged_doc_${System.currentTimeMillis()}.pdf"
    ): Result<Pair<File, Int>> = withContext(Dispatchers.IO) {
        if (sourceUris.size < 2) {
            return@withContext Result.failure(IllegalArgumentException("At least 2 documents are required to merge"))
        }

        PdfTextExtractor.initialize(context)
        val targetFile = File(context.cacheDir, cacheFileName)
        val loadedSourceDocs = mutableListOf<PDDocument>()
        val openInputStreams = mutableListOf<java.io.InputStream>()

        try {
            val targetDoc = PDDocument()
            var totalPages = 0

            targetDoc.use { mergedDoc ->
                for (uri in sourceUris) {
                    val inputStream = context.contentResolver.openInputStream(uri) ?: continue
                    openInputStreams.add(inputStream)
                    val srcDoc = PDDocument.load(inputStream)
                    loadedSourceDocs.add(srcDoc)

                    if (srcDoc.isEncrypted) {
                        try {
                            srcDoc.setAllSecurityToBeRemoved(true)
                        } catch (ignored: Exception) {}
                    }

                    val count = srcDoc.numberOfPages
                    for (i in 0 until count) {
                        mergedDoc.importPage(srcDoc.getPage(i))
                    }
                    totalPages += count
                }

                if (totalPages == 0) {
                    return@withContext Result.failure(IllegalStateException("No pages could be extracted from selected documents"))
                }

                FileOutputStream(targetFile).use { fos ->
                    mergedDoc.save(fos)
                }
            }

            Result.success(Pair(targetFile, totalPages))
        } catch (e: Exception) {
            e.printStackTrace()
            if (targetFile.exists()) targetFile.delete()
            Result.failure(e)
        } finally {
            // Close all source documents and streams ONLY after mergedDoc.save() has fully finished
            for (doc in loadedSourceDocs) {
                runCatching { doc.close() }
            }
            for (stream in openInputStreams) {
                runCatching { stream.close() }
            }
        }
    }

    /**
     * Merges multiple PDF documents from [sourceUris] in order directly into a destination URI.
     * Returns Result containing the total page count.
     */
    suspend fun mergePdfDocumentsToDestination(
        context: Context,
        sourceUris: List<Uri>,
        destinationUri: Uri
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (sourceUris.size < 2) {
            return@withContext Result.failure(IllegalArgumentException("At least 2 documents are required to merge"))
        }

        PdfTextExtractor.initialize(context)
        val tempFile = File(context.cacheDir, "xpdf_merge_${System.currentTimeMillis()}.pdf")
        val loadedSourceDocs = mutableListOf<PDDocument>()
        val openInputStreams = mutableListOf<java.io.InputStream>()

        try {
            val targetDoc = PDDocument()
            var totalPages = 0

            targetDoc.use { mergedDoc ->
                for (uri in sourceUris) {
                    val inputStream = context.contentResolver.openInputStream(uri) ?: continue
                    openInputStreams.add(inputStream)
                    val srcDoc = PDDocument.load(inputStream)
                    loadedSourceDocs.add(srcDoc)

                    if (srcDoc.isEncrypted) {
                        try {
                            srcDoc.setAllSecurityToBeRemoved(true)
                        } catch (ignored: Exception) {}
                    }

                    val count = srcDoc.numberOfPages
                    for (i in 0 until count) {
                        mergedDoc.importPage(srcDoc.getPage(i))
                    }
                    totalPages += count
                }

                if (totalPages == 0) {
                    return@withContext Result.failure(IllegalStateException("No pages could be extracted from selected documents"))
                }

                FileOutputStream(tempFile).use { fos ->
                    mergedDoc.save(fos)
                }
            }

            // Write to destination content URI
            val outputStream = context.contentResolver.openOutputStream(destinationUri, "wt")
                ?: return@withContext Result.failure(IllegalStateException("Could not write to destination file"))

            outputStream.use { fos ->
                tempFile.inputStream().use { fis ->
                    fis.copyTo(fos)
                }
            }

            Result.success(totalPages)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            for (doc in loadedSourceDocs) {
                runCatching { doc.close() }
            }
            for (stream in openInputStreams) {
                runCatching { stream.close() }
            }
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    /**
     * Copies a cached merged PDF file to a destination URI (e.g. user selected file via SAF).
     */
    suspend fun copyFileToUri(
        context: Context,
        sourceFile: File,
        destinationUri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val outputStream = context.contentResolver.openOutputStream(destinationUri, "wt")
                ?: return@withContext false
            outputStream.use { fos ->
                sourceFile.inputStream().use { fis ->
                    fis.copyTo(fos)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Fast retrieval of total page count for a given PDF document URI.
     */
    suspend fun getPdfPageCount(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                    return@withContext renderer.pageCount
                }
            }
        } catch (e: Exception) {
            try {
                PdfTextExtractor.initialize(context)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    PDDocument.load(stream).use { doc ->
                        return@withContext doc.numberOfPages
                    }
                }
            } catch (ignored: Exception) {}
        }
        0
    }
}
