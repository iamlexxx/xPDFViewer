package com.xpdfviewer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xpdfviewer.data.OutlineNode
import com.xpdfviewer.data.OutlineTab
import com.xpdfviewer.data.UserBookmark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentOutlineSheet(
    showSheet: Boolean,
    outlineNodes: List<OutlineNode>,
    userBookmarks: List<UserBookmark>,
    selectedTab: OutlineTab,
    currentPage: Int,
    fileName: String,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelectTab: (OutlineTab) -> Unit,
    onJumpToPage: (pageIndex: Int) -> Unit,
    onAddBookmarkForCurrentPage: () -> Unit,
    onEditBookmark: (UserBookmark) -> Unit,
    onDeleteBookmark: (bookmarkId: String) -> Unit
) {
    if (!showSheet) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                shape = CircleShape
            ) {
                Box(modifier = Modifier.size(width = 36.dp, height = 4.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Document Navigation",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = fileName.ifEmpty { "PDF Document" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close sheet",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Tab Selector
            PrimaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlineTab.entries.forEach { tab ->
                    val badgeCount = when (tab) {
                        OutlineTab.CONTENTS -> outlineNodes.sumOf { it.totalCount }
                        OutlineTab.BOOKMARKS -> userBookmarks.size
                    }

                    Tab(
                        selected = selectedTab == tab,
                        onClick = { onSelectTab(tab) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = tab.title,
                                    fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium
                                )
                                if (badgeCount > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selectedTab == tab) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ) {
                                        Text(
                                            text = badgeCount.toString(),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = if (selectedTab == tab) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                OutlineTab.CONTENTS -> {
                    ContentsTabView(
                        outlineNodes = outlineNodes,
                        currentPageIndex = currentPage - 1,
                        isLoading = isLoading,
                        onJumpToPage = {
                            onJumpToPage(it)
                            onDismiss()
                        },
                        onSwitchToBookmarks = { onSelectTab(OutlineTab.BOOKMARKS) }
                    )
                }

                OutlineTab.BOOKMARKS -> {
                    BookmarksTabView(
                        userBookmarks = userBookmarks,
                        currentPage = currentPage,
                        onJumpToPage = {
                            onJumpToPage(it)
                            onDismiss()
                        },
                        onAddBookmarkForCurrentPage = onAddBookmarkForCurrentPage,
                        onEditBookmark = onEditBookmark,
                        onDeleteBookmark = onDeleteBookmark
                    )
                }
            }
        }
    }
}

@Composable
private fun ContentsTabView(
    outlineNodes: List<OutlineNode>,
    currentPageIndex: Int,
    isLoading: Boolean,
    onJumpToPage: (Int) -> Unit,
    onSwitchToBookmarks: () -> Unit
) {
    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Reading document outline...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        return
    }

    if (outlineNodes.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Table of Contents",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "This PDF does not contain an embedded outline hierarchy. You can pin key pages in the Bookmarks tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onSwitchToBookmarks,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Go to Bookmarks")
                }
            }
        }
        return
    }

    // Interactive expandable tree
    var expandedNodeIds by remember(outlineNodes) {
        // Initially expand level 0 nodes
        mutableStateOf(outlineNodes.filter { it.hasChildren }.map { it.id }.toSet())
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        outlineNodes.forEach { rootNode ->
            renderOutlineNode(
                node = rootNode,
                currentPageIndex = currentPageIndex,
                expandedNodeIds = expandedNodeIds,
                onToggleExpand = { id ->
                    expandedNodeIds = if (expandedNodeIds.contains(id)) {
                        expandedNodeIds - id
                    } else {
                        expandedNodeIds + id
                    }
                },
                onJumpToPage = onJumpToPage
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.renderOutlineNode(
    node: OutlineNode,
    currentPageIndex: Int,
    expandedNodeIds: Set<String>,
    onToggleExpand: (String) -> Unit,
    onJumpToPage: (Int) -> Unit
) {
    val isExpanded = expandedNodeIds.contains(node.id)
    val isCurrent = node.pageIndex == currentPageIndex

    item(key = node.id) {
        OutlineNodeRow(
            node = node,
            isExpanded = isExpanded,
            isCurrent = isCurrent,
            onToggleExpand = { onToggleExpand(node.id) },
            onClick = {
                if (node.pageIndex >= 0) {
                    onJumpToPage(node.pageIndex)
                } else if (node.hasChildren) {
                    onToggleExpand(node.id)
                }
            }
        )
    }

    if (isExpanded && node.hasChildren) {
        node.children.forEach { child ->
            renderOutlineNode(
                node = child,
                currentPageIndex = currentPageIndex,
                expandedNodeIds = expandedNodeIds,
                onToggleExpand = onToggleExpand,
                onJumpToPage = onJumpToPage
            )
        }
    }
}

@Composable
private fun OutlineNodeRow(
    node: OutlineNode,
    isExpanded: Boolean,
    isCurrent: Boolean,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit
) {
    val indent = (node.level * 18).dp
    val rowBackground = if (isCurrent) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        androidx.compose.ui.graphics.Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .clickable(onClick = onClick)
            .padding(start = 12.dp + indent, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Expand / Collapse Chevron
        if (node.hasChildren) {
            IconButton(
                onClick = onToggleExpand,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Default.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    },
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        } else {
            Spacer(modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(4.dp))
        }

        // Section Title
        Text(
            text = node.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isCurrent) FontWeight.Bold else if (node.level == 0) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = if (node.level == 0) 14.5.sp else 13.5.sp
            ),
            color = if (isCurrent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Page Jump Badge
        if (node.pageIndex >= 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Text(
                    text = "p. ${node.displayPage}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun BookmarksTabView(
    userBookmarks: List<UserBookmark>,
    currentPage: Int,
    onJumpToPage: (Int) -> Unit,
    onAddBookmarkForCurrentPage: () -> Unit,
    onEditBookmark: (UserBookmark) -> Unit,
    onDeleteBookmark: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Quick Action Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Current: Page $currentPage",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onAddBookmarkForCurrentPage,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Bookmark Page $currentPage",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        if (userBookmarks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Bookmarks Saved",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Pin pages with custom labels and personal notes to jump to them immediately.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            val dateFormatter = remember {
                SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(userBookmarks, key = { it.id }) { bookmark ->
                    BookmarkCard(
                        bookmark = bookmark,
                        formattedDate = dateFormatter.format(Date(bookmark.createdAt)),
                        onClick = { onJumpToPage(bookmark.pageIndex) },
                        onEdit = { onEditBookmark(bookmark) },
                        onDelete = { onDeleteBookmark(bookmark.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkCard(
    bookmark: UserBookmark,
    formattedDate: String,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Page Number Badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "p.",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = bookmark.displayPage.toString(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Note Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bookmark.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (bookmark.note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = bookmark.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Edit Action
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Edit bookmark",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Delete Action
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete bookmark",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

