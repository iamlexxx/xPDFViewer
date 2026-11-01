package com.xpdfviewer.speech

import com.xpdfviewer.data.NormalizedRect
import com.xpdfviewer.data.SpatialWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSentenceSegmenterTest {

    @Test
    fun testSegmentIntoSentences_multipleSentences() {
        val words = listOf(
            SpatialWord(index = 0, text = "Hello", bounds = NormalizedRect(0.1f, 0.1f, 0.2f, 0.15f)),
            SpatialWord(index = 1, text = "world.", bounds = NormalizedRect(0.2f, 0.1f, 0.3f, 0.15f)),
            SpatialWord(index = 2, text = "How", bounds = NormalizedRect(0.1f, 0.2f, 0.2f, 0.25f)),
            SpatialWord(index = 3, text = "are", bounds = NormalizedRect(0.2f, 0.2f, 0.3f, 0.25f)),
            SpatialWord(index = 4, text = "you?", bounds = NormalizedRect(0.3f, 0.2f, 0.4f, 0.25f))
        )

        val pageContent = AudioSentenceSegmenter.segmentIntoSentences(pageIndex = 0, words = words, skipHeaderFooter = false)

        assertEquals(2, pageContent.sentences.size)
        assertEquals("Hello world.", pageContent.sentences[0].text)
        assertEquals(listOf(0, 1), pageContent.sentences[0].wordIndices)
        assertEquals(2, pageContent.sentences[0].wordBounds.size)

        assertEquals("How are you?", pageContent.sentences[1].text)
        assertEquals(listOf(2, 3, 4), pageContent.sentences[1].wordIndices)
        assertEquals(3, pageContent.sentences[1].wordBounds.size)
    }

    @Test
    fun testSegmentIntoSentences_skipsHeaderAndFooter() {
        val words = listOf(
            // Header at top (y: 0.03f..0.06f)
            SpatialWord(index = 0, text = "Chapter", bounds = NormalizedRect(0.1f, 0.03f, 0.2f, 0.06f)),
            SpatialWord(index = 1, text = "One.", bounds = NormalizedRect(0.22f, 0.03f, 0.3f, 0.06f)),

            // Body text (y: 0.25f..0.30f)
            SpatialWord(index = 2, text = "This", bounds = NormalizedRect(0.1f, 0.25f, 0.2f, 0.30f)),
            SpatialWord(index = 3, text = "is", bounds = NormalizedRect(0.22f, 0.25f, 0.28f, 0.30f)),
            SpatialWord(index = 4, text = "the", bounds = NormalizedRect(0.3f, 0.25f, 0.38f, 0.30f)),
            SpatialWord(index = 5, text = "story.", bounds = NormalizedRect(0.4f, 0.25f, 0.5f, 0.30f)),

            // Footer page number at bottom (y: 0.94f..0.97f)
            SpatialWord(index = 6, text = "Page", bounds = NormalizedRect(0.45f, 0.94f, 0.5f, 0.97f)),
            SpatialWord(index = 7, text = "1", bounds = NormalizedRect(0.52f, 0.94f, 0.55f, 0.97f))
        )

        // When skipHeaderFooter is TRUE:
        val skippedContent = AudioSentenceSegmenter.segmentIntoSentences(
            pageIndex = 0,
            words = words,
            skipHeaderFooter = true
        )
        assertEquals(1, skippedContent.sentences.size)
        assertEquals("This is the story.", skippedContent.sentences[0].text)
        assertEquals(listOf(2, 3, 4, 5), skippedContent.sentences[0].wordIndices)

        // When skipHeaderFooter is FALSE:
        val allContent = AudioSentenceSegmenter.segmentIntoSentences(
            pageIndex = 0,
            words = words,
            skipHeaderFooter = false
        )
        assertEquals(3, allContent.sentences.size)
        assertEquals("Chapter One.", allContent.sentences[0].text)
        assertEquals("This is the story.", allContent.sentences[1].text)
        assertEquals("Page 1", allContent.sentences[2].text)
    }

    @Test
    fun testIsHeaderOrFooterWord() {
        val headerWord = SpatialWord(index = 0, text = "Header", bounds = NormalizedRect(0.1f, 0.02f, 0.3f, 0.05f))
        val bodyWord = SpatialWord(index = 1, text = "Body", bounds = NormalizedRect(0.1f, 0.4f, 0.3f, 0.45f))
        val footerWord = SpatialWord(index = 2, text = "Footer", bounds = NormalizedRect(0.1f, 0.95f, 0.3f, 0.98f))
        val pageNumberWord = SpatialWord(index = 3, text = "42", bounds = NormalizedRect(0.5f, 0.90f, 0.55f, 0.93f))

        assertTrue(AudioSentenceSegmenter.isHeaderOrFooterWord(headerWord))
        assertFalse(AudioSentenceSegmenter.isHeaderOrFooterWord(bodyWord))
        assertTrue(AudioSentenceSegmenter.isHeaderOrFooterWord(footerWord))
        assertTrue(AudioSentenceSegmenter.isHeaderOrFooterWord(pageNumberWord))
    }

    @Test
    fun testSegmentIntoSentences_noTerminalPunctuation() {
        val words = listOf(
            SpatialWord(index = 0, text = "Document", bounds = NormalizedRect(0.1f, 0.2f, 0.2f, 0.25f)),
            SpatialWord(index = 1, text = "Body", bounds = NormalizedRect(0.2f, 0.2f, 0.3f, 0.25f))
        )

        val pageContent = AudioSentenceSegmenter.segmentIntoSentences(pageIndex = 1, words = words)

        assertEquals(1, pageContent.sentences.size)
        assertEquals("Document Body", pageContent.sentences[0].text)
        assertEquals(listOf(0, 1), pageContent.sentences[0].wordIndices)
    }

    @Test
    fun testSegmentIntoSentences_empty() {
        val pageContent = AudioSentenceSegmenter.segmentIntoSentences(pageIndex = 2, words = emptyList())
        assertTrue(pageContent.isEmpty)
        assertEquals(0, pageContent.sentences.size)
    }
}
