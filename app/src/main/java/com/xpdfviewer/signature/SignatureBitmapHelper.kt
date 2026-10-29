package com.xpdfviewer.signature

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import kotlin.math.max
import kotlin.math.min

object SignatureBitmapHelper {

    data class StrokePoint(val x: Float, val y: Float)

    data class Stroke(
        val points: List<StrokePoint>,
        val colorHex: Int,
        val strokeWidth: Float
    )

    fun createBitmapFromStrokes(strokes: List<Stroke>, targetWidth: Int = 800, targetHeight: Int = 400): Bitmap? {
        if (strokes.isEmpty() || strokes.all { it.points.isEmpty() }) return null

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (stroke in strokes) {
            for (p in stroke.points) {
                minX = min(minX, p.x)
                minY = min(minY, p.y)
                maxX = max(maxX, p.x)
                maxY = max(maxY, p.y)
            }
        }

        if (minX >= maxX || minY >= maxY) return null

        val padding = 24f
        val boundWidth = (maxX - minX + padding * 2).toInt().coerceAtLeast(100)
        val boundHeight = (maxY - minY + padding * 2).toInt().coerceAtLeast(60)

        val bitmap = Bitmap.createBitmap(boundWidth, boundHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        for (stroke in strokes) {
            if (stroke.points.size < 2) continue

            val paint = Paint().apply {
                color = stroke.colorHex
                this.strokeWidth = stroke.strokeWidth
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            val path = Path()
            val first = stroke.points[0]
            path.moveTo(first.x - minX + padding, first.y - minY + padding)

            for (i in 1 until stroke.points.size) {
                val prev = stroke.points[i - 1]
                val curr = stroke.points[i]
                val midX = (prev.x + curr.x) / 2f - minX + padding
                val midY = (prev.y + curr.y) / 2f - minY + padding
                path.quadTo(prev.x - minX + padding, prev.y - minY + padding, midX, midY)
            }
            val last = stroke.points.last()
            path.lineTo(last.x - minX + padding, last.y - minY + padding)

            canvas.drawPath(path, paint)
        }

        return bitmap
    }

    fun createTypedSignatureBitmap(name: String, colorHex: Int): Bitmap {
        val cleanName = if (name.isBlank()) "Signature" else name.trim()
        val paint = Paint().apply {
            color = colorHex
            textSize = 72f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }

        val textBounds = Rect()
        paint.getTextBounds(cleanName, 0, cleanName.length, textBounds)

        val padding = 32
        val width = textBounds.width() + padding * 2
        val height = textBounds.height() + padding * 2 + 16

        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(200), height.coerceAtLeast(100), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw name in flowing italic font
        val x = padding.toFloat()
        val y = padding.toFloat() + textBounds.height()
        canvas.drawText(cleanName, x, y, paint)

        // Draw elegant calligraphic flourish baseline underline
        val linePaint = Paint().apply {
            color = colorHex
            strokeWidth = 3f
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val lineY = y + 12f
        canvas.drawLine(x, lineY, x + textBounds.width().toFloat(), lineY, linePaint)

        return bitmap
    }

    fun createDateStampBitmap(dateText: String, colorHex: Int): Bitmap {
        val text = "Date: $dateText"
        val paint = Paint().apply {
            color = colorHex
            textSize = 36f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val textBounds = Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)

        val paddingX = 24
        val paddingY = 16
        val width = textBounds.width() + paddingX * 2
        val height = textBounds.height() + paddingY * 2

        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(150), height.coerceAtLeast(60), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val x = paddingX.toFloat()
        val y = paddingY.toFloat() + textBounds.height()
        canvas.drawText(text, x, y, paint)

        return bitmap
    }

    fun loadBitmapFromUri(context: android.content.Context, uri: android.net.Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                android.graphics.BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun processImageSignature(source: Bitmap, removeBackground: Boolean): Bitmap {
        val src = if (source.config != Bitmap.Config.ARGB_8888) {
            source.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            source
        }

        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        var minX = width
        var minY = height
        var maxX = 0
        var maxY = 0

        if (removeBackground) {
            for (y in 0 until height) {
                val rowOffset = y * width
                for (x in 0 until width) {
                    val pixel = pixels[rowOffset + x]
                    val a = android.graphics.Color.alpha(pixel)
                    if (a == 0) continue

                    val r = android.graphics.Color.red(pixel)
                    val g = android.graphics.Color.green(pixel)
                    val b = android.graphics.Color.blue(pixel)

                    // Luminance calculation
                    val luminance = (0.299f * r + 0.587f * g + 0.114f * b).toInt()

                    if (luminance >= 220) {
                        pixels[rowOffset + x] = 0 // Transparent
                    } else {
                        // Smoothly anti-alias edges
                        val newAlpha = if (luminance > 180) {
                            ((220 - luminance) * 255 / 40).coerceIn(0, 255)
                        } else {
                            a
                        }
                        pixels[rowOffset + x] = android.graphics.Color.argb(newAlpha, r, g, b)
                        if (newAlpha > 20) {
                            minX = min(minX, x)
                            minY = min(minY, y)
                            maxX = max(maxX, x)
                            maxY = max(maxY, y)
                        }
                    }
                }
            }
        } else {
            for (y in 0 until height) {
                val rowOffset = y * width
                for (x in 0 until width) {
                    val pixel = pixels[rowOffset + x]
                    val a = android.graphics.Color.alpha(pixel)
                    if (a > 20) {
                        minX = min(minX, x)
                        minY = min(minY, y)
                        maxX = max(maxX, x)
                        maxY = max(maxY, y)
                    }
                }
            }
        }

        if (minX > maxX || minY > maxY) {
            return src
        }

        val padding = 16
        val cropMinX = (minX - padding).coerceAtLeast(0)
        val cropMinY = (minY - padding).coerceAtLeast(0)
        val cropMaxX = (maxX + padding).coerceAtMost(width - 1)
        val cropMaxY = (maxY + padding).coerceAtMost(height - 1)

        val cropW = (cropMaxX - cropMinX + 1).coerceAtLeast(1)
        val cropH = (cropMaxY - cropMinY + 1).coerceAtLeast(1)

        val outBitmap = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(pixels, cropMinY * width + cropMinX, width, 0, 0, cropW, cropH)
        return outBitmap
    }
}
