package com.xpdfviewer.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.xpdfviewer.data.NormalizedRect
import com.xpdfviewer.data.PageSpatialData
import com.xpdfviewer.data.SpatialWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.StringWriter

object SpatialTextExtractor {

    suspend fun extractSpatialText(
        context: Context,
        uri: Uri,
        pageIndex: Int
    ): PageSpatialData = withContext(Dispatchers.IO) {
        PdfTextExtractor.initialize(context)
        val extractedWords = mutableListOf<SpatialWord>()
        val fullTextBuilder = StringBuilder()

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    val pageNumber = pageIndex + 1
                    if (pageNumber in 1..document.numberOfPages) {
                        val page = document.getPage(pageIndex)
                        val mediaBox = page.mediaBox
                        val pageWidth = mediaBox.width.coerceAtLeast(1f)
                        val pageHeight = mediaBox.height.coerceAtLeast(1f)

                        var currentWordIndex = 0
                        var currentLineIndex = 0

                        val stripper = object : PDFTextStripper() {
                            init {
                                startPage = pageNumber
                                endPage = pageNumber
                                sortByPosition = true
                            }

                            override fun writeString(text: String, textPositions: List<TextPosition>) {
                                if (textPositions.isEmpty()) return

                                val currentWordChars = mutableListOf<TextPosition>()
                                val currentWordText = StringBuilder()

                                for (i in textPositions.indices) {
                                    val tp = textPositions[i]
                                    val charStr = tp.unicode ?: ""

                                    if (charStr.isBlank()) {
                                        if (currentWordChars.isNotEmpty()) {
                                            val wordObj = createSpatialWord(
                                                index = currentWordIndex++,
                                                text = currentWordText.toString(),
                                                positions = currentWordChars,
                                                pageWidth = pageWidth,
                                                pageHeight = pageHeight,
                                                lineIndex = currentLineIndex
                                            )
                                            extractedWords.add(wordObj)
                                            fullTextBuilder.append(wordObj.text).append(" ")
                                            currentWordChars.clear()
                                            currentWordText.clear()
                                        }
                                    } else {
                                        currentWordChars.add(tp)
                                        currentWordText.append(charStr)
                                    }
                                }

                                if (currentWordChars.isNotEmpty()) {
                                    val wordObj = createSpatialWord(
                                        index = currentWordIndex++,
                                        text = currentWordText.toString(),
                                        positions = currentWordChars,
                                        pageWidth = pageWidth,
                                        pageHeight = pageHeight,
                                        lineIndex = currentLineIndex
                                    )
                                    extractedWords.add(wordObj)
                                    fullTextBuilder.append(wordObj.text).append(" ")
                                }

                                currentLineIndex++
                                fullTextBuilder.append("\n")
                            }
                        }

                        stripper.writeText(document, StringWriter())

                        return@withContext PageSpatialData(
                            pageIndex = pageIndex,
                            words = extractedWords,
                            fullText = fullTextBuilder.toString().trim(),
                            pagePixelWidth = pageWidth,
                            pagePixelHeight = pageHeight
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        PageSpatialData(
            pageIndex = pageIndex,
            words = emptyList(),
            fullText = "",
            pagePixelWidth = 1f,
            pagePixelHeight = 1f
        )
    }

    private fun createSpatialWord(
        index: Int,
        text: String,
        positions: List<TextPosition>,
        pageWidth: Float,
        pageHeight: Float,
        lineIndex: Int
    ): SpatialWord {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (tp in positions) {
            val left = tp.xDirAdj
            val width = tp.widthDirAdj
            val y = tp.yDirAdj
            val height = tp.heightDir.coerceAtLeast(tp.fontSizeInPt)
            val top = y - height
            val bottom = y + (height * 0.25f)

            if (left < minX) minX = left
            if (top < minY) minY = top
            if (left + width > maxX) maxX = left + width
            if (bottom > maxY) maxY = bottom
        }

        val normLeft = (minX / pageWidth).coerceIn(0f, 1f)
        val normTop = (minY / pageHeight).coerceIn(0f, 1f)
        val normRight = (maxX / pageWidth).coerceIn(0f, 1f)
        val normBottom = (maxY / pageHeight).coerceIn(0f, 1f)

        return SpatialWord(
            index = index,
            text = text,
            bounds = NormalizedRect(
                left = normLeft,
                top = normTop,
                right = normRight.coerceAtLeast(normLeft + 0.005f),
                bottom = normBottom.coerceAtLeast(normTop + 0.005f)
            ),
            lineIndex = lineIndex
        )
    }
}

