package com.xpdfviewer.data

import com.xpdfviewer.speech.piper.PiperDownloadStatus

enum class AudioPlaybackStatus {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED
}

enum class AudioSpeed(val speed: Float, val label: String) {
    SPEED_0_75(0.75f, "0.75×"),
    SPEED_1_0(1.0f, "1.0×"),
    SPEED_1_25(1.25f, "1.25×"),
    SPEED_1_5(1.5f, "1.5×"),
    SPEED_1_75(1.75f, "1.75×"),
    SPEED_2_0(2.0f, "2.0×")
}

enum class SleepTimerPreset(val minutes: Int, val label: String) {
    OFF(0, "Off"),
    MIN_15(15, "15 min"),
    MIN_30(30, "30 min"),
    MIN_45(45, "45 min"),
    MIN_60(60, "60 min"),
    END_OF_PAGE(-1, "End of Page")
}

enum class EmotionTone(val label: String, val iconSymbol: String) {
    NEUTRAL("Narrative", "📖"),
    EXCITED("Excited", "⚡"),
    INQUISITIVE("Question", "❓"),
    SUSPENSE_SOMBER("Somber", "🌙"),
    CHEERFUL("Cheerful", "✨")
}

data class SentenceProsody(
    val emotionTone: EmotionTone = EmotionTone.NEUTRAL,
    val isDialogue: Boolean = false,
    val pitchMultiplier: Float = 1.0f,
    val speedMultiplier: Float = 1.0f,
    val postPauseMs: Long = 140L,
    val isParagraphEnd: Boolean = false
)

data class AudioWordToken(
    val index: Int,
    val rawText: String,
    val spokenText: String,
    val charStart: Int,
    val charEnd: Int,
    val bounds: List<NormalizedRect>
)

data class AudioVoiceProfile(
    val id: String,
    val name: String,
    val localeLanguage: String,
    val localeCountry: String,
    val isNetworkRequired: Boolean = false,
    val quality: Int = 300,
    val isHighDefinition: Boolean = false,
    val gender: String? = null,
    val isStudioNeural: Boolean = false
)

data class AudioSentence(
    val index: Int,
    val text: String,
    val wordTokens: List<AudioWordToken> = emptyList(),
    val wordIndices: List<Int> = emptyList(),
    val wordBounds: List<NormalizedRect> = emptyList(),
    val prosody: SentenceProsody = SentenceProsody()
)

data class AudioPageContent(
    val pageIndex: Int,
    val sentences: List<AudioSentence>,
    val fullText: String
) {
    val isEmpty: Boolean get() = sentences.isEmpty()
}

data class AudioReaderUiState(
    val isActive: Boolean = false,
    val status: AudioPlaybackStatus = AudioPlaybackStatus.IDLE,
    val currentPageIndex: Int = 0,
    val currentSentenceIndex: Int = 0,
    val currentSentenceText: String = "",
    val activeSentenceWordBounds: List<NormalizedRect> = emptyList(),
    val activeWordBounds: List<NormalizedRect> = emptyList(),
    val activeWordText: String? = null,
    val totalSentencesOnPage: Int = 0,
    val speed: AudioSpeed = AudioSpeed.SPEED_1_0,
    val pitch: Float = 1.0f,
    val selectedVoiceId: String? = null,
    val selectedVoiceName: String? = null,
    val currentEmotionTone: EmotionTone = EmotionTone.NEUTRAL,
    val isCurrentDialogue: Boolean = false,
    val sleepTimer: SleepTimerPreset = SleepTimerPreset.OFF,
    val sleepTimerRemainingSeconds: Int = 0,
    val isAutoScrollEnabled: Boolean = true,
    val skipHeaderFooter: Boolean = true,
    val isPiperStudioReady: Boolean = false,
    val isPiperDownloading: Boolean = false,
    val piperDownloadProgress: Float? = null,
    val piperStatusMessage: String? = null,
    val studioVoiceStatuses: Map<String, PiperDownloadStatus> = emptyMap()
) {
    val isPlaying: Boolean get() = status == AudioPlaybackStatus.PLAYING
}
