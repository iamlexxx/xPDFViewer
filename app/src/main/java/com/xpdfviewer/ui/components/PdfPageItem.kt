package com.xpdfviewer.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xpdfviewer.viewmodel.PdfViewerViewModel

@Composable
fun PdfPageItem(
    pageIndex: Int,
    viewModel: PdfViewerViewModel,
    onLongPress: () -> Unit = {},
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier
) {
    var pageBitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    var aspectRatio by remember(pageIndex) { mutableFloatStateOf(1.0f / 1.414f) } // Default A4 ratio
    val density = LocalDensity.current
    val uiState by viewModel.uiState.collectAsState()
    val isSelectionMode = (uiState.onScreenSelectPageIndex == pageIndex)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = if (isLandscape) 4.dp else 6.dp),
        contentAlignment = Alignment.Center
    ) {
        val boxWidth = maxWidth
        val boxHeight = maxHeight
        val targetWidthPx = with(density) { boxWidth.roundToPx() }
        val targetHeightPx = with(density) { boxHeight.roundToPx() }

        // Fetch page dimensions and render bitmap asynchronously
        LaunchedEffect(pageIndex, targetWidthPx, targetHeightPx, isLandscape) {
            val dimensions = viewModel.getPageDimensions(pageIndex)
            if (dimensions != null && dimensions.aspectRatio > 0) {
                aspectRatio = dimensions.aspectRatio
            }

            val (renderW, renderH) = if (isLandscape) {
                val containerAspect = targetWidthPx.toFloat() / targetHeightPx.toFloat().coerceAtLeast(1f)
                if (containerAspect > aspectRatio) {
                    val h = targetHeightPx
                    val w = (h * aspectRatio).toInt().coerceAtLeast(100)
                    w to h
                } else {
                    val w = targetWidthPx
                    val h = (w / aspectRatio).toInt().coerceAtLeast(100)
                    w to h
                }
            } else {
                val w = targetWidthPx
                val h = (w / aspectRatio).toInt().coerceAtLeast(100)
                w to h
            }

            val rendered = viewModel.renderPage(pageIndex, renderW, renderH)
            pageBitmap = rendered
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = if (isLandscape) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
        ) {
            val pageModifier = if (isLandscape) {
                val isHeightConstrained = (boxWidth / boxHeight.coerceAtLeast(1.dp)) > aspectRatio
                if (isHeightConstrained) {
                    Modifier
                        .weight(1f, fill = false)
                        .aspectRatio(aspectRatio, matchHeightConstraintsFirst = true)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio, matchHeightConstraintsFirst = false)
                }
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
            }

            if (isSelectionMode) {
                // Live On-Screen Text Selection Canvas (Matches native text selection with handles & floating menu)
                Surface(
                    modifier = pageModifier
                        .shadow(elevation = 4.dp, shape = RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color.White
                ) {
                    when {
                        uiState.isExtractingOnScreenText -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Extracting text...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        uiState.onScreenSelectText.isBlank() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No selectable text found on Page ${pageIndex + 1}.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        else -> {
                            SelectionContainer(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp, vertical = 18.dp)
                            ) {
                                Text(
                                    text = uiState.onScreenSelectText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = if (isLandscape) 14.sp else 15.sp,
                                        lineHeight = if (isLandscape) 22.sp else 24.sp,
                                        fontFamily = FontFamily.Default
                                    ),
                                    color = Color(0xFF1D1B20)
                                )
                            }
                        }
                    }
                }
            } else {
                // Normal Rendered PDF Page Canvas
                Box(
                    modifier = pageModifier
                        .shadow(elevation = 3.dp, shape = RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White)
                        .pointerInput(pageIndex) {
                            detectTapGestures(
                                onLongPress = {
                                    onLongPress()
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = pageBitmap
                    if (bmp != null && !bmp.isRecycled) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Page ${pageIndex + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 10.dp))

            // Centered page label directly beneath the page content
            Text(
                text = "Page ${pageIndex + 1}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (isLandscape) 11.sp else 12.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = if (isLandscape) 2.dp else 8.dp)
            )
        }
    }
}
