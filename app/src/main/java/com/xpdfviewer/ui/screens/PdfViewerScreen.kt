package com.xpdfviewer.ui.screens

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xpdfviewer.ui.components.DeletePageDialog
import com.xpdfviewer.ui.components.ExtractPageDialog
import com.xpdfviewer.ui.components.JumpToPageDialog
import com.xpdfviewer.ui.components.PageActionsBottomSheet
import com.xpdfviewer.ui.components.PdfPageItem
import com.xpdfviewer.ui.components.PdfTopBar
import com.xpdfviewer.ui.components.SaveChoiceDialog
import com.xpdfviewer.ui.components.ZoomableBox
import com.xpdfviewer.viewmodel.PdfViewerViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun PdfViewerScreen(
    viewModel: PdfViewerViewModel,
    onOpenFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val saveCopyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destinationUri ->
        if (destinationUri != null) {
            viewModel.executePendingActionSaveCopy(context, destinationUri)
        } else {
            viewModel.cancelPendingAction()
        }
    }

    val pickPdfToInsertLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { insertUri ->
        if (insertUri != null) {
            viewModel.prepareInsertPdf(context, insertUri, uiState.pageActionsTargetIndex)
        }
    }

    val pagerState = rememberPagerState(
        initialPage = (uiState.currentPage - 1).coerceAtLeast(0),
        pageCount = { uiState.pageCount }
    )

    var isSignPdfOpen by remember { mutableStateOf(false) }

    // Hardware back navigation returns to library view or exits text selection
    BackHandler(enabled = uiState.uri != null && !isSignPdfOpen) {
        if (uiState.onScreenSelectPageIndex != null) {
            viewModel.exitOnScreenTextSelection()
        } else {
            viewModel.closeDocument()
        }
    }

    // Scroll to page when on-screen selection activates
    LaunchedEffect(uiState.onScreenSelectPageIndex) {
        val selIdx = uiState.onScreenSelectPageIndex
        if (selIdx != null) {
            if (isLandscape) {
                pagerState.animateScrollToPage(selIdx)
            } else {
                listState.animateScrollToItem(selIdx)
            }
        }
    }

    // Sync scroll / page position when document URI loads or orientation flips
    LaunchedEffect(isLandscape, uiState.uri) {
        if (uiState.uri != null && uiState.pageCount > 0) {
            val targetIndex = (uiState.currentPage - 1).coerceIn(0, uiState.pageCount - 1)
            if (isLandscape) {
                pagerState.scrollToPage(targetIndex)
            } else {
                listState.scrollToItem(targetIndex)
            }
        }
    }

    // Landscape Mode: sync active page from HorizontalPager swipe
    LaunchedEffect(pagerState, isLandscape) {
        if (isLandscape) {
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collect { page ->
                    viewModel.updateCurrentPage(page + 1)
                }
        }
    }

    // Portrait Mode: accurately calculate active page based on scroll position
    LaunchedEffect(listState, uiState.pageCount, isLandscape) {
        if (!isLandscape) {
            snapshotFlow {
                val firstIndex = listState.firstVisibleItemIndex
                val scrollOffset = listState.firstVisibleItemScrollOffset
                val totalPages = uiState.pageCount.coerceAtLeast(1)

                if (firstIndex == 0 && scrollOffset <= 100) {
                    1
                } else {
                    val layoutInfo = listState.layoutInfo
                    val lastItem = layoutInfo.visibleItemsInfo.lastOrNull()

                    // Check if last page is reached and resting at bottom
                    if (lastItem != null && lastItem.index == totalPages - 1 &&
                        lastItem.offset + lastItem.size <= layoutInfo.viewportEndOffset
                    ) {
                        totalPages
                    } else {
                        val firstItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == firstIndex }
                        val itemSize = firstItem?.size ?: 1000

                        // If top item has scrolled more than halfway off the screen, advance to next page
                        val calculatedPage = if (scrollOffset > itemSize / 2) {
                            firstIndex + 2
                        } else {
                            firstIndex + 1
                        }
                        calculatedPage.coerceIn(1, totalPages)
                    }
                }
            }
            .distinctUntilChanged()
            .collect { activePage ->
                viewModel.updateCurrentPage(activePage)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading document...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            uiState.errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "Unknown error",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onOpenFile) {
                            Text(text = "Choose Another PDF")
                        }
                    }
                }
            }

            uiState.uri == null -> {
                WelcomeScreen(onOpenFile = onOpenFile)
            }

            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Bar: contextual selection header or standard reader top bar
                    if (uiState.onScreenSelectPageIndex != null) {
                        val selPage = uiState.onScreenSelectPageIndex!!
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 8.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    IconButton(onClick = { viewModel.exitOnScreenTextSelection() }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Exit text selection",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Column {
                                        Text(
                                            text = "Page ${selPage + 1} Selection",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Drag handles to select text",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (uiState.onScreenSelectText.isNotBlank()) {
                                        TextButton(
                                            onClick = {
                                                viewModel.copyToClipboard(context, uiState.onScreenSelectText, "Page ${selPage + 1} Text")
                                            }
                                        ) {
                                            Text(
                                                text = "Copy All",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = { viewModel.exitOnScreenTextSelection() },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = "Done",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        PdfTopBar(
                            fileName = uiState.fileName,
                            currentPage = uiState.currentPage,
                            pageCount = uiState.pageCount,
                            isVisible = true,
                            onOpenJumpDialog = { viewModel.showJumpDialog(true) },
                            onCloseDocument = { viewModel.closeDocument() },
                            onPrintDocument = { viewModel.printDocument(context) }
                        )
                    }

                    val isSelectionActive = uiState.onScreenSelectPageIndex != null

                    // PDF Viewport container positioned strictly below TopBar
                    ZoomableBox(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        onTap = {}
                    ) { currentScale ->
                        if (isLandscape) {
                            // Landscape Mode: HorizontalPager (scroll disabled during text handle dragging)
                            HorizontalPager(
                                state = pagerState,
                                userScrollEnabled = !isSelectionActive && currentScale <= 1.05f,
                                modifier = Modifier.fillMaxSize()
                            ) { pageIndex ->
                                PdfPageItem(
                                    pageIndex = pageIndex,
                                    viewModel = viewModel,
                                    isLandscape = true,
                                    onLongPress = {
                                        viewModel.openPageActions(pageIndex)
                                    }
                                )
                            }
                        } else {
                            // Portrait Mode: Continuous vertical scroll (scroll disabled during text handle dragging)
                            LazyColumn(
                                state = listState,
                                userScrollEnabled = !isSelectionActive && currentScale <= 1.05f,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
                            ) {
                                items(
                                    count = uiState.pageCount,
                                    key = { pageIndex -> pageIndex }
                                ) { pageIndex ->
                                    PdfPageItem(
                                        pageIndex = pageIndex,
                                        viewModel = viewModel,
                                        isLandscape = false,
                                        onLongPress = {
                                            viewModel.openPageActions(pageIndex)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Jump to Page Dialog
        if (uiState.showJumpDialog && uiState.pageCount > 0) {
            JumpToPageDialog(
                currentPage = uiState.currentPage,
                pageCount = uiState.pageCount,
                onDismiss = { viewModel.showJumpDialog(false) },
                onJump = { targetPage ->
                    coroutineScope.launch {
                        val targetIndex = (targetPage - 1).coerceAtLeast(0)
                        if (isLandscape) {
                            pagerState.animateScrollToPage(targetIndex)
                        } else {
                            listState.animateScrollToItem(targetIndex)
                        }
                    }
                }
            )
        }


        // Multi-function Page Actions Bottom Sheet (Select Text, Add Page, Extract Page, Delete Page, Add E-Signature)
        if (uiState.showPageActionsSheet) {
            PageActionsBottomSheet(
                pageNumber = uiState.pageActionsTargetIndex + 1,
                pageCount = uiState.pageCount,
                onSelectText = {
                    viewModel.openTextSelectionForTargetPage(context)
                },
                onAddPage = {
                    viewModel.closePageActions()
                    pickPdfToInsertLauncher.launch(arrayOf("application/pdf"))
                },
                onExtractPage = {
                    viewModel.requestExtractPage()
                },
                onDeletePage = {
                    viewModel.requestDeletePage()
                },
                onAddSignature = {
                    viewModel.closePageActions()
                    isSignPdfOpen = true
                },
                onDismiss = {
                    viewModel.closePageActions()
                }
            )
        }

        // Extract Page Confirmation Dialog
        if (uiState.showExtractConfirmDialog) {
            ExtractPageDialog(
                pageNumber = uiState.pageActionsTargetIndex + 1,
                onDismiss = {
                    viewModel.dismissExtractDialog()
                },
                onConfirm = {
                    viewModel.confirmExtractRequest()
                }
            )
        }

        // Delete Page Confirmation Dialog
        if (uiState.showDeleteConfirmDialog) {
            DeletePageDialog(
                pageNumber = uiState.pageActionsTargetIndex + 1,
                onDismiss = {
                    viewModel.dismissDeleteDialog()
                },
                onConfirm = {
                    viewModel.confirmDeleteRequest()
                }
            )
        }

        // Save Choice Dialog: Overwrite vs Save As Copy
        if (uiState.showSaveChoiceDialog) {
            SaveChoiceDialog(
                actionTitle = viewModel.getPendingActionTitle(),
                onOverwrite = {
                    viewModel.executePendingActionOverwrite(context)
                },
                onSaveAsCopy = {
                    val suggestedName = viewModel.getSuggestedFileName()
                    saveCopyLauncher.launch(suggestedName)
                },
                onDismiss = {
                    viewModel.cancelPendingAction()
                }
            )
        }

        // Loading Overlay during PDF Page Modification (Add/Delete)
        if (uiState.isModifyingDocument) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Updating document...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Full-screen animated overlay for E-Signature
        AnimatedVisibility(
            visible = isSignPdfOpen,
            enter = slideInVertically(
                initialOffsetY = { (it * 0.28f).toInt() },
                animationSpec = tween(
                    durationMillis = 350,
                    easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
                )
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = 280,
                    easing = LinearOutSlowInEasing
                )
            ) + scaleIn(
                initialScale = 0.92f,
                animationSpec = tween(
                    durationMillis = 350,
                    easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
                )
            ),
            exit = slideOutVertically(
                targetOffsetY = { (it * 0.22f).toInt() },
                animationSpec = tween(
                    durationMillis = 240,
                    easing = FastOutLinearInEasing
                )
            ) + fadeOut(
                animationSpec = tween(
                    durationMillis = 200,
                    easing = FastOutLinearInEasing
                )
            ) + scaleOut(
                targetScale = 0.94f,
                animationSpec = tween(
                    durationMillis = 240,
                    easing = FastOutLinearInEasing
                )
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                SignPdfScreen(
                    onNavigateBack = { isSignPdfOpen = false },
                    onOpenSignedPdf = { signedUri ->
                        isSignPdfOpen = false
                        viewModel.loadPdf(context, signedUri)
                    },
                    initialPdfUri = uiState.uri,
                    initialPageIndex = uiState.pageActionsTargetIndex,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
