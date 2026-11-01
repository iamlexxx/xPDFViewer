package com.xpdfviewer.speech

import com.xpdfviewer.data.AudioSentence
import com.xpdfviewer.data.EmotionTone
import com.xpdfviewer.data.SentenceProsody

/**
 * Pre-Reading Discourse, Prosody, and Emotion Engine.
 *
 * Scans sentences and spatial bounding boxes prior to TTS synthesis to detect:
 * 1. Spatial paragraph breaks (vertical coordinate gaps) for natural breathing pauses.
 * 2. Character dialogue vs narrative exposition for voice pitch separation.
 * 3. Punctuation intonation (questions, exclamations, dramatic pauses).
 * 4. Lexical sentiment and emotional tone (Excited, Suspense/Somber, Cheerful).
 */
object AudioProsodyEngine {

    // Emotion keyword lexicons (100% offline, lightweight O(1) lookups)
    private val EXCITED_KEYWORDS = hashSetOf(
        "suddenly", "shouted", "screamed", "hurrah", "danger", "furious", "exploded",
        "cried", "rushed", "leaped", "alarm", "chase", "urgently", "fire", "attack",
        "run", "quick", "shocked", "thrilled", "astonished", "gasp", "screaming"
    )

    private val SUSPENSE_SOMBER_KEYWORDS = hashSetOf(
        "whispered", "darkness", "shadow", "crept", "silence", "silent", "quiet",
        "gloom", "gloomy", "sorrow", "secret", "secretly", "creepy", "fear", "ghost",
        "cold", "fog", "lonely", "vanished", "grave", "mourned", "haunted", "wept"
    )

    private val CHEERFUL_KEYWORDS = hashSetOf(
        "delighted", "laughed", "lovely", "magical", "magic", "joy", "joyful",
        "happy", "happiness", "singing", "smile", "smiling", "smiled", "beautiful",
        "wonder", "wonderful", "celebrate", "blessed", "sweet", "bright", "cheered", "giggled"
    )

    private val DIALOGUE_ATTRIBUTION = hashSetOf(
        "said", "replied", "asked", "whispered", "shouted", "cried", "exclaimed",
        "muttered", "demanded", "answered", "inquired", "breathed", "screamed"
    )

    /**
     * Analyzes sentences for a page and enriches each with dynamic prosody markers.
     */
    fun analyze(sentences: List<AudioSentence>): List<AudioSentence> {
        if (sentences.isEmpty()) return emptyList()

        return sentences.mapIndexed { index, sentence ->
            val isLastSentence = (index == sentences.size - 1)
            val nextSentence = if (!isLastSentence) sentences[index + 1] else null

            val prosody = calculateSentenceProsody(sentence, nextSentence, isLastSentence)
            sentence.copy(prosody = prosody)
        }
    }

    private fun calculateSentenceProsody(
        sentence: AudioSentence,
        nextSentence: AudioSentence?,
        isLastSentence: Boolean
    ): SentenceProsody {
        val trimmedText = sentence.text.trim()
        val isDialogue = detectDialogue(trimmedText)
        val punctuation = analyzePunctuation(trimmedText)
        val lexicon = analyzeLexicon(trimmedText)

        // Resolve dominant emotional tone for dock badge
        val dominantTone = when {
            punctuation.tone == EmotionTone.INQUISITIVE -> EmotionTone.INQUISITIVE
            punctuation.tone == EmotionTone.EXCITED -> EmotionTone.EXCITED
            lexicon.tone != EmotionTone.NEUTRAL -> lexicon.tone
            trimmedText.contains("...") || trimmedText.contains("…") || trimmedText.contains("—") -> EmotionTone.SUSPENSE_SOMBER
            else -> EmotionTone.NEUTRAL
        }

        // Natural Cadence & Breathing Pauses (retaining consistent, locked narrator voice pitch)
        var finalPauseMs = maxOf(140L, punctuation.pauseMs, lexicon.pauseMs)

        // Spatial Paragraph Boundary Detection
        var isParagraphEnd = isLastSentence
        if (nextSentence != null && sentence.wordBounds.isNotEmpty() && nextSentence.wordBounds.isNotEmpty()) {
            val currentBottom = sentence.wordBounds.maxOf { it.bottom }
            val nextTop = nextSentence.wordBounds.minOf { it.top }
            val verticalGap = nextTop - currentBottom
            val avgHeight = sentence.wordBounds.map { it.height }.average().toFloat()

            // If vertical gap between lines is greater than 1.25x line height, it's a paragraph separation
            if (verticalGap > (avgHeight * 1.25f)) {
                isParagraphEnd = true
            }
        }

        if (isParagraphEnd) {
            finalPauseMs = maxOf(finalPauseMs, 420L)
        }

        return SentenceProsody(
            emotionTone = dominantTone,
            isDialogue = isDialogue,
            pitchMultiplier = 1.0f, // Locked to user's chosen pitch; avoids erratic vocal morphing
            speedMultiplier = 1.0f,
            postPauseMs = finalPauseMs,
            isParagraphEnd = isParagraphEnd
        )
    }

    private fun detectDialogue(text: String): Boolean {
        // Direct speech starts or ends with quote marks
        if (text.startsWith("\"") || text.startsWith("“") || text.startsWith("‘") || text.startsWith("«")) {
            return true
        }
        if (text.endsWith("\"") || text.endsWith("”") || text.endsWith("’") || text.endsWith("»")) {
            return true
        }
        // Contains inline dialogue quotes
        if (text.contains("\"") || text.contains("“") || text.contains("”")) {
            return true
        }
        // Attribution check (e.g. said Lila, whispered Peter)
        val tokens = text.lowercase().split(Regex("[^a-zA-Z]+"))
        return tokens.any { it in DIALOGUE_ATTRIBUTION }
    }

    private fun analyzePunctuation(text: String): PunctuationResult {
        return when {
            text.endsWith("?") -> {
                PunctuationResult(
                    tone = EmotionTone.INQUISITIVE,
                    pitchMult = 1.12f,
                    speedMult = 1.0f,
                    pauseMs = 180L
                )
            }
            text.endsWith("!") -> {
                PunctuationResult(
                    tone = EmotionTone.EXCITED,
                    pitchMult = 1.15f,
                    speedMult = 1.08f,
                    pauseMs = 200L
                )
            }
            text.contains("...") || text.contains("…") || text.contains("—") || text.contains("--") -> {
                PunctuationResult(
                    tone = EmotionTone.SUSPENSE_SOMBER,
                    pitchMult = 0.94f,
                    speedMult = 0.92f,
                    pauseMs = 360L
                )
            }
            else -> {
                PunctuationResult(
                    tone = EmotionTone.NEUTRAL,
                    pitchMult = 1.0f,
                    speedMult = 1.0f,
                    pauseMs = 140L
                )
            }
        }
    }

    private fun analyzeLexicon(text: String): LexiconResult {
        val words = text.lowercase().split(Regex("[^a-zA-Z]+")).filter { it.isNotBlank() }
        var excitedScore = 0
        var somberScore = 0
        var cheerfulScore = 0

        for (word in words) {
            if (word in EXCITED_KEYWORDS) excitedScore++
            if (word in SUSPENSE_SOMBER_KEYWORDS) somberScore++
            if (word in CHEERFUL_KEYWORDS) cheerfulScore++
        }

        return when {
            excitedScore > somberScore && excitedScore > cheerfulScore -> {
                LexiconResult(EmotionTone.EXCITED, 1.08f, 1.06f, 160L)
            }
            somberScore > excitedScore && somberScore > cheerfulScore -> {
                LexiconResult(EmotionTone.SUSPENSE_SOMBER, 0.92f, 0.92f, 320L)
            }
            cheerfulScore > excitedScore && cheerfulScore > somberScore -> {
                LexiconResult(EmotionTone.CHEERFUL, 1.06f, 1.02f, 150L)
            }
            else -> {
                LexiconResult(EmotionTone.NEUTRAL, 1.0f, 1.0f, 140L)
            }
        }
    }

    private data class PunctuationResult(
        val tone: EmotionTone,
        val pitchMult: Float,
        val speedMult: Float,
        val pauseMs: Long
    )

    private data class LexiconResult(
        val tone: EmotionTone,
        val pitchMult: Float,
        val speedMult: Float,
        val pauseMs: Long
    )
}

