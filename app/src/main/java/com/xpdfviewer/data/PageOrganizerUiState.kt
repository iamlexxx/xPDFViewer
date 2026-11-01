package com.xpdfviewer.data

import android.net.Uri
import java.util.UUID

/**
 * Represents a single page card in the Visual Page Organizer grid.
 */
data class PageItemState(
    val pageId: String = UUID.randomUUID().toString(),
    val originalPageIndex: Int,
    val rotationOffset: Int = 0,
    val isSelected: Boolean = false
)

/**
 * UI State for the Visual Page Organizer screen.
 */
data class PageOrganizerUiState(
    val isLoading: Boolean = true,
    val sourceUri: Uri? = null,
    val fileName: String = "",
    val originalPageCount: Int = 0,
    val pages: List<PageItemState> = emptyList(),
    val isSelectionMode: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val isProcessingSave: Boolean = false,
    val showSaveChoiceDialog: Boolean = false,
    val showDiscardDialog: Boolean = false,
    val isExtractingSelected: Boolean = false,
    val errorMessage: String? = null
) {
    val selectedCount: Int
        get() = pages.count { it.isSelected }

    val activePageCount: Int
        get() = pages.size
}

