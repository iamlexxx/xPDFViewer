package com.xpdfviewer.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xpdfviewer.data.SplitMode
import com.xpdfviewer.data.SplitPdfUiState
import com.xpdfviewer.pdf.PdfSplitterEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SplitPdfViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SplitPdfUiState())
    val uiState: StateFlow<SplitPdfUiState> = _uiState.asStateFlow()

    fun loadDocument(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val (fileName, fileSize, pageCount) = PdfSplitterEngine.getDocumentMetadata(context, uri)

            if (pageCount <= 0) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "Unable to read pages from selected document (file may be corrupted or protected)."
                    )
                }
                return@launch
            }

            val defaultPrefix = fileName.substringBeforeLast(".pdf", fileName).replace(" ", "_")
            val defaultRange = if (pageCount > 1) "1-${minOf(pageCount, 3)}" else "1"

            val (segments, warning) = PdfSplitterEngine.parseRangeString(defaultRange, pageCount)

            _uiState.update {
                it.copy(
                    sourceUri = uri,
                    fileName = fileName,
                    fileSizeBytes = fileSize,
                    pageCount = pageCount,
                    splitMode = SplitMode.CUSTOM_RANGE,
                    rangeInput = defaultRange,
                    intervalPageCount = 2.coerceAtMost(pageCount),
                    customOutputPrefix = defaultPrefix,
                    plannedSegments = segments,
                    validationWarning = warning,
                    isProcessing = false,
                    errorMessage = null
                )
            }
        }
    }

    fun setSplitMode(mode: SplitMode) {
        _uiState.update { state ->
            val pageCount = state.pageCount
            val segments = when (mode) {
                SplitMode.CUSTOM_RANGE -> {
                    val (segs, _) = PdfSplitterEngine.parseRangeString(state.rangeInput, pageCount)
                    segs
                }
                SplitMode.FIXED_INTERVAL -> {
                    PdfSplitterEngine.generateFixedIntervalSegments(state.intervalPageCount, pageCount)
                }
                SplitMode.BURST_PAGES -> {
                    PdfSplitterEngine.generateBurstSegments(pageCount)
                }
            }
            val warning = if (mode == SplitMode.CUSTOM_RANGE) {
                val (_, w) = PdfSplitterEngine.parseRangeString(state.rangeInput, pageCount)
                w
            } else null

            state.copy(
                splitMode = mode,
                plannedSegments = segments,
                validationWarning = warning,
                errorMessage = null
            )
        }
    }

    fun setRangeInput(input: String) {
        _uiState.update { state ->
            val (segments, warning) = PdfSplitterEngine.parseRangeString(input, state.pageCount)
            state.copy(
                rangeInput = input,
                plannedSegments = segments,
                validationWarning = warning
            )
        }
    }

    fun setIntervalPageCount(interval: Int) {
        val safeInterval = interval.coerceAtLeast(1)
        _uiState.update { state ->
            val segments = PdfSplitterEngine.generateFixedIntervalSegments(safeInterval, state.pageCount)
            state.copy(
                intervalPageCount = safeInterval,
                plannedSegments = segments,
                validationWarning = null
            )
        }
    }

    fun setExtractAsSingleFile(singleFile: Boolean) {
        _uiState.update { it.copy(extractAsSingleFile = singleFile) }
    }

    fun setOutputPrefix(prefix: String) {
        _uiState.update { it.copy(customOutputPrefix = prefix) }
    }

    fun splitToDirectory(context: Context, treeUri: Uri) {
        val state = _uiState.value
        val srcUri = state.sourceUri ?: return
        val segments = state.plannedSegments
        if (segments.isEmpty()) return

        val prefix = if (state.customOutputPrefix.isNotBlank()) state.customOutputPrefix else "document"

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processedCount = 0,
                    totalToProcess = segments.size,
                    progressMessage = "Preparing to split document...",
                    errorMessage = null
                )
            }

            val result = PdfSplitterEngine.splitToDirectory(
                context = context,
                sourceUri = srcUri,
                treeUri = treeUri,
                segments = segments,
                filePrefix = prefix,
                onProgress = { current, total, fileName ->
                    _uiState.update {
                        it.copy(
                            processedCount = current,
                            totalToProcess = total,
                            progressMessage = "Writing $fileName ($current of $total)..."
                        )
                    }
                }
            )

            _uiState.update {
                it.copy(
                    isProcessing = false,
                    splitResult = result,
                    showSuccessDialog = result.success,
                    errorMessage = if (!result.success) result.errorMessage else null
                )
            }
        }
    }

    fun extractToSingleFile(context: Context, destinationUri: Uri) {
        val state = _uiState.value
        val srcUri = state.sourceUri ?: return
        val pageIndices = state.plannedSegments.flatMap { it.pageIndices }.distinct().sorted()
        if (pageIndices.isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processedCount = 0,
                    totalToProcess = pageIndices.size,
                    progressMessage = "Extracting pages...",
                    errorMessage = null
                )
            }

            val result = PdfSplitterEngine.extractPagesToSingleFile(
                context = context,
                sourceUri = srcUri,
                destinationUri = destinationUri,
                pageIndices = pageIndices,
                onProgress = { current, total ->
                    _uiState.update {
                        it.copy(
                            processedCount = current,
                            totalToProcess = total,
                            progressMessage = "Extracting page $current of $total..."
                        )
                    }
                }
            )

            _uiState.update {
                it.copy(
                    isProcessing = false,
                    splitResult = result,
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
        _uiState.value = SplitPdfUiState()
    }

    fun handleBackPress(onExit: () -> Unit) {
        val state = _uiState.value
        if (state.isProcessing) {
            return
        }
        if (state.isDocumentLoaded) {
            _uiState.update { it.copy(showDiscardDialog = true) }
        } else {
            onExit()
        }
    }

    fun dismissDiscardDialog() {
        _uiState.update { it.copy(showDiscardDialog = false) }
    }

    fun confirmDiscard(onExit: () -> Unit) {
        _uiState.update { it.copy(showDiscardDialog = false) }
        resetState()
        onExit()
    }
}

