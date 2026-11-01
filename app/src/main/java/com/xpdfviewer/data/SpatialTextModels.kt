package com.xpdfviewer.data

import android.graphics.RectF

/**
 * Normalized bounding box [0f..1f] relative to document page dimensions.
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f

    fun contains(x: Float, y: Float): Boolean {
        return x in left..right && y in top..bottom
    }

    fun toAndroidRectF(pagePixelWidth: Float, pagePixelHeight: Float): RectF {
        return RectF(
            left * pagePixelWidth,
            top * pagePixelHeight,
            right * pagePixelWidth,
            bottom * pagePixelHeight
        )
    }
}

/**
 * Represents a discrete word on a PDF page with its normalized coordinate bounds.
 */
data class SpatialWord(
    val index: Int,
    val text: String,
    val bounds: NormalizedRect,
    val lineIndex: Int = 0
)

/**
 * Full spatial textual mapping for an individual PDF page.
 */
data class PageSpatialData(
    val pageIndex: Int,
    val words: List<SpatialWord> = emptyList(),
    val fullText: String = "",
    val pagePixelWidth: Float = 1f,
    val pagePixelHeight: Float = 1f
) {
    val wordCount: Int get() = words.size
    val charCount: Int get() = fullText.length
    val isEmpty: Boolean get() = words.isEmpty()
}

