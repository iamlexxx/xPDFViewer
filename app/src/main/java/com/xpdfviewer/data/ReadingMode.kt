package com.xpdfviewer.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

/**
 * Visual display filter presets for the PDF reading canvas.
 * Applied directly via GPU-accelerated [ColorFilter.colorMatrix] with zero disk/raster overhead.
 */
enum class ReadingMode(
    val id: String,
    val label: String,
    val description: String,
    val swatchBgColor: Color,
    val swatchTextColor: Color,
    val cardBackgroundColor: Color
) {
    DEFAULT(
        id = "default",
        label = "Original",
        description = "Native document colors and white paper background",
        swatchBgColor = Color(0xFFFFFFFF),
        swatchTextColor = Color(0xFF111827),
        cardBackgroundColor = Color(0xFFFFFFFF)
    ),
    INVERTED(
        id = "inverted",
        label = "Night (Dark)",
        description = "Soft dark charcoal background with crisp off-white text",
        swatchBgColor = Color(0xFF1E1E1E),
        swatchTextColor = Color(0xFFE5E7EB),
        cardBackgroundColor = Color(0xFF1E1E1E)
    ),
    SEPIA(
        id = "sepia",
        label = "Warm Sepia",
        description = "Parchment tint filtering 26% of high-energy blue light",
        swatchBgColor = Color(0xFFFBF0D9),
        swatchTextColor = Color(0xFF452B1E),
        cardBackgroundColor = Color(0xFFFBF0D9)
    ),
    HIGH_CONTRAST(
        id = "high_contrast",
        label = "High Contrast",
        description = "Amplifies contrast for faint, aged, or degraded scanned text",
        swatchBgColor = Color(0xFFFFFFFF),
        swatchTextColor = Color(0xFF000000),
        cardBackgroundColor = Color(0xFFFFFFFF)
    ),
    MONOCHROME(
        id = "monochrome",
        label = "Grayscale",
        description = "Clean distraction-free black and white presentation",
        swatchBgColor = Color(0xFFF3F4F6),
        swatchTextColor = Color(0xFF1F2937),
        cardBackgroundColor = Color(0xFFFFFFFF)
    );

    val composeColorFilter: ColorFilter?
        get() = when (this) {
            DEFAULT -> null
            INVERTED -> ColorFilter.colorMatrix(ColorMatrix(INVERTED_MATRIX))
            SEPIA -> ColorFilter.colorMatrix(ColorMatrix(SEPIA_MATRIX))
            HIGH_CONTRAST -> ColorFilter.colorMatrix(ColorMatrix(HIGH_CONTRAST_MATRIX))
            MONOCHROME -> ColorFilter.colorMatrix(ColorMatrix(GRAYSCALE_MATRIX))
        }

    val viewportBackgroundColor: Color?
        get() = when (this) {
            INVERTED -> Color(0xFF121212)
            SEPIA -> Color(0xFFF4E8D1)
            else -> null
        }

    companion object {
        private val INVERTED_MATRIX = floatArrayOf(
            -0.784f,  0.0f,    0.0f,   0.0f, 225f,
             0.0f,   -0.784f,  0.0f,   0.0f, 225f,
             0.0f,    0.0f,   -0.784f, 0.0f, 225f,
             0.0f,    0.0f,    0.0f,   1.0f,   0f
        )

        private val SEPIA_MATRIX = floatArrayOf(
            0.95f, 0.00f, 0.00f, 0f, 0f,
            0.00f, 0.88f, 0.00f, 0f, 0f,
            0.00f, 0.00f, 0.74f, 0f, 0f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        )

        private val HIGH_CONTRAST_MATRIX = floatArrayOf(
            1.5f, 0f,   0f,   0f, -40f,
            0f,   1.5f, 0f,   0f, -40f,
            0f,   0f,   1.5f, 0f, -40f,
            0f,   0f,   0f,   1f,   0f
        )

        private val GRAYSCALE_MATRIX = floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f,     0f,     0f,     1f, 0f
        )

        fun fromId(id: String): ReadingMode {
            return entries.firstOrNull { it.id == id } ?: DEFAULT
        }
    }
}

