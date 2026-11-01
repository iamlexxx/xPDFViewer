package com.xpdfviewer.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xpdfviewer.data.ConverterMode
import com.xpdfviewer.data.DpiPreset
import com.xpdfviewer.data.ImageExportFormat
import com.xpdfviewer.data.ImageQualityPreset
import com.xpdfviewer.data.MarginPreset
import com.xpdfviewer.data.PageOrientationPreset
import com.xpdfviewer.data.PageSizePreset
import com.xpdfviewer.data.SelectedImageItem
import com.xpdfviewer.data.WatermarkPageScope
import com.xpdfviewer.ui.dialogs.DiscardChangesDialog
import com.xpdfviewer.ui.dialogs.ImageExportSuccessDialog
import com.xpdfviewer.viewmodel.ImagePdfConverterViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImagePdfConverterScreen(
    onNavigateBack: () -> Unit,
    onOpenPdf: (Uri) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImagePdfConverterViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var showDiscardDialog by remember { mutableStateOf(false) }

    // Pick multiple photos
    val pickImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addImages(uris)
        }
    }

    // Pick single PDF for PDF to Image
    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.loadSourcePdf(context, uri)
        }
    }

    // Save PDF from Images
    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null) {
            val name = uiState.imageToPdfConfig.outputPdfName.ifBlank { "Images_Export.pdf" }
            viewModel.executeImageToPdf(context, destUri, name)
        }
    }

    // Save ZIP from PDF
    val saveZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { destUri ->
        if (destUri != null) {
            val baseName = uiState.pdfFileName.substringBeforeLast(".pdf", "document")
            val zipName = "${baseName}_images.zip"
            viewModel.executePdfToImages(context, destUri, zipName)
        }
    }

    val hasPendingWork = uiState.selectedImages.isNotEmpty() || uiState.sourcePdfUri != null

    BackHandler {
        if (hasPendingWork && !uiState.isProcessing) {
            showDiscardDialog = true
        } else {
            onNavigateBack()
        }
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
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    IconButton(onClick = {
                        if (hasPendingWork && !uiState.isProcessing) {
                            showDiscardDialog = true
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Image & PDF Converter",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Bidirectional Media & Document Studio",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (hasPendingWork && !uiState.isProcessing) {
                        IconButton(onClick = { showDiscardDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (!uiState.isProcessing) {
                val canConvert = when (uiState.activeMode) {
                    ConverterMode.IMAGE_TO_PDF -> uiState.selectedImages.isNotEmpty()
                    ConverterMode.PDF_TO_IMAGE -> uiState.sourcePdfUri != null
                }

                if (canConvert) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (uiState.activeMode == ConverterMode.IMAGE_TO_PDF) {
                                        val name = uiState.imageToPdfConfig.outputPdfName.ifBlank { "Images_Export.pdf" }
                                        savePdfLauncher.launch(name)
                                    } else {
                                        val baseName = uiState.pdfFileName.substringBeforeLast(".pdf", "document")
                                        val zipName = "${baseName}_images.zip"
                                        saveZipLauncher.launch(zipName)
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                Icon(
                                    imageVector = if (uiState.activeMode == ConverterMode.IMAGE_TO_PDF) Icons.Default.PictureAsPdf else Icons.Default.FolderZip,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (uiState.activeMode == ConverterMode.IMAGE_TO_PDF) "Create PDF (${uiState.selectedImages.size} Photos)" else "Export Pages to ZIP",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Mode Tabs
                TabRow(
                    selectedTabIndex = uiState.activeMode.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    ConverterMode.values().forEach { mode ->
                        Tab(
                            selected = uiState.activeMode == mode,
                            onClick = { viewModel.setMode(mode) },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (mode == ConverterMode.IMAGE_TO_PDF) Icons.Default.Image else Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = mode.label, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        )
                    }
                }

                // Tab Content
                when (uiState.activeMode) {
                    ConverterMode.IMAGE_TO_PDF -> {
                        ImagesToPdfSection(
                            images = uiState.selectedImages,
                            config = uiState.imageToPdfConfig,
                            onPickImages = {
                                pickImagesLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onRemoveImage = { id -> viewModel.removeImage(id) },
                            onRotateImage = { id -> viewModel.rotateImage(id) },
                            onMoveImage = { from, to -> viewModel.moveImage(from, to) },
                            onUpdateConfig = { updater -> viewModel.updateImageToPdfConfig(updater) }
                        )
                    }
                    ConverterMode.PDF_TO_IMAGE -> {
                        PdfToImagesSection(
                            sourceUri = uiState.sourcePdfUri,
                            fileName = uiState.pdfFileName,
                            pageCount = uiState.pdfPageCount,
                            config = uiState.pdfToImageConfig,
                            onPickPdf = { openPdfLauncher.launch(arrayOf("application/pdf")) },
                            onUpdateConfig = { updater -> viewModel.updatePdfToImageConfig(updater) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(50.dp))
            }

            // Processing Dialog
            if (uiState.isProcessing) {
                Dialog(onDismissRequest = {}) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = uiState.progressMessage.ifBlank { "Processing..." },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            if (uiState.progressTotal > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "${uiState.progressCurrent} of ${uiState.progressTotal} items",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Success Dialog
            if (uiState.resultDialogVisible && uiState.lastResult != null) {
                ImageExportSuccessDialog(
                    result = uiState.lastResult!!,
                    isPdfOutput = uiState.activeMode == ConverterMode.IMAGE_TO_PDF,
                    onDismiss = { viewModel.dismissResultDialog() },
                    onOpenPdf = { uri ->
                        viewModel.dismissResultDialog()
                        onOpenPdf(uri)
                    }
                )
            }

            // Discard Confirmation Dialog
            if (showDiscardDialog) {
                DiscardChangesDialog(
                    title = "Clear Converter?",
                    message = "All selected files and conversion settings will be reset.",
                    confirmButtonText = "Clear",
                    dismissButtonText = "Cancel",
                    onConfirm = {
                        showDiscardDialog = false
                        viewModel.clearAll()
                        onNavigateBack()
                    },
                    onDismiss = { showDiscardDialog = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImagesToPdfSection(
    images: List<SelectedImageItem>,
    config: com.xpdfviewer.data.ImageToPdfConfig,
    onPickImages: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onRotateImage: (String) -> Unit,
    onMoveImage: (Int, Int) -> Unit,
    onUpdateConfig: ((com.xpdfviewer.data.ImageToPdfConfig) -> com.xpdfviewer.data.ImageToPdfConfig) -> Unit
) {
    // 1. Photos strip / selection card
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Selected Images (${images.size})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Arrange, rotate, or reorder images for PDF pages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onPickImages,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (images.isEmpty()) "Add Photos" else "Add More")
                }
            }

            if (images.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(images, key = { _, item -> item.id }) { index, item ->
                        ImageThumbnailCard(
                            item = item,
                            index = index,
                            totalCount = images.size,
                            onRotate = { onRotateImage(item.id) },
                            onDelete = { onRemoveImage(item.id) },
                            onMoveLeft = { if (index > 0) onMoveImage(index, index - 1) },
                            onMoveRight = { if (index < images.size - 1) onMoveImage(index, index + 1) }
                        )
                    }
                }
            }
        }
    }

    // 2. Conversion Settings Card
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "PDF Layout & Dimensions",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Page Size
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PageSizePreset.values().forEach { size ->
                    FilterChip(
                        selected = config.pageSize == size,
                        onClick = { onUpdateConfig { it.copy(pageSize = size) } },
                        label = { Text(size.label, fontSize = 12.sp) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Orientation
            if (config.pageSize != PageSizePreset.FIT_TO_IMAGE) {
                Text(
                    text = "Orientation",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageOrientationPreset.values().forEach { orient ->
                        FilterChip(
                            selected = config.orientation == orient,
                            onClick = { onUpdateConfig { it.copy(orientation = orient) } },
                            label = { Text(orient.label, fontSize = 12.sp) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Margins
            Text(
                text = "Page Margins",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MarginPreset.values().forEach { mar ->
                    FilterChip(
                        selected = config.margin == mar,
                        onClick = { onUpdateConfig { it.copy(margin = mar) } },
                        label = { Text(mar.label, fontSize = 12.sp) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Image Quality
            Text(
                text = "Image Quality / File Size",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageQualityPreset.values().forEach { qual ->
                    FilterChip(
                        selected = config.quality == qual,
                        onClick = { onUpdateConfig { it.copy(quality = qual) } },
                        label = { Text(qual.label, fontSize = 12.sp) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Output filename
            OutlinedTextField(
                value = config.outputPdfName,
                onValueChange = { newName -> onUpdateConfig { it.copy(outputPdfName = newName) } },
                label = { Text("Output PDF Filename") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
private fun ImageThumbnailCard(
    item: SelectedImageItem,
    index: Int,
    totalCount: Int,
    onRotate: () -> Unit,
    onDelete: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.width(130.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                ) {
                    Text(
                        text = "Page ${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                if (item.rotationDegrees % 360 != 0) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = "${item.rotationDegrees}°",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Actions row: Rotate, Move Left, Move Right, Delete
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onRotate, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onMoveLeft, enabled = index > 0, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Move Left", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onMoveRight, enabled = index < totalCount - 1, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Move Right", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PdfToImagesSection(
    sourceUri: Uri?,
    fileName: String,
    pageCount: Int,
    config: com.xpdfviewer.data.PdfToImageConfig,
    onPickPdf: () -> Unit,
    onUpdateConfig: ((com.xpdfviewer.data.PdfToImageConfig) -> com.xpdfviewer.data.PdfToImageConfig) -> Unit
) {
    // 1. Source PDF Card
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (sourceUri == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(14.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Choose PDF to Rasterize into Images",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(onClick = onPickPdf, shape = RoundedCornerShape(12.dp)) {
                        Icon(imageVector = Icons.Default.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select PDF")
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$pageCount Total Pages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = onPickPdf, shape = RoundedCornerShape(10.dp)) {
                        Text("Change")
                    }
                }
            }
        }
    }

    if (sourceUri != null) {
        // 2. Export Configuration Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Image Export Settings",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Format
                Text(
                    text = "Output Image Format",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImageExportFormat.values().forEach { fmt ->
                        FilterChip(
                            selected = config.format == fmt,
                            onClick = { onUpdateConfig { it.copy(format = fmt) } },
                            label = { Text(fmt.label, fontSize = 12.sp) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // DPI Resolution
                Text(
                    text = "Resolution & Clarity",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DpiPreset.values().forEach { dpi ->
                        FilterChip(
                            selected = config.dpi == dpi,
                            onClick = { onUpdateConfig { it.copy(dpi = dpi) } },
                            label = { Text(dpi.label, fontSize = 11.sp) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Page Scope
                Text(
                    text = "Page Range to Render",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WatermarkPageScope.values().forEach { scope ->
                        FilterChip(
                            selected = config.pageScope == scope,
                            onClick = { onUpdateConfig { it.copy(pageScope = scope) } },
                            label = { Text(scope.label, fontSize = 11.sp) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (config.pageScope == WatermarkPageScope.CUSTOM_RANGE) {
                    OutlinedTextField(
                        value = config.customRangeText,
                        onValueChange = { newRange -> onUpdateConfig { it.copy(customRangeText = newRange) } },
                        label = { Text("Custom range (e.g. 1-3, 5)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    }
}
