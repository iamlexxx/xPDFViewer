package com.xpdfviewer.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xpdfviewer.data.PageItemState
import com.xpdfviewer.data.PageOrganizerUiState
import com.xpdfviewer.pdf.PdfDocumentModifier
import com.xpdfviewer.pdf.PdfRendererCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

class PageOrganizerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PageOrganizerUiState())
    val uiState: StateFlow<PageOrganizerUiState> = _uiState.asStateFlow()

    private var renderer: PdfRendererCore? = null
    private val thumbnailCache = mutableMapOf<Int, Bitmap>()

    fun loadDocument(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, sourceUri = uri, errorMessage = null) }
            val core = PdfRendererCore(context.applicationContext, uri)
            val initialized = core.initialize()
            if (!initialized) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load document for page organizer."
                    )
                }
                return@launch
            }

            renderer?.close()
            renderer = core
            thumbnailCache.clear()

            val totalPages = core.pageCount
            val pageItems = (0 until totalPages).map { idx ->
                PageItemState(originalPageIndex = idx)
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    fileName = core.fileName,
                    originalPageCount = totalPages,
                    pages = pageItems,
                    hasUnsavedChanges = false,
                    isSelectionMode = false,
                    showSaveChoiceDialog = false,
                    showDiscardDialog = false,
                    isExtractingSelected = false
                )
            }
        }
    }

    suspend fun getThumbnail(originalIndex: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (originalIndex < 0) return@withContext null // Blank page or synthetic
        thumbnailCache[originalIndex]?.let { return@withContext it }
        val r = renderer ?: return@withContext null
        val bitmap = r.renderPage(originalIndex, targetWidth = 320, targetHeight = 440)
        if (bitmap != null) {
            thumbnailCache[originalIndex] = bitmap
        }
        bitmap
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val list = state.pages.toMutableList()
            if (fromIndex in list.indices && toIndex in list.indices && fromIndex != toIndex) {
                val item = list.removeAt(fromIndex)
                list.add(toIndex, item)
                state.copy(pages = list, hasUnsavedChanges = true)
            } else {
                state
            }
        }
    }

    fun rotatePage(index: Int) {
        _uiState.update { state ->
            val list = state.pages.toMutableList()
            if (index in list.indices) {
                val current = list[index]
                val nextRotation = (current.rotationOffset + 90) % 360
                list[index] = current.copy(rotationOffset = nextRotation)
                state.copy(pages = list, hasUnsavedChanges = true)
            } else {
                state
            }
        }
    }

    fun rotateAllPages() {
        _uiState.update { state ->
            val list = state.pages.map { item ->
                item.copy(rotationOffset = (item.rotationOffset + 90) % 360)
            }
            state.copy(pages = list, hasUnsavedChanges = true)
        }
    }

    fun togglePageSelection(index: Int) {
        _uiState.update { state ->
            val list = state.pages.toMutableList()
            if (index in list.indices) {
                val current = list[index]
                list[index] = current.copy(isSelected = !current.isSelected)
                val anySelected = list.any { it.isSelected }
                state.copy(
                    pages = list,
                    isSelectionMode = anySelected || state.isSelectionMode
                )
            } else {
                state
            }
        }
    }

    fun selectAllPages() {
        _uiState.update { state ->
            val list = state.pages.map { it.copy(isSelected = true) }
            state.copy(pages = list, isSelectionMode = true)
        }
    }

    fun clearSelection() {
        _uiState.update { state ->
            val list = state.pages.map { it.copy(isSelected = false) }
            state.copy(pages = list, isSelectionMode = false)
        }
    }

    fun setSelectionMode(enabled: Boolean) {
        _uiState.update { state ->
            if (!enabled) {
                val list = state.pages.map { it.copy(isSelected = false) }
                state.copy(pages = list, isSelectionMode = false)
            } else {
                state.copy(isSelectionMode = true)
            }
        }
    }

    fun deletePage(index: Int) {
        _uiState.update { state ->
            if (state.pages.size <= 1) return@update state // Disallow deleting the last page
            val list = state.pages.toMutableList()
            if (index in list.indices) {
                list.removeAt(index)
                state.copy(pages = list, hasUnsavedChanges = true)
            } else {
                state
            }
        }
    }

    fun deleteSelectedPages() {
        _uiState.update { state ->
            val unselected = state.pages.filter { !it.isSelected }
            if (unselected.isEmpty()) {
                // Cannot delete all pages
                state.copy(errorMessage = "Cannot delete all pages in the document.")
            } else {
                state.copy(
                    pages = unselected,
                    isSelectionMode = false,
                    hasUnsavedChanges = true
                )
            }
        }
    }

    fun addBlankPage(afterIndex: Int = -1) {
        _uiState.update { state ->
            val list = state.pages.toMutableList()
            val blankItem = PageItemState(originalPageIndex = -1)
            val insertAt = if (afterIndex in list.indices) afterIndex + 1 else list.size
            list.add(insertAt, blankItem)
            state.copy(pages = list, hasUnsavedChanges = true)
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // --- Save & Discard workflows ---

    fun requestSave() {
        _uiState.update { it.copy(showSaveChoiceDialog = true) }
    }

    fun dismissSaveChoiceDialog() {
        _uiState.update { it.copy(showSaveChoiceDialog = false) }
    }

    fun requestExtractSelected() {
        _uiState.update { it.copy(isExtractingSelected = true) }
    }

    fun cancelExtractSelected() {
        _uiState.update { it.copy(isExtractingSelected = false) }
    }

    fun handleBackNavigation(onAllowExit: () -> Unit) {
        if (_uiState.value.isSelectionMode) {
            clearSelection()
        } else if (_uiState.value.hasUnsavedChanges) {
            _uiState.update { it.copy(showDiscardDialog = true) }
        } else {
            onAllowExit()
        }
    }

    fun dismissDiscardDialog() {
        _uiState.update { it.copy(showDiscardDialog = false) }
    }

    fun confirmDiscard(onExit: () -> Unit) {
        _uiState.update { it.copy(showDiscardDialog = false, hasUnsavedChanges = false) }
        onExit()
    }

    fun saveOverwrite(
        context: Context,
        onComplete: (Uri) -> Unit
    ) {
        val srcUri = _uiState.value.sourceUri ?: return
        executeSave(context, destinationUri = srcUri, onComplete = onComplete)
    }

    fun saveAsCopy(
        context: Context,
        destinationUri: Uri,
        onComplete: (Uri) -> Unit
    ) {
        executeSave(context, destinationUri = destinationUri, onComplete = onComplete)
    }

    private fun executeSave(
        context: Context,
        destinationUri: Uri,
        onComplete: (Uri) -> Unit
    ) {
        val currentState = _uiState.value
        val srcUri = currentState.sourceUri ?: return
        val transformations = currentState.pages.map { page ->
            PdfDocumentModifier.PageTransformSpec(
                originalIndex = page.originalPageIndex,
                rotationOffset = page.rotationOffset
            )
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingSave = true, showSaveChoiceDialog = false) }
            val success = PdfDocumentModifier.applyPageOrganization(
                context = context,
                sourceUri = srcUri,
                destinationUri = destinationUri,
                transformations = transformations
            )
            _uiState.update { it.copy(isProcessingSave = false) }

            if (success) {
                _uiState.update { it.copy(hasUnsavedChanges = false) }
                onComplete(destinationUri)
            } else {
                _uiState.update {
                    it.copy(errorMessage = "Failed to save organized document.")
                }
            }
        }
    }

    fun extractSelectedPagesToDestination(
        context: Context,
        destinationUri: Uri,
        onComplete: (Uri) -> Unit
    ) {
        val currentState = _uiState.value
        val srcUri = currentState.sourceUri ?: return
        val selectedPages = currentState.pages.filter { it.isSelected }
        if (selectedPages.isEmpty()) return

        val transformations = selectedPages.map { page ->
            PdfDocumentModifier.PageTransformSpec(
                originalIndex = page.originalPageIndex,
                rotationOffset = page.rotationOffset
            )
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingSave = true, isExtractingSelected = false) }
            val success = PdfDocumentModifier.applyPageOrganization(
                context = context,
                sourceUri = srcUri,
                destinationUri = destinationUri,
                transformations = transformations
            )
            _uiState.update { it.copy(isProcessingSave = false) }

            if (success) {
                clearSelection()
                onComplete(destinationUri)
            } else {
                _uiState.update {
                    it.copy(errorMessage = "Failed to extract selected pages.")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        renderer?.close()
        renderer = null
        thumbnailCache.clear()
    }
}

