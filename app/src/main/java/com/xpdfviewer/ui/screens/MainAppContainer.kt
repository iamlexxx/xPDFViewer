package com.xpdfviewer.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.xpdfviewer.data.AppTab
import com.xpdfviewer.data.PdfViewerUiState
import com.xpdfviewer.data.RecentDocument
import com.xpdfviewer.scanner.DocumentScannerManager
import com.xpdfviewer.scanner.ScannedDocumentResult
import com.xpdfviewer.ui.components.AppBottomNav
import com.xpdfviewer.ui.components.AppDrawerContent
import com.xpdfviewer.ui.components.CreatePdfSpeedDial
import com.xpdfviewer.ui.dialogs.ScanFilenamePromptDialog
import com.xpdfviewer.ui.dialogs.ScannedDocumentDialog
import com.xpdfviewer.viewmodel.PdfViewerViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

enum class ActiveToolScreen {
    NONE,
    CUSTOM_SCANNER,
    OCR_SCANNER,
    MERGE_PDF,
    COMPRESS_PDF,
    SIGN_PDF,
    SECURITY_TOOL,
    SPLIT_PDF,
    REDACT_PDF
}

@Composable
fun MainAppContainer(
    uiState: PdfViewerUiState,
    viewModel: PdfViewerViewModel,
    onOpenFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeToolScreen by remember { mutableStateOf(ActiveToolScreen.NONE) }
    var displayedToolScreen by remember { mutableStateOf(ActiveToolScreen.NONE) }
    var isSpeedDialExpanded by remember { mutableStateOf(false) }

    if (activeToolScreen != ActiveToolScreen.NONE) {
        displayedToolScreen = activeToolScreen
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                AppDrawerContent(
                    isDarkMode = uiState.isDarkMode,
                    onToggleDarkMode = { viewModel.setDarkMode(it) },
                    onOpenFile = {
                        coroutineScope.launch { drawerState.close() }
                        onOpenFile()
                    },
                    onCloseDrawer = {
                        coroutineScope.launch { drawerState.close() }
                    }
                )
            }
        ) {
            Scaffold(
                topBar = {
                    MainTopAppBar(
                        onOpenDrawer = {
                            coroutineScope.launch { drawerState.open() }
                        }
                    )
                },
                bottomBar = {
                    AppBottomNav(
                        selectedTab = uiState.selectedTab,
                        onTabSelected = {
                            isSpeedDialExpanded = false
                            viewModel.selectTab(it)
                        }
                    )
                },
                floatingActionButton = {
                    CreatePdfSpeedDial(
                        expanded = isSpeedDialExpanded,
                        onToggle = { isSpeedDialExpanded = !isSpeedDialExpanded },
                        onScanDocument = {
                            isSpeedDialExpanded = false
                            activeToolScreen = ActiveToolScreen.CUSTOM_SCANNER
                        },
                        onMergePdf = {
                            isSpeedDialExpanded = false
                            activeToolScreen = ActiveToolScreen.MERGE_PDF
                        }
                    )
                },
                floatingActionButtonPosition = FabPosition.End,
                containerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Crossfade(
                        targetState = uiState.selectedTab,
                        modifier = Modifier.fillMaxSize()
                    ) { tab ->
                        when (tab) {
                            AppTab.HOME -> {
                                HomeScreen(
                                    recentDocuments = uiState.recentDocuments,
                                    onOpenFile = onOpenFile,
                                    onOpenRecentDocument = { doc ->
                                        viewModel.loadPdf(context, doc.uri, doc.lastReadPage)
                                    },
                                    onRemoveRecentDocument = { doc ->
                                        viewModel.removeRecentDocument(doc)
                                    },
                                    onViewAllRecents = { viewModel.selectTab(AppTab.RECENT) }
                                )
                            }
                            AppTab.RECENT -> {
                                RecentScreen(
                                    recentDocuments = uiState.recentDocuments,
                                    onOpenRecentDocument = { doc ->
                                        viewModel.loadPdf(context, doc.uri, doc.lastReadPage)
                                    },
                                    onRemoveRecentDocument = { doc ->
                                        viewModel.removeRecentDocument(doc)
                                    },
                                    onClearRecents = { viewModel.clearRecentHistory() }
                                )
                            }
                            AppTab.TOOLS -> {
                                ToolsScreen(
                                    onOpenOcrScanner = { activeToolScreen = ActiveToolScreen.OCR_SCANNER },
                                    onOpenMergePdf = { activeToolScreen = ActiveToolScreen.MERGE_PDF },
                                    onOpenCompressPdf = { activeToolScreen = ActiveToolScreen.COMPRESS_PDF },
                                    onOpenSignPdf = { activeToolScreen = ActiveToolScreen.SIGN_PDF },
                                    onOpenSecurity = { activeToolScreen = ActiveToolScreen.SECURITY_TOOL },
                                    onOpenSplitPdf = { activeToolScreen = ActiveToolScreen.SPLIT_PDF },
                                    onOpenRedactPdf = { activeToolScreen = ActiveToolScreen.REDACT_PDF }
                                )
                            }
                        }
                    }

                    // Scrim overlay when speed dial is open
                    if (isSpeedDialExpanded) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    isSpeedDialExpanded = false
                                }
                        )
                    }
                }
            }
        }

        // Full-screen animated overlay for PDF Utilities tool screens
        AnimatedVisibility(
            visible = activeToolScreen != ActiveToolScreen.NONE,
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
                when (displayedToolScreen) {
                    ActiveToolScreen.CUSTOM_SCANNER -> {
                        CustomDocumentScannerScreen(
                            onNavigateBack = { activeToolScreen = ActiveToolScreen.NONE },
                            onOpenPdf = { uri ->
                                activeToolScreen = ActiveToolScreen.NONE
                                viewModel.loadPdf(context, uri)
                            }
                        )
                    }
                    ActiveToolScreen.OCR_SCANNER -> {
                        OcrScannerScreen(
                            onNavigateBack = { activeToolScreen = ActiveToolScreen.NONE }
                        )
                    }
                    ActiveToolScreen.MERGE_PDF -> {
                        MergePdfScreen(
                            onNavigateBack = { activeToolScreen = ActiveToolScreen.NONE },
                            onOpenMergedPdf = { uri ->
                                activeToolScreen = ActiveToolScreen.NONE
                                viewModel.loadPdf(context, uri)
                            }
                        )
                    }
                    ActiveToolScreen.COMPRESS_PDF -> {
                        CompressPdfScreen(
                            onNavigateBack = { activeToolScreen = ActiveToolScreen.NONE },
                            onOpenCompressedPdf = { uri ->
                                activeToolScreen = ActiveToolScreen.NONE
                                viewModel.loadPdf(context, uri)
                            }
                        )
                    }
                    ActiveToolScreen.SIGN_PDF -> {
                        SignPdfScreen(
                            onNavigateBack = { activeToolScreen = ActiveToolScreen.NONE },
                            onOpenSignedPdf = { uri ->
                                activeToolScreen = ActiveToolScreen.NONE
                                viewModel.loadPdf(context, uri)
                            }
                        )
                    }
                    ActiveToolScreen.SECURITY_TOOL -> {
                        PdfSecurityScreen(
                            onNavigateBack = { activeToolScreen = ActiveToolScreen.NONE },
                            onOpenPdf = { uri ->
                                activeToolScreen = ActiveToolScreen.NONE
                                viewModel.loadPdf(context, uri)
                            }
                        )
                    }
                    ActiveToolScreen.SPLIT_PDF -> {
                        SplitPdfScreen(
                            onNavigateBack = { activeToolScreen = ActiveToolScreen.NONE },
                            onOpenPdf = { uri ->
                                activeToolScreen = ActiveToolScreen.NONE
                                viewModel.loadPdf(context, uri)
                            }
                        )
                    }
                    ActiveToolScreen.REDACT_PDF -> {
                        PdfRedactionScreen(
                            onNavigateBack = { activeToolScreen = ActiveToolScreen.NONE },
                            onOpenPdf = { uri ->
                                activeToolScreen = ActiveToolScreen.NONE
                                viewModel.loadPdf(context, uri)
                            }
                        )
                    }
                    ActiveToolScreen.NONE -> {
                        // Empty placeholder
                    }
                }
            }
        }
    }
}

@Composable
fun MainTopAppBar(
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            // Hamburger menu button
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open navigation drawer",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Brand title
            Row(modifier = Modifier.weight(1f)) {
                Text(
                    text = "xPDF",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Viewer",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
