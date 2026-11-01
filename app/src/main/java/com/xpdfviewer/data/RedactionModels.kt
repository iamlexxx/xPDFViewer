package com.xpdfviewer.data

import android.graphics.Bitmap
import android.net.Uri
import java.util.UUID

/**
 * Indicates how a pending redaction mark was originated.
 */
enum class RedactionSource {
    MANUAL_DRAG,
    KEYWORD_SEARCH
}

/**
 * Represents a single pending redaction bounding box on a specific PDF page.
 *
 * @param id Unique identifier for UI keying.
 * @param pageIndex 0-based page index.
 * @param bounds Page-normalized coordinates (0f..1f).
 * @param labelText Optional preview of target text under the redaction.
 * @param source Source of the redaction mark (manual touch drag or keyword auto-search).
 */
data class RedactionMark(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    val bounds: NormalizedRect,
    val labelText: String? = null,
    val source: RedactionSource = RedactionSource.MANUAL_DRAG
)

/**
 * Result returned after committing document redactions.
 */
data class RedactionResult(
    val success: Boolean,
    val totalRedactionsApplied: Int = 0,
    val outputUri: Uri? = null,
    val errorMessage: String? = null
)

/**
 * UI State for the PDF Redaction Studio.
 */
data class RedactionUiState(
    val sourceUri: Uri? = null,
    val fileName: String = "",
    val fileSizeBytes: Long = 0L,
    val pageCount: Int = 0,
    val currentPage: Int = 1,
    val currentPageBitmap: Bitmap? = null,
    val pageDimensions: PageDimensions? = null,
    val isLoadingPage: Boolean = false,
    val pendingMarks: List<RedactionMark> = emptyList(),
    val activeMarkId: String? = null,
    val keywordInput: String = "",
    val isSearchingKeyword: Boolean = false,
    val isProcessing: Boolean = false,
    val progressMessage: String = "",
    val showConfirmDialog: Boolean = false,
    val showSuccessDialog: Boolean = false,
    val lastResult: RedactionResult? = null,
    val errorMessage: String? = null
) {
    val isDocumentLoaded: Boolean
        get() = sourceUri != null && pageCount > 0

    val totalPendingCount: Int
        get() = pendingMarks.size

    val currentPageMarks: List<RedactionMark>
        get() = pendingMarks.filter { it.pageIndex == currentPage - 1 }

    val canApplyRedaction: Boolean
        get() = isDocumentLoaded && !isProcessing && pendingMarks.isNotEmpty()
}
