package com.xpdfviewer.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xpdfviewer.pdf.CompressResult
import com.xpdfviewer.pdf.PdfCompressionLevel
import com.xpdfviewer.viewmodel.CompressPdfViewModel

@Composable
fun CompressPdfScreen(
    onNavigateBack: () -> Unit,
    onOpenCompressedPdf: (Uri) -> Unit,
    modifier: Modifier = Modifier,
    compressViewModel: CompressPdfViewModel = viewModel()
) {
    val uiState by compressViewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showFilenameDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var targetFileName by remember { mutableStateOf("") }

    val handleBackPress = {
        if (uiState.hasDocument) {
            showDiscardDialog = true
        } else {
            onNavigateBack()
        }
    }

    BackHandler {
        handleBackPress()
    }

    // Picker for input PDF file
    val selectPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { compressViewModel.selectDocument(context, it) }
    }

    // SAF Creator for output PDF destination
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destinationUri: Uri? ->
        destinationUri?.let { dest ->
            compressViewModel.compressToDestination(context, dest, targetFileName)
        }
    }

    // 1. Filename Prompt Dialog before choosing save destination
    if (showFilenameDialog) {
        val baseName = uiState.fileName.removeSuffix(".pdf")
        val defaultName = "${baseName}_compressed"
        var inputName by remember { mutableStateOf(defaultName) }

        AlertDialog(
            onDismissRequest = { showFilenameDialog = false },
            title = {
                Text(
                    text = "Name Compressed PDF",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Choose a name for your compressed document before saving to storage:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("File name") },
                        suffix = { Text(".pdf") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sanitized = inputName.trim().ifEmpty { defaultName }
                        val finalFileName = if (sanitized.endsWith(".pdf", ignoreCase = true)) sanitized else "$sanitized.pdf"
                        targetFileName = finalFileName
                        showFilenameDialog = false
                        createDocumentLauncher.launch(finalFileName)
                    }
                ) {
                    Text("Choose Location")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFilenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 2. Active Compression In-Progress Dialog
    if (uiState.isCompressing) {
        Dialog(onDismissRequest = {}) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )

                    Text(
                        text = "Compressing PDF...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    if (uiState.progressTotalPages > 0) {
                        Text(
                            text = "Processing page ${uiState.progressCurrentPage} of ${uiState.progressTotalPages}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        LinearProgressIndicator(
                            progress = {
                                if (uiState.progressTotalPages > 0) {
                                    uiState.progressCurrentPage.toFloat() / uiState.progressTotalPages
                                } else 0f
                            },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        )
                    }

                    Text(
                        text = "Optimizing images and stream data",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }

    // 3. Post-Compression Result Dialog
    uiState.compressResult?.let { result ->
        if (result.success && uiState.compressedOutputUri != null) {
            CompressionSuccessDialog(
                result = result,
                outputUri = uiState.compressedOutputUri!!,
                fileName = uiState.compressedFileName,
                onOpen = {
                    compressViewModel.dismissResultDialog()
                    onOpenCompressedPdf(uiState.compressedOutputUri!!)
                },
                onShare = {
                    sharePdf(context, uiState.compressedOutputUri!!, uiState.compressedFileName)
                },
                onDismiss = { compressViewModel.dismissResultDialog() }
            )
        } else if (!result.success) {
            AlertDialog(
                onDismissRequest = { compressViewModel.dismissResultDialog() },
                title = { Text("Compression Failed") },
                text = { Text(result.errorMessage ?: "An unexpected error occurred during compression.") },
                confirmButton = {
                    Button(onClick = { compressViewModel.dismissResultDialog() }) {
                        Text("OK")
                    }
                }
            )
        }
    }

    // 4. DISCARD CONFIRMATION DIALOG
    if (showDiscardDialog) {
        com.xpdfviewer.ui.dialogs.DiscardChangesDialog(
            title = "Discard Changes?",
            message = "The selected document and compression settings will be cleared.",
            confirmButtonText = "Yes",
            dismissButtonText = "No",
            onConfirm = {
                showDiscardDialog = false
                compressViewModel.clearSelectedDocument()
                onNavigateBack()
            },
            onDismiss = { showDiscardDialog = false }
        )
    }

    // MAIN SCREEN LAYOUT
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // TOP APP BAR
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = handleBackPress) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Text(
                        text = "Compress PDF",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                    )
                }

                if (uiState.hasDocument) {
                    IconButton(onClick = { compressViewModel.clearSelectedDocument() }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear file",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        // CONTENT
        if (!uiState.hasDocument) {
            // STATE 1: NO FILE SELECTED (Hero Upload Area)
            EmptyCompressView(
                onSelectPdf = { selectPdfLauncher.launch(arrayOf("application/pdf")) }
            )
        } else {
            // STATE 2: DOCUMENT LOADED & COMPRESSION SETTINGS
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Document Info Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFFFEBEE))
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = Color(0xFFD32F2F),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.fileName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${uiState.originalSizeFormatted} • ${uiState.pageCount} pages",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        TextButton(
                            onClick = { selectPdfLauncher.launch(arrayOf("application/pdf")) }
                        ) {
                            Text("Change")
                        }
                    }
                }

                // Section Title
                Text(
                    text = "Compression Level",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Level Cards (Extreme, Recommended, Less)
                for (level in PdfCompressionLevel.values()) {
                    CompressionLevelCard(
                        level = level,
                        isSelected = uiState.compressionLevel == level,
                        onClick = { compressViewModel.setCompressionLevel(level) }
                    )
                }

                // Estimated Savings Callout Banner
                val isDark = isSystemInDarkTheme()
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFEFF6FF)
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isDark) Color(0xFF334155) else Color(0xFFDBEAFE)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isDark) Color(0xFF1E3A8A) else Color(0xFFDBEAFE),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = if (isDark) Color(0xFF93C5FD) else Color(0xFF1D4ED8),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Estimated Savings: ${uiState.compressionLevel.estimatedSavings}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (isDark) Color(0xFF93C5FD) else Color(0xFF1E40AF)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Text and vector diagrams remain sharp and selectable.",
                                fontSize = 12.sp,
                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
                            )
                        }
                    }
                }
            }

            // BOTTOM ACTION BAR
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = { showFilenameDialog = true },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Compress,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Compress PDF",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCompressView(
    onSelectPdf: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Upload Hero Card
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
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
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.Compress,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(42.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Reduce PDF File Size",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Shrink large documents for fast email sharing, messaging attachments, and storage savings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onSelectPdf,
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Select PDF to Compress",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Feature Highlights
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FeatureItem(
                icon = Icons.Default.Lock,
                title = "100% Offline",
                description = "Processed on device",
                modifier = Modifier.weight(1f)
            )
            FeatureItem(
                icon = Icons.Default.Speed,
                title = "Smart Resampling",
                description = "Preserves sharp text",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FeatureItem(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
        Text(
            text = description,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CompressionLevelCard(
    level: PdfCompressionLevel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()

    // Background: Crisp Ice Blue 50 in light mode, dark slate blue in dark mode
    val targetBg = when {
        isSelected && isDark -> Color(0xFF172554).copy(alpha = 0.5f)
        isSelected && !isDark -> Color(0xFFEFF6FF) // Tailwind Blue 50
        else -> MaterialTheme.colorScheme.surface
    }
    val cardBg by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(durationMillis = 200),
        label = "cardBg"
    )

    // Border: Bold 2.dp Accent Blue when selected, clean subtle outline when unselected
    val targetBorderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isDark -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        else -> Color(0xFFE2E8F0)
    }
    val borderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = tween(durationMillis = 200),
        label = "borderColor"
    )

    // Pill badge: Refined tone-on-tone container with rich legible text
    val badgeBg by animateColorAsState(
        targetValue = when {
            isSelected && isDark -> Color(0xFF1E3A8A)
            isSelected && !isDark -> Color(0xFFDBEAFE) // Tailwind Blue 100
            isDark -> Color(0xFF334155)
            else -> Color(0xFFF1F5F9) // Slate 100
        },
        animationSpec = tween(durationMillis = 200),
        label = "badgeBg"
    )
    val badgeTextColor by animateColorAsState(
        targetValue = when {
            isSelected && isDark -> Color(0xFF93C5FD)
            isSelected && !isDark -> Color(0xFF1D4ED8) // Tailwind Blue 700
            isDark -> Color(0xFF94A3B8)
            else -> Color(0xFF64748B) // Slate 500
        },
        animationSpec = tween(durationMillis = 200),
        label = "badgeText"
    )

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF94A3B8),
                modifier = Modifier.size(22.dp).padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = level.title,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = if (isSelected && !isDark) Color(0xFF0F172A) else MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = badgeBg
                    ) {
                        Text(
                            text = level.estimatedSavings,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeTextColor,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = level.description,
                    fontSize = 12.sp,
                    color = if (isSelected && !isDark) Color(0xFF475569) else MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun CompressionSuccessDialog(
    result: CompressResult,
    outputUri: Uri,
    fileName: String,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                // Celebration Icon
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDCFCE7))
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF166534),
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Compression Complete!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = fileName,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Comparison Box
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Before",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = CompressPdfViewModel.formatFileSize(result.originalSizeBytes),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "After",
                                fontSize = 11.sp,
                                color = Color(0xFF166534)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = CompressPdfViewModel.formatFileSize(result.compressedSizeBytes),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF166534)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Savings pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFDCFCE7),
                    border = BorderStroke(1.dp, Color(0xFF86EFAC))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = Color(0xFF166534),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Saved ${result.savedPercent}% (-${CompressPdfViewModel.formatFileSize(result.savedBytes)})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF166534)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onOpen,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Open in Viewer", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onShare,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share PDF", fontWeight = FontWeight.SemiBold)
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Done", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun sharePdf(context: Context, uri: Uri, fileName: String) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Compressed PDF"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
