package com.xpdfviewer.pdf

import com.xpdfviewer.data.WatermarkPageScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfWatermarkEngineTest {

    @Test
    fun testParsePageIndices_allPages() {
        val pages = PdfWatermarkEngine.parsePageIndices(
            scope = WatermarkPageScope.ALL_PAGES,
            customRangeText = "",
            totalPages = 5
        )
        assertEquals(setOf(0, 1, 2, 3, 4), pages)
    }

    @Test
    fun testParsePageIndices_skipFirst() {
        val pages = PdfWatermarkEngine.parsePageIndices(
            scope = WatermarkPageScope.SKIP_FIRST,
            customRangeText = "",
            totalPages = 4
        )
        assertEquals(setOf(1, 2, 3), pages)
    }

    @Test
    fun testParsePageIndices_customRange() {
        val pages = PdfWatermarkEngine.parsePageIndices(
            scope = WatermarkPageScope.CUSTOM_RANGE,
            customRangeText = "1-3, 5",
            totalPages = 10
        )
        assertEquals(setOf(0, 1, 2, 4), pages)
    }

    @Test
    fun testParsePageIndices_outOfBoundsClamped() {
        val pages = PdfWatermarkEngine.parsePageIndices(
            scope = WatermarkPageScope.CUSTOM_RANGE,
            customRangeText = "1-20",
            totalPages = 3
        )
        assertEquals(setOf(0, 1, 2), pages)
    }

    @Test
    fun testParsePageIndices_emptyOrZero() {
        val pages = PdfWatermarkEngine.parsePageIndices(
            scope = WatermarkPageScope.ALL_PAGES,
            customRangeText = "",
            totalPages = 0
        )
        assertTrue(pages.isEmpty())
    }
}

