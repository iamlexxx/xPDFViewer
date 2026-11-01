package com.xpdfviewer.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xpdfviewer.ui.components.OrganizerPageCard
import com.xpdfviewer.ui.components.SaveChoiceDialog
import com.xpdfviewer.ui.dialogs.DiscardChangesDialog
import com.xpdfviewer.viewmodel.PageOrganizerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageOrganizerScreen(
    viewModel: PageOrganizerViewModel,
    onNavigateBack: () -> Unit,
    onDocumentSaved: (Uri) -> Unit,
    initialPdfUri: Uri?,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(initialPdfUri) {
        if (initialPdfUri != null && uiState.sourceUri != initialPdfUri) {
            viewModel.loadDocument(context, initialPdfUri)
        }
    }

    val saveCopyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destinationUri ->
        if (destinationUri != null) {
            viewModel.saveAsCopy(context, destinationUri) { savedUri ->
                onDocumentSaved(savedUri)
            }
        }
    }

    val extractCopyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destinationUri ->
        if (destinationUri != null) {
            viewModel.extractSelectedPagesToDestination(context, destinationUri) { savedUri ->
                onDocumentSaved(savedUri)
            }
        } else {
            viewModel.cancelExtractSelected()
        }
    }

    BackHandler {
        viewModel.handleBackNavigation(onNavigateBack)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    IconButton(onClick = { viewModel.handleBackNavigation(onNavigateBack) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (uiState.isSelectionMode) "${uiState.selectedCount} Selected" else "Organize Pages",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${uiState.activePageCount} total pages" + if (uiState.hasUnsavedChanges) " • Unsaved edits" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.hasUnsavedChanges) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (uiState.isSelectionMode) {
                        IconButton(onClick = {
                            if (uiState.selectedCount == uiState.activePageCount) {
                                viewModel.clearSelection()
                            } else {
                                viewModel.selectAllPages()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = "Select / Deselect all",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(onClick = { viewModel.setSelectionMode(false) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Exit selection mode",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        IconButton(onClick = { viewModel.setSelectionMode(true) }) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = "Select pages",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Save action button
                        Button(
                            onClick = { viewModel.requestSave() },
                            enabled = uiState.hasUnsavedChanges && !uiState.isLoading && !uiState.isProcessingSave,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Save",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.isSelectionMode) {
                        // Batch actions for selected pages
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Extract Selected button
                            OutlinedButton(
                                onClick = {
                                    val defaultName = "${uiState.fileName.removeSuffix(".pdf")}_extracted.pdf"
                                    extractCopyLauncher.launch(defaultName)
                                },
                                enabled = uiState.selectedCount > 0,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCut,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Extract (${uiState.selectedCount})")
                            }

                            // Delete Selected button
                            Button(
                                onClick = { viewModel.deleteSelectedPages() },
                                enabled = uiState.selectedCount > 0 && uiState.selectedCount < uiState.activePageCount,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Delete (${uiState.selectedCount})")
                            }
                        }
                    } else {
                        // General actions
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.rotateAllPages() },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.RotateRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Rotate All")
                            }

                            OutlinedButton(
                                onClick = { viewModel.addBlankPage() },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("+ Blank Page")
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
            if (uiState.isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading page layout...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 145.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = uiState.pages,
                        key = { _, item -> item.pageId }
                    ) { index, pageItem ->
                        OrganizerPageCard(
                            page = pageItem,
                            displayIndex = index,
                            totalCount = uiState.activePageCount,
                            isSelectionMode = uiState.isSelectionMode,
                            onThumbnailRequest = { origIdx -> viewModel.getThumbnail(origIdx) },
                            onCardClick = {
                                if (uiState.isSelectionMode) {
                                    viewModel.togglePageSelection(index)
                                } else {
                                    // Clicking in normal mode toggles selection mode
                                    viewModel.togglePageSelection(index)
                                }
                            },
                            onCardLongClick = {
                                viewModel.togglePageSelection(index)
                            },
                            onRotateClick = { viewModel.rotatePage(index) },
                            onDeleteClick = { viewModel.deletePage(index) },
                            onMoveLeftClick = { viewModel.movePage(index, index - 1) },
                            onMoveRightClick = { viewModel.movePage(index, index + 1) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }

            // Processing overlay during save
            if (uiState.isProcessingSave) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Saving organized document...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    // Save Choice Dialog (Overwrite vs Save as Copy)
    if (uiState.showSaveChoiceDialog) {
        SaveChoiceDialog(
            actionTitle = "Organize Pages",
            onOverwrite = {
                viewModel.saveOverwrite(context) { savedUri ->
                    onDocumentSaved(savedUri)
                }
            },
            onSaveAsCopy = {
                val baseName = uiState.fileName.removeSuffix(".pdf")
                saveCopyLauncher.launch("${baseName}_organized.pdf")
            },
            onDismiss = { viewModel.dismissSaveChoiceDialog() }
        )
    }

    // Discard Changes Dialog
    if (uiState.showDiscardDialog) {
        DiscardChangesDialog(
            title = "Discard Page Changes?",
            message = "You have unsaved page reordering and rotation modifications. Are you sure you want to discard them?",
            confirmButtonText = "Discard",
            dismissButtonText = "Keep Editing",
            onConfirm = {
                viewModel.confirmDiscard(onNavigateBack)
            },
            onDismiss = { viewModel.dismissDiscardDialog() }
        )
    }
}

