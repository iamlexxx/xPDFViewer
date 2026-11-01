package com.xpdfviewer.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xpdfviewer.data.PaginationConfig
import com.xpdfviewer.data.WatermarkConfig
import com.xpdfviewer.data.WatermarkTab
import com.xpdfviewer.data.WatermarkUiState
import com.xpdfviewer.pdf.PdfWatermarkEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PdfWatermarkViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(WatermarkUiState())
    val uiState: StateFlow<WatermarkUiState> = _uiState.asStateFlow()

    fun loadDocument(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val (fileName, fileSize, pageCount) = PdfWatermarkEngine.getDocumentMetadata(context, uri)

            if (pageCount <= 0) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "Unable to read pages from document (file may be password-protected or invalid)."
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    sourceUri = uri,
                    fileName = fileName,
                    fileSizeBytes = fileSize,
                    pageCount = pageCount,
                    isProcessing = false,
                    errorMessage = null
                )
            }
        }
    }

    fun setActiveTab(tab: WatermarkTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun updateWatermarkConfig(update: (WatermarkConfig) -> WatermarkConfig) {
        _uiState.update { it.copy(watermarkConfig = update(it.watermarkConfig)) }
    }

    fun updatePaginationConfig(update: (PaginationConfig) -> PaginationConfig) {
        _uiState.update { it.copy(paginationConfig = update(it.paginationConfig)) }
    }

    fun executeWatermarkAndPagination(
        context: Context,
        destinationUri: Uri,
        outputFileName: String
    ) {
        val currentState = _uiState.value
        val sourceUri = currentState.sourceUri ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progressCurrent = 0,
                    progressTotal = currentState.pageCount,
                    progressMessage = "Applying watermark & pagination...",
                    errorMessage = null
                )
            }

            val result = PdfWatermarkEngine.applyWatermarkAndPagination(
                context = context,
                sourceUri = sourceUri,
                destinationUri = destinationUri,
                outputFileName = outputFileName,
                watermarkConfig = currentState.watermarkConfig,
                paginationConfig = currentState.paginationConfig,
                onProgress = { current, total ->
                    _uiState.update {
                        it.copy(
                            progressCurrent = current,
                            progressTotal = total,
                            progressMessage = "Processing page $current of $total..."
                        )
                    }
                }
            )

            if (result.success) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        resultDialogVisible = true,
                        lastResult = result,
                        errorMessage = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = result.errorMessage ?: "Failed to process PDF"
                    )
                }
            }
        }
    }

    fun dismissResultDialog() {
        _uiState.update { it.copy(resultDialogVisible = false) }
    }

    fun clearDocument() {
        _uiState.value = WatermarkUiState()
    }
}

