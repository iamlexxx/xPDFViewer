package com.xpdfviewer.data

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

data class AudioSentence(
    val index: Int,
    val text: String,
    val wordIndices: List<Int>,
    val wordBounds: List<NormalizedRect>
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
    val totalSentencesOnPage: Int = 0,
    val speed: AudioSpeed = AudioSpeed.SPEED_1_0,
    val sleepTimer: SleepTimerPreset = SleepTimerPreset.OFF,
    val sleepTimerRemainingSeconds: Int = 0,
    val isAutoScrollEnabled: Boolean = true,
    val skipHeaderFooter: Boolean = true
) {
    val isPlaying: Boolean get() = status == AudioPlaybackStatus.PLAYING
}

