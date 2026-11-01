package com.xpdfviewer.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.xpdfviewer.data.OutlineNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts native hierarchical document outlines (Table of Contents / bookmarks)
 * from PDF documents using PdfBox-Android.
 */
object PdfOutlineExtractor {

    /**
     * Extracts outline nodes from the given PDF content URI.
     *
     * @param context Android context for resolving content URI streams.
     * @param uri SAF document URI.
     * @return List of top-level [OutlineNode] instances, each containing its recursive children.
     */
    suspend fun extractOutline(context: Context, uri: Uri): List<OutlineNode> = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val result = mutableListOf<OutlineNode>()

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                PDDocument.load(inputStream)?.use { document ->
                    val outline: PDDocumentOutline? = document.documentCatalog.documentOutline
                    if (outline != null) {
                        var currentItem: PDOutlineItem? = outline.firstChild
                        while (currentItem != null) {
                            val node = parseOutlineItem(currentItem, document, level = 0)
                            result.add(node)
                            currentItem = currentItem.nextSibling
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        result
    }

    private fun parseOutlineItem(
        item: PDOutlineItem,
        document: PDDocument,
        level: Int
    ): OutlineNode {
        val rawTitle = item.title?.trim()
        val title = if (!rawTitle.isNullOrEmpty()) rawTitle else "Untitled Section"
        val pageIndex = resolvePageIndex(item, document)

        val children = mutableListOf<OutlineNode>()
        var childItem: PDOutlineItem? = item.firstChild
        while (childItem != null) {
            children.add(parseOutlineItem(childItem, document, level + 1))
            childItem = childItem.nextSibling
        }

        return OutlineNode(
            title = title,
            pageIndex = pageIndex,
            level = level,
            children = children
        )
    }

    private fun resolvePageIndex(item: PDOutlineItem, document: PDDocument): Int {
        try {
            // 1. Try built-in destination page resolution
            val destinationPage: PDPage? = item.findDestinationPage(document)
            if (destinationPage != null) {
                val index = document.pages.indexOf(destinationPage)
                if (index >= 0) return index
            }

            // 2. Direct destination lookup
            val destination = item.destination
            if (destination is PDPageDestination) {
                val resolvedIndex = destination.retrievePageNumber()
                if (resolvedIndex >= 0 && resolvedIndex < document.numberOfPages) {
                    return resolvedIndex
                }
                destination.page?.let { page ->
                    val index = document.pages.indexOf(page)
                    if (index >= 0) return index
                }
            } else if (destination is PDNamedDestination) {
                document.documentCatalog.findNamedDestinationPage(destination)?.let { namedDest ->
                    val resolvedIndex = namedDest.retrievePageNumber()
                    if (resolvedIndex >= 0 && resolvedIndex < document.numberOfPages) {
                        return resolvedIndex
                    }
                    namedDest.page?.let { page ->
                        val index = document.pages.indexOf(page)
                        if (index >= 0) return index
                    }
                }
            }

            // 3. Fallback to action if destination is enclosed within a GoTo action
            val action = item.action
            if (action is PDActionGoTo) {
                val actionDest = action.destination
                if (actionDest is PDPageDestination) {
                    val resolvedIndex = actionDest.retrievePageNumber()
                    if (resolvedIndex >= 0 && resolvedIndex < document.numberOfPages) {
                        return resolvedIndex
                    }
                    actionDest.page?.let { page ->
                        val index = document.pages.indexOf(page)
                        if (index >= 0) return index
                    }
                } else if (actionDest is PDNamedDestination) {
                    document.documentCatalog.findNamedDestinationPage(actionDest)?.let { namedDest ->
                        val resolvedIndex = namedDest.retrievePageNumber()
                        if (resolvedIndex >= 0 && resolvedIndex < document.numberOfPages) {
                            return resolvedIndex
                        }
                        namedDest.page?.let { page ->
                            val index = document.pages.indexOf(page)
                            if (index >= 0) return index
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return -1
    }
}
