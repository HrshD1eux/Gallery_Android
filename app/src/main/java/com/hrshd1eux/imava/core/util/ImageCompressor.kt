package com.hrshd1eux.imava.core.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import com.hrshd1eux.imava.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object ImageCompressor {

    suspend fun compressToTargetKb(
        context: Context,
        item: MediaItem,
        targetKb: Int
    ): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val originalBitmap = try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(item.uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, boundsOptions)
            }
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return@withContext null

            // safe inSampleSize for large images
            val maxDim = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            var sampleSize = 1
            while (maxDim / sampleSize > 4096) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            resolver.openInputStream(item.uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            }
        } catch (_: Exception) { null } ?: return@withContext null

        val targetSizeBytes = targetKb * 1024L
        var scale = 1.0f
        var bestBytes: ByteArray? = null

        // Try scaling down if original image is very large
        while (scale >= 0.1f) {
            val width = (originalBitmap.width * scale).toInt().coerceAtLeast(1)
            val height = (originalBitmap.height * scale).toInt().coerceAtLeast(1)
            val scaled = if (scale == 1.0f) originalBitmap else Bitmap.createScaledBitmap(originalBitmap, width, height, true)

            var lowQuality = 1
            var highQuality = 100
            var candidate: ByteArray? = null

            while (lowQuality <= highQuality) {
                val midQuality = (lowQuality + highQuality) / 2
                val stream = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, midQuality, stream)
                val bytes = stream.toByteArray()

                if (bytes.size <= targetSizeBytes) {
                    candidate = bytes
                    lowQuality = midQuality + 1 // try higher quality if still under limit
                } else {
                    highQuality = midQuality - 1
                }
            }

            if (scaled != originalBitmap) {
                scaled.recycle()
            }

            if (candidate != null) {
                bestBytes = candidate
                break
            } else {
                scale -= 0.2f // scale down resolution and try again
            }
        }

        originalBitmap.recycle()

        if (bestBytes == null) return@withContext null

        // Insert compressed image into MediaStore
        val displayName = "compressed_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Compressed")
        }

        val targetUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (targetUri != null) {
            resolver.openOutputStream(targetUri)?.use { output ->
                output.write(bestBytes)
            }
        }

        targetUri
    }
}
