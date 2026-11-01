package com.xpdfviewer.data

import java.util.UUID

/**
 * Represents a single node in a hierarchical document outline (Table of Contents).
 *
 * @param id Unique identifier for UI keying.
 * @param title Title or chapter heading extracted from the PDF outline dictionary.
 * @param pageIndex 0-based index of the target page (-1 if unresolvable).
 * @param level Indentation depth (0 = root chapter, 1 = sub-section, etc.).
 * @param children Nested child outline nodes.
 */
data class OutlineNode(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val pageIndex: Int,
    val level: Int = 0,
    val children: List<OutlineNode> = emptyList()
) {
    val displayPage: Int
        get() = if (pageIndex >= 0) pageIndex + 1 else 0

    val hasChildren: Boolean
        get() = children.isNotEmpty()

    val totalCount: Int
        get() = 1 + children.sumOf { it.totalCount }
}

/**
 * Represents a custom user-defined bookmark pinned to a specific page of a PDF document.
 *
 * @param id Unique identifier.
 * @param uriString Document URI identifier to associate bookmark with specific file.
 * @param pageIndex 0-based page index pinned by the user.
 * @param title Custom label or chapter name for the bookmark.
 * @param note Optional user annotation or note attached to the pinned page.
 * @param createdAt Timestamp when bookmark was created.
 */
data class UserBookmark(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String,
    val pageIndex: Int,
    val title: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val displayPage: Int
        get() = pageIndex + 1
}

/**
 * Active tab within the Document Outline & Navigation sheet.
 */
enum class OutlineTab(val title: String) {
    CONTENTS("Contents"),
    BOOKMARKS("Bookmarks")
}

/**
 * State for managing the Add / Edit Bookmark dialog.
 */
data class AddBookmarkDialogState(
    val isOpen: Boolean = false,
    val targetPageIndex: Int = 0,
    val initialTitle: String = "",
    val initialNote: String = "",
    val existingBookmarkId: String? = null
)

