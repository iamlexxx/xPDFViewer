package com.xpdfviewer.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import kotlin.math.sqrt
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.xpdfviewer.scanner.DocumentImageProcessor
import com.xpdfviewer.scanner.DocumentScanPage
import com.xpdfviewer.scanner.ScanFilterMode
import com.xpdfviewer.scanner.ScannedDocumentResult
import com.xpdfviewer.scanner.ScannedPdfCreator
import com.xpdfviewer.ui.dialogs.ScanFilenamePromptDialog
import com.xpdfviewer.ui.dialogs.ScannedDocumentDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

@Composable
fun CustomDocumentScannerScreen(
    onNavigateBack: () -> Unit,
    onOpenPdf: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pages = remember { mutableStateListOf<DocumentScanPage>() }
    var selectedPageIndex by remember { mutableIntStateOf(0) }
    var isCapturingMode by remember { mutableStateOf(true) }

    // Dialog & Save State
    var showFilenameDialog by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }
    var pendingSaveFileName by remember { mutableStateOf("") }
    var savedScanResult by remember { mutableStateOf<ScannedDocumentResult?>(null) }
    var isGeneratingPdf by remember { mutableStateOf(false) }

    // Launcher for Storage Access Framework destination picker
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destinationUri: Uri? ->
        if (destinationUri != null) {
            isGeneratingPdf = true
            val result = ScannedPdfCreator.generatePdfToDestination(
                context = context,
                pages = pages,
                destinationUri = destinationUri,
                fileName = pendingSaveFileName
            )
            isGeneratingPdf = false
            if (result != null) {
                savedScanResult = result
            } else {
                Toast.makeText(context, "Failed to create PDF", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Gallery launcher to import document photos
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { input ->
                    val bmp = BitmapFactory.decodeStream(input)
                    if (bmp != null) {
                        pages.add(DocumentScanPage(originalBitmap = bmp, uncroppedBitmap = bmp))
                        selectedPageIndex = pages.size - 1
                        isCapturingMode = false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler {
        if (!isCapturingMode && pages.isNotEmpty()) {
            showDiscardConfirmDialog = true
        } else {
            onNavigateBack()
        }
    }

    // 1. Filename Prompt Dialog before creating PDF
    if (showFilenameDialog) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        ScanFilenamePromptDialog(
            pageCount = pages.size,
            initialName = "Scanned_Document_$timeStamp",
            onConfirm = { chosenName ->
                showFilenameDialog = false
                pendingSaveFileName = chosenName
                saveFileLauncher.launch(chosenName)
            },
            onDismiss = { showFilenameDialog = false }
        )
    }

    // 2. Completion Dialog
    savedScanResult?.let { result ->
        ScannedDocumentDialog(
            result = result,
            onOpenInViewer = {
                val uri = result.uri
                savedScanResult = null
                onOpenPdf(uri)
            },
            onDismiss = {
                savedScanResult = null
                onNavigateBack()
            }
        )
    }

    // 3. Discard Scan Confirmation Dialog
    if (showDiscardConfirmDialog) {
        com.xpdfviewer.ui.dialogs.DiscardChangesDialog(
            title = "Discard Scan?",
            message = "All ${pages.size} captured pages will be cleared.",
            confirmButtonText = "Yes",
            dismissButtonText = "No",
            onConfirm = {
                showDiscardConfirmDialog = false
                pages.clear()
                onNavigateBack()
            },
            onDismiss = { showDiscardConfirmDialog = false }
        )
    }

    // 4. Loading overlay during PDF compilation
    if (isGeneratingPdf) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Creating PDF...") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    Text("Compiling ${pages.size} pages into PDF document")
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        if (isCapturingMode) {
            // CAMERA CAPTURE VIEW (3:4 aspect ratio with real-time auto-detecting document box)
            CameraCaptureView(
                pagesCount = pages.size,
                onPageCaptured = { croppedBitmap, uncroppedBitmap ->
                    pages.add(DocumentScanPage(originalBitmap = croppedBitmap, uncroppedBitmap = uncroppedBitmap))
                    selectedPageIndex = pages.size - 1
                    isCapturingMode = false
                },
                onOpenGallery = { galleryLauncher.launch("image/*") },
                onReviewPages = {
                    if (pages.isNotEmpty()) {
                        isCapturingMode = false
                    }
                },
                onClose = {
                    if (pages.isNotEmpty()) {
                        showDiscardConfirmDialog = true
                    } else {
                        onNavigateBack()
                    }
                }
            )
        } else {
            // DOCUMENT REVIEW & EDITOR VIEW
            if (pages.isNotEmpty() && selectedPageIndex in pages.indices) {
                val currentPage = pages[selectedPageIndex]

                DocumentReviewEditorView(
                    page = currentPage,
                    pageIndex = selectedPageIndex,
                    totalPages = pages.size,
                    onRotate = {
                        val nextRotation = (currentPage.rotationDegrees + 90) % 360
                        pages[selectedPageIndex] = currentPage.copy(rotationDegrees = nextRotation)
                    },
                    onToggleEnhance = {
                        pages[selectedPageIndex] = currentPage.copy(isEnhanced = !currentPage.isEnhanced)
                    },
                    onFilterSelected = { mode ->
                        pages[selectedPageIndex] = currentPage.copy(filterMode = mode)
                    },
                    onToggleClean = {
                        pages[selectedPageIndex] = currentPage.copy(isCleaned = !currentPage.isCleaned)
                    },
                    onApplyCrop = { cropped ->
                        pages[selectedPageIndex] = currentPage.copy(originalBitmap = cropped, rotationDegrees = 0)
                    },
                    onDeletePage = {
                        pages.removeAt(selectedPageIndex)
                        if (pages.isEmpty()) {
                            isCapturingMode = true
                        } else {
                            selectedPageIndex = (selectedPageIndex - 1).coerceAtLeast(0)
                        }
                    },
                    onSelectPage = { index ->
                        selectedPageIndex = index
                    },
                    pages = pages,
                    onAddPage = {
                        isCapturingMode = true
                    },
                    onDiscardScan = {
                        showDiscardConfirmDialog = true
                    },
                    onCreate = {
                        showFilenameDialog = true
                    },
                    onNavigateBack = {
                        isCapturingMode = true
                    }
                )
            }
        }
    }
}

/**
 * CameraX Viewfinder with:
 * 1. Flip camera button removed (fixed back camera)
 * 2. 3:4 aspect ratio viewfinder (no vertical stretch)
 * 3. Real-time auto-detect document bounding box overlay tracking documents dynamically
 */
@Composable
private fun CameraCaptureView(
    pagesCount: Int,
    onPageCaptured: (croppedBitmap: Bitmap, uncroppedBitmap: Bitmap?) -> Unit,
    onOpenGallery: () -> Unit,
    onReviewPages: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        // Permission Request UI
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Camera Permission Required",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "To scan documents with your camera, please grant camera permissions, or choose an existing photo from your gallery.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant Permission")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onOpenGallery) {
                        Text("Choose from Gallery")
                    }
                }
            }
        }
        return
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }

    var isFlashOn by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var detectedRect by remember { mutableStateOf<RectF?>(null) }
    var isDocumentLocked by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            textRecognizer.close()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
    ) {
        // TOP APP BAR (Back, Document counter badge, Flash toggle. Flip camera removed!)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }

            Text(
                text = if (pagesCount == 0) "Scan Document" else "$pagesCount pages captured",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )

            // Flash Toggle (Flip camera removed per user request!)
            IconButton(
                onClick = {
                    isFlashOn = !isFlashOn
                    imageCapture.flashMode = if (isFlashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Flash",
                    tint = if (isFlashOn) Color.Yellow else Color.White
                )
            }
        }

        // 3:4 CAMERA VIEWFINDER WITH REAL-TIME AUTO-DETECT BOUNDING BOX
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
            ) {
                // Live CameraX Preview
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder()
                                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                                .build()
                                .also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }

                            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                processImageProxyForAutoDetect(imageProxy, textRecognizer) { rect, locked ->
                                    ContextCompat.getMainExecutor(ctx).execute {
                                        detectedRect = rect
                                        isDocumentLocked = locked
                                    }
                                }
                            }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageCapture,
                                    imageAnalysis
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // DYNAMIC AUTO-DETECT DOCUMENT BOUNDING BOX OVERLAY
                AutoDetectDocumentOverlay(
                    detectedRect = detectedRect,
                    isLocked = isDocumentLocked
                )
            }
        }

        // BOTTOM CAPTURE CONTROLS
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp, top = 8.dp)
        ) {
            // Gallery Import
            IconButton(
                onClick = onOpenGallery,
                modifier = Modifier
                    .size(52.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = "Choose from gallery",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // High-Resolution Shutter Button
            Surface(
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier
                    .size(76.dp)
                    .clickable(enabled = !isCapturing) {
                        isCapturing = true
                        val cropRectToApply = if (isDocumentLocked && detectedRect != null) {
                            RectF(detectedRect)
                        } else null

                        imageCapture.takePicture(
                            cameraExecutor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                    val bitmap = imageProxy.toBitmap()
                                    val rotation = imageProxy.imageInfo.rotationDegrees
                                    val corrected = if (rotation != 0) {
                                        DocumentImageProcessor.rotateBitmap(bitmap, rotation.toFloat())
                                    } else {
                                        bitmap
                                    }
                                    imageProxy.close()

                                    val cropRect = cropRectToApply
                                    if (cropRect != null) {
                                        val cropped = DocumentImageProcessor.cropBitmapByNormRect(corrected, cropRect)
                                        ContextCompat.getMainExecutor(context).execute {
                                            isCapturing = false
                                            onPageCaptured(cropped, corrected)
                                        }
                                    } else {
                                        val inputImage = InputImage.fromBitmap(corrected, 0)
                                        textRecognizer.process(inputImage)
                                            .addOnSuccessListener { visionText ->
                                                val blocks = visionText.textBlocks
                                                if (blocks.isNotEmpty()) {
                                                    val frameWidth = corrected.width.toFloat()
                                                    val frameHeight = corrected.height.toFloat()
                                                    var minX = Float.MAX_VALUE
                                                    var minY = Float.MAX_VALUE
                                                    var maxX = Float.MIN_VALUE
                                                    var maxY = Float.MIN_VALUE

                                                    for (block in blocks) {
                                                        block.boundingBox?.let { box ->
                                                            minX = minOf(minX, box.left.toFloat())
                                                            minY = minOf(minY, box.top.toFloat())
                                                            maxX = maxOf(maxX, box.right.toFloat())
                                                            maxY = maxOf(maxY, box.bottom.toFloat())
                                                        }
                                                    }

                                                    if (maxX > minX && maxY > minY) {
                                                        val padX = (maxX - minX) * 0.16f + 24f
                                                        val padY = (maxY - minY) * 0.16f + 24f
                                                        val leftNorm = ((minX - padX) / frameWidth).coerceIn(0.02f, 0.45f)
                                                        val topNorm = ((minY - padY) / frameHeight).coerceIn(0.02f, 0.45f)
                                                        val rightNorm = ((maxX + padX) / frameWidth).coerceIn(0.55f, 0.98f)
                                                        val bottomNorm = ((maxY + padY) / frameHeight).coerceIn(0.55f, 0.98f)

                                                        val autoRect = RectF(leftNorm, topNorm, rightNorm, bottomNorm)
                                                        val cropped = DocumentImageProcessor.cropBitmapByNormRect(corrected, autoRect)
                                                        ContextCompat.getMainExecutor(context).execute {
                                                            isCapturing = false
                                                            onPageCaptured(cropped, corrected)
                                                        }
                                                        return@addOnSuccessListener
                                                    }
                                                }
                                                ContextCompat.getMainExecutor(context).execute {
                                                    isCapturing = false
                                                    onPageCaptured(corrected, null)
                                                }
                                            }
                                            .addOnFailureListener {
                                                ContextCompat.getMainExecutor(context).execute {
                                                    isCapturing = false
                                                    onPageCaptured(corrected, null)
                                                }
                                            }
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    exception.printStackTrace()
                                    ContextCompat.getMainExecutor(context).execute {
                                        isCapturing = false
                                        Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isCapturing) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), color = MaterialTheme.colorScheme.primary)
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .border(2.dp, Color.Black, CircleShape)
                        )
                    }
                }
            }

            // Review Shortcut
            if (pagesCount > 0) {
                Button(
                    onClick = onReviewPages,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Review", fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(modifier = Modifier.size(52.dp))
            }
        }
    }
}

/**
 * Intelligent real-time document bounding box overlay.
 * Dynamically frames the document or card with glowing reticles, smooth animations, and detection pill.
 */
@Composable
private fun AutoDetectDocumentOverlay(
    detectedRect: RectF?,
    isLocked: Boolean,
    modifier: Modifier = Modifier
) {
    // Default guide rect when no document text is detected yet
    val targetRect = detectedRect ?: RectF(0.08f, 0.08f, 0.92f, 0.92f)

    val animLeft by animateFloatAsState(
        targetValue = targetRect.left,
        animationSpec = tween(280),
        label = "animLeft"
    )
    val animTop by animateFloatAsState(
        targetValue = targetRect.top,
        animationSpec = tween(280),
        label = "animTop"
    )
    val animRight by animateFloatAsState(
        targetValue = targetRect.right,
        animationSpec = tween(280),
        label = "animRight"
    )
    val animBottom by animateFloatAsState(
        targetValue = targetRect.bottom,
        animationSpec = tween(280),
        label = "animBottom"
    )

    // Pulse animation for active scanning feedback
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaPulse by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alphaPulse"
    )

    val activeColor = if (isLocked) Color(0xFF00E676) else Color(0xFF00E5FF)

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            val leftPx = animLeft * canvasWidth
            val topPx = animTop * canvasHeight
            val rightPx = animRight * canvasWidth
            val bottomPx = animBottom * canvasHeight
            val boxWidth = rightPx - leftPx
            val boxHeight = bottomPx - topPx

            // 1. Subtle Outer Scrim (Dim area outside the document)
            drawRect(
                color = Color.Black.copy(alpha = 0.30f),
                topLeft = Offset(0f, 0f),
                size = Size(canvasWidth, topPx)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.30f),
                topLeft = Offset(0f, bottomPx),
                size = Size(canvasWidth, canvasHeight - bottomPx)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.30f),
                topLeft = Offset(0f, topPx),
                size = Size(leftPx, boxHeight)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.30f),
                topLeft = Offset(rightPx, topPx),
                size = Size(canvasWidth - rightPx, boxHeight)
            )

            // 2. Translucent document border line
            drawRoundRect(
                color = activeColor.copy(alpha = if (isLocked) 0.5f * alphaPulse else 0.3f),
                topLeft = Offset(leftPx, topPx),
                size = Size(boxWidth, boxHeight),
                cornerRadius = CornerRadius(14f, 14f),
                style = Stroke(width = 1.5f * density)
            )

            // 3. Four Bold Corner Reticles
            val bracketLength = 26f * density
            val bracketStroke = 3.5f * density
            val bracketColor = activeColor.copy(alpha = alphaPulse)

            // Top-Left corner
            drawLine(
                color = bracketColor,
                start = Offset(leftPx, topPx + bracketLength),
                end = Offset(leftPx, topPx),
                strokeWidth = bracketStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = bracketColor,
                start = Offset(leftPx, topPx),
                end = Offset(leftPx + bracketLength, topPx),
                strokeWidth = bracketStroke,
                cap = StrokeCap.Round
            )

            // Top-Right corner
            drawLine(
                color = bracketColor,
                start = Offset(rightPx - bracketLength, topPx),
                end = Offset(rightPx, topPx),
                strokeWidth = bracketStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = bracketColor,
                start = Offset(rightPx, topPx),
                end = Offset(rightPx, topPx + bracketLength),
                strokeWidth = bracketStroke,
                cap = StrokeCap.Round
            )

            // Bottom-Left corner
            drawLine(
                color = bracketColor,
                start = Offset(leftPx, bottomPx - bracketLength),
                end = Offset(leftPx, bottomPx),
                strokeWidth = bracketStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = bracketColor,
                start = Offset(leftPx, bottomPx),
                end = Offset(leftPx + bracketLength, bottomPx),
                strokeWidth = bracketStroke,
                cap = StrokeCap.Round
            )

            // Bottom-Right corner
            drawLine(
                color = bracketColor,
                start = Offset(rightPx - bracketLength, bottomPx),
                end = Offset(rightPx, bottomPx),
                strokeWidth = bracketStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = bracketColor,
                start = Offset(rightPx, bottomPx),
                end = Offset(rightPx, bottomPx - bracketLength),
                strokeWidth = bracketStroke,
                cap = StrokeCap.Round
            )
        }

        // Status Indicator Badge at bottom of viewfinder
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.70f),
                border = BorderStroke(1.dp, activeColor.copy(alpha = 0.45f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(activeColor, CircleShape)
                    )
                    Text(
                        text = if (isLocked) "Document Detected" else "Auto-detecting document...",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * ImageAnalysis helper for real-time document boundary extraction via ML Kit
 */
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processImageProxyForAutoDetect(
    imageProxy: ImageProxy,
    textRecognizer: com.google.mlkit.vision.text.TextRecognizer,
    onResult: (RectF?, Boolean) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
    val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

    textRecognizer.process(inputImage)
        .addOnSuccessListener { visionText ->
            val blocks = visionText.textBlocks
            if (blocks.isNotEmpty()) {
                val isRotated = rotationDegrees == 90 || rotationDegrees == 270
                val frameWidth = if (isRotated) imageProxy.height.toFloat() else imageProxy.width.toFloat()
                val frameHeight = if (isRotated) imageProxy.width.toFloat() else imageProxy.height.toFloat()

                var minX = Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxX = Float.MIN_VALUE
                var maxY = Float.MIN_VALUE

                for (block in blocks) {
                    block.boundingBox?.let { box ->
                        minX = minOf(minX, box.left.toFloat())
                        minY = minOf(minY, box.top.toFloat())
                        maxX = maxOf(maxX, box.right.toFloat())
                        maxY = maxOf(maxY, box.bottom.toFloat())
                    }
                }

                if (maxX > minX && maxY > minY) {
                    val padX = (maxX - minX) * 0.16f + 24f
                    val padY = (maxY - minY) * 0.16f + 24f

                    val leftNorm = ((minX - padX) / frameWidth).coerceIn(0.04f, 0.45f)
                    val topNorm = ((minY - padY) / frameHeight).coerceIn(0.04f, 0.45f)
                    val rightNorm = ((maxX + padX) / frameWidth).coerceIn(0.55f, 0.96f)
                    val bottomNorm = ((maxY + padY) / frameHeight).coerceIn(0.55f, 0.96f)

                    onResult(RectF(leftNorm, topNorm, rightNorm, bottomNorm), true)
                } else {
                    onResult(null, false)
                }
            } else {
                onResult(null, false)
            }
        }
        .addOnFailureListener {
            onResult(null, false)
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

/**
 * High-fidelity Document Review & Editor Screen
 * Features fully responsive buttons:
 * - Enhance (contrast & sharpness boost)
 * - Filters (Original, Enhance, Grayscale, B&W, Color)
 * - Crop (dedicated crop dialog with trim presets & margins)
 * - Rotate (instant 90° clockwise rotation)
 * - Clean (background paper whitening)
 * - Page Thumbnails + Labeled "+ Add Page" button
 * - "Discard scan" and "Create" primary action button
 */
@Composable
private fun DocumentReviewEditorView(
    page: DocumentScanPage,
    pageIndex: Int,
    totalPages: Int,
    onRotate: () -> Unit,
    onToggleEnhance: () -> Unit,
    onFilterSelected: (ScanFilterMode) -> Unit,
    onToggleClean: () -> Unit,
    onApplyCrop: (Bitmap) -> Unit,
    onDeletePage: () -> Unit,
    onSelectPage: (Int) -> Unit,
    pages: List<DocumentScanPage>,
    onAddPage: () -> Unit,
    onDiscardScan: () -> Unit,
    onCreate: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var showFilterMenu by remember { mutableStateOf(false) }
    var showCropDialog by remember { mutableStateOf(false) }

    val processedBitmap = remember(
        page.id,
        page.originalBitmap,
        page.rotationDegrees,
        page.filterMode,
        page.isEnhanced,
        page.isCleaned
    ) {
        page.getProcessedBitmap()
    }

    // Full-Screen Interactive Touch Crop Studio View
    if (showCropDialog) {
        CropDocumentStudioView(
            page = page,
            onConfirmCrop = { cropped ->
                showCropDialog = false
                onApplyCrop(cropped)
            },
            onDismiss = { showCropDialog = false }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF141414))
        ) {
        // TOP APP BAR
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Page ${pageIndex + 1} of $totalPages",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = Color.White
                )
            }

            // Delete current page
            IconButton(onClick = onDeletePage) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Page",
                    tint = Color(0xFFEF5350)
                )
            }
        }

        // CENTER IMAGE CANVAS
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = processedBitmap.asImageBitmap(),
                        contentDescription = "Scanned Document Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // TOOL BUTTONS ROW (Enhance, Filters, Crop, Rotate, Clean)
        // Styled with horizontal scroll so NO button is ever cut off!
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 1. ENHANCE BUTTON
            FilterChip(
                selected = page.isEnhanced,
                onClick = onToggleEnhance,
                label = { Text("Enhance", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    containerColor = Color(0xFF242424),
                    labelColor = Color.White
                ),
                shape = RoundedCornerShape(20.dp)
            )

            // 2. FILTERS BUTTON
            FilterChip(
                selected = page.filterMode != ScanFilterMode.ORIGINAL,
                onClick = { showFilterMenu = !showFilterMenu },
                label = { Text("Filters: ${page.filterMode.label}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FilterAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    containerColor = Color(0xFF242424),
                    labelColor = Color.White
                ),
                shape = RoundedCornerShape(20.dp)
            )

            // 3. CROP BUTTON
            FilterChip(
                selected = false,
                onClick = { showCropDialog = true },
                label = { Text("Crop", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Crop,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    containerColor = Color(0xFF242424),
                    labelColor = Color.White
                ),
                shape = RoundedCornerShape(20.dp)
            )

            // 4. ROTATE BUTTON
            FilterChip(
                selected = page.rotationDegrees != 0,
                onClick = onRotate,
                label = {
                    Text(
                        if (page.rotationDegrees == 0) "Rotate" else "Rotate (${page.rotationDegrees}°)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.RotateRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    containerColor = Color(0xFF242424),
                    labelColor = Color.White
                ),
                shape = RoundedCornerShape(20.dp)
            )

            // 5. CLEAN BUTTON
            FilterChip(
                selected = page.isCleaned,
                onClick = onToggleClean,
                label = { Text("Clean", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    containerColor = Color(0xFF242424),
                    labelColor = Color.White
                ),
                shape = RoundedCornerShape(20.dp)
            )
        }

        // POPUP FILTER SELECTION ROW (When Filters chip is tapped)
        AnimatedVisibility(visible = showFilterMenu) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                ScanFilterMode.values().forEach { mode ->
                    val isSelected = page.filterMode == mode
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF2A2A2A),
                        modifier = Modifier.clickable {
                            onFilterSelected(mode)
                            showFilterMenu = false
                        }
                    ) {
                        Text(
                            text = mode.label,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // PAGE THUMBNAILS & LABELED ADD BUTTON
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Page Thumbnails
            pages.forEachIndexed { index, scanPage ->
                val isSelected = index == pageIndex
                Card(
                    shape = RoundedCornerShape(8.dp),
                    border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF202020)),
                    modifier = Modifier
                        .size(46.dp, 60.dp)
                        .clickable { onSelectPage(index) }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            bitmap = scanPage.originalBitmap.asImageBitmap(),
                            contentDescription = "Page ${index + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Page Number Badge
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 2.dp)
                        ) {
                            Text(
                                text = "${index + 1}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // LABELED ADD BUTTON
            Button(
                onClick = onAddPage,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Add Page",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // BOTTOM ACTION BAR ("Discard scan" and "Create" - renamed from Next!)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Discard Scan button
            OutlinedButton(
                onClick = onDiscardScan,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFF666666)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text(
                    text = "Discard scan",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }

            // CREATE BUTTON (Renamed from Next!)
            Button(
                onClick = onCreate,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFA6C8FF),
                    contentColor = Color(0xFF002F6C)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text(
                    text = "Create",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
    }
}

private enum class CropDragHandle {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_RIGHT,
    BOTTOM_LEFT,
    EDGE_TOP,
    EDGE_RIGHT,
    EDGE_BOTTOM,
    EDGE_LEFT,
    BODY
}

/**
 * Full-screen interactive touch crop studio with 4 independent draggable corners.
 * Allows moving one corner freely without affecting the other corners.
 * On applying crop, performs full perspective warp to stretch/deskew skewed angles into true 90-degree corners.
 */
@Composable
private fun CropDocumentStudioView(
    page: DocumentScanPage,
    onConfirmCrop: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    var useUncroppedSource by remember { mutableStateOf(false) }

    val activeSource = if (useUncroppedSource && page.uncroppedBitmap != null) {
        page.uncroppedBitmap
    } else {
        page.originalBitmap
    }

    val baseBitmap = remember(activeSource, page.rotationDegrees) {
        DocumentImageProcessor.rotateBitmap(activeSource, page.rotationDegrees.toFloat())
    }

    // 4 Independent Quad Corners [0.0..1.0] relative to baseBitmap dimensions
    var pTL by remember(baseBitmap) { mutableStateOf(Offset(0.02f, 0.02f)) }
    var pTR by remember(baseBitmap) { mutableStateOf(Offset(0.98f, 0.02f)) }
    var pBR by remember(baseBitmap) { mutableStateOf(Offset(0.98f, 0.98f)) }
    var pBL by remember(baseBitmap) { mutableStateOf(Offset(0.02f, 0.98f)) }

    var activeHandle by remember { mutableStateOf(CropDragHandle.NONE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. TOP APP BAR
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel",
                    tint = Color.White
                )
            }

            Text(
                text = "Drag Corners to Crop",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = Color.White
            )

            TextButton(
                onClick = {
                    pTL = Offset(0f, 0f)
                    pTR = Offset(1f, 0f)
                    pBR = Offset(1f, 1f)
                    pBL = Offset(0f, 1f)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color(0xFFA6C8FF)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Reset",
                    color = Color(0xFFA6C8FF),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 2. CENTER INTERACTIVE CANVAS (4 Independent Draggable Corners & Edges)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(baseBitmap) {
                        detectDragGestures(
                            onDragStart = { touchOffset ->
                                val canvasW = size.width.toFloat()
                                val canvasH = size.height.toFloat()
                                val bmpW = baseBitmap.width.toFloat()
                                val bmpH = baseBitmap.height.toFloat()

                                val scale = minOf(canvasW / bmpW, canvasH / bmpH) * 0.94f
                                val imgW = bmpW * scale
                                val imgH = bmpH * scale
                                val imgLeft = (canvasW - imgW) / 2f
                                val imgTop = (canvasH - imgH) / 2f

                                fun toScreen(norm: Offset) = Offset(imgLeft + norm.x * imgW, imgTop + norm.y * imgH)

                                val sTL = toScreen(pTL)
                                val sTR = toScreen(pTR)
                                val sBR = toScreen(pBR)
                                val sBL = toScreen(pBL)

                                val touchRadius = 44f * density

                                fun dist(p: Offset): Float {
                                    val dx = touchOffset.x - p.x
                                    val dy = touchOffset.y - p.y
                                    return sqrt(dx * dx + dy * dy)
                                }

                                fun distToSegment(p: Offset, a: Offset, b: Offset): Float {
                                    val l2 = (b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y)
                                    if (l2 == 0f) return dist(a)
                                    val t = maxOf(0f, minOf(1f, ((p.x - a.x) * (b.x - a.x) + (p.y - a.y) * (b.y - a.y)) / l2))
                                    val proj = Offset(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y))
                                    return sqrt((p.x - proj.x) * (p.x - proj.x) + (p.y - proj.y) * (p.y - proj.y))
                                }

                                fun isInsideQuad(p: Offset): Boolean {
                                    fun cross(a: Offset, b: Offset, c: Offset): Float =
                                        (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
                                    val c1 = cross(sTL, sTR, p)
                                    val c2 = cross(sTR, sBR, p)
                                    val c3 = cross(sBR, sBL, p)
                                    val c4 = cross(sBL, sTL, p)
                                    return (c1 >= 0 && c2 >= 0 && c3 >= 0 && c4 >= 0) || (c1 <= 0 && c2 <= 0 && c3 <= 0 && c4 <= 0)
                                }

                                val edgeRadius = 26f * density

                                activeHandle = when {
                                    dist(sTL) < touchRadius -> CropDragHandle.TOP_LEFT
                                    dist(sTR) < touchRadius -> CropDragHandle.TOP_RIGHT
                                    dist(sBR) < touchRadius -> CropDragHandle.BOTTOM_RIGHT
                                    dist(sBL) < touchRadius -> CropDragHandle.BOTTOM_LEFT
                                    distToSegment(touchOffset, sTL, sTR) < edgeRadius -> CropDragHandle.EDGE_TOP
                                    distToSegment(touchOffset, sTR, sBR) < edgeRadius -> CropDragHandle.EDGE_RIGHT
                                    distToSegment(touchOffset, sBL, sBR) < edgeRadius -> CropDragHandle.EDGE_BOTTOM
                                    distToSegment(touchOffset, sTL, sBL) < edgeRadius -> CropDragHandle.EDGE_LEFT
                                    isInsideQuad(touchOffset) -> CropDragHandle.BODY
                                    else -> CropDragHandle.NONE
                                }
                            },
                            onDrag = { change, dragAmount ->
                                if (activeHandle != CropDragHandle.NONE) {
                                    change.consume()
                                    val canvasW = size.width.toFloat()
                                    val canvasH = size.height.toFloat()
                                    val bmpW = baseBitmap.width.toFloat()
                                    val bmpH = baseBitmap.height.toFloat()

                                    val scale = minOf(canvasW / bmpW, canvasH / bmpH) * 0.94f
                                    val imgW = bmpW * scale
                                    val imgH = bmpH * scale

                                    val dx = dragAmount.x / imgW
                                    val dy = dragAmount.y / imgH

                                    when (activeHandle) {
                                        CropDragHandle.TOP_LEFT -> {
                                            pTL = Offset((pTL.x + dx).coerceIn(0f, 1f), (pTL.y + dy).coerceIn(0f, 1f))
                                        }
                                        CropDragHandle.TOP_RIGHT -> {
                                            pTR = Offset((pTR.x + dx).coerceIn(0f, 1f), (pTR.y + dy).coerceIn(0f, 1f))
                                        }
                                        CropDragHandle.BOTTOM_RIGHT -> {
                                            pBR = Offset((pBR.x + dx).coerceIn(0f, 1f), (pBR.y + dy).coerceIn(0f, 1f))
                                        }
                                        CropDragHandle.BOTTOM_LEFT -> {
                                            pBL = Offset((pBL.x + dx).coerceIn(0f, 1f), (pBL.y + dy).coerceIn(0f, 1f))
                                        }
                                        CropDragHandle.EDGE_TOP -> {
                                            pTL = Offset((pTL.x + dx).coerceIn(0f, 1f), (pTL.y + dy).coerceIn(0f, 1f))
                                            pTR = Offset((pTR.x + dx).coerceIn(0f, 1f), (pTR.y + dy).coerceIn(0f, 1f))
                                        }
                                        CropDragHandle.EDGE_RIGHT -> {
                                            pTR = Offset((pTR.x + dx).coerceIn(0f, 1f), (pTR.y + dy).coerceIn(0f, 1f))
                                            pBR = Offset((pBR.x + dx).coerceIn(0f, 1f), (pBR.y + dy).coerceIn(0f, 1f))
                                        }
                                        CropDragHandle.EDGE_BOTTOM -> {
                                            pBL = Offset((pBL.x + dx).coerceIn(0f, 1f), (pBL.y + dy).coerceIn(0f, 1f))
                                            pBR = Offset((pBR.x + dx).coerceIn(0f, 1f), (pBR.y + dy).coerceIn(0f, 1f))
                                        }
                                        CropDragHandle.EDGE_LEFT -> {
                                            pTL = Offset((pTL.x + dx).coerceIn(0f, 1f), (pTL.y + dy).coerceIn(0f, 1f))
                                            pBL = Offset((pBL.x + dx).coerceIn(0f, 1f), (pBL.y + dy).coerceIn(0f, 1f))
                                        }
                                        CropDragHandle.BODY -> {
                                            val minX = minOf(pTL.x, pTR.x, pBR.x, pBL.x)
                                            val maxX = maxOf(pTL.x, pTR.x, pBR.x, pBL.x)
                                            val minY = minOf(pTL.y, pTR.y, pBR.y, pBL.y)
                                            val maxY = maxOf(pTL.y, pTR.y, pBR.y, pBL.y)

                                            val clampedDx = dx.coerceIn(-minX, 1f - maxX)
                                            val clampedDy = dy.coerceIn(-minY, 1f - maxY)

                                            pTL = Offset(pTL.x + clampedDx, pTL.y + clampedDy)
                                            pTR = Offset(pTR.x + clampedDx, pTR.y + clampedDy)
                                            pBR = Offset(pBR.x + clampedDx, pBR.y + clampedDy)
                                            pBL = Offset(pBL.x + clampedDx, pBL.y + clampedDy)
                                        }
                                        CropDragHandle.NONE -> {}
                                    }
                                }
                            },
                            onDragEnd = { activeHandle = CropDragHandle.NONE },
                            onDragCancel = { activeHandle = CropDragHandle.NONE }
                        )
                    }
            ) {
                val canvasW = size.width
                val canvasH = size.height
                val bmpW = baseBitmap.width.toFloat()
                val bmpH = baseBitmap.height.toFloat()

                val scale = minOf(canvasW / bmpW, canvasH / bmpH) * 0.94f
                val imgW = bmpW * scale
                val imgH = bmpH * scale
                val imgLeft = (canvasW - imgW) / 2f
                val imgTop = (canvasH - imgH) / 2f

                fun toScreen(norm: Offset) = Offset(imgLeft + norm.x * imgW, imgTop + norm.y * imgH)

                val sTL = toScreen(pTL)
                val sTR = toScreen(pTR)
                val sBR = toScreen(pBR)
                val sBL = toScreen(pBL)

                // 1. Draw image centered
                drawImage(
                    image = baseBitmap.asImageBitmap(),
                    dstOffset = IntOffset(imgLeft.toInt(), imgTop.toInt()),
                    dstSize = IntSize(imgW.toInt(), imgH.toInt())
                )

                // 2. Draw dimming scrim outside quadrilateral boundary
                val fullPath = Path().apply {
                    addRect(Rect(0f, 0f, canvasW, canvasH))
                }
                val quadPath = Path().apply {
                    moveTo(sTL.x, sTL.y)
                    lineTo(sTR.x, sTR.y)
                    lineTo(sBR.x, sBR.y)
                    lineTo(sBL.x, sBL.y)
                    close()
                }
                val scrimPath = Path()
                scrimPath.op(fullPath, quadPath, PathOperation.Difference)
                drawPath(scrimPath, color = Color.Black.copy(alpha = 0.58f))

                // 3. Perspective Rule-of-Thirds Grid
                val gridColor = Color.White.copy(alpha = 0.35f)
                val gridStroke = 1f * density
                fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

                val topThird1 = lerp(sTL, sTR, 1f / 3f)
                val topThird2 = lerp(sTL, sTR, 2f / 3f)
                val bottomThird1 = lerp(sBL, sBR, 1f / 3f)
                val bottomThird2 = lerp(sBL, sBR, 2f / 3f)

                val leftThird1 = lerp(sTL, sBL, 1f / 3f)
                val leftThird2 = lerp(sTL, sBL, 2f / 3f)
                val rightThird1 = lerp(sTR, sBR, 1f / 3f)
                val rightThird2 = lerp(sTR, sBR, 2f / 3f)

                drawLine(gridColor, topThird1, bottomThird1, gridStroke)
                drawLine(gridColor, topThird2, bottomThird2, gridStroke)
                drawLine(gridColor, leftThird1, rightThird1, gridStroke)
                drawLine(gridColor, leftThird2, rightThird2, gridStroke)

                // 4. Draw quadrilateral bounding outline
                val activeColor = Color(0xFF00E676)
                drawPath(
                    path = quadPath,
                    color = activeColor,
                    style = Stroke(width = 2.5f * density)
                )

                // 5. Draw 4 circular corner touch anchors
                val cornerRadius = 10f * density
                val innerRadius = 5f * density
                val glowRadius = 22f * density
                val corners = listOf(
                    Pair(sTL, CropDragHandle.TOP_LEFT),
                    Pair(sTR, CropDragHandle.TOP_RIGHT),
                    Pair(sBR, CropDragHandle.BOTTOM_RIGHT),
                    Pair(sBL, CropDragHandle.BOTTOM_LEFT)
                )

                for ((pos, handleType) in corners) {
                    if (activeHandle == handleType) {
                        drawCircle(activeColor.copy(alpha = 0.40f), radius = glowRadius, center = pos)
                    }
                    drawCircle(Color.White, radius = cornerRadius, center = pos)
                    drawCircle(activeColor, radius = innerRadius, center = pos)
                }

                // 6. Draw midpoint edge grips
                val edgePillLen = 18f * density
                val edgePillThick = 4.5f * density
                fun drawEdgePill(p1: Offset, p2: Offset) {
                    val mid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
                    val angle = atan2(p2.y - p1.y, p2.x - p1.x)
                    val cosA = cos(angle)
                    val sinA = sin(angle)
                    val half = edgePillLen / 2f
                    val start = Offset(mid.x - half * cosA, mid.y - half * sinA)
                    val end = Offset(mid.x + half * cosA, mid.y + half * sinA)
                    drawLine(Color.White, start, end, edgePillThick, StrokeCap.Round)
                }

                drawEdgePill(sTL, sTR)
                drawEdgePill(sTR, sBR)
                drawEdgePill(sBR, sBL)
                drawEdgePill(sBL, sTL)
            }
        }

        // 3. BOTTOM PRESETS & ACTION BAR
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Source Toggle Chip & Preset Chips Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                if (page.uncroppedBitmap != null) {
                    FilterChip(
                        selected = useUncroppedSource,
                        onClick = {
                            useUncroppedSource = !useUncroppedSource
                            pTL = Offset(0.02f, 0.02f)
                            pTR = Offset(0.98f, 0.02f)
                            pBR = Offset(0.98f, 0.98f)
                            pBL = Offset(0.02f, 0.98f)
                        },
                        label = {
                            Text(
                                if (useUncroppedSource) "Source: Full Camera Photo" else "Source: Auto-Cropped",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                FilterChip(
                    selected = pTL == Offset(0f, 0f) && pTR == Offset(1f, 0f) && pBR == Offset(1f, 1f) && pBL == Offset(0f, 1f),
                    onClick = {
                        pTL = Offset(0f, 0f)
                        pTR = Offset(1f, 0f)
                        pBR = Offset(1f, 1f)
                        pBL = Offset(0f, 1f)
                    },
                    label = { Text("Fit All", fontSize = 11.sp) },
                    shape = RoundedCornerShape(16.dp)
                )

                FilterChip(
                    selected = pTL == Offset(0.05f, 0.05f) && pTR == Offset(0.95f, 0.05f) && pBR == Offset(0.95f, 0.95f) && pBL == Offset(0.05f, 0.95f),
                    onClick = {
                        pTL = Offset(0.05f, 0.05f)
                        pTR = Offset(0.95f, 0.05f)
                        pBR = Offset(0.95f, 0.95f)
                        pBL = Offset(0.05f, 0.95f)
                    },
                    label = { Text("Trim 5%", fontSize = 11.sp) },
                    shape = RoundedCornerShape(16.dp)
                )

                FilterChip(
                    selected = pTL == Offset(0.10f, 0.10f) && pTR == Offset(0.90f, 0.10f) && pBR == Offset(0.90f, 0.90f) && pBL == Offset(0.10f, 0.90f),
                    onClick = {
                        pTL = Offset(0.10f, 0.10f)
                        pTR = Offset(0.90f, 0.10f)
                        pBR = Offset(0.90f, 0.90f)
                        pBL = Offset(0.10f, 0.90f)
                    },
                    label = { Text("Trim 10%", fontSize = 11.sp) },
                    shape = RoundedCornerShape(16.dp)
                )
            }

            // Action Buttons (Cancel and Apply Crop)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 14.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color(0xFF666666)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }

                Button(
                    onClick = {
                        val cropped = DocumentImageProcessor.cropPerspectiveWarp(
                            source = baseBitmap,
                            tlX = pTL.x, tlY = pTL.y,
                            trX = pTR.x, trY = pTR.y,
                            brX = pBR.x, brY = pBR.y,
                            blX = pBL.x, blY = pBL.y
                        )
                        onConfirmCrop(cropped)
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFA6C8FF),
                        contentColor = Color(0xFF002F6C)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text("Apply Crop", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}
