package com.xpdfviewer.speech

import com.xpdfviewer.data.AudioWordToken
import com.xpdfviewer.data.NormalizedRect
import com.xpdfviewer.data.SpatialWord

object AudioTextNormalizer {

    private val LIGATURE_MAP = mapOf(
        "ﬁ" to "fi",
        "ﬂ" to "fl",
        "ﬀ" to "ff",
        "ﬃ" to "ffi",
        "ﬄ" to "ffl",
        "ﬆ" to "st",
        "œ" to "oe",
        "Œ" to "Oe",
        "æ" to "ae",
        "Æ" to "Ae"
    )

    private val ABBREVIATION_EXPANSIONS = mapOf(
        "e.g." to "for example",
        "e.g.," to "for example,",
        "i.e." to "that is",
        "i.e.," to "that is,",
        "et al." to "and others",
        "et al.," to "and others,",
        "etc." to "etcetera",
        "etc.," to "etcetera,",
        "dr." to "Doctor",
        "mr." to "Mister",
        "ms." to "Miz",
        "mrs." to "Missus",
        "prof." to "Professor",
        "fig." to "Figure",
        "figs." to "Figures",
        "vs." to "versus",
        "no." to "number",
        "vol." to "volume"
    )

    private val NON_SPLITTING_ABBREVIATIONS = setOf(
        "dr.", "mr.", "ms.", "mrs.", "prof.", "e.g.", "i.e.", "vs.",
        "etc.", "fig.", "figs.", "no.", "vol.", "al.", "gen.", "rep.", "sen."
    )

    private val NUMERIC_CITATION_REGEX = Regex("""^\[\s*\d+(?:[\s,–-]+\d+)*\s*\][.,;:]?$""")
    private val PARENTHETICAL_CITATION_REGEX = Regex("""^\([A-Za-z\s]+(?:et\s+al\.)?,\s*\d{4}[a-z]?\)[.,;:]?$""")
    private val URL_REGEX = Regex("""^https?://\S+$""", RegexOption.IGNORE_CASE)

    fun expandLigatures(text: String): String {
        var result = text
        for ((ligature, replacement) in LIGATURE_MAP) {
            if (result.contains(ligature)) {
                result = result.replace(ligature, replacement)
            }
        }
        return result
    }

    fun isCitation(text: String): Boolean {
        val trimmed = text.trim()
        return NUMERIC_CITATION_REGEX.matches(trimmed) || PARENTHETICAL_CITATION_REGEX.matches(trimmed)
    }

    fun isUrl(text: String): Boolean {
        return URL_REGEX.matches(text.trim())
    }

    fun isNonSplittingAbbreviation(text: String): Boolean {
        val lower = text.trim().lowercase()
        return NON_SPLITTING_ABBREVIATIONS.contains(lower)
    }

    fun normalizeWordText(text: String): String {
        val ligFree = expandLigatures(text)
        val lower = ligFree.trim().lowercase()

        val expansion = ABBREVIATION_EXPANSIONS[lower]
        if (expansion != null) {
            return expansion
        }

        // Symbol expansions
        return when (ligFree.trim()) {
            "&" -> "and"
            "%" -> "percent"
            "$" -> "dollars"
            "€" -> "euros"
            "£" -> "pounds"
            else -> {
                if (isUrl(ligFree)) {
                    "web link"
                } else {
                    ligFree
                }
            }
        }
    }

    /**
     * Checks if word1 and word2 form a hyphenated line-break word pair (e.g. "inter-" + "national").
     */
    fun isHyphenatedPair(word1: SpatialWord, word2: SpatialWord): Boolean {
        val t1 = word1.text.trim()
        val t2 = word2.text.trim()

        val endsWithHyphen = t1.length > 1 && (t1.endsWith("-") || t1.endsWith("—"))
        if (!endsWithHyphen) return false

        val startsWithLetter = t2.isNotEmpty() && t2[0].isLetter()
        if (!startsWithLetter) return false

        // Vertical relationship: word2 should be on the next line or to the right
        val isNextLineOrAfter = word2.bounds.top >= word1.bounds.top - 0.02f
        return isNextLineOrAfter
    }

    /**
     * Pre-processes raw extracted spatial words into normalized tokens,
     * merging hyphenated line-breaks, expanding ligatures, and suppressing citations.
     */
    fun processSpatialWords(words: List<SpatialWord>): List<ProcessedWord> {
        if (words.isEmpty()) return emptyList()

        val result = mutableListOf<ProcessedWord>()
        var i = 0

        while (i < words.size) {
            val currentWord = words[i]

            // Check if hyphenated with next word
            if (i < words.size - 1 && isHyphenatedPair(currentWord, words[i + 1])) {
                val nextWord = words[i + 1]
                val cleanWord1 = currentWord.text.trim().dropLast(1)
                val cleanWord2 = nextWord.text.trim()
                val mergedRaw = "$cleanWord1$cleanWord2"
                val normalizedSpoken = normalizeWordText(mergedRaw)

                result.add(
                    ProcessedWord(
                        originalWords = listOf(currentWord, nextWord),
                        rawText = mergedRaw,
                        spokenText = normalizedSpoken,
                        bounds = listOf(currentWord.bounds, nextWord.bounds),
                        isCitation = false
                    )
                )
                i += 2
            } else {
                val raw = currentWord.text.trim()
                val isCit = isCitation(raw)
                val spoken = if (isCit) "" else normalizeWordText(raw)

                result.add(
                    ProcessedWord(
                        originalWords = listOf(currentWord),
                        rawText = raw,
                        spokenText = spoken,
                        bounds = listOf(currentWord.bounds),
                        isCitation = isCit
                    )
                )
                i += 1
            }
        }

        return result
    }

    data class ProcessedWord(
        val originalWords: List<SpatialWord>,
        val rawText: String,
        val spokenText: String,
        val bounds: List<NormalizedRect>,
        val isCitation: Boolean
    )
}

