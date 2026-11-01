package com.xpdfviewer.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xpdfviewer.data.NormalizedRect
import com.xpdfviewer.data.RedactionMark
import com.xpdfviewer.data.RedactionUiState
import com.xpdfviewer.ui.dialogs.RedactionConfirmDialog
import com.xpdfviewer.ui.dialogs.RedactionSuccessDialog
import com.xpdfviewer.viewmodel.PdfRedactionViewModel
import java.util.Locale

@Composable
fun PdfRedactionScreen(
    onNavigateBack: () -> Unit,
    onOpenPdf: (Uri) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PdfRedactionViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val pickPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.loadDocument(context, uri)
        }
    }

    val saveRedactedLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null) {
            viewModel.applyRedactions(context, destUri)
        }
    }

    BackHandler {
        viewModel.handleBackPress(onNavigateBack)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    IconButton(onClick = { viewModel.handleBackPress(onNavigateBack) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Permanent Redaction",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Purge vector streams, pixels & metadata",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (uiState.isDocumentLoaded) {
                        OutlinedButton(
                            onClick = { viewModel.resetState() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Reset", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (uiState.isDocumentLoaded) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Button(
                            onClick = { viewModel.setConfirmDialogVisible(true) },
                            enabled = uiState.canApplyRedaction,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Sanitize & Export (${uiState.totalPendingCount} Redactions)",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            if (!uiState.isDocumentLoaded) {
                EmptyStateCard(
                    onSelectPdf = { pickPdfLauncher.launch(arrayOf("application/pdf")) }
                )
            } else {
                // Source PDF Metadata Card
                SourceDocumentCard(
                    fileName = uiState.fileName,
                    fileSizeBytes = uiState.fileSizeBytes,
                    pageCount = uiState.pageCount,
                    onChangeFile = { pickPdfLauncher.launch(arrayOf("application/pdf")) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Auto-Redact Keyword Search Bar
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Auto-Redact Keyword",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Finds all occurrences across all pages and marks them for redaction.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = uiState.keywordInput,
                                onValueChange = { viewModel.setKeywordInput(it) },
                                placeholder = { Text("e.g. SSN, Account, Name...") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = { viewModel.searchAndMarkKeyword(context) },
                                enabled = uiState.keywordInput.isNotBlank() && !uiState.isSearchingKeyword,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(52.dp)
                            ) {
                                if (uiState.isSearchingKeyword) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Mark All")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Page Navigation Bar
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.setCurrentPage(uiState.currentPage - 1) },
                            enabled = uiState.currentPage > 1
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
                        }

                        Text(
                            text = "Page ${uiState.currentPage} of ${uiState.pageCount}",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )

                        IconButton(
                            onClick = { viewModel.setCurrentPage(uiState.currentPage + 1) },
                            enabled = uiState.currentPage < uiState.pageCount
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page")
                        }

                        if (uiState.currentPageMarks.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearCurrentPageMarks() }) {
                                Text("Clear Page", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Drag a box on the page to mark a manual redaction area:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Interactive Redaction Canvas
                RedactionPageCanvas(
                    uiState = uiState,
                    onAddMark = { bounds -> viewModel.addManualRedactionMark(bounds) },
                    onRemoveMark = { markId -> viewModel.removeRedactionMark(markId) }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Confirmation Warning Dialog
    if (uiState.showConfirmDialog) {
        RedactionConfirmDialog(
            totalMarksCount = uiState.totalPendingCount,
            onDismiss = { viewModel.setConfirmDialogVisible(false) },
            onConfirm = {
                val baseName = uiState.fileName.substringBeforeLast(".pdf", uiState.fileName)
                saveRedactedLauncher.launch("${baseName}_redacted.pdf")
            }
        )
    }

    // Success Dialog
    if (uiState.showSuccessDialog && uiState.lastResult != null) {
        RedactionSuccessDialog(
            result = uiState.lastResult!!,
            onDismiss = { viewModel.dismissSuccessDialog() },
            onOpenPdf = onOpenPdf
        )
    }

    // Processing Dialog
    if (uiState.isProcessing) {
        Dialog(onDismissRequest = { /* Modal lock */ }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Redacting Document...",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.progressMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // Error Alert Banner
    uiState.errorMessage?.let { error ->
        Dialog(onDismissRequest = { viewModel.clearError() }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Redaction Error",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = { viewModel.clearError() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}

@Composable
private fun RedactionPageCanvas(
    uiState: RedactionUiState,
    onAddMark: (NormalizedRect) -> Unit,
    onRemoveMark: (String) -> Unit
) {
    val aspectRatio = uiState.pageDimensions?.aspectRatio ?: (8.5f / 11f)

    var dragStartOffset by remember { mutableStateOf<Offset?>(null) }
    var dragCurrentOffset by remember { mutableStateOf<Offset?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .shadow(4.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
    ) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight

        val bmp = uiState.currentPageBitmap
        if (bmp != null && !bmp.isRecycled && !uiState.isLoadingPage) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Current page to redact",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                CircularProgressIndicator(strokeWidth = 3.dp)
            }
        }

        // Touch drag gesture layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(uiState.currentPage) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragStartOffset = offset
                            dragCurrentOffset = offset
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dragCurrentOffset = change.position
                        },
                        onDragEnd = {
                            val start = dragStartOffset
                            val end = dragCurrentOffset
                            if (start != null && end != null) {
                                val canvasW = size.width.toFloat()
                                val canvasH = size.height.toFloat()
                                if (canvasW > 0 && canvasH > 0) {
                                    val left = (minOf(start.x, end.x) / canvasW).coerceIn(0f, 1f)
                                    val top = (minOf(start.y, end.y) / canvasH).coerceIn(0f, 1f)
                                    val right = (maxOf(start.x, end.x) / canvasW).coerceIn(0f, 1f)
                                    val bottom = (maxOf(start.y, end.y) / canvasH).coerceIn(0f, 1f)

                                    // Filter out tiny accidental taps
                                    if ((right - left) > 0.02f && (bottom - top) > 0.01f) {
                                        onAddMark(NormalizedRect(left, top, right, bottom))
                                    }
                                }
                            }
                            dragStartOffset = null
                            dragCurrentOffset = null
                        },
                        onDragCancel = {
                            dragStartOffset = null
                            dragCurrentOffset = null
                        }
                    )
                }
        ) {
            // Live Dragging Box Preview
            val start = dragStartOffset
            val end = dragCurrentOffset
            if (start != null && end != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val x = minOf(start.x, end.x)
                    val y = minOf(start.y, end.y)
                    val w = kotlin.math.abs(end.x - start.x)
                    val h = kotlin.math.abs(end.y - start.y)

                    drawRoundRect(
                        color = Color(0xFFEF4444).copy(alpha = 0.35f),
                        topLeft = Offset(x, y),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                    drawRoundRect(
                        color = Color(0xFFDC2626),
                        topLeft = Offset(x, y),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(4f, 4f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            // Existing Pending Redaction Marks
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasW = size.width
                val canvasH = size.height

                for (mark in uiState.currentPageMarks) {
                    val left = mark.bounds.left * canvasW
                    val top = mark.bounds.top * canvasH
                    val w = (mark.bounds.right - mark.bounds.left) * canvasW
                    val h = (mark.bounds.bottom - mark.bounds.top) * canvasH

                    // Translucent red warning block
                    drawRoundRect(
                        color = Color(0xFFEF4444).copy(alpha = 0.40f),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                    drawRoundRect(
                        color = Color(0xFFDC2626),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(4f, 4f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            // Interactive Delete Chips on Pending Marks
            for (mark in uiState.currentPageMarks) {
                val left = containerWidth * mark.bounds.left
                val top = containerHeight * mark.bounds.top
                Box(
                    modifier = Modifier
                        .padding(start = left, top = top)
                        .clip(CircleShape)
                        .background(Color(0xFFDC2626))
                        .clickable { onRemoveMark(mark.id) }
                        .padding(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete mark",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(
    onSelectPdf: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFEE2E2))
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Select Document to Redact",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "True sanitization permanently obliterates underlying vector glyphs, pixel streams, and metadata.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    FeatureBullet(title = "Vector Glyph Token Purge", desc = "Removes text operators so copy-paste cannot recover words")
                    Spacer(modifier = Modifier.height(8.dp))
                    FeatureBullet(title = "Auto-Redact Keyword Search", desc = "Automatically mark all SSN or confidential occurrences")
                    Spacer(modifier = Modifier.height(8.dp))
                    FeatureBullet(title = "Metadata & Revision Scrub", desc = "Wipes author, producer, history and XMP schema packages")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onSelectPdf,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Choose PDF File", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FeatureBullet(title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(Color(0xFFDC2626))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SourceDocumentCard(
    fileName: String,
    fileSizeBytes: Long,
    pageCount: Int,
    onChangeFile: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFEE2E2))
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$pageCount Pages",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = Color(0xFFDC2626)
                    )
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatFileSize(fileSizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(
                onClick = onChangeFile,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Change", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

