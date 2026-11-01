package com.xpdfviewer.speech

import com.xpdfviewer.data.NormalizedRect
import com.xpdfviewer.data.SpatialWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTextNormalizerTest {

    @Test
    fun testExpandLigatures() {
        assertEquals("office", AudioTextNormalizer.expandLigatures("ofﬁce"))
        assertEquals("fluid", AudioTextNormalizer.expandLigatures("ﬂuid"))
        assertEquals("difficult", AudioTextNormalizer.expandLigatures("diﬃcult"))
        assertEquals("manoeuvre", AudioTextNormalizer.expandLigatures("manœuvre"))
        assertEquals("aegis", AudioTextNormalizer.expandLigatures("ægis"))
    }

    @Test
    fun testIsCitation() {
        assertTrue(AudioTextNormalizer.isCitation("[1]"))
        assertTrue(AudioTextNormalizer.isCitation("[12]"))
        assertTrue(AudioTextNormalizer.isCitation("[1, 2, 3]"))
        assertTrue(AudioTextNormalizer.isCitation("[14-16]"))
        assertTrue(AudioTextNormalizer.isCitation("[1]."))
        assertTrue(AudioTextNormalizer.isCitation("(Smith, 2020)"))
        assertTrue(AudioTextNormalizer.isCitation("(Johnson et al., 2021);"))

        assertFalse(AudioTextNormalizer.isCitation("Hello"))
        assertFalse(AudioTextNormalizer.isCitation("Table 1"))
        assertFalse(AudioTextNormalizer.isCitation("Section 4"))
    }

    @Test
    fun testAbbreviationExpansions() {
        assertEquals("for example", AudioTextNormalizer.normalizeWordText("e.g."))
        assertEquals("that is", AudioTextNormalizer.normalizeWordText("i.e."))
        assertEquals("Doctor", AudioTextNormalizer.normalizeWordText("Dr."))
        assertEquals("Figure", AudioTextNormalizer.normalizeWordText("Fig."))
        assertEquals("versus", AudioTextNormalizer.normalizeWordText("vs."))
        assertEquals("and", AudioTextNormalizer.normalizeWordText("&"))
        assertEquals("percent", AudioTextNormalizer.normalizeWordText("%"))
    }

    @Test
    fun testDeHyphenationPair() {
        val w1 = SpatialWord(0, "in-", NormalizedRect(0.8f, 0.2f, 0.9f, 0.25f))
        val w2 = SpatialWord(1, "formation", NormalizedRect(0.1f, 0.26f, 0.3f, 0.31f))

        assertTrue(AudioTextNormalizer.isHyphenatedPair(w1, w2))

        val processed = AudioTextNormalizer.processSpatialWords(listOf(w1, w2))
        assertEquals(1, processed.size)
        assertEquals("information", processed[0].rawText)
        assertEquals("information", processed[0].spokenText)
        assertEquals(2, processed[0].bounds.size)
    }

    @Test
    fun testSentenceSegmenter_withAbbreviationAndCitations() {
        val words = listOf(
            SpatialWord(0, "Dr.", NormalizedRect(0.1f, 0.2f, 0.2f, 0.25f)),
            SpatialWord(1, "Smith", NormalizedRect(0.22f, 0.2f, 0.3f, 0.25f)),
            SpatialWord(2, "[1]", NormalizedRect(0.31f, 0.2f, 0.35f, 0.25f)),
            SpatialWord(3, "arrived.", NormalizedRect(0.36f, 0.2f, 0.45f, 0.25f))
        )

        val content = AudioSentenceSegmenter.segmentIntoSentences(0, words, skipHeaderFooter = false)
        // Ensure "Dr." didn't break into two sentences:
        assertEquals(1, content.sentences.size)
        val sentence = content.sentences[0]

        // Citation [1] should be stripped from spoken text:
        assertEquals("Doctor Smith arrived.", sentence.text)

        // Word tokens should accurately record character offsets:
        assertEquals(3, sentence.wordTokens.size)
        assertEquals("Doctor", sentence.wordTokens[0].spokenText)
        assertEquals(0, sentence.wordTokens[0].charStart)
        assertEquals(6, sentence.wordTokens[0].charEnd)

        assertEquals("Smith", sentence.wordTokens[1].spokenText)
        assertEquals(7, sentence.wordTokens[1].charStart)
        assertEquals(12, sentence.wordTokens[1].charEnd)

        assertEquals("arrived.", sentence.wordTokens[2].spokenText)
        assertEquals(13, sentence.wordTokens[2].charStart)
        assertEquals(21, sentence.wordTokens[2].charEnd)
    }
}

