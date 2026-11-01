package com.xpdfviewer.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.xpdfviewer.data.AudioPageContent
import com.xpdfviewer.data.AudioPlaybackStatus
import com.xpdfviewer.data.AudioSentence
import com.xpdfviewer.data.AudioSpeed
import com.xpdfviewer.data.SleepTimerPreset
import com.xpdfviewer.data.SpatialWord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

object AudioSentenceSegmenter {

    const val DEFAULT_HEADER_THRESHOLD = 0.08f
    const val DEFAULT_FOOTER_THRESHOLD = 0.92f

    fun isHeaderOrFooterWord(
        word: SpatialWord,
        headerThreshold: Float = DEFAULT_HEADER_THRESHOLD,
        footerThreshold: Float = DEFAULT_FOOTER_THRESHOLD
    ): Boolean {
        // Direct geometric zone check: top 8% or bottom 8%
        if (word.bounds.centerY <= headerThreshold || word.bounds.bottom <= headerThreshold) {
            return true
        }
        if (word.bounds.centerY >= footerThreshold || word.bounds.top >= footerThreshold) {
            return true
        }

        // Extended margin check (top 10% or bottom 11%) for page numbers / labels
        val text = word.text.trim()
        val isPageNumOrLabel = text.matches(Regex("""^[-—]?\d+[-—]?$""")) ||
                text.equals("page", ignoreCase = true) ||
                text.matches(Regex("""^\d+\s*/\s*\d+$""")) ||
                text.matches(Regex("""^[ivxlcdm]+$""", RegexOption.IGNORE_CASE))

        if (isPageNumOrLabel) {
            if (word.bounds.bottom <= 0.10f || word.bounds.top >= 0.89f) {
                return true
            }
        }

        return false
    }

    fun segmentIntoSentences(
        pageIndex: Int,
        words: List<SpatialWord>,
        skipHeaderFooter: Boolean = true,
        headerThreshold: Float = DEFAULT_HEADER_THRESHOLD,
        footerThreshold: Float = DEFAULT_FOOTER_THRESHOLD
    ): AudioPageContent {
        if (words.isEmpty()) {
            return AudioPageContent(pageIndex, emptyList(), "")
        }

        val candidateWords = if (skipHeaderFooter) {
            words.filterNot { isHeaderOrFooterWord(it, headerThreshold, footerThreshold) }
        } else {
            words
        }

        if (candidateWords.isEmpty()) {
            return AudioPageContent(pageIndex, emptyList(), "")
        }

        val sentences = mutableListOf<AudioSentence>()
        val fullTextBuilder = StringBuilder()

        val currentWordList = mutableListOf<SpatialWord>()
        var sentenceIndex = 0

        fun flushSentence() {
            if (currentWordList.isNotEmpty()) {
                val sentenceText = currentWordList.joinToString(" ") { it.text }
                val wordIndices = currentWordList.map { it.index }
                val wordBounds = currentWordList.map { it.bounds }
                sentences.add(
                    AudioSentence(
                        index = sentenceIndex++,
                        text = sentenceText,
                        wordIndices = wordIndices,
                        wordBounds = wordBounds
                    )
                )
                currentWordList.clear()
            }
        }

        for (word in candidateWords) {
            currentWordList.add(word)
            fullTextBuilder.append(word.text).append(" ")
            val cleanText = word.text.trim()
            if (cleanText.endsWith(".") || cleanText.endsWith("?") || cleanText.endsWith("!") ||
                cleanText.endsWith(";") || cleanText.endsWith(":") || cleanText.endsWith("。") ||
                cleanText.endsWith("！") || cleanText.endsWith("？")
            ) {
                flushSentence()
            }
        }
        flushSentence()

        return AudioPageContent(pageIndex, sentences, fullTextBuilder.toString().trim())
    }
}

class ContinuousAudioReader(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private var currentSpeed: AudioSpeed = AudioSpeed.SPEED_1_0
    private var currentSleepTimer: SleepTimerPreset = SleepTimerPreset.OFF
    private var timerJob: Job? = null
    private var remainingSeconds = 0

    // Playback state
    private var activePageContent: AudioPageContent? = null
    private var currentSentenceIdx = 0
    private var currentUtteranceId: String? = null

    // Listeners
    var onSentenceChanged: ((pageIndex: Int, sentence: AudioSentence, totalSentences: Int) -> Unit)? = null
    var onPlaybackStatusChanged: ((AudioPlaybackStatus) -> Unit)? = null
    var onPageCompleted: ((pageIndex: Int) -> Unit)? = null
    var onSleepTimerTick: ((remainingSeconds: Int) -> Unit)? = null
    var onSleepTimerExpired: (() -> Unit)? = null

    private val _status = MutableStateFlow(AudioPlaybackStatus.IDLE)
    val status: StateFlow<AudioPlaybackStatus> = _status.asStateFlow()

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pause()
            }
        }
    }

    init {
        tts = TextToSpeech(context.applicationContext) { initStatus ->
            if (initStatus == TextToSpeech.SUCCESS) {
                isInitialized = true
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(currentSpeed.speed)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (utteranceId == currentUtteranceId) {
                            updateStatus(AudioPlaybackStatus.PLAYING)
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == currentUtteranceId) {
                            scope.launch(Dispatchers.Main) {
                                advanceNextSentence()
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == currentUtteranceId) {
                            updateStatus(AudioPlaybackStatus.PAUSED)
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (utteranceId == currentUtteranceId) {
                            updateStatus(AudioPlaybackStatus.PAUSED)
                        }
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        if (utteranceId == currentUtteranceId && !interrupted) {
                            updateStatus(AudioPlaybackStatus.PAUSED)
                        }
                    }
                })
            } else {
                updateStatus(AudioPlaybackStatus.IDLE)
            }
        }
    }

    var skipHeaderFooter: Boolean = true

    fun segmentIntoSentences(
        pageIndex: Int,
        words: List<SpatialWord>,
        skipHeaderFooter: Boolean = this.skipHeaderFooter
    ): AudioPageContent =
        AudioSentenceSegmenter.segmentIntoSentences(pageIndex, words, skipHeaderFooter)

    fun loadAndPlayPage(pageContent: AudioPageContent, startSentenceIndex: Int = 0) {
        activePageContent = pageContent
        currentSentenceIdx = startSentenceIndex.coerceIn(0, (pageContent.sentences.size - 1).coerceAtLeast(0))

        if (pageContent.sentences.isEmpty()) {
            updateStatus(AudioPlaybackStatus.IDLE)
            onPageCompleted?.invoke(pageContent.pageIndex)
            return
        }

        playCurrentSentence()
    }

    private fun playCurrentSentence() {
        val content = activePageContent ?: return
        if (currentSentenceIdx !in content.sentences.indices) {
            handlePageFinished()
            return
        }

        val sentence = content.sentences[currentSentenceIdx]
        onSentenceChanged?.invoke(content.pageIndex, sentence, content.sentences.size)

        requestAudioFocus()

        val utteranceId = "audio_read_${content.pageIndex}_${sentence.index}_${UUID.randomUUID()}"
        currentUtteranceId = utteranceId

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        updateStatus(AudioPlaybackStatus.BUFFERING)
        tts?.setSpeechRate(currentSpeed.speed)
        tts?.speak(sentence.text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    private fun advanceNextSentence() {
        val content = activePageContent ?: return
        if (currentSentenceIdx < content.sentences.size - 1) {
            currentSentenceIdx++
            playCurrentSentence()
        } else {
            handlePageFinished()
        }
    }

    private fun handlePageFinished() {
        val content = activePageContent ?: return
        if (currentSleepTimer == SleepTimerPreset.END_OF_PAGE) {
            stop()
            onSleepTimerExpired?.invoke()
            return
        }
        updateStatus(AudioPlaybackStatus.BUFFERING)
        onPageCompleted?.invoke(content.pageIndex)
    }

    fun pause() {
        tts?.stop()
        updateStatus(AudioPlaybackStatus.PAUSED)
    }

    fun resume() {
        if (activePageContent != null) {
            playCurrentSentence()
        }
    }

    fun skipNext() {
        val content = activePageContent ?: return
        if (currentSentenceIdx < content.sentences.size - 1) {
            tts?.stop()
            currentSentenceIdx++
            playCurrentSentence()
        } else {
            handlePageFinished()
        }
    }

    fun skipPrevious() {
        val content = activePageContent ?: return
        if (currentSentenceIdx > 0) {
            tts?.stop()
            currentSentenceIdx--
            playCurrentSentence()
        } else {
            // Replay current sentence from beginning
            playCurrentSentence()
        }
    }

    fun setSpeed(speed: AudioSpeed) {
        currentSpeed = speed
        tts?.setSpeechRate(speed.speed)
    }

    fun setSleepTimer(preset: SleepTimerPreset) {
        currentSleepTimer = preset
        timerJob?.cancel()

        if (preset == SleepTimerPreset.OFF || preset == SleepTimerPreset.END_OF_PAGE) {
            remainingSeconds = 0
            onSleepTimerTick?.invoke(0)
            return
        }

        remainingSeconds = preset.minutes * 60
        timerJob = scope.launch(Dispatchers.Main) {
            while (remainingSeconds > 0) {
                delay(1000)
                remainingSeconds--
                onSleepTimerTick?.invoke(remainingSeconds)
            }
            stop()
            onSleepTimerExpired?.invoke()
        }
    }

    fun stop() {
        tts?.stop()
        abandonAudioFocus()
        timerJob?.cancel()
        remainingSeconds = 0
        updateStatus(AudioPlaybackStatus.IDLE)
    }

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
    }

    private fun updateStatus(newStatus: AudioPlaybackStatus) {
        _status.value = newStatus
        onPlaybackStatusChanged?.invoke(newStatus)
    }

    private fun requestAudioFocus(): Boolean {
        if (audioManager == null) return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (audioManager == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
    }
}
