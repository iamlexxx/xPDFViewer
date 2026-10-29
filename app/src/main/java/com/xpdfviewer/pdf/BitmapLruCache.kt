package com.xpdfviewer.pdf

import android.graphics.Bitmap
import android.util.LruCache

/**
 * High-performance LRU memory cache for rendered PDF page bitmaps.
 * Prevents OutOfMemoryErrors by sizing cache dynamically to 20% of available heap.
 */
class BitmapLruCache(
    maxSizeKb: Int = (Runtime.getRuntime().maxMemory() / 1024 / 5).toInt()
) {
    private val cache: LruCache<Int, Bitmap> = object : LruCache<Int, Bitmap>(maxSizeKb) {
        override fun sizeOf(key: Int, value: Bitmap): Int {
            return value.byteCount / 1024
        }

        override fun entryRemoved(
            evicted: Boolean,
            key: Int,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            // Avoid calling oldValue.recycle() immediately if still drawn on screen,
            // let GC reclaim when unreferenced.
        }
    }

    fun get(pageIndex: Int): Bitmap? {
        return synchronized(cache) {
            cache.get(pageIndex)
        }
    }

    fun put(pageIndex: Int, bitmap: Bitmap) {
        synchronized(cache) {
            cache.put(pageIndex, bitmap)
        }
    }

    fun clear() {
        synchronized(cache) {
            cache.evictAll()
        }
    }
}
