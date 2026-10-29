package com.xpdfviewer.signature

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object SignatureStorageManager {

    private const val SIGNATURE_DIR = "saved_signatures"

    suspend fun saveSignature(context: Context, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, SIGNATURE_DIR)
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, "sig_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun loadSavedSignatures(context: Context): List<Pair<File, Bitmap>> = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, SIGNATURE_DIR)
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()

        val files = dir.listFiles { file -> file.extension.lowercase() == "png" }
            ?.sortedByDescending { it.lastModified() }
            ?: return@withContext emptyList()

        files.mapNotNull { file ->
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) Pair(file, bitmap) else null
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun deleteSignature(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            file.delete()
        } catch (e: Exception) {
            false
        }
    }
}
