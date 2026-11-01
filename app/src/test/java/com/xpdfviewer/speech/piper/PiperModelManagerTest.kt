package com.xpdfviewer.speech.piper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PiperModelManagerTest {

    @Test
    fun testVoiceConstantsAndDefaults() {
        assertEquals("piper_studio_en_us_lessac", PiperModelManager.PIPER_VOICE_ID)
        assertEquals("vits-piper-en_US-lessac-medium", PiperModelManager.PIPER_MODEL_SUBDIR)
        assertEquals("en_US-lessac-medium.onnx", PiperModelManager.MODEL_FILE_NAME)
        assertEquals("tokens.txt", PiperModelManager.TOKENS_FILE_NAME)
        assertEquals("espeak-ng-data", PiperModelManager.ESPEAK_DIR_NAME)
    }

    @Test
    fun testMultiVoiceCatalog() {
        val voices = PiperModelManager.ALL_STUDIO_VOICES
        assertEquals(4, voices.size)

        val lessac = PiperModelManager.getVoiceById("piper_studio_en_us_lessac")
        assertNotNull(lessac)
        assertEquals("en-us", lessac?.langCode)
        assertEquals("en_US-lessac-medium.onnx", lessac?.modelFileName)

        val alan = PiperModelManager.getVoiceById("piper_studio_en_gb_alan")
        assertNotNull(alan)
        assertEquals("en-gb", alan?.langCode)
        assertEquals("en-gb-x-rp", alan?.espeakVoice)
        assertEquals("British English", alan?.accentTag)
        assertEquals("en_GB-alan-medium.onnx", alan?.modelFileName)

        val amy = PiperModelManager.getVoiceById("piper_studio_en_us_amy")
        assertNotNull(amy)
        assertEquals("en-us", amy?.langCode)
        assertEquals("Amy Studio", amy?.name)

        val ryan = PiperModelManager.getVoiceById("piper_studio_en_us_ryan")
        assertNotNull(ryan)
        assertEquals("en-us", ryan?.langCode)
        assertEquals("Ryan Studio", ryan?.name)

        assertTrue(PiperModelManager.isStudioVoice("piper_studio_en_us_lessac"))
        assertTrue(PiperModelManager.isStudioVoice("piper_studio_en_gb_alan"))
        assertFalse(PiperModelManager.isStudioVoice("system_oem_voice_id"))
        assertFalse(PiperModelManager.isStudioVoice(null))
        assertNull(PiperModelManager.getVoiceById("invalid_voice"))
    }

    @Test
    fun testDownloadStatusTransitions() {
        var status: PiperDownloadStatus = PiperDownloadStatus.Idle
        assertEquals(PiperDownloadStatus.Idle, status)

        status = PiperDownloadStatus.Downloading(0.5f, 33_000_000L, 67_000_000L)
        assertTrue(status is PiperDownloadStatus.Downloading)
        assertEquals(0.5f, (status as PiperDownloadStatus.Downloading).progress, 0.001f)

        status = PiperDownloadStatus.Extracting
        assertEquals(PiperDownloadStatus.Extracting, status)

        status = PiperDownloadStatus.Ready
        assertEquals(PiperDownloadStatus.Ready, status)

        status = PiperDownloadStatus.Error("Network failure")
        assertTrue(status is PiperDownloadStatus.Error)
        assertEquals("Network failure", (status as PiperDownloadStatus.Error).message)
    }

    @Test
    fun testZipSlipProtectionLogic() {
        val baseDir = File("/test/storage/piper")
        val safeEntry = "vits-piper-en_US-lessac-medium/tokens.txt"
        val maliciousEntry = "../../system/bin/sh"

        val targetSafe = File(baseDir, safeEntry)
        val targetMalicious = File(baseDir, maliciousEntry)

        assertTrue(targetSafe.canonicalPath.startsWith(baseDir.canonicalPath))
        assertFalse(targetMalicious.canonicalPath.startsWith(baseDir.canonicalPath))
    }
}
