package com.xpdfviewer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.xpdfviewer.data.SearchMatch

/**
 * Transparent canvas drawing search highlight bounding boxes directly over the rendered page bitmap.
 *
 * @param matches All search matches occurring on the active page.
 * @param currentMatchIndex Global match index of the currently focused match (-1 if none).
 * @param modifier Compose layout modifier.
 */
@Composable
fun PdfSearchHighlightCanvas(
    matches: List<SearchMatch>,
    currentMatchIndex: Int,
    modifier: Modifier = Modifier
) {
    if (matches.isEmpty()) return

    val inactiveFillColor = Color(0xFFFFEB3B).copy(alpha = 0.38f)
    val inactiveStrokeColor = Color(0xFFFBC02D).copy(alpha = 0.60f)
    val activeFillColor = Color(0xFFFF9800).copy(alpha = 0.55f)
    val activeStrokeColor = Color(0xFFE65100)

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val cornerRadius = CornerRadius(3.dp.toPx())
        val strokeWidthInactive = 1.dp.toPx()
        val strokeWidthActive = 2.dp.toPx()

        for (match in matches) {
            val isCurrent = (match.matchIndex == currentMatchIndex)

            val fillColor = if (isCurrent) activeFillColor else inactiveFillColor
            val strokeColor = if (isCurrent) activeStrokeColor else inactiveStrokeColor
            val strokeWidth = if (isCurrent) strokeWidthActive else strokeWidthInactive

            for (bound in match.bounds) {
                val left = bound.left * canvasWidth
                val top = bound.top * canvasHeight
                val width = (bound.width * canvasWidth).coerceAtLeast(4f)
                val height = (bound.height * canvasHeight).coerceAtLeast(4f)

                // Fill highlight
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = cornerRadius
                )

                // Outline border
                drawRoundRect(
                    color = strokeColor,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = cornerRadius,
                    style = Stroke(width = strokeWidth)
                )
            }
        }
    }
}

