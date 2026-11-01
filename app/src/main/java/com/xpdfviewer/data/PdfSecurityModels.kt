package com.xpdfviewer.data

import android.net.Uri

enum class SecurityToolMode {
    PROTECT,
    UNLOCK
}

data class SecurityPermissions(
    val allowPrinting: Boolean = true,
    val allowContentCopying: Boolean = true,
    val allowAnnotations: Boolean = true
)

data class SecurityProtectionConfig(
    val userPassword: String,
    val ownerPassword: String = userPassword,
    val keyLength: Int = 128,
    val permissions: SecurityPermissions = SecurityPermissions()
)

data class SecurityResult(
    val outputUri: Uri,
    val fileName: String,
    val fileSizeBytes: Long,
    val pageCount: Int,
    val isProtected: Boolean
)

data class PdfSecurityUiState(
    val mode: SecurityToolMode = SecurityToolMode.PROTECT,
    val sourceUri: Uri? = null,
    val fileName: String = "",
    val fileSizeBytes: Long = 0L,
    val pageCount: Int = 0,
    val isSourceEncrypted: Boolean = false,
    val userPassword: String = "",
    val ownerPassword: String = "",
    val confirmPassword: String = "",
    val unlockPassword: String = "",
    val permissions: SecurityPermissions = SecurityPermissions(),
    val keyLength: Int = 128,
    val isAdvancedExpanded: Boolean = false,
    val isProcessing: Boolean = false,
    val result: SecurityResult? = null,
    val showDiscardDialog: Boolean = false,
    val errorMessage: String? = null
) {
    val isProtectValid: Boolean
        get() = sourceUri != null &&
                userPassword.isNotBlank() &&
                userPassword == confirmPassword &&
                (ownerPassword.isBlank() || ownerPassword.length >= 4)

    val isUnlockValid: Boolean
        get() = sourceUri != null && unlockPassword.isNotBlank()
}

