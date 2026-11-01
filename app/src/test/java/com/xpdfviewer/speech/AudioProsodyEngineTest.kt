package com.xpdfviewer.speech

import com.xpdfviewer.data.AudioSentence
import com.xpdfviewer.data.EmotionTone
import com.xpdfviewer.data.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioProsodyEngineTest {

    @Test
    fun testDialogueDetectionQuotes() {
        val s1 = AudioSentence(index = 0, text = "\"We must go to the Crystal Stream,\" said Lila.")
        val analyzed = AudioProsodyEngine.analyze(listOf(s1))

        assertTrue(analyzed[0].prosody.isDialogue)
        assertEquals(1.0f, analyzed[0].prosody.pitchMultiplier, 0.001f)
    }

    @Test
    fun testQuestionIntonation() {
        val s1 = AudioSentence(index = 0, text = "Where is the hidden key located?")
        val analyzed = AudioProsodyEngine.analyze(listOf(s1))

        assertEquals(EmotionTone.INQUISITIVE, analyzed[0].prosody.emotionTone)
        assertEquals(1.0f, analyzed[0].prosody.pitchMultiplier, 0.001f)
    }

    @Test
    fun testExclamationIntonation() {
        val s1 = AudioSentence(index = 0, text = "Look out for the falling rocks!")
        val analyzed = AudioProsodyEngine.analyze(listOf(s1))

        assertEquals(EmotionTone.EXCITED, analyzed[0].prosody.emotionTone)
        assertEquals(1.0f, analyzed[0].prosody.pitchMultiplier, 0.001f)
        assertTrue(analyzed[0].prosody.postPauseMs >= 140L)
    }

    @Test
    fun testSuspenseEllipsesPause() {
        val s1 = AudioSentence(index = 0, text = "Behind the locked door, footsteps approached...")
        val analyzed = AudioProsodyEngine.analyze(listOf(s1))

        assertEquals(EmotionTone.SUSPENSE_SOMBER, analyzed[0].prosody.emotionTone)
        assertTrue(analyzed[0].prosody.postPauseMs >= 300L)
    }

    @Test
    fun testSentimentLexiconScoring() {
        // Excitement
        val sExcited = AudioSentence(index = 0, text = "Suddenly the alarm exploded into chaos.")
        val analyzedExcited = AudioProsodyEngine.analyze(listOf(sExcited))
        assertEquals(EmotionTone.EXCITED, analyzedExcited[0].prosody.emotionTone)

        // Somber / Suspense
        val sSomber = AudioSentence(index = 0, text = "The cold creeping shadow whispered in silence.")
        val analyzedSomber = AudioProsodyEngine.analyze(listOf(sSomber))
        assertEquals(EmotionTone.SUSPENSE_SOMBER, analyzedSomber[0].prosody.emotionTone)

        // Cheerful
        val sCheerful = AudioSentence(index = 0, text = "She smiled with delightful joy and laughed warmly.")
        val analyzedCheerful = AudioProsodyEngine.analyze(listOf(sCheerful))
        assertEquals(EmotionTone.CHEERFUL, analyzedCheerful[0].prosody.emotionTone)
    }

    @Test
    fun testSpatialParagraphBreakDetection() {
        // Sentence 1 on line y = 0.10 to 0.12 (height = 0.02)
        val boundsS1 = listOf(NormalizedRect(0.1f, 0.10f, 0.9f, 0.12f))
        val s1 = AudioSentence(index = 0, text = "This is the end of the first paragraph.", wordBounds = boundsS1)

        // Sentence 2 starts after a vertical gap of 0.04 (more than 1.25 * 0.02 = 0.025)
        val boundsS2 = listOf(NormalizedRect(0.1f, 0.16f, 0.9f, 0.18f))
        val s2 = AudioSentence(index = 1, text = "This begins a new chapter.", wordBounds = boundsS2)

        val analyzed = AudioProsodyEngine.analyze(listOf(s1, s2))

        assertTrue("Sentence 1 should be detected as paragraph end due to vertical gap", analyzed[0].prosody.isParagraphEnd)
        assertTrue("Paragraph end should have longer pause >= 400ms", analyzed[0].prosody.postPauseMs >= 400L)

        // Last sentence on page should also be marked as paragraph end
        assertTrue("Last sentence on page should mark paragraph end", analyzed[1].prosody.isParagraphEnd)
    }

    @Test
    fun testIntraParagraphSentenceDoesNotMarkParagraphEnd() {
        // Sentence 1 on line y = 0.10 to 0.12
        val boundsS1 = listOf(NormalizedRect(0.1f, 0.10f, 0.5f, 0.12f))
        val s1 = AudioSentence(index = 0, text = "The sky was blue.", wordBounds = boundsS1)

        // Sentence 2 on the same line or immediate next line (y = 0.122 to 0.142, gap = 0.002)
        val boundsS2 = listOf(NormalizedRect(0.52f, 0.10f, 0.9f, 0.12f))
        val s2 = AudioSentence(index = 1, text = "The sun was warm.", wordBounds = boundsS2)

        // Sentence 3 on immediate next line
        val boundsS3 = listOf(NormalizedRect(0.1f, 0.125f, 0.9f, 0.145f))
        val s3 = AudioSentence(index = 2, text = "Birds flew overhead.", wordBounds = boundsS3)

        val analyzed = AudioProsodyEngine.analyze(listOf(s1, s2, s3))

        assertFalse("Sentence 1 on same line should not be paragraph end", analyzed[0].prosody.isParagraphEnd)
        assertFalse("Sentence 2 immediately above line 3 should not be paragraph end", analyzed[1].prosody.isParagraphEnd)
    }
}

