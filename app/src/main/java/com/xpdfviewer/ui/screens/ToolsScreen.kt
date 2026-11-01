package com.xpdfviewer.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BrandingWatermark
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ToolsScreen(
    onOpenOcrScanner: () -> Unit = {},
    onOpenMergePdf: () -> Unit = {},
    onOpenCompressPdf: () -> Unit = {},
    onOpenSignPdf: () -> Unit = {},
    onOpenSecurity: () -> Unit = {},
    onOpenSplitPdf: () -> Unit = {},
    onOpenRedactPdf: () -> Unit = {},
    onOpenWatermarkPdf: () -> Unit = {},
    onOpenImageConverter: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header
        Text(
            text = "PDF Utilities",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tools 2-column Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                ToolGridCard(
                    title = "OCR Scanner",
                    description = "Extract text from scanned photo documents.",
                    icon = Icons.Default.DocumentScanner,
                    onClick = onOpenOcrScanner
                )
            }

            item {
                ToolGridCard(
                    title = "Merge PDFs",
                    description = "Combine multiple documents into one single file.",
                    icon = Icons.Default.Layers,
                    onClick = onOpenMergePdf
                )
            }

            item {
                ToolGridCard(
                    title = "Compress",
                    description = "Reduce PDF file size for easy email sharing.",
                    icon = Icons.Default.Compress,
                    onClick = onOpenCompressPdf
                )
            }

            item {
                ToolGridCard(
                    title = "E-Signature",
                    description = "Sign contracts and export encrypted files.",
                    icon = Icons.Default.Draw,
                    onClick = onOpenSignPdf
                )
            }

            item {
                ToolGridCard(
                    title = "Protect & Unlock",
                    description = "Add or remove password protection and security permissions.",
                    icon = Icons.Default.Lock,
                    onClick = onOpenSecurity
                )
            }

            item {
                ToolGridCard(
                    title = "Split PDF",
                    description = "Extract pages by custom range or split into multiple files.",
                    icon = Icons.AutoMirrored.Filled.CallSplit,
                    onClick = onOpenSplitPdf
                )
            }

            item {
                ToolGridCard(
                    title = "Redact PDF",
                    description = "Permanently sanitize sensitive text, images, and metadata.",
                    icon = Icons.Default.Security,
                    onClick = onOpenRedactPdf
                )
            }

            item {
                ToolGridCard(
                    title = "Watermark & Numbers",
                    description = "Stamp confidential watermarks and dynamic page numbering.",
                    icon = Icons.AutoMirrored.Filled.BrandingWatermark,
                    onClick = onOpenWatermarkPdf
                )
            }

            item {
                ToolGridCard(
                    title = "Image & PDF Studio",
                    description = "Convert photos to PDF or export PDF pages as images.",
                    icon = Icons.Default.AddPhotoAlternate,
                    onClick = onOpenImageConverter
                )
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun ToolGridCard(
    title: String,
    description: String,
    icon: ImageVector,
    badgeText: String? = null,
    badgeColor: Color = Color(0xFFFEF3C7),
    badgeTextColor: Color = Color(0xFF92400E),
    onClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy
        ),
        label = "ToolCardPressScale"
    )

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isPressed) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (onClick != null) 3.dp else 2.dp,
            pressedElevation = 6.dp
        ),
        onClick = onClick ?: {},
        enabled = onClick != null,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Mini tag in top-right
            if (badgeText != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = badgeTextColor
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Icon Box
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
