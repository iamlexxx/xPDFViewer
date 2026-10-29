package com.xpdfviewer.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF

enum class ScanFilterMode(val label: String) {
    ORIGINAL("Original"),
    ENHANCE("Enhance"),
    GRAYSCALE("Grayscale"),
    BLACK_AND_WHITE("B&W"),
    MAGIC_COLOR("Color")
}

object DocumentImageProcessor {

    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360 == 0f) return source
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun cropBitmap(
        source: Bitmap,
        leftPercent: Float,
        topPercent: Float,
        rightPercent: Float,
        bottomPercent: Float
    ): Bitmap {
        val l = leftPercent.coerceIn(0f, 0.45f)
        val t = topPercent.coerceIn(0f, 0.45f)
        val r = rightPercent.coerceIn(l + 0.1f, 1f)
        val b = bottomPercent.coerceIn(t + 0.1f, 1f)

        val x = (source.width * l).toInt().coerceIn(0, source.width - 1)
        val y = (source.height * t).toInt().coerceIn(0, source.height - 1)
        val w = (source.width * (r - l)).toInt().coerceIn(1, source.width - x)
        val h = (source.height * (b - t)).toInt().coerceIn(1, source.height - y)

        return Bitmap.createBitmap(source, x, y, w, h)
    }

    fun cropBitmapByNormRect(
        source: Bitmap,
        rect: RectF,
        paddingPercent: Float = 0.012f
    ): Bitmap {
        val l = (rect.left - paddingPercent).coerceIn(0f, 0.45f)
        val t = (rect.top - paddingPercent).coerceIn(0f, 0.45f)
        val r = (rect.right + paddingPercent).coerceIn(l + 0.1f, 1f)
        val b = (rect.bottom + paddingPercent).coerceIn(t + 0.1f, 1f)

        val x = (source.width * l).toInt().coerceIn(0, source.width - 10)
        val y = (source.height * t).toInt().coerceIn(0, source.height - 10)
        val w = ((source.width * (r - l)).toInt()).coerceIn(10, source.width - x)
        val h = ((source.height * (b - t)).toInt()).coerceIn(10, source.height - y)

        return Bitmap.createBitmap(source, x, y, w, h)
    }

    fun applyFilter(
        source: Bitmap,
        filterMode: ScanFilterMode,
        isEnhanced: Boolean = false,
        isCleaned: Boolean = false
    ): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val cm = ColorMatrix()

        when (filterMode) {
            ScanFilterMode.ORIGINAL -> {
                // Identity
            }
            ScanFilterMode.ENHANCE -> {
                cm.set(floatArrayOf(
                    1.3f, 0f, 0f, 0f, 15f,
                    0f, 1.3f, 0f, 0f, 15f,
                    0f, 0f, 1.3f, 0f, 15f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            ScanFilterMode.GRAYSCALE -> {
                cm.setSaturation(0f)
            }
            ScanFilterMode.BLACK_AND_WHITE -> {
                val gray = ColorMatrix().apply { setSaturation(0f) }
                val threshold = ColorMatrix(floatArrayOf(
                    2.2f, 0f, 0f, 0f, -130f,
                    0f, 2.2f, 0f, 0f, -130f,
                    0f, 0f, 2.2f, 0f, -130f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.set(gray)
                cm.postConcat(threshold)
            }
            ScanFilterMode.MAGIC_COLOR -> {
                cm.setSaturation(1.4f)
                val bright = ColorMatrix(floatArrayOf(
                    1.15f, 0f, 0f, 0f, 15f,
                    0f, 1.15f, 0f, 0f, 15f,
                    0f, 0f, 1.15f, 0f, 15f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(bright)
            }
        }

        // Apply Enhance modifier (boost contrast & clarity) if enabled and not already using ENHANCE filter
        if (isEnhanced && filterMode != ScanFilterMode.ENHANCE) {
            val enhance = ColorMatrix(floatArrayOf(
                1.25f, 0f, 0f, 0f, 18f,
                0f, 1.25f, 0f, 0f, 18f,
                0f, 0f, 1.25f, 0f, 18f,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(enhance)
        }

        // Apply Clean modifier (whiten paper background, eliminate shadow noise)
        if (isCleaned) {
            val clean = ColorMatrix(floatArrayOf(
                1.22f, 0f, 0f, 0f, 28f,
                0f, 1.22f, 0f, 0f, 28f,
                0f, 0f, 1.22f, 0f, 28f,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(clean)
        }

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    /**
     * Warps and crops an arbitrary quadrilateral region defined by 4 normalized points
     * (top-left, top-right, bottom-right, bottom-left in [0.0..1.0]) into a rectified rectangle.
     * Stretches and perspective-corrects skewed angles so all corners become flat 90-degree corners.
     */
    fun cropPerspectiveWarp(
        source: Bitmap,
        tlX: Float, tlY: Float,
        trX: Float, trY: Float,
        brX: Float, brY: Float,
        blX: Float, blY: Float
    ): Bitmap {
        val srcW = source.width.toFloat()
        val srcH = source.height.toFloat()

        // Absolute pixel coordinates on the source bitmap
        val pTLx = (tlX * srcW).coerceIn(0f, srcW)
        val pTLy = (tlY * srcH).coerceIn(0f, srcH)
        val pTRx = (trX * srcW).coerceIn(0f, srcW)
        val pTRy = (trY * srcH).coerceIn(0f, srcH)
        val pBRx = (brX * srcW).coerceIn(0f, srcW)
        val pBRy = (brY * srcH).coerceIn(0f, srcH)
        val pBLx = (blX * srcW).coerceIn(0f, srcW)
        val pBLy = (blY * srcH).coerceIn(0f, srcH)

        fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
            val dx = x2 - x1
            val dy = y2 - y1
            return kotlin.math.sqrt(dx * dx + dy * dy)
        }

        // Target rectangular dimensions derived from quadrilateral edge lengths
        val topDist = dist(pTLx, pTLy, pTRx, pTRy)
        val bottomDist = dist(pBLx, pBLy, pBRx, pBRy)
        val leftDist = dist(pTLx, pTLy, pBLx, pBLy)
        val rightDist = dist(pTRx, pTRy, pBRx, pBRy)

        val targetWidth = maxOf(32, maxOf(topDist, bottomDist).toInt().coerceAtMost(4096))
        val targetHeight = maxOf(32, maxOf(leftDist, rightDist).toInt().coerceAtMost(4096))

        val srcPoints = floatArrayOf(
            pTLx, pTLy,
            pTRx, pTRy,
            pBRx, pBRy,
            pBLx, pBLy
        )
        val dstPoints = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat()
        )

        val matrix = Matrix()
        val success = matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        return if (success) {
            val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
            canvas.drawBitmap(source, matrix, paint)
            output
        } else {
            val minX = minOf(pTLx, pTRx, pBRx, pBLx).toInt().coerceIn(0, source.width - 1)
            val minY = minOf(pTLy, pTRy, pBRy, pBLy).toInt().coerceIn(0, source.height - 1)
            val maxX = maxOf(pTLx, pTRx, pBRx, pBLx).toInt().coerceIn(minX + 1, source.width)
            val maxY = maxOf(pTLy, pTRy, pBRy, pBLy).toInt().coerceIn(minY + 1, source.height)
            Bitmap.createBitmap(source, minX, minY, maxX - minX, maxY - minY)
        }
    }
}
