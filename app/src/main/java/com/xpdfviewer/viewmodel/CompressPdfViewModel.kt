package com.xpdfviewer.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xpdfviewer.pdf.CompressResult
import com.xpdfviewer.pdf.PdfCompressor
import com.xpdfviewer.pdf.PdfCompressionLevel
import com.xpdfviewer.pdf.PdfRendererCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

data class CompressPdfUiState(
    val selectedUri: Uri? = null,
    val fileName: String = "",
    val originalSizeBytes: Long = 0L,
    val originalSizeFormatted: String = "",
    val pageCount: Int = 0,
    val compressionLevel: PdfCompressionLevel = PdfCompressionLevel.RECOMMENDED,
    val isCompressing: Boolean = false,
    val progressCurrentPage: Int = 0,
    val progressTotalPages: Int = 0,
    val compressResult: CompressResult? = null,
    val compressedOutputUri: Uri? = null,
    val compressedFileName: String = "",
    val errorMessage: String? = null
) {
    val hasDocument: Boolean get() = selectedUri != null
}

class CompressPdfViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CompressPdfUiState())
    val uiState: StateFlow<CompressPdfUiState> = _uiState.asStateFlow()

    fun selectDocument(context: Context, uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val name = PdfRendererCore.resolveFileName(context, uri)
                val sizeBytes = PdfCompressor.getFileSize(context, uri)
                val sizeFormatted = formatFileSize(sizeBytes)
                val pages = PdfCompressor.getPageCount(context, uri)

                _uiState.update { current ->
                    current.copy(
                        selectedUri = uri,
                        fileName = name,
                        originalSizeBytes = sizeBytes,
                        originalSizeFormatted = sizeFormatted,
                        pageCount = if (pages > 0) pages else 1,
                        compressResult = null,
                        compressedOutputUri = null,
                        errorMessage = null
                    )
                }
            }
        }
    }

    fun setCompressionLevel(level: PdfCompressionLevel) {
        _uiState.update { it.copy(compressionLevel = level) }
    }

    fun clearSelectedDocument() {
        _uiState.update {
            CompressPdfUiState(compressionLevel = it.compressionLevel)
        }
    }

    fun compressToDestination(
        context: Context,
        destinationUri: Uri,
        outputFileName: String
    ) {
        val currentUri = _uiState.value.selectedUri ?: return
        val level = _uiState.value.compressionLevel

        _uiState.update {
            it.copy(
                isCompressing = true,
                progressCurrentPage = 0,
                progressTotalPages = it.pageCount,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val result = PdfCompressor.compressPdf(
                context = context,
                sourceUri = currentUri,
                destinationUri = destinationUri,
                level = level,
                onProgress = { current, total ->
                    _uiState.update {
                        it.copy(
                            progressCurrentPage = current,
                            progressTotalPages = total
                        )
                    }
                }
            )

            _uiState.update { current ->
                current.copy(
                    isCompressing = false,
                    compressResult = result,
                    compressedOutputUri = if (result.success) destinationUri else null,
                    compressedFileName = outputFileName,
                    errorMessage = if (!result.success) result.errorMessage else null
                )
            }
        }
    }

    fun dismissResultDialog() {
        _uiState.update { it.copy(compressResult = null) }
    }

    companion object {
        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
        }
    }
}
