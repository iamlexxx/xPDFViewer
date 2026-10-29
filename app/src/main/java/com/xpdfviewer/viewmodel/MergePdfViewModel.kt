package com.xpdfviewer.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xpdfviewer.pdf.PdfDocumentModifier
import com.xpdfviewer.pdf.PdfRendererCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class MergeDocumentItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val name: String,
    val sizeFormatted: String,
    val pageCount: Int
)

data class MergePdfUiState(
    val documents: List<MergeDocumentItem> = emptyList(),
    val isMerging: Boolean = false,
    val isSuccess: Boolean = false,
    val mergedUri: Uri? = null,
    val mergedFileName: String = "",
    val mergedPageCount: Int = 0,
    val errorMessage: String? = null
) {
    val totalPages: Int get() = documents.sumOf { it.pageCount }
    val canMerge: Boolean get() = documents.size >= 2 && !isMerging
}

class MergePdfViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MergePdfUiState())
    val uiState: StateFlow<MergePdfUiState> = _uiState.asStateFlow()

    fun addDocuments(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val newItems = mutableListOf<MergeDocumentItem>()
            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    val name = PdfRendererCore.resolveFileName(context, uri)
                    val sizeFormatted = resolveFileSize(context, uri)
                    val pageCount = PdfDocumentModifier.getPdfPageCount(context, uri)
                    newItems.add(
                        MergeDocumentItem(
                            uri = uri,
                            name = name,
                            sizeFormatted = sizeFormatted,
                            pageCount = if (pageCount > 0) pageCount else 1
                        )
                    )
                }
            }

            _uiState.update { current ->
                current.copy(
                    documents = current.documents + newItems,
                    errorMessage = null
                )
            }
        }
    }

    fun moveItemUp(index: Int) {
        if (index <= 0) return
        _uiState.update { current ->
            val list = current.documents.toMutableList()
            val item = list.removeAt(index)
            list.add(index - 1, item)
            current.copy(documents = list)
        }
    }

    fun moveItemDown(index: Int) {
        _uiState.update { current ->
            if (index >= current.documents.size - 1) return@update current
            val list = current.documents.toMutableList()
            val item = list.removeAt(index)
            list.add(index + 1, item)
            current.copy(documents = list)
        }
    }

    fun removeDocument(index: Int) {
        _uiState.update { current ->
            if (index !in current.documents.indices) return@update current
            val list = current.documents.toMutableList()
            list.removeAt(index)
            current.copy(documents = list)
        }
    }

    fun clearAll() {
        _uiState.update {
            it.copy(
                documents = emptyList(),
                errorMessage = null,
                isSuccess = false,
                mergedUri = null,
                mergedFileName = "",
                mergedPageCount = 0
            )
        }
    }

    /**
     * Merges selected documents directly into [destinationUri] chosen by user,
     * and clears the selected documents list upon success.
     */
    fun mergeDocumentsToDestination(context: Context, destinationUri: Uri, chosenFileName: String) {
        val currentDocs = _uiState.value.documents
        if (currentDocs.size < 2) {
            _uiState.update { it.copy(errorMessage = "Please select at least 2 PDF files to merge.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isMerging = true, errorMessage = null) }
            val uris = currentDocs.map { it.uri }
            val result = PdfDocumentModifier.mergePdfDocumentsToDestination(
                context = context,
                sourceUris = uris,
                destinationUri = destinationUri
            )

            result.fold(
                onSuccess = { totalPages ->
                    _uiState.update {
                        it.copy(
                            documents = emptyList(), // Clear selected files after merging
                            isMerging = false,
                            isSuccess = true,
                            mergedUri = destinationUri,
                            mergedFileName = chosenFileName,
                            mergedPageCount = totalPages
                        )
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Merged PDF saved successfully!", Toast.LENGTH_SHORT).show()
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isMerging = false,
                            errorMessage = error.localizedMessage ?: "Failed to merge PDF documents."
                        )
                    }
                }
            )
        }
    }

    fun shareMergedFile(context: Context) {
        val uri = _uiState.value.mergedUri ?: return
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Merged PDF"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error sharing file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun resetMerge() {
        _uiState.update {
            it.copy(
                isSuccess = false,
                mergedUri = null,
                mergedFileName = "",
                mergedPageCount = 0,
                errorMessage = null
            )
        }
    }

    private fun resolveFileSize(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        val sizeBytes = cursor.getLong(sizeIndex)
                        if (sizeBytes > 0) {
                            val mb = sizeBytes / (1024f * 1024f)
                            return if (mb >= 1.0f) {
                                String.format("%.1f MB", mb)
                            } else {
                                val kb = sizeBytes / 1024f
                                String.format("%.0f KB", kb)
                            }
                        }
                    }
                }
            }
        } catch (ignored: Exception) {}
        return "Unknown size"
    }
}
