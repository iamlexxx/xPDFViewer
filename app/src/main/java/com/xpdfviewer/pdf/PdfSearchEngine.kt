package com.xpdfviewer.pdf

import android.content.Context
import android.net.Uri
import com.xpdfviewer.data.PageSpatialData
import com.xpdfviewer.data.SearchMatch
import com.xpdfviewer.data.SpatialWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * High-performance background search engine for PDF documents.
 * Scans page text spatial layers, locates query occurrences, and yields
 * coordinate-aligned [SearchMatch] instances.
 */
object PdfSearchEngine {

    private data class WordSpan(
        val word: SpatialWord,
        val startChar: Int,
        val endChar: Int
    )

    /**
     * Executes a full-text search across the document.
     *
     * @param context Application context for SAF content streaming.
     * @param uri Document SAF URI.
     * @param pageCount Total number of pages in the document.
     * @param query Search term or multi-word phrase.
     * @param onProgress Callback invoked as new matches are progressively discovered.
     */
    suspend fun searchDocument(
        context: Context,
        uri: Uri,
        pageCount: Int,
        query: String,
        onProgress: suspend (matches: List<SearchMatch>, isComplete: Boolean) -> Unit
    ) = withContext(Dispatchers.Default) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            onProgress(emptyList(), true)
            return@withContext
        }

        val allMatches = mutableListOf<SearchMatch>()
        var globalMatchIndex = 0

        for (pageIndex in 0 until pageCount) {
            coroutineContext.ensureActive()

            val spatialData = SpatialTextExtractor.extractSpatialText(context, uri, pageIndex)
            coroutineContext.ensureActive()

            if (!spatialData.isEmpty) {
                val pageMatches = searchPage(spatialData, cleanQuery, globalMatchIndex)
                if (pageMatches.isNotEmpty()) {
                    globalMatchIndex += pageMatches.size
                    allMatches.addAll(pageMatches)
                    onProgress(allMatches.toList(), false)
                }
            }
        }

        onProgress(allMatches.toList(), true)
    }

    private fun searchPage(
        spatialData: PageSpatialData,
        query: String,
        startingIndex: Int
    ): List<SearchMatch> {
        val words = spatialData.words
        if (words.isEmpty()) return emptyList()

        val wordSpans = mutableListOf<WordSpan>()
        val joinedTextBuilder = StringBuilder()

        for (word in words) {
            val start = joinedTextBuilder.length
            joinedTextBuilder.append(word.text)
            val end = joinedTextBuilder.length
            wordSpans.add(WordSpan(word, start, end))
            joinedTextBuilder.append(" ")
        }

        val joinedText = joinedTextBuilder.toString()
        val lowerText = joinedText.lowercase()
        val lowerQuery = query.lowercase()
        val queryLength = lowerQuery.length

        val matches = mutableListOf<SearchMatch>()
        var currentIndex = startingIndex
        var searchPos = 0

        while (searchPos < lowerText.length) {
            val matchStart = lowerText.indexOf(lowerQuery, searchPos)
            if (matchStart == -1) break

            val matchEnd = matchStart + queryLength

            // Collect all words overlapping this character span
            val overlappingWords = wordSpans.filter { span ->
                span.startChar < matchEnd && span.endChar > matchStart
            }

            if (overlappingWords.isNotEmpty()) {
                val bounds = overlappingWords.map { it.word.bounds }
                val matchedText = joinedText.substring(matchStart, matchEnd)

                val snippetStart = (matchStart - 24).coerceAtLeast(0)
                val snippetEnd = (matchEnd + 24).coerceAtMost(joinedText.length)
                val rawSnippet = joinedText.substring(snippetStart, snippetEnd).trim()
                val snippetPrefix = if (snippetStart > 0) "..." else ""
                val snippetSuffix = if (snippetEnd < joinedText.length) "..." else ""
                val snippet = "$snippetPrefix$rawSnippet$snippetSuffix"

                matches.add(
                    SearchMatch(
                        matchIndex = currentIndex++,
                        pageIndex = spatialData.pageIndex,
                        bounds = bounds,
                        matchedText = matchedText,
                        snippet = snippet
                    )
                )
            }

            searchPos = matchStart + queryLength.coerceAtLeast(1)
        }

        return matches
    }
}

