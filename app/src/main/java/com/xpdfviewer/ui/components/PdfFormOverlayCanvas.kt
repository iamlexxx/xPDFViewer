package com.xpdfviewer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.xpdfviewer.data.FormFieldItem
import com.xpdfviewer.data.FormFieldType

@Composable
fun PdfFormOverlayCanvas(
    fields: List<FormFieldItem>,
    activeFieldId: String?,
    onFieldClick: (FormFieldItem) -> Unit,
    onToggleCheckbox: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(fields) {
                detectTapGestures { tapOffset ->
                    val canvasW = size.width.toFloat()
                    val canvasH = size.height.toFloat()
                    if (canvasW <= 0f || canvasH <= 0f) return@detectTapGestures

                    val normX = tapOffset.x / canvasW
                    val normY = tapOffset.y / canvasH

                    // Find tapped field (favor smaller inner targets like checkboxes if overlapping)
                    val tapped = fields
                        .filter { f ->
                            normX in f.bounds.left..f.bounds.right && normY in f.bounds.top..f.bounds.bottom
                        }
                        .minByOrNull { f -> (f.bounds.right - f.bounds.left) * (f.bounds.bottom - f.bounds.top) }

                    if (tapped != null) {
                        if (tapped.type == FormFieldType.CHECKBOX) {
                            onToggleCheckbox(tapped.id, !tapped.isChecked)
                        } else {
                            onFieldClick(tapped)
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            for (field in fields) {
                val left = field.bounds.left * canvasW
                val top = field.bounds.top * canvasH
                val w = (field.bounds.right - field.bounds.left) * canvasW
                val h = (field.bounds.bottom - field.bounds.top) * canvasH

                if (w <= 0f || h <= 0f) continue

                val isActive = field.id == activeFieldId
                val fillColor = when {
                    isActive -> Color(0xFF2563EB).copy(alpha = 0.22f)
                    field.type == FormFieldType.CHECKBOX -> Color(0xFF3B82F6).copy(alpha = 0.10f)
                    field.hasValue -> Color(0xFF3B82F6).copy(alpha = 0.08f)
                    else -> Color(0xFF3B82F6).copy(alpha = 0.14f)
                }

                val strokeColor = if (isActive) {
                    Color(0xFF1D4ED8)
                } else {
                    Color(0xFF3B82F6).copy(alpha = 0.45f)
                }

                val strokeWidth = if (isActive) 2.5f else 1.2f
                val cornerRadius = CornerRadius(4f, 4f)

                // Fill field bounding rect
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                    cornerRadius = cornerRadius
                )

                // Draw bounding outline
                drawRoundRect(
                    color = strokeColor,
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                    cornerRadius = cornerRadius,
                    style = Stroke(width = strokeWidth)
                )

                // Checkbox mark indicator
                if (field.type == FormFieldType.CHECKBOX && field.isChecked) {
                    val checkPaddingX = w * 0.22f
                    val checkPaddingY = h * 0.22f

                    val checkPath = Path().apply {
                        moveTo(left + checkPaddingX, top + h * 0.52f)
                        lineTo(left + w * 0.42f, top + h - checkPaddingY)
                        lineTo(left + w - checkPaddingX, top + checkPaddingY)
                    }

                    drawPath(
                        path = checkPath,
                        color = Color(0xFF16A34A),
                        style = Stroke(
                            width = (w * 0.18f).coerceIn(2.5f, 6f),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }
    }
}

