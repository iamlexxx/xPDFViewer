package com.xpdfviewer.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xpdfviewer.data.NormalizedRect
import com.xpdfviewer.data.PageDimensions
import com.xpdfviewer.data.RedactionMark
import com.xpdfviewer.data.RedactionSource
import com.xpdfviewer.data.RedactionUiState
import com.xpdfviewer.pdf.PdfRedactionEngine
import com.xpdfviewer.pdf.PdfRendererCore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PdfRedactionViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RedactionUiState())
    val uiState: StateFlow<RedactionUiState> = _uiState.asStateFlow()

    private var rendererCore: PdfRendererCore? = null

    fun loadDocument(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val (fileName, fileSize, pageCount) = PdfRedactionEngine.getDocumentMetadata(context, uri)

            if (pageCount <= 0) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "Unable to read pages from selected document."
                    )
                }
                return@launch
            }

            rendererCore?.close()
            val core = PdfRendererCore(context.applicationContext, uri)
            val success = core.initialize()
            if (!success || core.pageCount <= 0) {
                core.close()
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "Unable to render PDF document."
                    )
                }
                return@launch
            }

            rendererCore = core
            val pageDims = core.getPageDimensions(0)
            val pageBmp = core.renderPage(0, 1080, 0)

            _uiState.update {
                it.copy(
                    sourceUri = uri,
                    fileName = fileName,
                    fileSizeBytes = fileSize,
                    pageCount = pageCount,
                    currentPage = 1,
                    currentPageBitmap = pageBmp,
                    pageDimensions = pageDims,
                    isLoadingPage = false,
                    pendingMarks = emptyList(),
                    activeMarkId = null,
                    keywordInput = "",
                    isProcessing = false,
                    errorMessage = null
                )
            }
        }
    }

    fun setCurrentPage(page: Int) {
        val total = _uiState.value.pageCount
        val safePage = page.coerceIn(1, if (total > 0) total else 1)
        if (safePage == _uiState.value.currentPage && _uiState.value.currentPageBitmap != null) return

        val pageIdx = safePage - 1
        val core = rendererCore
        if (core != null) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingPage = true, currentPage = safePage) }
                val dims = core.getPageDimensions(pageIdx)
                val bmp = core.renderPage(pageIdx, 1080, 0)
                _uiState.update {
                    it.copy(
                        isLoadingPage = false,
                        currentPage = safePage,
                        pageDimensions = dims,
                        currentPageBitmap = bmp
                    )
                }
            }
        } else {
            _uiState.update { it.copy(currentPage = safePage) }
        }
    }

    fun addManualRedactionMark(bounds: NormalizedRect, label: String? = null) {
        val pageIdx = _uiState.value.currentPage - 1
        val newMark = RedactionMark(
            pageIndex = pageIdx,
            bounds = bounds,
            labelText = label,
            source = RedactionSource.MANUAL_DRAG
        )
        _uiState.update { state ->
            state.copy(
                pendingMarks = state.pendingMarks + newMark,
                activeMarkId = newMark.id
            )
        }
    }

    fun removeRedactionMark(markId: String) {
        _uiState.update { state ->
            state.copy(
                pendingMarks = state.pendingMarks.filter { it.id != markId },
                activeMarkId = if (state.activeMarkId == markId) null else state.activeMarkId
            )
        }
    }

    fun clearCurrentPageMarks() {
        val pageIdx = _uiState.value.currentPage - 1
        _uiState.update { state ->
            state.copy(
                pendingMarks = state.pendingMarks.filter { it.pageIndex != pageIdx },
                activeMarkId = null
            )
        }
    }

    fun clearAllMarks() {
        _uiState.update { it.copy(pendingMarks = emptyList(), activeMarkId = null) }
    }

    fun setKeywordInput(input: String) {
        _uiState.update { it.copy(keywordInput = input) }
    }

    fun searchAndMarkKeyword(context: Context) {
        val query = _uiState.value.keywordInput.trim()
        val uri = _uiState.value.sourceUri ?: return
        val pageCount = _uiState.value.pageCount
        if (query.isBlank() || pageCount <= 0) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingKeyword = true) }
            val newMarks = PdfRedactionEngine.searchKeywordRedactions(
                context = context,
                uri = uri,
                pageCount = pageCount,
                keyword = query
            )

            _uiState.update { state ->
                state.copy(
                    pendingMarks = state.pendingMarks + newMarks,
                    isSearchingKeyword = false
                )
            }
        }
    }

    fun setConfirmDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showConfirmDialog = visible) }
    }

    fun applyRedactions(context: Context, destinationUri: Uri) {
        val state = _uiState.value
        val srcUri = state.sourceUri ?: return
        val marks = state.pendingMarks
        if (marks.isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progressMessage = "Applying permanent redactions and purging metadata...",
                    showConfirmDialog = false,
                    errorMessage = null
                )
            }

            val result = PdfRedactionEngine.applyRedactions(
                context = context,
                sourceUri = srcUri,
                destinationUri = destinationUri,
                marks = marks,
                onProgress = { current, total ->
                    _uiState.update {
                        it.copy(progressMessage = "Sanitizing page $current of $total...")
                    }
                }
            )

            _uiState.update {
                it.copy(
                    isProcessing = false,
                    lastResult = result,
                    showSuccessDialog = result.success,
                    errorMessage = if (!result.success) result.errorMessage else null
                )
            }
        }
    }

    fun dismissSuccessDialog() {
        _uiState.update { it.copy(showSuccessDialog = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun resetState() {
        rendererCore?.close()
        rendererCore = null
        _uiState.value = RedactionUiState()
    }

    override fun onCleared() {
        super.onCleared()
        rendererCore?.close()
        rendererCore = null
    }

    fun handleBackPress(onExit: () -> Unit) {
        val state = _uiState.value
        if (state.isProcessing) return
        if (state.pendingMarks.isNotEmpty()) {
            _uiState.update { it.copy(showConfirmDialog = true) }
        } else {
            onExit()
        }
    }
}
