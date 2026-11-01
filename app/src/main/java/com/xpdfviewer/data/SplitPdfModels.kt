package com.xpdfviewer.data

import android.net.Uri

/**
 * Operating mode for splitting a PDF document.
 */
enum class SplitMode(val title: String, val description: String) {
    CUSTOM_RANGE(
        title = "Custom Range",
        description = "Specify exact page ranges like 1-3, 5, 8-12."
    ),
    FIXED_INTERVAL(
        title = "Fixed Chunks",
        description = "Split into equal chunks every N pages."
    ),
    BURST_PAGES(
        title = "Burst Pages",
        description = "Explode into individual 1-page PDF documents."
    )
}

/**
 * Represents a single planned output PDF file segment containing a subset of pages.
 *
 * @param label Human-readable description, e.g. "Pages 1–3" or "Page 5".
 * @param pageIndices 0-based page indices from the source document included in this output file.
 */
data class SplitSegment(
    val label: String,
    val pageIndices: List<Int>
) {
    val pageCount: Int
        get() = pageIndices.size

    val displaySummary: String
        get() = if (pageIndices.size == 1) {
            "Page ${pageIndices.first() + 1}"
        } else if (isContiguous()) {
            "Pages ${pageIndices.first() + 1}–${pageIndices.last() + 1} ($pageCount pages)"
        } else {
            "${pageIndices.size} selected pages"
        }

    private fun isContiguous(): Boolean {
        if (pageIndices.size <= 1) return true
        for (i in 1 until pageIndices.size) {
            if (pageIndices[i] != pageIndices[i - 1] + 1) return false
        }
        return true
    }
}

/**
 * Execution outcome returned by [com.xpdfviewer.pdf.PdfSplitterEngine].
 */
data class SplitResult(
    val success: Boolean,
    val createdFileCount: Int = 0,
    val outputDirectoryUri: Uri? = null,
    val outputFileUri: Uri? = null,
    val createdFiles: List<String> = emptyList(),
    val errorMessage: String? = null
)

/**
 * UI State for the Split PDF utility screen.
 */
data class SplitPdfUiState(
    val sourceUri: Uri? = null,
    val fileName: String = "",
    val fileSizeBytes: Long = 0L,
    val pageCount: Int = 0,
    val splitMode: SplitMode = SplitMode.CUSTOM_RANGE,
    val rangeInput: String = "1",
    val intervalPageCount: Int = 2,
    val extractAsSingleFile: Boolean = true,
    val customOutputPrefix: String = "",
    val plannedSegments: List<SplitSegment> = emptyList(),
    val validationWarning: String? = null,
    val isProcessing: Boolean = false,
    val processedCount: Int = 0,
    val totalToProcess: Int = 0,
    val progressMessage: String = "",
    val splitResult: SplitResult? = null,
    val showSuccessDialog: Boolean = false,
    val showDiscardDialog: Boolean = false,
    val errorMessage: String? = null
) {
    val isDocumentLoaded: Boolean
        get() = sourceUri != null && pageCount > 0

    val totalOutputFiles: Int
        get() = if (splitMode == SplitMode.CUSTOM_RANGE && extractAsSingleFile) {
            if (plannedSegments.isNotEmpty()) 1 else 0
        } else {
            plannedSegments.size
        }

    val canExecuteSplit: Boolean
        get() = isDocumentLoaded && !isProcessing && plannedSegments.isNotEmpty() && validationWarning == null
}

