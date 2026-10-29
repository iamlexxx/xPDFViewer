package com.xpdfviewer.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xpdfviewer.data.PageDimensions
import com.xpdfviewer.pdf.PdfRendererCore
import com.xpdfviewer.signature.PdfSignerEngine
import com.xpdfviewer.signature.SignResult
import com.xpdfviewer.signature.SignatureStamp
import com.xpdfviewer.signature.SignatureStorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SignPdfUiState(
    val sourceUri: Uri? = null,
    val fileName: String = "",
    val pageCount: Int = 0,
    val currentPageIndex: Int = 0,
    val currentPageBitmap: Bitmap? = null,
    val pageDimensions: PageDimensions? = null,
    val stamps: List<SignatureStamp> = emptyList(),
    val selectedStampId: String? = null,
    val savedSignatures: List<Pair<File, Bitmap>> = emptyList(),
    val isLoading: Boolean = false,
    val isSigning: Boolean = false,
    val signResult: SignResult? = null,
    val errorMessage: String? = null
) {
    val currentStamps: List<SignatureStamp>
        get() = stamps.filter { it.pageIndex == currentPageIndex }
}

class SignPdfViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SignPdfUiState())
    val uiState: StateFlow<SignPdfUiState> = _uiState.asStateFlow()

    private var rendererCore: PdfRendererCore? = null

    init {
        loadSavedSignatures()
    }

    fun loadPdf(context: Context, uri: Uri, initialPageIndex: Int = 0) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, sourceUri = uri) }

            rendererCore?.close()
            val core = PdfRendererCore(context.applicationContext, uri)
            val success = core.initialize()

            if (!success || core.pageCount == 0) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Could not open or parse PDF document."
                    )
                }
                return@launch
            }

            rendererCore = core
            val pageCount = core.pageCount
            val fileName = core.fileName

            val validIndex = initialPageIndex.coerceIn(0, pageCount - 1)
            val dims = core.getPageDimensions(validIndex)
            val bitmap = core.renderPage(validIndex, 1080, 0)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    sourceUri = uri,
                    fileName = fileName,
                    pageCount = pageCount,
                    currentPageIndex = validIndex,
                    pageDimensions = dims,
                    currentPageBitmap = bitmap,
                    stamps = emptyList(),
                    selectedStampId = null,
                    signResult = null,
                    errorMessage = null
                )
            }
        }
    }

    fun selectPage(pageIndex: Int) {
        val core = rendererCore ?: return
        if (pageIndex !in 0 until core.pageCount) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, currentPageIndex = pageIndex, selectedStampId = null) }
            val dims = core.getPageDimensions(pageIndex)
            val bitmap = core.renderPage(pageIndex, 1080, 0)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    currentPageIndex = pageIndex,
                    pageDimensions = dims,
                    currentPageBitmap = bitmap
                )
            }
        }
    }

    fun addSignatureStamp(bitmap: Bitmap, isDateStamp: Boolean = false, dateText: String? = null) {
        val state = _uiState.value
        val pageIdx = state.currentPageIndex

        val dims = state.pageDimensions
        val pageAspect = dims?.aspectRatio ?: (state.currentPageBitmap?.let { it.width.toFloat() / it.height.toFloat().coerceAtLeast(1f) } ?: 1f)

        // Calculate proportional default width and height based on bitmap aspect ratio and page aspect ratio
        val bitmapAspect = if (bitmap.height > 0) bitmap.width.toFloat() / bitmap.height.toFloat() else 2.5f
        val defaultWidth = if (isDateStamp) 0.30f else 0.38f
        // normH = normW * (pageAspect / bitmapAspect) ensures un-distorted stamp on both portrait and landscape pages
        val defaultHeight = (defaultWidth * (pageAspect / bitmapAspect)).coerceIn(0.04f, 0.40f)

        // Center on the page initially
        val posX = (0.5f - defaultWidth / 2f).coerceIn(0.05f, 0.95f - defaultWidth)
        val posY = (0.6f - defaultHeight / 2f).coerceIn(0.05f, 0.95f - defaultHeight)

        val newStamp = SignatureStamp(
            pageIndex = pageIdx,
            normalizedX = posX,
            normalizedY = posY,
            normalizedWidth = defaultWidth,
            normalizedHeight = defaultHeight,
            bitmap = bitmap,
            isDateStamp = isDateStamp,
            dateText = dateText
        )

        _uiState.update {
            it.copy(
                stamps = it.stamps + newStamp,
                selectedStampId = newStamp.id
            )
        }
    }

    fun addTodayDateStamp(colorHex: Int) {
        val todayStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())
        val bmp = com.xpdfviewer.signature.SignatureBitmapHelper.createDateStampBitmap(todayStr, colorHex)
        addSignatureStamp(bmp, isDateStamp = true, dateText = todayStr)
    }

    fun selectStamp(id: String?) {
        _uiState.update { it.copy(selectedStampId = id) }
    }

    fun updateStampPosition(id: String, normX: Float, normY: Float) {
        _uiState.update { state ->
            val updated = state.stamps.map { stamp ->
                if (stamp.id == id) {
                    val clampedX = normX.coerceIn(0f, (1f - stamp.normalizedWidth).coerceAtLeast(0f))
                    val clampedY = normY.coerceIn(0f, (1f - stamp.normalizedHeight).coerceAtLeast(0f))
                    stamp.copy(normalizedX = clampedX, normalizedY = clampedY)
                } else stamp
            }
            state.copy(stamps = updated)
        }
    }

    fun updateStampSize(id: String, newWidth: Float, newHeight: Float) {
        _uiState.update { state ->
            val updated = state.stamps.map { stamp ->
                if (stamp.id == id) {
                    val clampedW = newWidth.coerceIn(0.06f, 0.95f)
                    val clampedH = newHeight.coerceIn(0.02f, 0.85f)
                    val clampedX = stamp.normalizedX.coerceIn(0f, (1f - clampedW).coerceAtLeast(0f))
                    val clampedY = stamp.normalizedY.coerceIn(0f, (1f - clampedH).coerceAtLeast(0f))
                    stamp.copy(
                        normalizedWidth = clampedW,
                        normalizedHeight = clampedH,
                        normalizedX = clampedX,
                        normalizedY = clampedY
                    )
                } else stamp
            }
            state.copy(stamps = updated)
        }
    }

    fun removeStamp(id: String) {
        _uiState.update { state ->
            state.copy(
                stamps = state.stamps.filterNot { it.id == id },
                selectedStampId = if (state.selectedStampId == id) null else state.selectedStampId
            )
        }
    }

    fun saveSignatureToStorage(context: Context, bitmap: Bitmap) {
        viewModelScope.launch {
            SignatureStorageManager.saveSignature(context, bitmap)
            loadSavedSignatures()
        }
    }

    fun deleteSavedSignature(file: File) {
        viewModelScope.launch {
            SignatureStorageManager.deleteSignature(file)
            loadSavedSignatures()
        }
    }

    fun loadSavedSignatures() {
        viewModelScope.launch {
            val list = SignatureStorageManager.loadSavedSignatures(getApplication())
            _uiState.update { it.copy(savedSignatures = list) }
        }
    }

    fun signAndExport(
        context: Context,
        destinationUri: Uri,
        fileName: String,
        password: String? = null
    ) {
        val srcUri = _uiState.value.sourceUri ?: return
        val stamps = _uiState.value.stamps
        if (stamps.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSigning = true, errorMessage = null) }
            try {
                val result = PdfSignerEngine.signPdfDocument(
                    context = context,
                    sourceUri = srcUri,
                    destinationUri = destinationUri,
                    outputFileName = fileName,
                    stamps = stamps,
                    password = password
                )
                _uiState.update {
                    it.copy(
                        isSigning = false,
                        signResult = result
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isSigning = false,
                        errorMessage = "Signing failed: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun clearResult() {
        _uiState.update { it.copy(signResult = null, errorMessage = null) }
    }

    fun clearDocument() {
        rendererCore?.close()
        rendererCore = null
        _uiState.update {
            it.copy(
                sourceUri = null,
                fileName = "",
                pageCount = 0,
                currentPageIndex = 0,
                currentPageBitmap = null,
                pageDimensions = null,
                stamps = emptyList(),
                selectedStampId = null,
                signResult = null,
                errorMessage = null,
                isLoading = false,
                isSigning = false
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        rendererCore?.close()
    }
}
