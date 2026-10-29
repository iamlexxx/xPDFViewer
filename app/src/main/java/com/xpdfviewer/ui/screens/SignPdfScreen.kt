package com.xpdfviewer.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xpdfviewer.signature.SignatureStamp
import com.xpdfviewer.ui.dialogs.SignExportDialog
import com.xpdfviewer.ui.dialogs.SignSuccessDialog
import com.xpdfviewer.ui.dialogs.SignaturePadDialog
import com.xpdfviewer.viewmodel.SignPdfViewModel
import kotlin.math.roundToInt

@Composable
fun SignPdfScreen(
    onNavigateBack: () -> Unit,
    onOpenSignedPdf: (Uri) -> Unit,
    modifier: Modifier = Modifier,
    initialPdfUri: Uri? = null,
    initialPageIndex: Int = 0,
    signViewModel: SignPdfViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by signViewModel.uiState.collectAsState()
    val isDark = isSystemInDarkTheme()

    LaunchedEffect(initialPdfUri, initialPageIndex) {
        if (initialPdfUri != null) {
            if (uiState.sourceUri != initialPdfUri) {
                signViewModel.loadPdf(context, initialPdfUri, initialPageIndex)
            } else if (uiState.currentPageIndex != initialPageIndex) {
                signViewModel.selectPage(initialPageIndex)
            }
        }
    }

    var showSignaturePad by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var pendingExportFileName by remember { mutableStateOf("") }
    var pendingExportPassword by remember { mutableStateOf<String?>(null) }

    // Launcher for picking source PDF
    val pickPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { signViewModel.loadPdf(context, it) }
    }

    // Launcher for SAF create signed document
    val saveSignedPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destinationUri: Uri? ->
        if (destinationUri != null) {
            signViewModel.signAndExport(
                context = context,
                destinationUri = destinationUri,
                fileName = pendingExportFileName,
                password = pendingExportPassword
            )
        }
    }

    val handleBackPress = {
        if (uiState.sourceUri != null) {
            showDiscardDialog = true
        } else {
            onNavigateBack()
        }
    }

    BackHandler {
        handleBackPress()
    }

    ScaffoldWrapper(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // TOP BAR
            SignTopAppBar(
                fileName = uiState.fileName,
                pageIndex = uiState.currentPageIndex,
                pageCount = uiState.pageCount,
                hasStamps = uiState.stamps.isNotEmpty(),
                onNavigateBack = handleBackPress,
                onExportClick = {
                    if (uiState.stamps.isNotEmpty()) {
                        val baseName = uiState.fileName.removeSuffix(".pdf")
                        pendingExportFileName = "${baseName}_signed.pdf"
                        showExportDialog = true
                    }
                }
            )

            if (uiState.sourceUri == null) {
                // Empty state: Hero Picker
                SignEmptyHero(onSelectPdf = { pickPdfLauncher.launch(arrayOf("application/pdf")) })
            } else {
                // Document Viewport with Interactive Stamps
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9))
                ) {
                    if (uiState.isLoading || uiState.currentPageBitmap == null) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Rendering page...", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    } else {
                        val bitmap = uiState.currentPageBitmap!!
                        val pageAspect = uiState.pageDimensions?.aspectRatio
                            ?: if (bitmap.height > 0) bitmap.width.toFloat() / bitmap.height.toFloat() else 1.0f

                        BoxWithConstraints(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .clickable { signViewModel.selectStamp(null) }
                        ) {
                            val containerWidthPx = constraints.maxWidth.toFloat()
                            val containerHeightPx = constraints.maxHeight.toFloat()

                            // Compute dimensions to fit page inside constraints preserving aspect ratio
                            val renderWidthPx: Float
                            val renderHeightPx: Float
                            if (containerWidthPx / containerHeightPx > pageAspect) {
                                renderHeightPx = containerHeightPx
                                renderWidthPx = containerHeightPx * pageAspect
                            } else {
                                renderWidthPx = containerWidthPx
                                renderHeightPx = containerWidthPx / pageAspect
                            }

                            // Rendered PDF Page with Stamps overlay
                            Box(
                                modifier = Modifier
                                    .size(
                                        width = (renderWidthPx / LocalContext.current.resources.displayMetrics.density).dp,
                                        height = (renderHeightPx / LocalContext.current.resources.displayMetrics.density).dp
                                    )
                                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                            ) {
                                // Background rendered page
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "PDF Page ${uiState.currentPageIndex + 1}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                )

                                // Interactive Stamped Signatures on this page
                                uiState.currentStamps.forEach { stamp ->
                                    val isSelected = stamp.id == uiState.selectedStampId
                                    StampItemView(
                                        stamp = stamp,
                                        isSelected = isSelected,
                                        pageWidthPx = renderWidthPx,
                                        pageHeightPx = renderHeightPx,
                                        onSelect = { signViewModel.selectStamp(stamp.id) },
                                        onMove = { normX, normY ->
                                            signViewModel.updateStampPosition(stamp.id, normX, normY)
                                        },
                                        onResize = { normW, normH ->
                                            signViewModel.updateStampSize(stamp.id, normW, normH)
                                        },
                                        onDelete = { signViewModel.removeStamp(stamp.id) }
                                    )
                                }
                            }
                        }
                    }
                }

                // BOTTOM DECK: Navigation & Tools
                SignBottomDeck(
                    currentPage = uiState.currentPageIndex,
                    pageCount = uiState.pageCount,
                    hasStamps = uiState.currentStamps.isNotEmpty(),
                    onPrevPage = {
                        if (uiState.currentPageIndex > 0) {
                            signViewModel.selectPage(uiState.currentPageIndex - 1)
                        }
                    },
                    onNextPage = {
                        if (uiState.currentPageIndex < uiState.pageCount - 1) {
                            signViewModel.selectPage(uiState.currentPageIndex + 1)
                        }
                    },
                    onAddSignature = { showSignaturePad = true }
                )
            }
        }
    }

    // SIGNATURE PAD DIALOG
    if (showSignaturePad) {
        SignaturePadDialog(
            savedSignatures = uiState.savedSignatures,
            onDismiss = { showSignaturePad = false },
            onSignatureCreated = { bmp, shouldSave ->
                signViewModel.addSignatureStamp(bmp)
                if (shouldSave) {
                    signViewModel.saveSignatureToStorage(context, bmp)
                }
            },
            onDeleteSavedSignature = { file ->
                signViewModel.deleteSavedSignature(file)
            }
        )
    }

    // EXPORT FILENAME & ENCRYPTION DIALOG
    if (showExportDialog) {
        SignExportDialog(
            initialFileName = pendingExportFileName,
            onDismiss = { showExportDialog = false },
            onConfirm = { fileName, password ->
                pendingExportFileName = fileName
                pendingExportPassword = password
                showExportDialog = false
                saveSignedPdfLauncher.launch(fileName)
            }
        )
    }

    // SIGNING IN-PROGRESS MODAL
    if (uiState.isSigning) {
        Dialog(onDismissRequest = {}) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Stamping & Encrypting Document...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Preserving original text clarity and vectors.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // SUCCESS CELEBRATION MODAL
    uiState.signResult?.let { result ->
        SignSuccessDialog(
            result = result,
            onDismiss = {
                signViewModel.clearResult()
                signViewModel.clearDocument()
                onNavigateBack()
            },
            onOpenPdf = { uri ->
                signViewModel.clearResult()
                signViewModel.clearDocument()
                onOpenSignedPdf(uri)
            }
        )
    }

    // REDESIGNED DISCARD CONFIRMATION DIALOG
    if (showDiscardDialog) {
        com.xpdfviewer.ui.dialogs.DiscardChangesDialog(
            title = "Discard Changes?",
            message = "The open document and any placed signatures will be cleared.",
            confirmButtonText = "Yes",
            dismissButtonText = "No",
            onConfirm = {
                showDiscardDialog = false
                signViewModel.clearDocument()
                onNavigateBack()
            },
            onDismiss = { showDiscardDialog = false }
        )
    }

    // ERROR ALERT
    uiState.errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { signViewModel.clearResult() },
            title = { Text("Signing Notice", fontWeight = FontWeight.Bold) },
            text = { Text(error) },
            confirmButton = {
                Button(onClick = { signViewModel.clearResult() }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun ScaffoldWrapper(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) {
        content()
    }
}

@Composable
private fun SignTopAppBar(
    fileName: String,
    pageIndex: Int,
    pageCount: Int,
    hasStamps: Boolean,
    onNavigateBack: () -> Unit,
    onExportClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Column {
                    Text(
                        text = if (fileName.isNotBlank()) fileName else "E-Signature",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (pageCount > 0) {
                        Text(
                            text = "Page ${pageIndex + 1} of $pageCount",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (pageCount > 0) {
                Button(
                    onClick = onExportClick,
                    enabled = hasStamps,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save PDF", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun StampItemView(
    stamp: SignatureStamp,
    isSelected: Boolean,
    pageWidthPx: Float,
    pageHeightPx: Float,
    onSelect: () -> Unit,
    onMove: (normX: Float, normY: Float) -> Unit,
    onResize: (normW: Float, normH: Float) -> Unit,
    onDelete: () -> Unit
) {
    val density = LocalContext.current.resources.displayMetrics.density

    val currentStamp by rememberUpdatedState(stamp)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnResize by rememberUpdatedState(onResize)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentPageWidthPx by rememberUpdatedState(pageWidthPx)
    val currentPageHeightPx by rememberUpdatedState(pageHeightPx)

    val stampLeftPx = stamp.normalizedX * pageWidthPx
    val stampTopPx = stamp.normalizedY * pageHeightPx
    val stampWidthPx = stamp.normalizedWidth * pageWidthPx
    val stampHeightPx = stamp.normalizedHeight * pageHeightPx

    val stampWidthDp = (stampWidthPx / density).dp
    val stampHeightDp = (stampHeightPx / density).dp

    var moveAccumulatorX by remember(stamp.id) { mutableFloatStateOf(0f) }
    var moveAccumulatorY by remember(stamp.id) { mutableFloatStateOf(0f) }

    var resizeStartW by remember(stamp.id) { mutableFloatStateOf(0f) }
    var resizeStartH by remember(stamp.id) { mutableFloatStateOf(0f) }
    var resizeAccumulatorX by remember(stamp.id) { mutableFloatStateOf(0f) }
    var resizeAccumulatorY by remember(stamp.id) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .offset { IntOffset(stampLeftPx.roundToInt(), stampTopPx.roundToInt()) }
            .size(stampWidthDp, stampHeightDp)
    ) {
        // 1. Stamp Body (Image + border + move drag gesture)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(6.dp)
                )
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent
                )
                .clickable { currentOnSelect() }
                .pointerInput(stamp.id) {
                    detectDragGestures(
                        onDragStart = {
                            currentOnSelect()
                            moveAccumulatorX = currentStamp.normalizedX * currentPageWidthPx
                            moveAccumulatorY = currentStamp.normalizedY * currentPageHeightPx
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            moveAccumulatorX += dragAmount.x
                            moveAccumulatorY += dragAmount.y
                            val newNormX = moveAccumulatorX / currentPageWidthPx
                            val newNormY = moveAccumulatorY / currentPageHeightPx
                            currentOnMove(newNormX, newNormY)
                        }
                    )
                }
        ) {
            // Transparent signature bitmap
            Image(
                bitmap = stamp.bitmap.asImageBitmap(),
                contentDescription = "Signature Stamp",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            )
        }

        if (isSelected) {
            // 2. Delete badge at top-right (isolated sibling, generous touch target)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-10).dp)
                    .size(36.dp)
                    .clickable { onDelete() }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Delete Stamp",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // 3. Resize handle at bottom-right (isolated sibling, dedicated gesture with accumulated proportional scaling)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 10.dp, y = 10.dp)
                    .size(44.dp)
                    .pointerInput(stamp.id) {
                        detectDragGestures(
                            onDragStart = {
                                resizeStartW = currentStamp.normalizedWidth * currentPageWidthPx
                                resizeStartH = currentStamp.normalizedHeight * currentPageHeightPx
                                resizeAccumulatorX = 0f
                                resizeAccumulatorY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                resizeAccumulatorX += dragAmount.x
                                resizeAccumulatorY += dragAmount.y

                                val aspect = resizeStartW / resizeStartH.coerceAtLeast(1f)
                                val effectiveDelta = (resizeAccumulatorX + resizeAccumulatorY * aspect) / 2f
                                val newWidthPx = (resizeStartW + effectiveDelta)
                                    .coerceIn(currentPageWidthPx * 0.06f, currentPageWidthPx * 0.95f)
                                val newHeightPx = (newWidthPx / aspect)
                                    .coerceIn(currentPageHeightPx * 0.02f, currentPageHeightPx * 0.85f)

                                val newNormW = newWidthPx / currentPageWidthPx
                                val newNormH = newHeightPx / currentPageHeightPx
                                currentOnResize(newNormW, newNormH)
                            }
                        )
                    }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInFull,
                        contentDescription = "Resize Stamp",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SignEmptyHero(
    onSelectPdf: () -> Unit
) {
    val isDark = isSystemInDarkTheme()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF1E293B) else Color(0xFFEFF6FF))
                ) {
                    Icon(
                        imageVector = Icons.Default.Draw,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(42.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Sign Any PDF Contract",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Draw, type, or import digital signatures, place them anywhere on your documents, and export securely with 128-bit AES encryption.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Feature Highlights
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FeatureCheckRow("Smooth touch drawing canvas & typed script fonts")
                    FeatureCheckRow("Saved signatures for instant 1-tap contract signing")
                    FeatureCheckRow("Interactive drag-and-resize placement on any page")
                    FeatureCheckRow("Optional 128-bit AES password encryption")
                }

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = onSelectPdf,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select PDF to Sign", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun FeatureCheckRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF16A34A),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SignBottomDeck(
    currentPage: Int,
    pageCount: Int,
    hasStamps: Boolean,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onAddSignature: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Page navigation row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onPrevPage,
                    enabled = currentPage > 0
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Page")
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "Page ${currentPage + 1} of $pageCount",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                IconButton(
                    onClick = onNextPage,
                    enabled = currentPage < pageCount - 1
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Page")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action button: + Signature (full width)
            Button(
                onClick = onAddSignature,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("+ Signature", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}
