package com.xpdfviewer.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OcrScannerEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeTextFromUri(context: Context, imageUri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val inputImage = InputImage.fromFilePath(context, imageUri)
                val text = processInputImage(inputImage)
                Result.success(text.text.trim())
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }

    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val text = processInputImage(inputImage)
                Result.success(text.text.trim())
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }

    private suspend fun processInputImage(inputImage: InputImage): Text =
        suspendCancellableCoroutine { continuation ->
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText)
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        }
}
