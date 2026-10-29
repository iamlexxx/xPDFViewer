package com.xpdfviewer.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

object PdfTextExtractor {

    private val isInitialized = AtomicBoolean(false)

    fun initialize(context: Context) {
        if (isInitialized.compareAndSet(false, true)) {
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun extractPageText(
        context: Context,
        uri: Uri,
        pageIndex: Int
    ): String = withContext(Dispatchers.IO) {
        initialize(context)
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    val pageNumber = pageIndex + 1
                    if (pageNumber in 1..document.numberOfPages) {
                        val stripper = PDFTextStripper().apply {
                            startPage = pageNumber
                            endPage = pageNumber
                            sortByPosition = true
                        }
                        stripper.getText(document).trim()
                    } else {
                        ""
                    }
                }
            } ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    suspend fun extractAllText(
        context: Context,
        uri: Uri
    ): String = withContext(Dispatchers.IO) {
        initialize(context)
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    val stripper = PDFTextStripper().apply {
                        sortByPosition = true
                    }
                    stripper.getText(document).trim()
                }
            } ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
