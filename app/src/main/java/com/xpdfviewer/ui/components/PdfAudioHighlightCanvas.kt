package com.xpdfviewer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.xpdfviewer.data.NormalizedRect

@Composable
fun PdfAudioHighlightCanvas(
    wordBounds: List<NormalizedRect>,
    modifier: Modifier = Modifier
) {
    if (wordBounds.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val highlightColor = Color(0xFFF59E0B).copy(alpha = 0.30f)
        val strokeColor = Color(0xFFD97706).copy(alpha = 0.60f)
        val strokeWidthPx = 1.5.dp.toPx()
        val cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

        for (rect in wordBounds) {
            val left = rect.left * canvasWidth
            val top = rect.top * canvasHeight
            val width = rect.width * canvasWidth
            val height = rect.height * canvasHeight

            if (width > 0 && height > 0) {
                // Background fill
                drawRoundRect(
                    color = highlightColor,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = cornerRadius,
                    style = Fill
                )

                // Outline border
                drawRoundRect(
                    color = strokeColor,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = cornerRadius,
                    style = Stroke(width = strokeWidthPx)
                )
            }
        }
    }
}

