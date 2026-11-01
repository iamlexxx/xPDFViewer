package com.xpdfviewer.speech.piper

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class AudioTrackPcmPlayer(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "AudioTrackPcmPlayer"
    }

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val isPlayingState = AtomicBoolean(false)
    private val isPausedState = AtomicBoolean(false)

    var onPlaybackStarted: (() -> Unit)? = null
    var onPlaybackProgress: ((currentSample: Int, totalSamples: Int) -> Unit)? = null
    var onPlaybackCompleted: (() -> Unit)? = null
    var onPlaybackError: ((String) -> Unit)? = null

    val isPlaying: Boolean get() = isPlayingState.get()
    val isPaused: Boolean get() = isPausedState.get()

    fun play(samples: FloatArray, sampleRate: Int) {
        stop()

        if (samples.isEmpty()) {
            onPlaybackCompleted?.invoke()
            return
        }

        playbackJob = scope.launch(Dispatchers.Default) {
            var progressJob: Job? = null
            try {
                val speechSampleCount = samples.size

                // Append 120ms of acoustic silence padding at the end so the final word
                // phoneme finishes with a clean acoustic tail and is never clipped by buffer drain
                val paddingSamples = (sampleRate * 0.12f).toInt().coerceAtLeast(1024)
                val totalSamplesWithPadding = speechSampleCount + paddingSamples

                val shortSamples = ShortArray(totalSamplesWithPadding)
                for (i in 0 until speechSampleCount) {
                    shortSamples[i] = (samples[i].coerceIn(-1.0f, 1.0f) * 32767f).toInt().toShort()
                }
                // Trailing padding remains 0 (silence)

                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                // 500ms hardware buffer headroom (22050 samples = 44100 bytes)
                val bufferSizeInBytes = maxOf(minBufferSize * 2, sampleRate)

                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val format = AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()

                val track = AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSizeInBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                        }
                    }
                    .build()

                audioTrack = track
                track.play()
                isPlayingState.set(true)
                isPausedState.set(false)

                val initialHead = (track.playbackHeadPosition.toLong() and 0xFFFFFFFFL)

                withContext(Dispatchers.Main) {
                    onPlaybackStarted?.invoke()
                }

                // Smooth real-time word spotlight synced to actual audio frames leaving the speaker
                progressJob = launch(Dispatchers.Default) {
                    while (isActive && isPlayingState.get()) {
                        if (!isPausedState.get()) {
                            val currentHead = (track.playbackHeadPosition.toLong() and 0xFFFFFFFFL) - initialHead
                            val playedSamples = currentHead.coerceIn(0L, speechSampleCount.toLong()).toInt()

                            withContext(Dispatchers.Main) {
                                onPlaybackProgress?.invoke(playedSamples, speechSampleCount)
                            }

                            if (playedSamples >= speechSampleCount) {
                                break
                            }
                        }
                        delay(25) // ~40 updates/sec
                    }
                }

                // Streaming audio write loop
                val chunkSize = 2048
                var offset = 0

                while (isActive && isPlayingState.get() && offset < totalSamplesWithPadding) {
                    if (isPausedState.get()) {
                        delay(30)
                        continue
                    }

                    val toWrite = minOf(chunkSize, totalSamplesWithPadding - offset)
                    val written = track.write(shortSamples, offset, toWrite)
                    if (written > 0) {
                        offset += written
                    } else if (written < 0) {
                        Log.w(TAG, "AudioTrack.write returned error: $written")
                        break
                    }
                }

                // Buffer drain: wait until the speaker hardware has presented all samples including final word
                var activeDrainWaitMs = 0L
                while (isActive && isPlayingState.get() && offset >= totalSamplesWithPadding) {
                    if (isPausedState.get()) {
                        delay(30)
                        continue
                    }

                    val currentHead = (track.playbackHeadPosition.toLong() and 0xFFFFFFFFL) - initialHead
                    if (currentHead >= totalSamplesWithPadding) {
                        break
                    }

                    delay(20)
                    activeDrainWaitMs += 20
                    // Safety timeout: buffer should drain within max 2.5 seconds
                    if (activeDrainWaitMs > 2500L) {
                        break
                    }
                }

                // Natural inter-sentence breath pause
                delay(120)

                progressJob?.cancel()

                if (isActive && isPlayingState.get()) {
                    isPlayingState.set(false)
                    withContext(Dispatchers.Main) {
                        onPlaybackProgress?.invoke(speechSampleCount, speechSampleCount)
                        onPlaybackCompleted?.invoke()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "AudioTrack playback error", e)
                isPlayingState.set(false)
                withContext(Dispatchers.Main) {
                    onPlaybackError?.invoke(e.localizedMessage ?: "AudioTrack error")
                }
            } finally {
                progressJob?.cancel()
                cleanupTrack()
            }
        }
    }

    fun pause() {
        if (isPlayingState.get() && !isPausedState.get()) {
            isPausedState.set(true)
            try {
                audioTrack?.pause()
            } catch (_: Exception) {}
        }
    }

    fun resume() {
        if (isPlayingState.get() && isPausedState.get()) {
            isPausedState.set(false)
            try {
                audioTrack?.play()
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        isPlayingState.set(false)
        isPausedState.set(false)
        playbackJob?.cancel()
        playbackJob = null
        cleanupTrack()
    }

    private fun cleanupTrack() {
        try {
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
            }
        } catch (_: Exception) {}
        audioTrack = null
    }

    fun release() {
        stop()
    }
}

