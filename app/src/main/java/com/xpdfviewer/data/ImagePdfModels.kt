package com.xpdfviewer.data

import android.net.Uri
import java.util.UUID

enum class ConverterMode(val label: String) {
    IMAGE_TO_PDF("Images to PDF"),
    PDF_TO_IMAGE("PDF to Images")
}

enum class PageSizePreset(val label: String, val widthPt: Float, val heightPt: Float) {
    A4("A4 Standard", 595.27557f, 841.8898f),
    US_LETTER("US Letter", 612.0f, 792.0f),
    FIT_TO_IMAGE("Fit Image (Borderless)", 0f, 0f)
}

enum class PageOrientationPreset(val label: String) {
    AUTO("Auto-Detect"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class MarginPreset(val label: String, val paddingPt: Float) {
    NONE("No Margins (0 pt)", 0f),
    COMPACT("Compact (18 pt)", 18f),
    STANDARD("Standard (36 pt)", 36f)
}

enum class ImageQualityPreset(val label: String, val qualityInt: Int) {
    HIGH("High (90%)", 90),
    MEDIUM("Medium (75%)", 75),
    COMPACT("Compact (55%)", 55)
}

enum class ImageExportFormat(val label: String, val extension: String, val mimeType: String) {
    JPEG("JPEG (.jpg)", "jpg", "image/jpeg"),
    PNG("PNG (.png)", "png", "image/png")
}

enum class DpiPreset(val label: String, val scaleFactor: Float) {
    STANDARD("Standard (150 DPI)", 1.5f),
    HIGH("High Quality (300 DPI)", 2.5f),
    ULTRA("Ultra Archival (450 DPI)", 3.5f)
}

data class SelectedImageItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val rotationDegrees: Int = 0
)

data class ImageToPdfConfig(
    val pageSize: PageSizePreset = PageSizePreset.A4,
    val orientation: PageOrientationPreset = PageOrientationPreset.AUTO,
    val margin: MarginPreset = MarginPreset.NONE,
    val quality: ImageQualityPreset = ImageQualityPreset.HIGH,
    val outputPdfName: String = "Images_Export.pdf"
)

data class PdfToImageConfig(
    val format: ImageExportFormat = ImageExportFormat.JPEG,
    val dpi: DpiPreset = DpiPreset.STANDARD,
    val pageScope: WatermarkPageScope = WatermarkPageScope.ALL_PAGES,
    val customRangeText: String = "",
    val zipArchive: Boolean = true
)

data class ConverterResult(
    val success: Boolean,
    val outputUri: Uri? = null,
    val outputFileName: String = "",
    val processedCount: Int = 0,
    val errorMessage: String? = null
)

data class ImagePdfUiState(
    val activeMode: ConverterMode = ConverterMode.IMAGE_TO_PDF,
    val selectedImages: List<SelectedImageItem> = emptyList(),
    val imageToPdfConfig: ImageToPdfConfig = ImageToPdfConfig(),
    val sourcePdfUri: Uri? = null,
    val pdfFileName: String = "",
    val pdfFileSizeBytes: Long = 0L,
    val pdfPageCount: Int = 0,
    val pdfToImageConfig: PdfToImageConfig = PdfToImageConfig(),
    val isProcessing: Boolean = false,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val progressMessage: String = "",
    val resultDialogVisible: Boolean = false,
    val lastResult: ConverterResult? = null,
    val errorMessage: String? = null
)

