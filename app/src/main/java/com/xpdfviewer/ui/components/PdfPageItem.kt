package com.xpdfviewer.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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

            // Rendered PDF Page Canvas (Bitmap always visible)
            Box(
                modifier = pageModifier
                    .shadow(elevation = if (isSelectionMode) 5.dp else 3.dp, shape = RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(uiState.readingMode.cardBackgroundColor)
                    .then(
                        if (!isSelectionMode) {
                            Modifier.pointerInput(pageIndex) {
                                detectTapGestures(
                                    onLongPress = {
                                        onLongPress()
                                    }
                                )
                            }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                val bmp = pageBitmap
                if (bmp != null && !bmp.isRecycled) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Page ${pageIndex + 1}",
                        contentScale = ContentScale.Fit,
                        colorFilter = uiState.readingMode.composeColorFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                }

                // Spatial Text Overlay when in selection mode
                if (isSelectionMode) {
                    val spatialData = uiState.onScreenSpatialData
                    when {
                        uiState.isExtractingOnScreenText -> {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Extracting text layer...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                        spatialData != null && !spatialData.isEmpty -> {
                            PdfTextOverlayCanvas(
                                spatialData = spatialData,
                                selectedIndices = uiState.selectedWordIndices,
                                onWordTap = { viewModel.selectWord(it) },
                                onWordRangeSelect = { s, e -> viewModel.selectWordRange(s, e) },
                                onClearSelection = { viewModel.clearWordSelection() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        spatialData != null && spatialData.isEmpty -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 16.dp)
                            ) {
                                Text(
                                    text = "No selectable text on Page ${pageIndex + 1} (scanned document)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Search Matches Highlight Overlay
                if (uiState.searchState.isSearchActive && uiState.searchState.hasMatches) {
                    val pageMatches = remember(uiState.searchState.matches, pageIndex) {
                        uiState.searchState.matches.filter { it.pageIndex == pageIndex }
                    }
                    if (pageMatches.isNotEmpty()) {
                        PdfSearchHighlightCanvas(
                            matches = pageMatches,
                            currentMatchIndex = uiState.searchState.currentMatchIndex,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Interactive Form Fields Overlay
                if (uiState.formState.hasAcroForm && !isSelectionMode) {
                    val pageFields = remember(uiState.formState.fields, pageIndex) {
                        uiState.formState.fields.filter { it.pageIndex == pageIndex }
                    }
                    if (pageFields.isNotEmpty()) {
                        PdfFormOverlayCanvas(
                            fields = pageFields,
                            activeFieldId = uiState.formState.activeFieldId,
                            onFieldClick = { field -> viewModel.onFormFieldClicked(field) },
                            onToggleCheckbox = { fieldId, isChecked -> viewModel.updateCheckboxField(fieldId, isChecked) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Continuous Audio Reader Sentence Highlight Overlay & Tap-to-Speak
                if (uiState.audioReaderState.isActive && uiState.audioReaderState.currentPageIndex == pageIndex) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    PdfAudioHighlightCanvas(
                        sentenceWordBounds = uiState.audioReaderState.activeSentenceWordBounds,
                        activeWordBounds = uiState.audioReaderState.activeWordBounds,
                        onTapCoordinate = { normX, normY ->
                            viewModel.jumpAudioToCoordinate(context, pageIndex, normX, normY)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
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
