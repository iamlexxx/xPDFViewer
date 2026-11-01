package com.xpdfviewer.data

import java.util.UUID

/**
 * Represents a single text search match occurrence within a PDF document.
 *
 * @param id Unique identifier for UI keying.
 * @param matchIndex 0-based global match index across the entire document.
 * @param pageIndex 0-based index of the page where the match occurs.
 * @param bounds List of normalized bounding boxes covering the matched characters/words.
 * @param matchedText The exact text sequence that matched.
 * @param snippet Contextual text snippet around the match.
 */
data class SearchMatch(
    val id: String = UUID.randomUUID().toString(),
    val matchIndex: Int,
    val pageIndex: Int,
    val bounds: List<NormalizedRect>,
    val matchedText: String,
    val snippet: String = ""
) {
    val displayPage: Int
        get() = pageIndex + 1
}

/**
 * Encapsulates the complete reactive state for in-document full-text search.
 *
 * @param isSearchActive Whether the search bar deck is currently expanded.
 * @param query Current text search query entered by the user.
 * @param isSearching True if background text indexing/matching is currently executing.
 * @param matches Ordered list of all discovered search occurrences across document pages.
 * @param currentMatchIndex Index within [matches] corresponding to the active focused match.
 */
data class PdfSearchState(
    val isSearchActive: Boolean = false,
    val query: String = "",
    val isSearching: Boolean = false,
    val matches: List<SearchMatch> = emptyList(),
    val currentMatchIndex: Int = -1
) {
    val totalMatches: Int
        get() = matches.size

    val currentMatch: SearchMatch?
        get() = if (currentMatchIndex in matches.indices) matches[currentMatchIndex] else null

    val matchCountDisplay: String
        get() = when {
            isSearching && totalMatches == 0 -> "Searching..."
            query.isBlank() -> ""
            totalMatches == 0 -> "0 matches"
            else -> "${currentMatchIndex + 1} of $totalMatches"
        }

    val hasMatches: Boolean
        get() = matches.isNotEmpty()
}

