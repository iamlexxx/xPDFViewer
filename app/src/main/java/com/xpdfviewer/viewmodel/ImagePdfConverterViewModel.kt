package com.xpdfviewer.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xpdfviewer.data.ConverterMode
import com.xpdfviewer.data.ImagePdfUiState
import com.xpdfviewer.data.ImageToPdfConfig
import com.xpdfviewer.data.PdfToImageConfig
import com.xpdfviewer.data.SelectedImageItem
import com.xpdfviewer.pdf.ImageToPdfEngine
import com.xpdfviewer.pdf.PdfToImageEngine
import com.xpdfviewer.pdf.PdfWatermarkEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ImagePdfConverterViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ImagePdfUiState())
    val uiState: StateFlow<ImagePdfUiState> = _uiState.asStateFlow()

    fun setMode(mode: ConverterMode) {
        _uiState.update { it.copy(activeMode = mode, errorMessage = null) }
    }

    fun addImages(uris: List<Uri>) {
        val newItems = uris.map { SelectedImageItem(uri = it) }
        _uiState.update { state ->
            state.copy(
                selectedImages = state.selectedImages + newItems,
                errorMessage = null
            )
        }
    }

    fun removeImage(id: String) {
        _uiState.update { state ->
            state.copy(selectedImages = state.selectedImages.filterNot { it.id == id })
        }
    }

    fun rotateImage(id: String) {
        _uiState.update { state ->
            state.copy(
                selectedImages = state.selectedImages.map { item ->
                    if (item.id == id) {
                        item.copy(rotationDegrees = (item.rotationDegrees + 90) % 360)
                    } else item
                }
            )
        }
    }

    fun moveImage(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val list = state.selectedImages.toMutableList()
            if (fromIndex in list.indices && toIndex in list.indices) {
                val item = list.removeAt(fromIndex)
                list.add(toIndex, item)
                state.copy(selectedImages = list)
            } else state
        }
    }

    fun updateImageToPdfConfig(update: (ImageToPdfConfig) -> ImageToPdfConfig) {
        _uiState.update { it.copy(imageToPdfConfig = update(it.imageToPdfConfig)) }
    }

    fun loadSourcePdf(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val (name, size, pages) = PdfWatermarkEngine.getDocumentMetadata(context, uri)
            if (pages <= 0) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "Unable to read pages from selected document"
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    sourcePdfUri = uri,
                    pdfFileName = name,
                    pdfFileSizeBytes = size,
                    pdfPageCount = pages,
                    isProcessing = false,
                    errorMessage = null
                )
            }
        }
    }

    fun updatePdfToImageConfig(update: (PdfToImageConfig) -> PdfToImageConfig) {
        _uiState.update { it.copy(pdfToImageConfig = update(it.pdfToImageConfig)) }
    }

    fun executeImageToPdf(context: Context, destUri: Uri, outName: String) {
        val state = _uiState.value
        val images = state.selectedImages
        if (images.isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progressCurrent = 0,
                    progressTotal = images.size,
                    progressMessage = "Compiling images into PDF..."
                )
            }

            val result = ImageToPdfEngine.convertImagesToPdf(
                context = context,
                images = images,
                config = state.imageToPdfConfig,
                destinationUri = destUri,
                outputFileName = outName,
                onProgress = { current, total ->
                    _uiState.update {
                        it.copy(
                            progressCurrent = current,
                            progressTotal = total,
                            progressMessage = "Processing image $current of $total..."
                        )
                    }
                }
            )

            _uiState.update {
                it.copy(
                    isProcessing = false,
                    resultDialogVisible = result.success,
                    lastResult = result,
                    errorMessage = if (!result.success) result.errorMessage else null
                )
            }
        }
    }

    fun executePdfToImages(context: Context, destZipUri: Uri, outZipName: String) {
        val state = _uiState.value
        val pdfUri = state.sourcePdfUri ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progressCurrent = 0,
                    progressTotal = state.pdfPageCount,
                    progressMessage = "Rendering pages to images..."
                )
            }

            val result = PdfToImageEngine.convertPdfToImagesZip(
                context = context,
                sourcePdfUri = pdfUri,
                config = state.pdfToImageConfig,
                destinationZipUri = destZipUri,
                outputZipName = outZipName,
                onProgress = { current, total ->
                    _uiState.update {
                        it.copy(
                            progressCurrent = current,
                            progressTotal = total,
                            progressMessage = "Rendering page $current of $total..."
                        )
                    }
                }
            )

            _uiState.update {
                it.copy(
                    isProcessing = false,
                    resultDialogVisible = result.success,
                    lastResult = result,
                    errorMessage = if (!result.success) result.errorMessage else null
                )
            }
        }
    }

    fun dismissResultDialog() {
        _uiState.update { it.copy(resultDialogVisible = false) }
    }

    fun clearAll() {
        _uiState.value = ImagePdfUiState()
    }
}

