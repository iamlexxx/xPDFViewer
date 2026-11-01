package com.xpdfviewer.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BrandingWatermark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xpdfviewer.data.PaginationAnchor
import com.xpdfviewer.data.PaginationFormat
import com.xpdfviewer.data.WatermarkColor
import com.xpdfviewer.data.WatermarkLayer
import com.xpdfviewer.data.WatermarkPageScope
import com.xpdfviewer.data.WatermarkRotation
import com.xpdfviewer.data.WatermarkTab
import com.xpdfviewer.ui.dialogs.DiscardChangesDialog
import com.xpdfviewer.ui.dialogs.WatermarkSuccessDialog
import com.xpdfviewer.viewmodel.PdfWatermarkViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfWatermarkScreen(
    onNavigateBack: () -> Unit,
    onOpenPdf: (Uri) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PdfWatermarkViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var showDiscardDialog by remember { mutableStateOf(false) }

    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.loadDocument(context, uri)
        }
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null) {
            val outName = uiState.fileName.substringBeforeLast(".pdf", uiState.fileName) + "_watermarked.pdf"
            viewModel.executeWatermarkAndPagination(context, destUri, outName)
        }
    }

    BackHandler {
        if (uiState.sourceUri != null && !uiState.isProcessing) {
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
                        if (uiState.sourceUri != null && !uiState.isProcessing) {
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
                            text = "Watermark & Numbers",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Offline Document Stamp & Pagination",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (uiState.sourceUri != null && !uiState.isProcessing) {
                        IconButton(onClick = { showDiscardDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Document",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (uiState.sourceUri != null && !uiState.isProcessing) {
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
                                val defaultOutName = uiState.fileName.substringBeforeLast(".pdf", uiState.fileName) + "_watermarked.pdf"
                                savePdfLauncher.launch(defaultOutName)
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Apply & Save PDF",
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
                // 1. Source Document Card
                if (uiState.sourceUri == null) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.BrandingWatermark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Select a PDF to Add Watermark",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Stamp confidential notices, custom text watermarks, or add page numbering to header and footer.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            Button(
                                onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.UploadFile, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choose PDF File")
                            }
                        }
                    }
                } else {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
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
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = uiState.fileName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = "${uiState.pageCount} Pages",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Change")
                            }
                        }
                    }

                    // 2. Tab Navigation
                    TabRow(
                        selectedTabIndex = uiState.activeTab.ordinal,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        WatermarkTab.values().forEach { tab ->
                            Tab(
                                selected = uiState.activeTab == tab,
                                onClick = { viewModel.setActiveTab(tab) },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (tab == WatermarkTab.WATERMARK) Icons.AutoMirrored.Filled.BrandingWatermark else Icons.Default.FormatListNumbered,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = tab.label, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            )
                        }
                    }

                    // 3. Tab Content
                    when (uiState.activeTab) {
                        WatermarkTab.WATERMARK -> {
                            WatermarkOptionsSection(
                                config = uiState.watermarkConfig,
                                onUpdate = { updater -> viewModel.updateWatermarkConfig(updater) }
                            )
                        }
                        WatermarkTab.PAGINATION -> {
                            PaginationOptionsSection(
                                config = uiState.paginationConfig,
                                onUpdate = { updater -> viewModel.updatePaginationConfig(updater) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                }
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
                            CircularProgressIndicator(
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = uiState.progressMessage.ifBlank { "Processing document..." },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            if (uiState.progressTotal > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "${uiState.progressCurrent} of ${uiState.progressTotal} pages",
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
                WatermarkSuccessDialog(
                    result = uiState.lastResult!!,
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
                    title = "Close Document?",
                    message = "Your current watermark configuration will be cleared.",
                    confirmButtonText = "Close",
                    dismissButtonText = "Cancel",
                    onConfirm = {
                        showDiscardDialog = false
                        viewModel.clearDocument()
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
private fun WatermarkOptionsSection(
    config: com.xpdfviewer.data.WatermarkConfig,
    onUpdate: ((com.xpdfviewer.data.WatermarkConfig) -> com.xpdfviewer.data.WatermarkConfig) -> Unit
) {
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
                text = "Watermark Text",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = config.text,
                onValueChange = { newText -> onUpdate { it.copy(text = newText) } },
                label = { Text("Watermark text") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Preset suggestions
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("CONFIDENTIAL", "DRAFT", "SAMPLE", "DO NOT COPY").forEach { preset ->
                    FilterChip(
                        selected = config.text == preset,
                        onClick = { onUpdate { it.copy(text = preset) } },
                        label = { Text(preset, fontSize = 12.sp) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Color Palette
            Text(
                text = "Watermark Color",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WatermarkColor.values().forEach { col ->
                    FilterChip(
                        selected = config.color == col,
                        onClick = { onUpdate { it.copy(color = col) } },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(col.hex))
                            )
                        },
                        label = { Text(col.label, fontSize = 12.sp) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Rotation
            Text(
                text = "Orientation & Rotation",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WatermarkRotation.values().forEach { rot ->
                    FilterChip(
                        selected = config.rotation == rot,
                        onClick = { onUpdate { it.copy(rotation = rot) } },
                        label = { Text(rot.label, fontSize = 12.sp) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Layer
            Text(
                text = "Layer Position",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WatermarkLayer.values().forEach { lay ->
                    FilterChip(
                        selected = config.layer == lay,
                        onClick = { onUpdate { it.copy(layer = lay) } },
                        label = { Text(if (lay == WatermarkLayer.OVER_CONTENT) "Foreground" else "Background", fontSize = 12.sp) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Opacity Slider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Opacity",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${(config.opacity * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Slider(
                value = config.opacity,
                onValueChange = { newOp -> onUpdate { it.copy(opacity = newOp) } },
                valueRange = 0.10f..0.80f,
                steps = 13
            )

            // Font Size Slider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Font Size",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${config.fontSize.toInt()} pt",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Slider(
                value = config.fontSize,
                onValueChange = { newSize -> onUpdate { it.copy(fontSize = newSize) } },
                valueRange = 24f..96f,
                steps = 11
            )

            // Page Scope
            Text(
                text = "Apply To",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WatermarkPageScope.values().forEach { scope ->
                    FilterChip(
                        selected = config.pageScope == scope,
                        onClick = { onUpdate { it.copy(pageScope = scope) } },
                        label = { Text(scope.label, fontSize = 12.sp) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (config.pageScope == WatermarkPageScope.CUSTOM_RANGE) {
                OutlinedTextField(
                    value = config.customRangeText,
                    onValueChange = { newRange -> onUpdate { it.copy(customRangeText = newRange) } },
                    label = { Text("Custom range (e.g. 1-3, 5, 8)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaginationOptionsSection(
    config: com.xpdfviewer.data.PaginationConfig,
    onUpdate: ((com.xpdfviewer.data.PaginationConfig) -> com.xpdfviewer.data.PaginationConfig) -> Unit
) {
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
            // Enable toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable Page Numbering",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Stamp page or Bates numbers onto pages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = config.isEnabled,
                    onCheckedChange = { checked -> onUpdate { it.copy(isEnabled = checked) } }
                )
            }

            if (config.isEnabled) {
                Spacer(modifier = Modifier.height(4.dp))

                // Format choices
                Text(
                    text = "Numbering Format",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PaginationFormat.values().forEach { fmt ->
                        FilterChip(
                            selected = config.format == fmt,
                            onClick = { onUpdate { it.copy(format = fmt) } },
                            label = { Text(fmt.example, fontSize = 12.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Anchor Position
                Text(
                    text = "Placement Anchor",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(PaginationAnchor.TOP_LEFT, PaginationAnchor.TOP_CENTER, PaginationAnchor.TOP_RIGHT).forEach { anc ->
                            FilterChip(
                                selected = config.anchor == anc,
                                onClick = { onUpdate { it.copy(anchor = anc) } },
                                label = { Text(anc.label, fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(PaginationAnchor.BOTTOM_LEFT, PaginationAnchor.BOTTOM_CENTER, PaginationAnchor.BOTTOM_RIGHT).forEach { anc ->
                            FilterChip(
                                selected = config.anchor == anc,
                                onClick = { onUpdate { it.copy(anchor = anc) } },
                                label = { Text(anc.label, fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Font Size Slider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Font Size",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${config.fontSize.toInt()} pt",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = config.fontSize,
                    onValueChange = { newSize -> onUpdate { it.copy(fontSize = newSize) } },
                    valueRange = 8f..18f,
                    steps = 9
                )

                // Margin Inset
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Edge Margin Padding",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${config.marginPadding.toInt()} pt",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = config.marginPadding,
                    onValueChange = { newMargin -> onUpdate { it.copy(marginPadding = newMargin) } },
                    valueRange = 12f..48f,
                    steps = 8
                )

                // Scope
                Text(
                    text = "Apply To",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WatermarkPageScope.values().forEach { scope ->
                        FilterChip(
                            selected = config.pageScope == scope,
                            onClick = { onUpdate { it.copy(pageScope = scope) } },
                            label = { Text(scope.label, fontSize = 12.sp) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (config.pageScope == WatermarkPageScope.CUSTOM_RANGE) {
                    OutlinedTextField(
                        value = config.customRangeText,
                        onValueChange = { newRange -> onUpdate { it.copy(customRangeText = newRange) } },
                        label = { Text("Custom range (e.g. 1-3, 5, 8)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    }
}
