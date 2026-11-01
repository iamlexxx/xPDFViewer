package com.xpdfviewer.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class TextToSpeechManager(context: Context) {

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingText: String? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                tts?.language = Locale.getDefault()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _isSpeaking.value = false
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        _isSpeaking.value = false
                    }
                })
                pendingText?.let { text ->
                    pendingText = null
                    speak(text)
                }
            } else {
                _isSpeaking.value = false
                pendingText = null
            }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (!isInitialized) {
            pendingText = text
            _isSpeaking.value = true
            return
        }
        stop()
        val utteranceId = UUID.randomUUID().toString()
        _isSpeaking.value = true
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            _isSpeaking.value = false
        }
    }

    fun stop() {
        pendingText = null
        tts?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        pendingText = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        _isSpeaking.value = false
    }
}

