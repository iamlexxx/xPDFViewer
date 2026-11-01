package com.xpdfviewer.pdf

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.xpdfviewer.data.SecurityProtectionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfSecurityManager {

    suspend fun checkEncryptionStatus(
        context: Context,
        uri: Uri
    ): Pair<Boolean, Int> = withContext(Dispatchers.IO) {
        PdfTextExtractor.initialize(context)
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                PDDocument.load(stream).use { doc ->
                    return@withContext Pair(doc.isEncrypted, doc.numberOfPages)
                }
            }
        } catch (e: InvalidPasswordException) {
            return@withContext Pair(true, 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Pair(false, 0)
    }

    suspend fun protectPdf(
        context: Context,
        sourceUri: Uri,
        destinationUri: Uri,
        config: SecurityProtectionConfig
    ): Boolean = withContext(Dispatchers.IO) {
        PdfTextExtractor.initialize(context)
        val tempFile = File(context.cacheDir, "xpdf_protect_${System.currentTimeMillis()}.pdf")
        var doc: PDDocument? = null
        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return@withContext false
            doc = PDDocument.load(inputStream)

            if (doc.isEncrypted) {
                runCatching { doc.setAllSecurityToBeRemoved(true) }
            }

            val accessPermission = AccessPermission().apply {
                setCanPrint(config.permissions.allowPrinting)
                setCanExtractContent(config.permissions.allowContentCopying)
                setCanModifyAnnotations(config.permissions.allowAnnotations)
            }

            val ownerPwd = if (config.ownerPassword.isNotBlank()) config.ownerPassword else config.userPassword
            val policy = StandardProtectionPolicy(ownerPwd, config.userPassword, accessPermission).apply {
                encryptionKeyLength = config.keyLength
            }
            doc.protect(policy)

            FileOutputStream(tempFile).use { out ->
                doc.save(out)
            }

            context.contentResolver.openOutputStream(destinationUri, "wt")?.use { out ->
                tempFile.inputStream().use { it.copyTo(out) }
            } ?: return@withContext false

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            runCatching { doc?.close() }
            if (tempFile.exists()) tempFile.delete()
        }
    }

    suspend fun unlockPdf(
        context: Context,
        sourceUri: Uri,
        destinationUri: Uri,
        password: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        PdfTextExtractor.initialize(context)
        val tempFile = File(context.cacheDir, "xpdf_unlock_${System.currentTimeMillis()}.pdf")
        var doc: PDDocument? = null
        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(IllegalArgumentException("Cannot open document"))

            doc = try {
                PDDocument.load(inputStream, password)
            } catch (e: InvalidPasswordException) {
                return@withContext Result.failure(e)
            }

            if (doc.isEncrypted) {
                doc.setAllSecurityToBeRemoved(true)
            }

            FileOutputStream(tempFile).use { out ->
                doc.save(out)
            }

            context.contentResolver.openOutputStream(destinationUri, "wt")?.use { out ->
                tempFile.inputStream().use { it.copyTo(out) }
            } ?: return@withContext Result.failure(IllegalStateException("Cannot write output"))

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { doc?.close() }
            if (tempFile.exists()) tempFile.delete()
        }
    }

    fun queryFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    cursor.getLong(sizeIndex)
                } else {
                    0L
                }
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun resolveFileName(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else {
                    null
                }
            } ?: uri.lastPathSegment ?: "document.pdf"
        } catch (e: Exception) {
            "document.pdf"
        }
    }
}
