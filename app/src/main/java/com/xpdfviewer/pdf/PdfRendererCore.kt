package com.xpdfviewer.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.xpdfviewer.data.PageDimensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Thread-safe wrapper around native android.graphics.pdf.PdfRenderer.
 * Ensures strict sequential access to openPage/closePage calls via Mutex.
 */
class PdfRendererCore(
    private val context: Context,
    val uri: Uri
) {
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val mutex = Mutex()
    private val cache = BitmapLruCache()

    var pageCount: Int = 0
        private set

    val fileName: String = resolveFileName(context, uri)

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                pfd = context.contentResolver.openFileDescriptor(uri, "r")
                val descriptor = pfd ?: return@withLock false
                renderer = PdfRenderer(descriptor)
                pageCount = renderer?.pageCount ?: 0
                true
            } catch (e: Exception) {
                e.printStackTrace()
                closeInternal()
                false
            }
        }
    }

    suspend fun getPageDimensions(pageIndex: Int): PageDimensions? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val r = renderer ?: return@withLock null
            if (pageIndex !in 0 until pageCount) return@withLock null
            var page: PdfRenderer.Page? = null
            try {
                page = r.openPage(pageIndex)
                val width = page.width
                val height = page.height
                val aspect = if (height > 0) width.toFloat() / height.toFloat() else 1.0f
                PageDimensions(pageIndex, width, height, aspect)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                try {
                    page?.close()
                } catch (ignored: Exception) {}
            }
        }
    }

    suspend fun renderPage(
        pageIndex: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (pageIndex !in 0 until pageCount) return@withContext null

        // 1. Check in-memory cache first
        cache.get(pageIndex)?.let { return@withContext it }

        // 2. Render from native engine under lock
        mutex.withLock {
            // Double check cache after acquiring lock
            cache.get(pageIndex)?.let { return@withLock it }

            val r = renderer ?: return@withLock null
            var page: PdfRenderer.Page? = null
            try {
                page = r.openPage(pageIndex)

                // Strictly preserve page aspect ratio to prevent stretching across portrait and landscape pages
                val pageAspect = if (page.height > 0) page.width.toFloat() / page.height.toFloat() else 1.0f
                val renderWidth: Int
                val renderHeight: Int
                if (targetWidth > 0 && targetHeight > 0) {
                    val targetAspect = targetWidth.toFloat() / targetHeight.toFloat()
                    if (targetAspect > pageAspect) {
                        renderHeight = targetHeight
                        renderWidth = (targetHeight * pageAspect).toInt().coerceAtLeast(1)
                    } else {
                        renderWidth = targetWidth
                        renderHeight = (targetWidth / pageAspect).toInt().coerceAtLeast(1)
                    }
                } else if (targetWidth > 0) {
                    renderWidth = targetWidth
                    renderHeight = (targetWidth / pageAspect).toInt().coerceAtLeast(1)
                } else if (targetHeight > 0) {
                    renderHeight = targetHeight
                    renderWidth = (targetHeight * pageAspect).toInt().coerceAtLeast(1)
                } else {
                    renderWidth = page.width * 2
                    renderHeight = page.height * 2
                }

                val bitmap = Bitmap.createBitmap(
                    renderWidth,
                    renderHeight,
                    Bitmap.Config.ARGB_8888
                )
                // Fill with white background as PDF pages can have transparent backgrounds
                bitmap.eraseColor(Color.WHITE)

                page.render(
                    bitmap,
                    null,
                    null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )

                cache.put(pageIndex, bitmap)
                bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                try {
                    page?.close()
                } catch (ignored: Exception) {}
            }
        }
    }

    fun close() {
        closeInternal()
    }

    private fun closeInternal() {
        cache.clear()
        try {
            renderer?.close()
        } catch (ignored: Exception) {}
        renderer = null

        try {
            pfd?.close()
        } catch (ignored: Exception) {}
        pfd = null
    }

    companion object {
        fun resolveFileName(context: Context, uri: Uri): String {
            var result = "Document.pdf"
            if (uri.scheme == "content") {
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                val name = cursor.getString(nameIndex)
                                if (!name.isNullOrBlank()) {
                                    result = name
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if (uri.scheme == "file") {
                uri.lastPathSegment?.let { result = it }
            }
            return result
        }
    }
}
