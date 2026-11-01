package com.xpdfviewer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xpdfviewer.data.PdfSearchState
import com.xpdfviewer.data.ReadingMode

@Composable
fun PdfTopBar(
    fileName: String,
    currentPage: Int,
    pageCount: Int,
    isVisible: Boolean,
    onOpenJumpDialog: () -> Unit,
    onCloseDocument: () -> Unit = {},
    onPrintDocument: () -> Unit = {},
    onOpenPageOrganizer: () -> Unit = {},
    isCurrentPageBookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {},
    onOpenOutline: () -> Unit = {},
    searchState: PdfSearchState = PdfSearchState(),
    onOpenSearch: () -> Unit = {},
    onCloseSearch: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onPreviousSearchMatch: () -> Unit = {},
    onNextSearchMatch: () -> Unit = {},
    readingMode: ReadingMode = ReadingMode.DEFAULT,
    onOpenReadingMode: () -> Unit = {},
    hasAcroForm: Boolean = false,
    onOpenFormSheet: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier
    ) {
        if (searchState.isSearchActive) {
            PdfSearchBar(
                searchState = searchState,
                onQueryChange = onSearchQueryChange,
                onPreviousMatch = onPreviousSearchMatch,
                onNextMatch = onNextSearchMatch,
                onCloseSearch = onCloseSearch
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Back button to exit reader
                    IconButton(onClick = onCloseDocument) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Return to home",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Document Name
                    Text(
                        text = fileName.ifEmpty { "xPDFViewer" },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Page indicator badge (clickable to jump)
                    if (pageCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenJumpDialog() }
                        ) {
                            Text(
                                text = "$currentPage / $pageCount",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    // Search in document action button
                    IconButton(onClick = onOpenSearch) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search in document",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Table of contents / Outline navigation button
                    IconButton(onClick = onOpenOutline) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = "Document outline",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Bookmark toggle action button
                    IconButton(onClick = onToggleBookmark) {
                        Icon(
                            imageVector = if (isCurrentPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isCurrentPageBookmarked) "Page bookmarked" else "Bookmark page",
                            tint = if (isCurrentPageBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Form filling assistant action button (visible if AcroForm detected)
                    if (hasAcroForm) {
                        IconButton(onClick = onOpenFormSheet) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = "Fill form fields",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Reading mode / Eye comfort action button
                    IconButton(onClick = onOpenReadingMode) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Reading mode",
                            tint = if (readingMode != ReadingMode.DEFAULT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Page Organizer grid action button
                    IconButton(onClick = onOpenPageOrganizer) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "Organize pages",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Print document action button
                    IconButton(onClick = onPrintDocument) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Print document",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
