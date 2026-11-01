package com.xpdfviewer.data

import android.net.Uri

enum class PageModificationType {
    ADD_BLANK_PAGE,
    DELETE_PAGE,
    INSERT_PDF_DOCUMENT,
    EXTRACT_PAGE
}

data class PendingPageAction(
    val type: PageModificationType,
    val targetPageIndex: Int,
    val insertUri: Uri? = null,
    val insertFileName: String? = null
)

data class PdfViewerUiState(
    val uri: Uri? = null,
    val fileName: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 1,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isDarkMode: Boolean = false,
    val zoom: Float = 1.0f,
    val showJumpDialog: Boolean = false,
    val showControls: Boolean = true,
    val showTextSheet: Boolean = false,
    val textSheetPage: Int = 1,
    val isExtractingText: Boolean = false,
    val extractedText: String = "",
    val isEntireDocumentMode: Boolean = false,
    val selectedTab: AppTab = AppTab.HOME,
    val recentDocuments: List<RecentDocument> = emptyList(),
    val showPageActionsSheet: Boolean = false,
    val pageActionsTargetIndex: Int = 0,
    val showDeleteConfirmDialog: Boolean = false,
    val showExtractConfirmDialog: Boolean = false,
    val isModifyingDocument: Boolean = false,
    val showSaveChoiceDialog: Boolean = false,
    val pendingPageAction: PendingPageAction? = null,
    val onScreenSelectPageIndex: Int? = null,
    val onScreenSelectText: String = "",
    val isExtractingOnScreenText: Boolean = false,
    val onScreenSpatialData: PageSpatialData? = null,
    val selectedWordIndices: Set<Int> = emptySet(),
    val selectedWordText: String = "",
    val isTtsSpeaking: Boolean = false,
    val outlineNodes: List<OutlineNode> = emptyList(),
    val userBookmarks: List<UserBookmark> = emptyList(),
    val isOutlineLoading: Boolean = false,
    val showOutlineSheet: Boolean = false,
    val selectedOutlineTab: OutlineTab = OutlineTab.CONTENTS,
    val addBookmarkDialogState: AddBookmarkDialogState = AddBookmarkDialogState(),
    val searchState: PdfSearchState = PdfSearchState(),
    val readingMode: ReadingMode = ReadingMode.DEFAULT,
    val showReadingModeSheet: Boolean = false,
    val formState: DocumentFormState = DocumentFormState()
) {
    val isSpatialSelectionActive: Boolean
        get() = onScreenSelectPageIndex != null

    val selectedWordCount: Int
        get() = selectedWordIndices.size

    val isCurrentPageBookmarked: Boolean
        get() {
            val activeIndex = currentPage - 1
            return userBookmarks.any { it.pageIndex == activeIndex }
        }
}

data class PageDimensions(
    val pageIndex: Int,
    val width: Int,
    val height: Int,
    val aspectRatio: Float
)
