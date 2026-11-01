package com.xpdfviewer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.xpdfviewer.data.PageSpatialData
import com.xpdfviewer.data.SpatialWord

@Composable
fun PdfTextOverlayCanvas(
    spatialData: PageSpatialData,
    selectedIndices: Set<Int>,
    onWordTap: (Int) -> Unit,
    onWordRangeSelect: (startIndex: Int, endIndex: Int) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val handleColor = MaterialTheme.colorScheme.primary

    var dragStartIndex by remember { mutableStateOf<Int?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(spatialData) {
                detectTapGestures(
                    onTap = { offset ->
                        val normX = offset.x / size.width.toFloat()
                        val normY = offset.y / size.height.toFloat()
                        val tappedWord = spatialData.words.find { it.bounds.contains(normX, normY) }
                        if (tappedWord != null) {
                            onWordTap(tappedWord.index)
                        } else {
                            onClearSelection()
                        }
                    },
                    onDoubleTap = { offset ->
                        val normX = offset.x / size.width.toFloat()
                        val normY = offset.y / size.height.toFloat()
                        val tappedWord = spatialData.words.find { it.bounds.contains(normX, normY) }
                        if (tappedWord != null) {
                            onWordTap(tappedWord.index)
                        }
                    }
                )
            }
            .pointerInput(spatialData) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        val normX = startOffset.x / size.width.toFloat()
                        val normY = startOffset.y / size.height.toFloat()
                        val startWord = spatialData.words.find { it.bounds.contains(normX, normY) }
                            ?: spatialData.words.minByOrNull {
                                val dx = it.bounds.centerX - normX
                                val dy = it.bounds.centerY - normY
                                dx * dx + dy * dy
                            }
                        if (startWord != null) {
                            dragStartIndex = startWord.index
                            onWordRangeSelect(startWord.index, startWord.index)
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val startIdx = dragStartIndex ?: return@detectDragGestures
                        val normX = change.position.x / size.width.toFloat()
                        val normY = change.position.y / size.height.toFloat()
                        val currentWord = spatialData.words.find { it.bounds.contains(normX, normY) }
                            ?: spatialData.words.minByOrNull {
                                val dx = it.bounds.centerX - normX
                                val dy = it.bounds.centerY - normY
                                dx * dx + dy * dy
                            }
                        if (currentWord != null) {
                            onWordRangeSelect(startIdx, currentWord.index)
                        }
                    },
                    onDragEnd = {
                        dragStartIndex = null
                    },
                    onDragCancel = {
                        dragStartIndex = null
                    }
                )
            }
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // 1. Draw rounded highlight boxes over selected words
        for (word in spatialData.words) {
            if (word.index in selectedIndices) {
                val leftPx = word.bounds.left * canvasWidth
                val topPx = word.bounds.top * canvasHeight
                val widthPx = word.bounds.width * canvasWidth
                val heightPx = word.bounds.height * canvasHeight

                drawRoundRect(
                    color = highlightColor,
                    topLeft = Offset(leftPx - 2f, topPx - 1f),
                    size = Size(widthPx + 4f, heightPx + 2f),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }
        }

        // 2. Draw selection handles at boundary words
        val selectedWords = spatialData.words.filter { it.index in selectedIndices }
        if (selectedWords.isNotEmpty()) {
            val firstWord = selectedWords.first()
            val lastWord = selectedWords.last()

            val handleRadius = 6.dp.toPx()

            // Start handle (top-left of first word)
            val startX = firstWord.bounds.left * canvasWidth
            val startY = firstWord.bounds.top * canvasHeight
            drawCircle(
                color = handleColor,
                radius = handleRadius,
                center = Offset(startX, startY)
            )

            // End handle (bottom-right of last word)
            val endX = lastWord.bounds.right * canvasWidth
            val endY = lastWord.bounds.bottom * canvasHeight
            drawCircle(
                color = handleColor,
                radius = handleRadius,
                center = Offset(endX, endY)
            )
        }
    }
}

