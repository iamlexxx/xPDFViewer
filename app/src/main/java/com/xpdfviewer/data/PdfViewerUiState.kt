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
    val isExtractingOnScreenText: Boolean = false
)

data class PageDimensions(
    val pageIndex: Int,
    val width: Int,
    val height: Int,
    val aspectRatio: Float
)
