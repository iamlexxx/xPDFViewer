package com.xpdfviewer.data

import android.graphics.Bitmap
import android.net.Uri

enum class WatermarkTab(val label: String) {
    WATERMARK("Watermark"),
    PAGINATION("Page Numbers")
}

enum class WatermarkType(val label: String) {
    TEXT("Text Watermark"),
    IMAGE("Image / Logo")
}

enum class WatermarkRotation(val degrees: Float, val label: String) {
    DIAGONAL(-45f, "Diagonal (-45°)"),
    HORIZONTAL(0f, "Horizontal (0°)"),
    VERTICAL(90f, "Vertical (90°)")
}

enum class WatermarkLayer(val label: String) {
    OVER_CONTENT("Foreground (Over Content)"),
    UNDER_CONTENT("Background (Under Content)")
}

enum class WatermarkColor(
    val label: String,
    val hex: Long,
    val r: Float,
    val g: Float,
    val b: Float
) {
    GRAY("Neutral Gray", 0xFF6B7280, 0.42f, 0.45f, 0.50f),
    RED("Crimson Red", 0xFFEF4444, 0.93f, 0.27f, 0.27f),
    BLUE("Royal Blue", 0xFF3B82F6, 0.23f, 0.51f, 0.96f),
    GREEN("Forest Green", 0xFF10B981, 0.06f, 0.73f, 0.51f),
    BLACK("Pitch Black", 0xFF111827, 0.07f, 0.09f, 0.15f)
}

enum class PaginationAnchor(
    val label: String,
    val isTop: Boolean,
    val isLeft: Boolean,
    val isCenter: Boolean
) {
    TOP_LEFT("Top Left", true, true, false),
    TOP_CENTER("Top Center", true, false, true),
    TOP_RIGHT("Top Right", true, false, false),
    BOTTOM_LEFT("Bottom Left", false, true, false),
    BOTTOM_CENTER("Bottom Center", false, false, true),
    BOTTOM_RIGHT("Bottom Right", false, false, false)
}

enum class PaginationFormat(val label: String, val example: String) {
    PAGE_OF_TOTAL("Page {page} of {total}", "Page 1 of 12"),
    PAGE_ONLY("{page}", "1"),
    PAGE_PREFIX("Page {page}", "Page 1"),
    DASHES("- {page} -", "- 1 -"),
    BATES("DOC-{bates}", "DOC-00001")
}

enum class WatermarkPageScope(val label: String) {
    ALL_PAGES("All Pages"),
    SKIP_FIRST("Skip Cover / First Page"),
    CUSTOM_RANGE("Custom Range")
}

data class WatermarkConfig(
    val text: String = "CONFIDENTIAL",
    val imageUri: Uri? = null,
    val type: WatermarkType = WatermarkType.TEXT,
    val fontSize: Float = 52f,
    val opacity: Float = 0.25f,
    val rotation: WatermarkRotation = WatermarkRotation.DIAGONAL,
    val layer: WatermarkLayer = WatermarkLayer.OVER_CONTENT,
    val color: WatermarkColor = WatermarkColor.GRAY,
    val pageScope: WatermarkPageScope = WatermarkPageScope.ALL_PAGES,
    val customRangeText: String = ""
)

data class PaginationConfig(
    val isEnabled: Boolean = false,
    val format: PaginationFormat = PaginationFormat.PAGE_OF_TOTAL,
    val anchor: PaginationAnchor = PaginationAnchor.BOTTOM_CENTER,
    val fontSize: Float = 10f,
    val marginPadding: Float = 24f,
    val color: WatermarkColor = WatermarkColor.GRAY,
    val startNumber: Int = 1,
    val pageScope: WatermarkPageScope = WatermarkPageScope.ALL_PAGES,
    val customRangeText: String = ""
)

data class WatermarkResult(
    val success: Boolean,
    val outputUri: Uri? = null,
    val outputFileName: String = "",
    val processedPages: Int = 0,
    val errorMessage: String? = null
)

data class WatermarkUiState(
    val sourceUri: Uri? = null,
    val fileName: String = "",
    val fileSizeBytes: Long = 0L,
    val pageCount: Int = 0,
    val activeTab: WatermarkTab = WatermarkTab.WATERMARK,
    val watermarkConfig: WatermarkConfig = WatermarkConfig(),
    val paginationConfig: PaginationConfig = PaginationConfig(),
    val previewBitmap: Bitmap? = null,
    val previewPageIndex: Int = 0,
    val isRenderingPreview: Boolean = false,
    val isProcessing: Boolean = false,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val progressMessage: String = "",
    val resultDialogVisible: Boolean = false,
    val lastResult: WatermarkResult? = null,
    val errorMessage: String? = null
)

