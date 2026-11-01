package com.xpdfviewer.speech.piper

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed interface PiperDownloadStatus {
    object Idle : PiperDownloadStatus
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : PiperDownloadStatus
    object Extracting : PiperDownloadStatus
    object Ready : PiperDownloadStatus
    data class Error(val message: String) : PiperDownloadStatus
}

data class PiperVoiceModel(
    val id: String,
    val name: String,
    val speakerRole: String,
    val accentTag: String,
    val flagEmoji: String,
    val langCode: String,
    val espeakVoice: String = "en-us",
    val subDir: String,
    val modelFileName: String,
    val tokensFileName: String = "tokens.txt",
    val downloadUrl: String,
    val approxBytes: Long = 67_220_000L
)

data class PiperModelPaths(
    val modelPath: String,
    val tokensPath: String,
    val dataDir: String,
    val isAsset: Boolean = false
)

object PiperModelManager {

    val LESSAC_STUDIO = PiperVoiceModel(
        id = "piper_studio_en_us_lessac",
        name = "Lessac Studio",
        speakerRole = "Warm, expressive storytelling (Classic Narrator)",
        accentTag = "US English",
        flagEmoji = "🇺🇸",
        langCode = "en-us",
        subDir = "vits-piper-en_US-lessac-medium",
        modelFileName = "en_US-lessac-medium.onnx",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-lessac-medium.tar.bz2",
        approxBytes = 67_230_653L
    )

    val ALAN_STUDIO = PiperVoiceModel(
        id = "piper_studio_en_gb_alan",
        name = "Alan Studio",
        speakerRole = "Deep, rich BBC-style British English male narrator",
        accentTag = "British English",
        flagEmoji = "🇬🇧",
        langCode = "en-gb",
        espeakVoice = "en-gb-x-rp",
        subDir = "vits-piper-en_GB-alan-medium",
        modelFileName = "en_GB-alan-medium.onnx",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_GB-alan-medium.tar.bz2",
        approxBytes = 67_220_121L
    )

    val AMY_STUDIO = PiperVoiceModel(
        id = "piper_studio_en_us_amy",
        name = "Amy Studio",
        speakerRole = "Calm, gentle, intimate bedtime female narrator",
        accentTag = "US English",
        flagEmoji = "🇺🇸",
        langCode = "en-us",
        subDir = "vits-piper-en_US-amy-medium",
        modelFileName = "en_US-amy-medium.onnx",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-amy-medium.tar.bz2",
        approxBytes = 67_223_746L
    )

    val RYAN_STUDIO = PiperVoiceModel(
        id = "piper_studio_en_us_ryan",
        name = "Ryan Studio",
        speakerRole = "Crisp, modern, dynamic male narrator for nonfiction & tech",
        accentTag = "US English",
        flagEmoji = "🇺🇸",
        langCode = "en-us",
        subDir = "vits-piper-en_US-ryan-medium",
        modelFileName = "en_US-ryan-medium.onnx",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-ryan-medium.tar.bz2",
        approxBytes = 67_213_100L
    )

    val ALL_STUDIO_VOICES = listOf(
        LESSAC_STUDIO,
        ALAN_STUDIO,
        AMY_STUDIO,
        RYAN_STUDIO
    )

    val DEFAULT_VOICE: PiperVoiceModel = LESSAC_STUDIO

    // Backward compatibility constants
    const val PIPER_VOICE_ID = "piper_studio_en_us_lessac"
    const val PIPER_VOICE_NAME = "Lessac Studio (Natural US English Narrator)"
    const val PIPER_MODEL_SUBDIR = "vits-piper-en_US-lessac-medium"
    const val MODEL_FILE_NAME = "en_US-lessac-medium.onnx"
    const val TOKENS_FILE_NAME = "tokens.txt"
    const val ESPEAK_DIR_NAME = "espeak-ng-data"
    const val DOWNLOAD_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-lessac-medium.tar.bz2"

    private val _downloadStatuses = MutableStateFlow<Map<String, PiperDownloadStatus>>(emptyMap())
    val downloadStatuses: StateFlow<Map<String, PiperDownloadStatus>> = _downloadStatuses.asStateFlow()

    // Backward compatibility single status flow
    private val _legacyDownloadStatus = MutableStateFlow<PiperDownloadStatus>(PiperDownloadStatus.Idle)
    val downloadStatus: StateFlow<PiperDownloadStatus> = _legacyDownloadStatus.asStateFlow()

    private val downloadJobs = mutableMapOf<String, Job>()

    fun getPiperBaseDir(context: Context): File {
        return File(context.filesDir, "piper")
    }

    fun isStudioVoice(voiceId: String?): Boolean {
        if (voiceId == null) return false
        return ALL_STUDIO_VOICES.any { it.id == voiceId }
    }

    fun getVoiceById(voiceId: String?): PiperVoiceModel? {
        if (voiceId == null) return null
        return ALL_STUDIO_VOICES.firstOrNull { it.id == voiceId }
    }

    fun isVoiceInstalled(context: Context, voiceId: String): Boolean {
        val voice = getVoiceById(voiceId) ?: return false
        val modelDir = File(getPiperBaseDir(context), voice.subDir)
        val onnxFile = File(modelDir, voice.modelFileName)
        val tokensFile = File(modelDir, voice.tokensFileName)

        if (onnxFile.exists() && onnxFile.length() > 50_000_000L &&
            tokensFile.exists() && tokensFile.length() > 50L
        ) {
            return true
        }

        // Assets directory fallback
        return try {
            val assetList = context.assets.list("piper/${voice.subDir}") ?: emptyArray()
            assetList.contains(voice.modelFileName) && assetList.contains(voice.tokensFileName)
        } catch (_: Exception) {
            false
        }
    }

    fun isModelInstalled(context: Context): Boolean {
        // True if at least one studio voice is installed
        return ALL_STUDIO_VOICES.any { isVoiceInstalled(context, it.id) }
    }

    fun getInstalledVoices(context: Context): List<PiperVoiceModel> {
        return ALL_STUDIO_VOICES.filter { isVoiceInstalled(context, it.id) }
    }

    fun getVoicePaths(context: Context, voiceId: String): PiperModelPaths? {
        val voice = getVoiceById(voiceId) ?: return null
        val modelDir = File(getPiperBaseDir(context), voice.subDir)
        val onnxFile = File(modelDir, voice.modelFileName)
        val tokensFile = File(modelDir, voice.tokensFileName)
        val espeakDir = File(modelDir, ESPEAK_DIR_NAME)

        if (onnxFile.exists() && tokensFile.exists()) {
            return PiperModelPaths(
                modelPath = onnxFile.absolutePath,
                tokensPath = tokensFile.absolutePath,
                dataDir = if (espeakDir.exists()) espeakDir.absolutePath else "",
                isAsset = false
            )
        }

        return try {
            val assetList = context.assets.list("piper/${voice.subDir}") ?: emptyArray()
            if (assetList.contains(voice.modelFileName) && assetList.contains(voice.tokensFileName)) {
                PiperModelPaths(
                    modelPath = "piper/${voice.subDir}/${voice.modelFileName}",
                    tokensPath = "piper/${voice.subDir}/${voice.tokensFileName}",
                    dataDir = "piper/${voice.subDir}/$ESPEAK_DIR_NAME",
                    isAsset = true
                )
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun getModelPaths(context: Context): PiperModelPaths? {
        val installed = getInstalledVoices(context)
        val activeVoice = installed.firstOrNull { it.id == DEFAULT_VOICE.id } ?: installed.firstOrNull() ?: DEFAULT_VOICE
        return getVoicePaths(context, activeVoice.id)
    }

    fun startDownload(
        context: Context,
        scope: CoroutineScope,
        voice: PiperVoiceModel = DEFAULT_VOICE
    ) {
        val currentStatus = _downloadStatuses.value[voice.id]
        if (currentStatus is PiperDownloadStatus.Downloading ||
            currentStatus is PiperDownloadStatus.Extracting
        ) {
            return
        }

        downloadJobs[voice.id]?.cancel()
        val job = scope.launch(Dispatchers.IO) {
            val piperBaseDir = getPiperBaseDir(context)
            if (!piperBaseDir.exists()) {
                piperBaseDir.mkdirs()
            }

            val tempArchive = File(piperBaseDir, "${voice.subDir}_download.tar.bz2")
            var connection: HttpURLConnection? = null

            try {
                updateStatus(voice.id, PiperDownloadStatus.Downloading(0.0f, 0L, voice.approxBytes))

                val url = URL(voice.downloadUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.connect()

                val totalBytes = connection.contentLengthLong.takeIf { it > 0 } ?: voice.approxBytes
                var downloadedBytes = 0L

                connection.inputStream.use { input ->
                    FileOutputStream(tempArchive).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var bytesRead: Int
                        var lastProgressUpdate = System.currentTimeMillis()

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdate > 100) {
                                val progress = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                updateStatus(
                                    voice.id,
                                    PiperDownloadStatus.Downloading(progress, downloadedBytes, totalBytes)
                                )
                                lastProgressUpdate = now
                            }
                        }
                    }
                }

                updateStatus(voice.id, PiperDownloadStatus.Extracting)

                // Unpack tar.bz2 directly into context.filesDir/piper/
                BufferedInputStream(tempArchive.inputStream()).use { bis ->
                    BZip2CompressorInputStream(bis).use { bzIn ->
                        TarArchiveInputStream(bzIn).use { tarIn ->
                            var entry = tarIn.nextEntry
                            while (entry != null) {
                                val targetFile = File(piperBaseDir, entry.name)

                                // Prevent Zip Slip path traversal vulnerability
                                if (!targetFile.canonicalPath.startsWith(piperBaseDir.canonicalPath)) {
                                    throw SecurityException("Invalid archive entry path: ${entry.name}")
                                }

                                if (entry.isDirectory) {
                                    targetFile.mkdirs()
                                } else {
                                    targetFile.parentFile?.mkdirs()
                                    FileOutputStream(targetFile).use { fos ->
                                        tarIn.copyTo(fos)
                                    }
                                }
                                entry = tarIn.nextEntry
                            }
                        }
                    }
                }

                // Clean up temporary download file
                tempArchive.delete()

                if (isVoiceInstalled(context, voice.id)) {
                    updateStatus(voice.id, PiperDownloadStatus.Ready)
                } else {
                    updateStatus(voice.id, PiperDownloadStatus.Error("Extraction completed but model files missing"))
                }

            } catch (e: Exception) {
                tempArchive.delete()
                updateStatus(voice.id, PiperDownloadStatus.Error(e.localizedMessage ?: "Download failed"))
            } finally {
                connection?.disconnect()
            }
        }
        downloadJobs[voice.id] = job
    }

    fun cancelDownload(voiceId: String? = null) {
        if (voiceId != null) {
            downloadJobs[voiceId]?.cancel()
            downloadJobs.remove(voiceId)
            updateStatus(voiceId, PiperDownloadStatus.Idle)
        } else {
            downloadJobs.values.forEach { it.cancel() }
            downloadJobs.clear()
            _downloadStatuses.value = emptyMap()
            _legacyDownloadStatus.value = PiperDownloadStatus.Idle
        }
    }

    fun deleteVoice(context: Context, voiceId: String): Boolean {
        val voice = getVoiceById(voiceId) ?: return false
        val modelDir = File(getPiperBaseDir(context), voice.subDir)
        return try {
            if (modelDir.exists()) {
                modelDir.deleteRecursively()
            }
            updateStatus(voiceId, PiperDownloadStatus.Idle)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun updateStatus(voiceId: String, status: PiperDownloadStatus) {
        val current = _downloadStatuses.value.toMutableMap()
        current[voiceId] = status
        _downloadStatuses.value = current

        if (voiceId == DEFAULT_VOICE.id || _legacyDownloadStatus.value !is PiperDownloadStatus.Ready) {
            _legacyDownloadStatus.value = status
        }
    }
}
