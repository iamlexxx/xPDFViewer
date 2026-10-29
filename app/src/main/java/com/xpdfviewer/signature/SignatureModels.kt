package com.xpdfviewer.signature

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.Color
import java.util.UUID

/**
 * Represents a signature or date stamp placed onto a PDF page.
 * Coordinates are normalized (0.0f .. 1.0f) relative to page width and height,
 * with (0,0) at the TOP-LEFT of the page.
 */
data class SignatureStamp(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    val normalizedX: Float,
    val normalizedY: Float,
    val normalizedWidth: Float,
    val normalizedHeight: Float,
    val bitmap: Bitmap,
    val isDateStamp: Boolean = false,
    val dateText: String? = null
)

data class SignaturePenColor(
    val name: String,
    val color: Color,
    val hexColor: Int
)

data class SignResult(
    val outputUri: Uri,
    val fileName: String,
    val fileSizeBytes: Long,
    val pageCount: Int,
    val signaturesCount: Int,
    val isEncrypted: Boolean
)
