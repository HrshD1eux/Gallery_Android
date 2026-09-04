package com.hrshd1eux.imava.core.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class OcrBlock(
    val text: String,
    val boundingBox: android.graphics.Rect? = null
)

data class OcrResult(
    val fullText: String,
    val blocks: List<OcrBlock>
)

object OcrHelper {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeTextFromUri(context: Context, uri: Uri): OcrResult? = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromFilePath(context, uri)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val blocks = visionText.textBlocks.map { block ->
                            OcrBlock(text = block.text, boundingBox = block.boundingBox)
                        }
                        continuation.resume(OcrResult(fullText = visionText.text, blocks = blocks))
                    }
                    .addOnFailureListener { e ->
                        continuation.resume(null)
                    }
            } catch (e: Exception) {
                continuation.resume(null)
            }
        }
    }

    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): OcrResult? = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val blocks = visionText.textBlocks.map { block ->
                            OcrBlock(text = block.text, boundingBox = block.boundingBox)
                        }
                        continuation.resume(OcrResult(fullText = visionText.text, blocks = blocks))
                    }
                    .addOnFailureListener {
                        continuation.resume(null)
                    }
            } catch (e: Exception) {
                continuation.resume(null)
            }
        }
    }
}
