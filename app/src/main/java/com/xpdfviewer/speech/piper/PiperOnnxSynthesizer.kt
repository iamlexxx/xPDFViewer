package com.xpdfviewer.speech.piper

import android.content.Context
import android.util.Log
import com.github.csukuangfj.piper.phonemize.piperPhonemizeInitialize
import com.github.csukuangfj.piper.phonemize.piperPhonemizeText
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.xpdfviewer.data.AudioSentence
import com.xpdfviewer.data.AudioWordToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PiperOnnxSynthesizer(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "PiperOnnxSynthesizer"
        @Volatile
        private var isPhonemizerReady = false
        private val initLock = Any()
    }

    private var offlineTts: OfflineTts? = null
    private var activeVoiceId: String? = null
    private val pcmPlayer = AudioTrackPcmPlayer(context, scope)
    private var isInitialized = false
    private var activeJob: Job? = null

    val isPlaying: Boolean get() = pcmPlayer.isPlaying
    val isPaused: Boolean get() = pcmPlayer.isPaused
    val currentVoiceId: String? get() = activeVoiceId

    fun isReady(): Boolean = isInitialized && offlineTts != null

    suspend fun initialize(voiceId: String? = null): Boolean = withContext(Dispatchers.Default) {
        val targetVoiceId = voiceId ?: activeVoiceId ?: PiperModelManager.DEFAULT_VOICE.id

        if (isInitialized && offlineTts != null && activeVoiceId == targetVoiceId) {
            return@withContext true
        }

        val paths = PiperModelManager.getVoicePaths(context, targetVoiceId)
            ?: PiperModelManager.getModelPaths(context)
            ?: return@withContext false

        try {
            // Ensure piper phonemizer (espeak-ng backend) is initialized
            synchronized(initLock) {
                if (!isPhonemizerReady) {
                    try {
                        piperPhonemizeInitialize(context.applicationContext)
                        isPhonemizerReady = true
                        Log.i(TAG, "piperPhonemizeInitialize succeeded")
                    } catch (e: Exception) {
                        Log.e(TAG, "piperPhonemizeInitialize failed", e)
                    }
                }
            }

            // Release any previously loaded voice model
            try {
                offlineTts?.release()
            } catch (_: Exception) {}
            offlineTts = null

            val vitsConfig = OfflineTtsVitsModelConfig(
                model = paths.modelPath,
                tokens = paths.tokensPath,
                dataDir = paths.dataDir
            )
            val modelConfig = OfflineTtsModelConfig(
                vits = vitsConfig,
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )
            val config = OfflineTtsConfig(
                model = modelConfig,
                maxNumSentences = 1
            )

            val tts = if (paths.isAsset) {
                OfflineTts(assetManager = context.assets, config = config)
            } else {
                OfflineTts(assetManager = null, config = config)
            }

            offlineTts = tts
            activeVoiceId = targetVoiceId
            isInitialized = true
            Log.i(TAG, "Piper ONNX TTS loaded voice '$targetVoiceId' successfully: sampleRate=${tts.sampleRate()}, speakers=${tts.numSpeakers()}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Piper ONNX voice '$targetVoiceId'", e)
            isInitialized = false
            offlineTts = null
            false
        }
    }

    fun speak(
        sentence: AudioSentence,
        speed: Float,
        voiceId: String? = null,
        onStart: () -> Unit,
        onWordProgress: (AudioWordToken) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        stop()

        activeJob = scope.launch(Dispatchers.Default) {
            val targetVoiceId = voiceId ?: activeVoiceId ?: PiperModelManager.DEFAULT_VOICE.id

            if (offlineTts == null || activeVoiceId != targetVoiceId) {
                val success = initialize(targetVoiceId)
                if (!success || offlineTts == null) {
                    withContext(Dispatchers.Main) {
                        onError("Piper Studio voice model not ready or not installed")
                    }
                    return@launch
                }
            }

            val readyTts = offlineTts ?: return@launch
            val voiceModel = PiperModelManager.getVoiceById(targetVoiceId)

            try {
                // Dynamically route phonemizer voice (British Alan requires "en-gb-x-rp", US models use "en-us")
                val voiceName = voiceModel?.espeakVoice
                    ?: when {
                        targetVoiceId.contains("alan", ignoreCase = true) || targetVoiceId.contains("gb", ignoreCase = true) -> "en-gb-x-rp"
                        else -> "en-us"
                    }

                // Phonemize English text to codepoints for the VITS neural model with robust fallback
                var phonemesResult = try {
                    piperPhonemizeText(sentence.text, voiceName)
                } catch (e: Exception) {
                    Log.w(TAG, "piperPhonemizeText exception for voice '$voiceName'", e)
                    null
                }

                if (phonemesResult == null && voiceName != "en-us") {
                    Log.w(TAG, "piperPhonemizeText returned null for voice '$voiceName', attempting fallbacks (en-gb-x-rp / en-us)")
                    phonemesResult = try {
                        piperPhonemizeText(sentence.text, "en-gb-x-rp")
                    } catch (_: Exception) { null }
                        ?: try {
                            piperPhonemizeText(sentence.text, "en-us")
                        } catch (_: Exception) { null }
                }

                if (phonemesResult == null) {
                    Log.e(TAG, "piperPhonemizeText failed for all voice candidates for text: '${sentence.text}' (tried: $voiceName)")
                    withContext(Dispatchers.Main) {
                        onError("Phonemizer failed to convert text")
                    }
                    return@launch
                }

                val phonemeCodepoints = try {
                    Array(phonemesResult.numSentences) { i -> phonemesResult.getPhonemes(i) }
                } finally {
                    phonemesResult.close()
                }

                val genConfig = GenerationConfig(
                    sid = 0,
                    speed = speed
                ).apply {
                    this.phonemeCodepoints = phonemeCodepoints
                }

                // Generate studio-grade 16-bit PCM waveform using VITS neural model
                val audio = readyTts.generateWithConfig(text = "", config = genConfig)
                val samples = audio.samples
                val sampleRate = audio.sampleRate

                if (samples.isEmpty()) {
                    Log.w(TAG, "Generated audio waveform was empty")
                    withContext(Dispatchers.Main) { onDone() }
                    return@launch
                }

                pcmPlayer.onPlaybackStarted = {
                    onStart()
                    sentence.wordTokens.firstOrNull()?.let { onWordProgress(it) }
                }

                pcmPlayer.onPlaybackProgress = { currentSample, totalSamples ->
                    if (sentence.wordTokens.isNotEmpty() && totalSamples > 0) {
                        val progressFraction = (currentSample.toFloat() / totalSamples.toFloat()).coerceIn(0f, 1f)
                        val textLen = sentence.text.length
                        if (textLen > 0) {
                            val targetChar = (textLen * progressFraction).toInt().coerceIn(0, textLen - 1)
                            val token = sentence.wordTokens.firstOrNull { targetChar in it.charStart..it.charEnd }
                                ?: sentence.wordTokens.minByOrNull { Math.abs(it.charStart - targetChar) }
                                ?: sentence.wordTokens.last()
                            onWordProgress(token)
                        } else {
                            val tokenIndex = (sentence.wordTokens.size * progressFraction).toInt()
                                .coerceIn(0, sentence.wordTokens.size - 1)
                            onWordProgress(sentence.wordTokens[tokenIndex])
                        }
                    }
                }

                pcmPlayer.onPlaybackCompleted = {
                    onDone()
                }

                pcmPlayer.onPlaybackError = { errMsg ->
                    onError(errMsg)
                }

                pcmPlayer.play(samples, sampleRate)

            } catch (e: Exception) {
                Log.e(TAG, "Piper synthesis error", e)
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Synthesis error")
                }
            }
        }
    }

    fun pause() {
        pcmPlayer.pause()
    }

    fun resume() {
        pcmPlayer.resume()
    }

    fun stop() {
        activeJob?.cancel()
        activeJob = null
        pcmPlayer.stop()
    }

    fun release() {
        stop()
        pcmPlayer.release()
        try {
            offlineTts?.release()
        } catch (_: Exception) {}
        offlineTts = null
        activeVoiceId = null
        isInitialized = false
    }
}
