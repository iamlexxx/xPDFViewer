package com.xpdfviewer.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * High-performance zoom container for PDF reading.
 * Intercepts multi-touch gestures in PointerEventPass.Initial to guarantee
 * 100% reliable pinch-to-zoom on the very first touch without conflict
 * from child scrollables (LazyColumn).
 */
@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    minScale: Float = 1.0f,
    maxScale: Float = 4.0f,
    onTap: () -> Unit = {},
    content: @Composable (scale: Float) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val coroutineScope = rememberCoroutineScope()

    val currentScale by remember { derivedStateOf { scale } }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { containerSize = it }
            .pointerInput(containerSize) {
                if (containerSize.width <= 0 || containerSize.height <= 0) return@pointerInput

                val touchSlop = viewConfiguration.touchSlop
                val doubleTapTimeout = 300L
                val doubleTapSlop = touchSlop * 2.5f

                var lastTapTime = 0L
                var lastTapPos = Offset.Zero
                var pendingTapJob: Job? = null

                awaitPointerEventScope {
                    while (true) {
                        val initialEvent = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = initialEvent.changes.filter { it.pressed }

                        if (pressed.size >= 2) {
                            // Multi-touch: 2+ fingers down = Pinch zoom & pan
                            pendingTapJob?.cancel()
                            pendingTapJob = null

                            // Consume immediately in Initial pass so LazyColumn never steals the gesture
                            initialEvent.changes.forEach { it.consume() }

                            var prevCentroid = (pressed[0].position + pressed[1].position) / 2f
                            var prevDistance = distance(pressed[0].position, pressed[1].position)

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val currentPressed = event.changes.filter { it.pressed }

                                if (currentPressed.size < 2) {
                                    break
                                }

                                event.changes.forEach { it.consume() }

                                val p0 = currentPressed[0]
                                val p1 = currentPressed[1]
                                val currentCentroid = (p0.position + p1.position) / 2f
                                val currentDistance = distance(p0.position, p1.position)

                                if (prevDistance > 1f && currentDistance > 1f) {
                                    val zoomFactor = currentDistance / prevDistance
                                    val panDelta = currentCentroid - prevCentroid

                                    val newScale = (scale * zoomFactor).coerceIn(minScale * 0.75f, maxScale * 1.5f)
                                    val boxCenter = Offset(containerSize.width / 2f, containerSize.height / 2f)
                                    val focalDelta = (currentCentroid - boxCenter) * (1f - zoomFactor)
                                    val newOffset = offset * zoomFactor + focalDelta + panDelta

                                    scale = newScale
                                    offset = clampOffset(newOffset, newScale, containerSize)
                                }

                                prevCentroid = currentCentroid
                                prevDistance = currentDistance
                            }

                            // Elastic snap back when fingers released
                            if (scale < minScale) {
                                coroutineScope.launch {
                                    val startScale = scale
                                    val startOffset = offset
                                    animate(0f, 1f, animationSpec = tween(200)) { fraction, _ ->
                                        scale = startScale + (minScale - startScale) * fraction
                                        offset = startOffset * (1f - fraction)
                                    }
                                    scale = minScale
                                    offset = Offset.Zero
                                }
                            } else if (scale > maxScale) {
                                coroutineScope.launch {
                                    val startScale = scale
                                    animate(0f, 1f, animationSpec = tween(200)) { fraction, _ ->
                                        scale = startScale + (maxScale - startScale) * fraction
                                    }
                                    scale = maxScale
                                    offset = clampOffset(offset, maxScale, containerSize)
                                }
                            }
                        } else if (pressed.size == 1) {
                            // Single pointer touch tracking
                            val downPointer = pressed[0]
                            val downPos = downPointer.position
                            val downTime = downPointer.uptimeMillis
                            var movedPastSlop = false
                            var hadMultiTouch = false
                            var prevPos = downPos

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val currentPressed = event.changes.filter { it.pressed }

                                if (currentPressed.size >= 2) {
                                    hadMultiTouch = true
                                    break
                                }

                                if (currentPressed.isEmpty()) {
                                    // Finger lifted: evaluate tap or double tap
                                    val upPointer = event.changes.firstOrNull()
                                    val upPos = upPointer?.position ?: prevPos
                                    val upTime = upPointer?.uptimeMillis ?: System.currentTimeMillis()

                                    if (!hadMultiTouch && !movedPastSlop && (upTime - downTime) < 350L) {
                                        val currentTime = System.currentTimeMillis()
                                        val timeSinceLast = currentTime - lastTapTime
                                        val distFromLast = distance(upPos, lastTapPos)

                                        if (timeSinceLast in 40L..doubleTapTimeout && distFromLast < doubleTapSlop) {
                                            // Double tap: toggle between 1.0x and 2.5x
                                            pendingTapJob?.cancel()
                                            pendingTapJob = null
                                            lastTapTime = 0L

                                            val targetScale = if (scale > 1.2f) 1.0f else 2.5f
                                            val boxCenter = Offset(containerSize.width / 2f, containerSize.height / 2f)
                                            val targetOffset = if (targetScale > 1.0f) {
                                                clampOffset(
                                                    (boxCenter - upPos) * (targetScale - 1f),
                                                    targetScale,
                                                    containerSize
                                                )
                                            } else {
                                                Offset.Zero
                                            }

                                            coroutineScope.launch {
                                                val startScale = scale
                                                val startOffset = offset
                                                animate(0f, 1f, animationSpec = tween(250)) { fraction, _ ->
                                                    scale = startScale + (targetScale - startScale) * fraction
                                                    offset = startOffset + (targetOffset - startOffset) * fraction
                                                }
                                                scale = targetScale
                                                offset = targetOffset
                                            }
                                        } else {
                                            // Single tap: queue callback
                                            lastTapTime = currentTime
                                            lastTapPos = upPos
                                            pendingTapJob?.cancel()
                                            pendingTapJob = coroutineScope.launch {
                                                delay(doubleTapTimeout)
                                                onTap()
                                            }
                                        }
                                    }
                                    break
                                }

                                val pointer = currentPressed[0]
                                val pan = pointer.position - prevPos
                                prevPos = pointer.position

                                if (!movedPastSlop && distance(pointer.position, downPos) > touchSlop) {
                                    movedPastSlop = true
                                    pendingTapJob?.cancel()
                                    pendingTapJob = null
                                }

                                if (scale > 1.05f && movedPastSlop) {
                                    // When zoomed in, 1-finger drag pans the viewport
                                    pointer.consume()
                                    val newOffset = clampOffset(offset + pan, scale, containerSize)
                                    offset = newOffset
                                }
                                // When scale <= 1.05f, do not consume so LazyColumn handles vertical scroll
                            }
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
    ) {
        content(currentScale)
    }
}

private fun distance(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}

private fun clampOffset(
    offset: Offset,
    scale: Float,
    containerSize: IntSize
): Offset {
    if (scale <= 1.0f || containerSize.width <= 0 || containerSize.height <= 0) {
        return Offset.Zero
    }
    val maxOffsetX = ((containerSize.width * scale - containerSize.width) / 2f).coerceAtLeast(0f)
    val maxOffsetY = ((containerSize.height * scale - containerSize.height) / 2f).coerceAtLeast(0f)
    return Offset(
        x = offset.x.coerceIn(-maxOffsetX, maxOffsetX),
        y = offset.y.coerceIn(-maxOffsetY, maxOffsetY)
    )
}
