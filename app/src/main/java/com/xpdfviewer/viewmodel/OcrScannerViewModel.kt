package com.xpdfviewer.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xpdfviewer.ocr.OcrScannerEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OcrScannerUiState(
    val imageUri: Uri? = null,
    val imageBitmap: Bitmap? = null,
    val extractedText: String = "",
    val isScanning: Boolean = false,
    val errorMessage: String? = null
)

class OcrScannerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(OcrScannerUiState())
    val uiState: StateFlow<OcrScannerUiState> = _uiState.asStateFlow()

    fun scanImageUri(context: Context, uri: Uri) {
        _uiState.update {
            it.copy(
                imageUri = uri,
                imageBitmap = null,
                isScanning = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val result = OcrScannerEngine.recognizeTextFromUri(context.applicationContext, uri)
            result.onSuccess { text ->
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        extractedText = text,
                        errorMessage = if (text.isBlank()) "No readable text detected in this image." else null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        errorMessage = "OCR Recognition failed: ${err.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun scanBitmap(bitmap: Bitmap) {
        _uiState.update {
            it.copy(
                imageBitmap = bitmap,
                imageUri = null,
                isScanning = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val result = OcrScannerEngine.recognizeTextFromBitmap(bitmap)
            result.onSuccess { text ->
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        extractedText = text,
                        errorMessage = if (text.isBlank()) "No readable text detected in this image." else null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        errorMessage = "OCR Recognition failed: ${err.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun updateExtractedText(newText: String) {
        _uiState.update { it.copy(extractedText = newText) }
    }

    fun clear() {
        _uiState.value = OcrScannerUiState()
    }

    fun copyToClipboard(context: Context) {
        val text = _uiState.value.extractedText
        if (text.isBlank()) return
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Scanned Document Text", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun shareText(context: Context) {
        val text = _uiState.value.extractedText
        if (text.isBlank()) return
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "OCR Scanned Text")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(intent, "Share Scanned Text"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
