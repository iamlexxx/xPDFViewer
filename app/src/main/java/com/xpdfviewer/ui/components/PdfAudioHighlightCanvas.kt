package com.xpdfviewer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.xpdfviewer.data.NormalizedRect

@Composable
fun PdfAudioHighlightCanvas(
    sentenceWordBounds: List<NormalizedRect>,
    activeWordBounds: List<NormalizedRect> = emptyList(),
    onTapCoordinate: ((Float, Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (sentenceWordBounds.isEmpty() && activeWordBounds.isEmpty()) return

    val tapModifier = if (onTapCoordinate != null) {
        Modifier.pointerInput(onTapCoordinate) {
            detectTapGestures { offset ->
                if (size.width > 0 && size.height > 0) {
                    onTapCoordinate(offset.x / size.width, offset.y / size.height)
                }
            }
        }
    } else {
        Modifier
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .then(tapModifier)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // 1. Sentence-level background highlight (soft warm amber)
        val sentenceHighlightColor = Color(0xFFF59E0B).copy(alpha = 0.20f)
        val sentenceStrokeColor = Color(0xFFD97706).copy(alpha = 0.40f)
        val sentenceStrokeWidthPx = 1.2.dp.toPx()
        val cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

        for (rect in sentenceWordBounds) {
            val left = rect.left * canvasWidth
            val top = rect.top * canvasHeight
            val width = rect.width * canvasWidth
            val height = rect.height * canvasHeight

            if (width > 0 && height > 0) {
                drawRoundRect(
                    color = sentenceHighlightColor,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = cornerRadius,
                    style = Fill
                )
                drawRoundRect(
                    color = sentenceStrokeColor,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = cornerRadius,
                    style = Stroke(width = sentenceStrokeWidthPx)
                )
            }
        }

        // 2. Word-level karaoke spotlight (vibrant focused amber pill)
        if (activeWordBounds.isNotEmpty()) {
            val wordFillColor = Color(0xFFD97706).copy(alpha = 0.45f)
            val wordStrokeColor = Color(0xFF92400E)
            val wordStrokeWidthPx = 2.dp.toPx()
            val wordCornerRadius = CornerRadius(4.5.dp.toPx(), 4.5.dp.toPx())

            for (rect in activeWordBounds) {
                val left = (rect.left * canvasWidth) - 1.dp.toPx()
                val top = (rect.top * canvasHeight) - 1.dp.toPx()
                val width = (rect.width * canvasWidth) + 2.dp.toPx()
                val height = (rect.height * canvasHeight) + 2.dp.toPx()

                if (width > 0 && height > 0) {
                    drawRoundRect(
                        color = wordFillColor,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        cornerRadius = wordCornerRadius,
                        style = Fill
                    )
                    drawRoundRect(
                        color = wordStrokeColor,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        cornerRadius = wordCornerRadius,
                        style = Stroke(width = wordStrokeWidthPx)
                    )
                }
            }
        }
    }
}
