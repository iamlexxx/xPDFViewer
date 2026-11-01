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
import com.xpdfviewer.ui.components.AudioPlayerDock
import com.xpdfviewer.ui.components.AudioVoiceSettingsSheet
import com.xpdfviewer.ui.components.DeletePageDialog
import com.xpdfviewer.ui.components.DocumentOutlineSheet
import com.xpdfviewer.ui.components.ExtractPageDialog
import com.xpdfviewer.data.ReadingMode
import com.xpdfviewer.ui.components.FloatingTextToolbar
import com.xpdfviewer.ui.components.FloatingViewerNavigationMenu
import com.xpdfviewer.ui.components.FormActionsBar
import com.xpdfviewer.ui.components.FormFieldsBottomSheet
import com.xpdfviewer.ui.components.JumpToPageDialog
import com.xpdfviewer.ui.components.PageActionsBottomSheet
import com.xpdfviewer.ui.components.PdfPageItem
import com.xpdfviewer.ui.components.PdfTopBar
import com.xpdfviewer.ui.components.ReadingModeBottomSheet
import com.xpdfviewer.ui.components.SaveChoiceDialog
import com.xpdfviewer.ui.components.ZoomableBox
import com.xpdfviewer.ui.dialogs.AddEditBookmarkDialog
import com.xpdfviewer.ui.dialogs.EditFormFieldDialog
import com.xpdfviewer.ui.dialogs.FormSaveChoiceDialog
import com.xpdfviewer.viewmodel.PageOrganizerViewModel
import com.xpdfviewer.viewmodel.PdfViewerViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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

    var pendingFormFlatten by remember { mutableStateOf(false) }

    val saveFormLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destinationUri ->
        if (destinationUri != null) {
            viewModel.saveFormToDestination(context, destinationUri, flatten = pendingFormFlatten)
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
    var isPageOrganizerOpen by remember { mutableStateOf(false) }
    var showVoiceSettingsSheet by remember { mutableStateOf(false) }
    val pageOrganizerViewModel: PageOrganizerViewModel = viewModel()

    // Hardware back navigation returns to library view or exits text selection / search
    BackHandler(enabled = uiState.uri != null && !isSignPdfOpen && !isPageOrganizerOpen) {
        if (uiState.searchState.isSearchActive) {
            viewModel.closeSearch()
        } else if (uiState.onScreenSelectPageIndex != null) {
            viewModel.exitOnScreenTextSelection()
        } else {
            viewModel.closeDocument()
        }
    }

    // Scroll to page when active search match changes
    LaunchedEffect(uiState.searchState.currentMatchIndex) {
        val targetMatch = uiState.searchState.currentMatch
        if (targetMatch != null) {
            val pageIdx = targetMatch.pageIndex
            if (isLandscape) {
                pagerState.animateScrollToPage(pageIdx)
            } else {
                listState.animateScrollToItem(pageIdx)
            }
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
                                            text = if (uiState.selectedWordCount > 0) "${uiState.selectedWordCount} words selected" else "Tap or drag to select text",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                            color = if (uiState.selectedWordCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
                            isVisible = uiState.showControls,
                            onOpenJumpDialog = { viewModel.showJumpDialog(true) },
                            onCloseDocument = { viewModel.closeDocument() },
                            onPrintDocument = { viewModel.printDocument(context) },
                            onOpenPageOrganizer = { isPageOrganizerOpen = true },
                            isCurrentPageBookmarked = uiState.isCurrentPageBookmarked,
                            onToggleBookmark = { viewModel.toggleCurrentPageBookmark() },
                            onOpenOutline = { viewModel.openOutlineSheet() },
                            searchState = uiState.searchState,
                            onOpenSearch = { viewModel.openSearch() },
                            onCloseSearch = { viewModel.closeSearch() },
                            onSearchQueryChange = { query -> viewModel.updateSearchQuery(context, query) },
                            onPreviousSearchMatch = { viewModel.previousSearchMatch() },
                            onNextSearchMatch = { viewModel.nextSearchMatch() },
                            readingMode = uiState.readingMode,
                            onOpenReadingMode = { viewModel.openReadingModeSheet() },
                            hasAcroForm = uiState.formState.hasAcroForm,
                            onOpenFormSheet = { viewModel.setFormSheetVisible(true) },
                            isAudioReaderActive = uiState.audioReaderState.isActive,
                            onToggleAudioReader = { viewModel.toggleAudioReader(context) }
                        )
                    }

                    val isSelectionActive = uiState.onScreenSelectPageIndex != null
                    val viewportBg = uiState.readingMode.viewportBackgroundColor ?: MaterialTheme.colorScheme.background

                    // PDF Viewport container positioned strictly below TopBar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(viewportBg)
                    ) {
                        ZoomableBox(
                            modifier = Modifier.fillMaxSize(),
                            onTap = {
                                if (!isSelectionActive && !uiState.audioReaderState.isActive) {
                                    viewModel.toggleControls()
                                }
                            }
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

                        // Floating Text Action Toolbar (Copy, Share, Web Search, Speak)
                        if (isSelectionActive) {
                            FloatingTextToolbar(
                                selectedText = uiState.selectedWordText,
                                wordCount = uiState.selectedWordCount,
                                isSpeaking = uiState.isTtsSpeaking,
                                onCopy = {
                                    viewModel.copyToClipboard(context, uiState.selectedWordText, "Selected Text")
                                },
                                onSelectAll = { viewModel.selectAllWords() },
                                onSpeakToggle = {
                                    if (uiState.isTtsSpeaking) {
                                        viewModel.stopSpeaking()
                                    } else {
                                        viewModel.speakText(context, uiState.selectedWordText)
                                    }
                                },
                                onDismiss = { viewModel.clearWordSelection() },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(bottom = 16.dp)
                            )
                        }

                        // Floating Form Actions Bar (Fill Form, Save/Flatten)
                        if (!isSelectionActive && uiState.formState.hasAcroForm) {
                            FormActionsBar(
                                formState = uiState.formState,
                                onOpenFormSheet = { viewModel.setFormSheetVisible(true) },
                                onOpenSaveDialog = { viewModel.setFormSaveChoiceDialogVisible(true) },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(bottom = 16.dp)
                            )
                        }

                        // Floating Continuous Audio Reader Dock
                        if (uiState.audioReaderState.isActive && !isSelectionActive) {
                            AudioPlayerDock(
                                uiState = uiState.audioReaderState,
                                totalPages = uiState.pageCount,
                                onTogglePlayPause = { viewModel.toggleAudioPlayPause(context) },
                                onSkipNext = { viewModel.skipAudioNext(context) },
                                onSkipPrevious = { viewModel.skipAudioPrevious(context) },
                                onSelectSpeed = { viewModel.setAudioSpeed(it) },
                                onSelectSleepTimer = { viewModel.setAudioSleepTimer(it) },
                                onToggleSkipHeaderFooter = { viewModel.toggleSkipHeaderFooter(context) },
                                onOpenVoiceSettings = { showVoiceSettingsSheet = true },
                                onClose = { viewModel.stopAudioReader(context) },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(bottom = if (uiState.formState.hasAcroForm) 76.dp else 16.dp)
                            )
                        }

                        // Floating Viewer Navigation Menu (Outline, Bookmark, Audio, Reading Mode, Page Grid)
                        if (!isSelectionActive && !uiState.audioReaderState.isActive) {
                            FloatingViewerNavigationMenu(
                                isVisible = uiState.showControls,
                                onOpenOutline = { viewModel.openOutlineSheet() },
                                isCurrentPageBookmarked = uiState.isCurrentPageBookmarked,
                                onToggleBookmark = { viewModel.toggleCurrentPageBookmark() },
                                isAudioReaderActive = uiState.audioReaderState.isActive,
                                onToggleAudioReader = { viewModel.toggleAudioReader(context) },
                                isReadingModeActive = (uiState.readingMode != ReadingMode.DEFAULT),
                                onOpenReadingMode = { viewModel.openReadingModeSheet() },
                                onOpenPageOrganizer = { isPageOrganizerOpen = true },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(bottom = if (uiState.formState.hasAcroForm) 76.dp else 20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Voice & Pitch Settings Bottom Sheet
        if (showVoiceSettingsSheet) {
            AudioVoiceSettingsSheet(
                availableVoices = viewModel.getAvailableVoices(context),
                selectedVoiceId = uiState.audioReaderState.selectedVoiceId,
                currentPitch = uiState.audioReaderState.pitch,
                currentSpeed = uiState.audioReaderState.speed,
                isPiperStudioReady = uiState.audioReaderState.isPiperStudioReady,
                isPiperDownloading = uiState.audioReaderState.isPiperDownloading,
                piperDownloadProgress = uiState.audioReaderState.piperDownloadProgress,
                piperStatusMessage = uiState.audioReaderState.piperStatusMessage,
                studioVoiceStatuses = uiState.audioReaderState.studioVoiceStatuses,
                onDownloadPiperVoice = { voiceId -> viewModel.downloadPiperStudioVoice(context, voiceId) },
                onCancelPiperDownload = { voiceId -> viewModel.cancelPiperDownload(voiceId) },
                onDeletePiperVoice = { voiceId -> viewModel.deletePiperStudioVoice(context, voiceId) },
                onSelectVoice = { viewModel.setAudioVoice(it) },
                onSelectPitch = { viewModel.setAudioPitch(it) },
                onSelectSpeed = { viewModel.setAudioSpeed(it) },
                onDismiss = { showVoiceSettingsSheet = false }
            )
        }

        // Auto-scroll when Continuous Audio Reader advances page
        LaunchedEffect(uiState.audioReaderState.currentPageIndex, uiState.audioReaderState.isActive) {
            if (uiState.audioReaderState.isActive && uiState.pageCount > 0) {
                val targetIdx = uiState.audioReaderState.currentPageIndex.coerceIn(0, uiState.pageCount - 1)
                if (isLandscape) {
                    pagerState.animateScrollToPage(targetIdx)
                } else {
                    listState.animateScrollToItem(targetIdx)
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
                onOrganizePages = {
                    viewModel.closePageActions()
                    isPageOrganizerOpen = true
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

        // Document Outline (Table of Contents) & User Bookmarks Bottom Sheet
        if (uiState.showOutlineSheet) {
            DocumentOutlineSheet(
                showSheet = true,
                outlineNodes = uiState.outlineNodes,
                userBookmarks = uiState.userBookmarks,
                selectedTab = uiState.selectedOutlineTab,
                currentPage = uiState.currentPage,
                fileName = uiState.fileName,
                isLoading = uiState.isOutlineLoading,
                onDismiss = { viewModel.closeOutlineSheet() },
                onSelectTab = { viewModel.selectOutlineTab(it) },
                onJumpToPage = { targetPageIndex ->
                    viewModel.jumpToOutlinePage(targetPageIndex)
                    coroutineScope.launch {
                        if (isLandscape) {
                            pagerState.animateScrollToPage(targetPageIndex)
                        } else {
                            listState.animateScrollToItem(targetPageIndex)
                        }
                    }
                },
                onAddBookmarkForCurrentPage = {
                    viewModel.openAddBookmarkDialog(uiState.currentPage - 1)
                },
                onEditBookmark = { bookmark ->
                    viewModel.openAddBookmarkDialog(bookmark.pageIndex)
                },
                onDeleteBookmark = { bookmarkId ->
                    viewModel.deleteBookmark(bookmarkId)
                }
            )
        }

        // Add / Edit Bookmark Dialog
        if (uiState.addBookmarkDialogState.isOpen) {
            AddEditBookmarkDialog(
                dialogState = uiState.addBookmarkDialogState,
                onDismiss = { viewModel.closeAddBookmarkDialog() },
                onSave = { pageIndex, title, note, existingId ->
                    viewModel.saveBookmark(pageIndex, title, note, existingId)
                },
                onDelete = { bookmarkId ->
                    viewModel.deleteBookmark(bookmarkId)
                }
            )
        }

        // Reading Mode & Eye Comfort Presets Bottom Sheet
        if (uiState.showReadingModeSheet) {
            ReadingModeBottomSheet(
                showSheet = true,
                currentMode = uiState.readingMode,
                onSelectMode = { viewModel.selectReadingMode(it) },
                onDismiss = { viewModel.closeReadingModeSheet() }
            )
        }

        // Form Fields Bottom Sheet Assistant
        if (uiState.formState.showFormSheet) {
            FormFieldsBottomSheet(
                formState = uiState.formState,
                onDismiss = { viewModel.setFormSheetVisible(false) },
                onUpdateTextField = { id, text -> viewModel.updateFormFieldValue(id, text) },
                onToggleCheckbox = { id, isChecked -> viewModel.updateCheckboxField(id, isChecked) },
                onSelectRadio = { id, opt -> viewModel.updateFormFieldValue(id, opt) },
                onSelectChoice = { id, opt -> viewModel.updateFormFieldValue(id, opt) },
                onJumpToPage = { targetPage ->
                    coroutineScope.launch {
                        val targetIndex = (targetPage - 1).coerceAtLeast(0)
                        if (isLandscape) {
                            pagerState.animateScrollToPage(targetIndex)
                        } else {
                            listState.animateScrollToItem(targetIndex)
                        }
                    }
                },
                onClearAllFields = { viewModel.clearAllFormFields() },
                onOpenSaveDialog = { viewModel.setFormSaveChoiceDialogVisible(true) }
            )
        }

        // Form Save Choice Dialog (Flattened vs Interactive)
        if (uiState.formState.showSaveChoiceDialog) {
            FormSaveChoiceDialog(
                onDismiss = { viewModel.setFormSaveChoiceDialogVisible(false) },
                onSaveFlattened = {
                    pendingFormFlatten = true
                    val baseName = uiState.fileName.substringBeforeLast(".pdf", uiState.fileName)
                    saveFormLauncher.launch("${baseName}_flattened.pdf")
                },
                onSaveInteractive = {
                    pendingFormFlatten = false
                    val baseName = uiState.fileName.substringBeforeLast(".pdf", uiState.fileName)
                    saveFormLauncher.launch("${baseName}_filled.pdf")
                }
            )
        }

        // Direct Field Tapping Edit Dialog
        uiState.formState.editingField?.let { field ->
            EditFormFieldDialog(
                field = field,
                onDismiss = { viewModel.dismissEditingField() },
                onSaveValue = { newVal ->
                    viewModel.updateFormFieldValue(field.id, newVal)
                }
            )
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

        // Fullscreen Visual Page Organizer Overlay
        AnimatedVisibility(
            visible = isPageOrganizerOpen,
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
                PageOrganizerScreen(
                    viewModel = pageOrganizerViewModel,
                    onNavigateBack = { isPageOrganizerOpen = false },
                    onDocumentSaved = { savedUri ->
                        isPageOrganizerOpen = false
                        viewModel.loadPdf(context, savedUri)
                    },
                    initialPdfUri = uiState.uri,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
