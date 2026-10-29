package com.xpdfviewer.ui.dialogs

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xpdfviewer.signature.SignatureBitmapHelper
import java.io.File

private enum class SignatureTab { DRAW, TYPE, IMAGE, SAVED }

@Composable
fun SignaturePadDialog(
    savedSignatures: List<Pair<File, Bitmap>>,
    onDismiss: () -> Unit,
    onSignatureCreated: (bitmap: Bitmap, shouldSave: Boolean) -> Unit,
    onDeleteSavedSignature: (File) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember {
        mutableStateOf(SignatureTab.DRAW)
    }

    // Drawing state
    val strokes = remember { mutableStateListOf<SignatureBitmapHelper.Stroke>() }
    var currentPoints = remember { mutableStateListOf<SignatureBitmapHelper.StrokePoint>() }

    val colors = listOf(
        Pair("Black", Color(0xFF0F172A)),
        Pair("Navy", Color(0xFF1E3A8A)),
        Pair("Blue", Color(0xFF2563EB))
    )
    var selectedColor by remember { mutableStateOf(colors[0].second) }
    var strokeWidth by remember { mutableFloatStateOf(6f) }

    // Type signature state
    var typedName by remember { mutableStateOf("") }

    // Image signature state
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var rawImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var removeWhiteBackground by remember { mutableStateOf(true) }
    var processedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            val loaded = SignatureBitmapHelper.loadBitmapFromUri(context, uri)
            rawImageBitmap = loaded
        }
    }

    LaunchedEffect(rawImageBitmap, removeWhiteBackground) {
        val raw = rawImageBitmap
        processedImageBitmap = if (raw != null) {
            SignatureBitmapHelper.processImageSignature(raw, removeWhiteBackground)
        } else {
            null
        }
    }

    // Save toggle
    var saveSignatureForFuture by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Create Signature",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Clear, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tabs: Draw, Type, Image, Saved
                TabRow(
                    selectedTabIndex = selectedTab.ordinal.coerceAtMost(if (savedSignatures.isNotEmpty()) 3 else 2),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == SignatureTab.DRAW,
                        onClick = { selectedTab = SignatureTab.DRAW },
                        text = { Text("Draw", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == SignatureTab.TYPE,
                        onClick = { selectedTab = SignatureTab.TYPE },
                        text = { Text("Type", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == SignatureTab.IMAGE,
                        onClick = { selectedTab = SignatureTab.IMAGE },
                        text = { Text("Image", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    if (savedSignatures.isNotEmpty()) {
                        Tab(
                            selected = selectedTab == SignatureTab.SAVED,
                            onClick = { selectedTab = SignatureTab.SAVED },
                            text = { Text("Saved", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                            icon = { Icon(Icons.Default.FolderSpecial, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedTab) {
                    SignatureTab.DRAW -> {
                        // Drawing Pad View
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                .pointerInput(selectedColor, strokeWidth) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            currentPoints.clear()
                                            currentPoints.add(SignatureBitmapHelper.StrokePoint(offset.x, offset.y))
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            currentPoints.add(SignatureBitmapHelper.StrokePoint(change.position.x, change.position.y))
                                        },
                                        onDragEnd = {
                                            if (currentPoints.isNotEmpty()) {
                                                strokes.add(
                                                    SignatureBitmapHelper.Stroke(
                                                        points = currentPoints.toList(),
                                                        colorHex = selectedColor.toArgb(),
                                                        strokeWidth = strokeWidth
                                                    )
                                                )
                                                currentPoints.clear()
                                            }
                                        }
                                    )
                                }
                        ) {
                            // Canvas to render strokes
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // Draw baseline guide
                                val guideY = size.height * 0.72f
                                drawLine(
                                    color = Color(0xFFE2E8F0),
                                    start = Offset(24f, guideY),
                                    end = Offset(size.width - 24f, guideY),
                                    strokeWidth = 2f
                                )

                                for (stroke in strokes) {
                                    if (stroke.points.size < 2) continue
                                    val path = Path()
                                    val first = stroke.points[0]
                                    path.moveTo(first.x, first.y)
                                    for (i in 1 until stroke.points.size) {
                                        val prev = stroke.points[i - 1]
                                        val curr = stroke.points[i]
                                        val midX = (prev.x + curr.x) / 2f
                                        val midY = (prev.y + curr.y) / 2f
                                        path.quadraticTo(prev.x, prev.y, midX, midY)
                                    }
                                    val last = stroke.points.last()
                                    path.lineTo(last.x, last.y)

                                    drawPath(
                                        path = path,
                                        color = Color(stroke.colorHex),
                                        style = Stroke(
                                            width = stroke.strokeWidth,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }

                                // Draw in-progress stroke
                                if (currentPoints.size >= 2) {
                                    val inProgressPath = Path()
                                    inProgressPath.moveTo(currentPoints[0].x, currentPoints[0].y)
                                    for (i in 1 until currentPoints.size) {
                                        val prev = currentPoints[i - 1]
                                        val curr = currentPoints[i]
                                        inProgressPath.quadraticTo(
                                            prev.x,
                                            prev.y,
                                            (prev.x + curr.x) / 2f,
                                            (prev.y + curr.y) / 2f
                                        )
                                    }
                                    drawPath(
                                        path = inProgressPath,
                                        color = selectedColor,
                                        style = Stroke(
                                            width = strokeWidth,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                            }

                            if (strokes.isEmpty() && currentPoints.isEmpty()) {
                                Text(
                                    text = "Sign here with your finger",
                                    fontSize = 13.sp,
                                    color = Color(0xFF94A3B8),
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Controls: Ink Color, Stroke Width, Undo, Clear
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Colors
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                colors.forEach { (_, c) ->
                                    val isSelected = selectedColor == c
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(c)
                                            .clickable { selectedColor = c }
                                            .border(
                                                width = if (isSelected) 2.5.dp else 0.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                shape = CircleShape
                                            )
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Stroke Width and Actions
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
                                    },
                                    enabled = strokes.isNotEmpty()
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                                }

                                IconButton(
                                    onClick = { strokes.clear() },
                                    enabled = strokes.isNotEmpty()
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Clear")
                                }
                            }
                        }
                    }

                    SignatureTab.TYPE -> {
                        // Type signature
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = typedName,
                                onValueChange = { typedName = it },
                                label = { Text("Enter your full name") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Preview Box
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White)
                                    .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                    .padding(16.dp)
                            ) {
                                if (typedName.isNotBlank()) {
                                    val bmp = remember(typedName, selectedColor) {
                                        SignatureBitmapHelper.createTypedSignatureBitmap(
                                            name = typedName,
                                            colorHex = selectedColor.toArgb()
                                        )
                                    }
                                    androidx.compose.foundation.Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Typed signature preview",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = "Your signature preview will appear here",
                                        fontSize = 13.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Color picker for typed signature
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Ink Color: ", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.width(8.dp))
                                colors.forEach { (_, c) ->
                                    val isSelected = selectedColor == c
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(c)
                                            .clickable { selectedColor = c }
                                            .border(
                                                width = if (isSelected) 2.5.dp else 0.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                shape = CircleShape
                                            )
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SignatureTab.IMAGE -> {
                        // Image / Open File Signature
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (processedImageBitmap == null) {
                                // Upload / Open File Dropzone Card
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    ),
                                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clickable {
                                            pickImageLauncher.launch(
                                                arrayOf("image/png", "image/jpeg", "image/jpg", "image/webp")
                                            )
                                        }
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.UploadFile,
                                                contentDescription = "Open File",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Text(
                                            text = "Open Signature File",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = "Supports PNG, JPG, or WEBP image format",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                // Preview Box
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White)
                                        .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                        .padding(16.dp)
                                ) {
                                    androidx.compose.foundation.Image(
                                        bitmap = processedImageBitmap!!.asImageBitmap(),
                                        contentDescription = "Image signature preview",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Background removal switch
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 8.dp)
                                        ) {
                                            Text(
                                                text = "Transparent Background",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Filter out white paper background",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Switch(
                                            checked = removeWhiteBackground,
                                            onCheckedChange = { removeWhiteBackground = it }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            pickImageLauncher.launch(
                                                arrayOf("image/png", "image/jpeg", "image/jpg", "image/webp")
                                            )
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.UploadFile,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Change Image", fontSize = 12.sp)
                                    }

                                    TextButton(
                                        onClick = {
                                            rawImageBitmap = null
                                            selectedImageUri = null
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Remove", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                    SignatureTab.SAVED -> {
                        // Saved Signatures List
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Select a saved signature to stamp directly:",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(savedSignatures) { (file, bmp) ->
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFCBD5E1)),
                                        modifier = Modifier
                                            .size(width = 160.dp, height = 110.dp)
                                            .clickable {
                                                onSignatureCreated(bmp, false)
                                                onDismiss()
                                            }
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            androidx.compose.foundation.Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = "Saved signature",
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(12.dp)
                                            )
                                            IconButton(
                                                onClick = { onDeleteSavedSignature(file) },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Clear,
                                                    contentDescription = "Delete saved signature",
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedTab != SignatureTab.SAVED) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = saveSignatureForFuture,
                            onCheckedChange = { saveSignatureForFuture = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Save signature for future contracts",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Button
                    Button(
                        onClick = {
                            val bitmap = when (selectedTab) {
                                SignatureTab.DRAW -> SignatureBitmapHelper.createBitmapFromStrokes(strokes)
                                SignatureTab.TYPE -> {
                                    if (typedName.isNotBlank()) {
                                        SignatureBitmapHelper.createTypedSignatureBitmap(
                                            name = typedName,
                                            colorHex = selectedColor.toArgb()
                                        )
                                    } else null
                                }
                                SignatureTab.IMAGE -> processedImageBitmap
                                else -> null
                            }

                            if (bitmap != null) {
                                onSignatureCreated(bitmap, saveSignatureForFuture)
                                onDismiss()
                            }
                        },
                        enabled = when (selectedTab) {
                            SignatureTab.DRAW -> strokes.isNotEmpty()
                            SignatureTab.TYPE -> typedName.isNotBlank()
                            SignatureTab.IMAGE -> processedImageBitmap != null
                            else -> false
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text("Apply Signature", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
