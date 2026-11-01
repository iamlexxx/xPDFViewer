package com.xpdfviewer.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.xpdfviewer.data.PdfSecurityUiState
import com.xpdfviewer.data.SecurityPermissions
import com.xpdfviewer.data.SecurityProtectionConfig
import com.xpdfviewer.data.SecurityResult
import com.xpdfviewer.data.SecurityToolMode
import com.xpdfviewer.pdf.PdfSecurityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PdfSecurityViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PdfSecurityUiState())
    val uiState: StateFlow<PdfSecurityUiState> = _uiState.asStateFlow()

    fun loadDocument(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val fileName = PdfSecurityManager.resolveFileName(context, uri)
            val fileSize = PdfSecurityManager.queryFileSize(context, uri)
            val (isEncrypted, pageCount) = PdfSecurityManager.checkEncryptionStatus(context, uri)

            _uiState.update {
                it.copy(
                    sourceUri = uri,
                    fileName = fileName,
                    fileSizeBytes = fileSize,
                    pageCount = pageCount,
                    isSourceEncrypted = isEncrypted,
                    mode = if (isEncrypted) SecurityToolMode.UNLOCK else SecurityToolMode.PROTECT,
                    userPassword = "",
                    ownerPassword = "",
                    confirmPassword = "",
                    unlockPassword = "",
                    isProcessing = false
                )
            }
        }
    }

    fun setMode(mode: SecurityToolMode) {
        _uiState.update { it.copy(mode = mode, errorMessage = null) }
    }

    fun setUserPassword(password: String) {
        _uiState.update { it.copy(userPassword = password, errorMessage = null) }
    }

    fun setOwnerPassword(password: String) {
        _uiState.update { it.copy(ownerPassword = password, errorMessage = null) }
    }

    fun setConfirmPassword(password: String) {
        _uiState.update { it.copy(confirmPassword = password, errorMessage = null) }
    }

    fun setUnlockPassword(password: String) {
        _uiState.update { it.copy(unlockPassword = password, errorMessage = null) }
    }

    fun setKeyLength(length: Int) {
        _uiState.update { it.copy(keyLength = length) }
    }

    fun togglePrinting(allow: Boolean) {
        _uiState.update {
            it.copy(permissions = it.permissions.copy(allowPrinting = allow))
        }
    }

    fun toggleCopying(allow: Boolean) {
        _uiState.update {
            it.copy(permissions = it.permissions.copy(allowContentCopying = allow))
        }
    }

    fun toggleAnnotations(allow: Boolean) {
        _uiState.update {
            it.copy(permissions = it.permissions.copy(allowAnnotations = allow))
        }
    }

    fun toggleAdvancedSettings() {
        _uiState.update { it.copy(isAdvancedExpanded = !it.isAdvancedExpanded) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun protectDocument(context: Context, destinationUri: Uri) {
        val currentState = _uiState.value
        val srcUri = currentState.sourceUri ?: return

        if (currentState.userPassword != currentState.confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Passwords do not match.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val config = SecurityProtectionConfig(
                userPassword = currentState.userPassword,
                ownerPassword = currentState.ownerPassword.ifBlank { currentState.userPassword },
                keyLength = currentState.keyLength,
                permissions = currentState.permissions
            )

            val success = PdfSecurityManager.protectPdf(
                context = context,
                sourceUri = srcUri,
                destinationUri = destinationUri,
                config = config
            )

            if (success) {
                val outSize = PdfSecurityManager.queryFileSize(context, destinationUri)
                val outName = PdfSecurityManager.resolveFileName(context, destinationUri)
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        result = SecurityResult(
                            outputUri = destinationUri,
                            fileName = outName,
                            fileSizeBytes = outSize,
                            pageCount = currentState.pageCount,
                            isProtected = true
                        )
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "Failed to encrypt and protect document."
                    )
                }
            }
        }
    }

    fun unlockDocument(context: Context, destinationUri: Uri) {
        val currentState = _uiState.value
        val srcUri = currentState.sourceUri ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val result = PdfSecurityManager.unlockPdf(
                context = context,
                sourceUri = srcUri,
                destinationUri = destinationUri,
                password = currentState.unlockPassword
            )

            result.fold(
                onSuccess = {
                    val outSize = PdfSecurityManager.queryFileSize(context, destinationUri)
                    val outName = PdfSecurityManager.resolveFileName(context, destinationUri)
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            result = SecurityResult(
                                outputUri = destinationUri,
                                fileName = outName,
                                fileSizeBytes = outSize,
                                pageCount = currentState.pageCount,
                                isProtected = false
                            )
                        )
                    }
                },
                onFailure = { error ->
                    val message = if (error is InvalidPasswordException) {
                        "Incorrect password. Please verify and try again."
                    } else {
                        "Failed to unlock document: ${error.localizedMessage ?: "Unknown error"}"
                    }
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = message
                        )
                    }
                }
            )
        }
    }

    fun dismissResult() {
        _uiState.update {
            it.copy(
                result = null,
                userPassword = "",
                ownerPassword = "",
                confirmPassword = "",
                unlockPassword = ""
            )
        }
    }

    fun reset() {
        _uiState.value = PdfSecurityUiState()
    }

    fun handleBackPress(onExit: () -> Unit) {
        val state = _uiState.value
        val hasContent = state.sourceUri != null ||
                state.userPassword.isNotBlank() ||
                state.unlockPassword.isNotBlank()
        if (hasContent) {
            _uiState.update { it.copy(showDiscardDialog = true) }
        } else {
            onExit()
        }
    }

    fun dismissDiscardDialog() {
        _uiState.update { it.copy(showDiscardDialog = false) }
    }

    fun confirmDiscard(onExit: () -> Unit) {
        reset()
        onExit()
    }
}

