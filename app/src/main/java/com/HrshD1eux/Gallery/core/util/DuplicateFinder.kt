package com.HrshD1eux.Gallery.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.HrshD1eux.Gallery.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DuplicateGroup(
    val id: String,
    val items: List<MediaItem>,
    val bestItem: MediaItem
)

private data class PhotoSignature(
    val item: MediaItem.Photo,
    val dHash: Long,
    val aHash: Long
)

object DuplicateFinder {

    /**
     * Scans list of media items and groups duplicates based on dual perceptual hashing (dHash + aHash)
     * and exact file attribute matching.
     */
    suspend fun findDuplicates(
        context: Context,
        items: List<MediaItem>
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val photos = items.filterIsInstance<MediaItem.Photo>()
        if (photos.size < 2) return@withContext emptyList()

        val signatures = mutableListOf<PhotoSignature>()
        val resolver = context.contentResolver

        photos.forEach { item ->
            try {
                resolver.openInputStream(item.uri)?.use { input ->
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 4 // Downsample for rapid, memory-safe hashing
                    }
                    val bitmap = BitmapFactory.decodeStream(input, null, options)
                    if (bitmap != null) {
                        val dHash = computeDHash(bitmap)
                        val aHash = computeAHash(bitmap)
                        signatures.add(PhotoSignature(item, dHash, aHash))
                        bitmap.recycle()
                    }
                }
            } catch (_: Exception) {}
        }

        val visited = mutableSetOf<Long>()
        val resultGroups = mutableListOf<DuplicateGroup>()

        for (i in signatures.indices) {
            val sigA = signatures[i]
            if (visited.contains(sigA.item.id)) continue

            val cluster = mutableListOf<MediaItem.Photo>()
            cluster.add(sigA.item)

            for (j in i + 1 until signatures.size) {
                val sigB = signatures[j]
                if (visited.contains(sigB.item.id)) continue

                if (areDuplicates(sigA, sigB)) {
                    cluster.add(sigB.item)
                    visited.add(sigB.item.id)
                }
            }

            if (cluster.size > 1) {
                visited.add(sigA.item.id)
                // Pick the item with largest resolution and file size as the best item to keep
                val best = cluster.maxWithOrNull(
                    compareBy<MediaItem.Photo> { it.width * it.height }
                        .thenBy { it.size }
                        .thenByDescending { it.dateTaken }
                ) ?: sigA.item

                resultGroups.add(
                    DuplicateGroup(
                        id = "group_${sigA.item.id}",
                        items = cluster,
                        bestItem = best
                    )
                )
            }
        }

        resultGroups
    }

    private fun areDuplicates(a: PhotoSignature, b: PhotoSignature): Boolean {
        // 1. Exact file match check
        if (a.item.size > 0 && a.item.size == b.item.size && a.item.width == b.item.width && a.item.height == b.item.height) {
            return true
        }

        // 2. Perceptual hash comparison
        val dDist = java.lang.Long.bitCount(a.dHash xor b.dHash)
        val aDist = java.lang.Long.bitCount(a.aHash xor b.aHash)

        // Threshold of <= 10 bits out of 64 bits detects identical screenshots, bursts, and re-saved images
        return dDist <= 10 || (dDist <= 12 && aDist <= 8)
    }

    /**
     * Computes 64-bit dHash (difference hash) by resizing bitmap to 9x8 and tracking horizontal gradients.
     */
    fun computeDHash(src: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(src, 9, 8, true)
        var hash = 0L
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = getGrayscalePixel(scaled.getPixel(x, y))
                val right = getGrayscalePixel(scaled.getPixel(x + 1, y))
                if (left > right) {
                    hash = hash or (1L shl (y * 8 + x))
                }
            }
        }
        if (scaled != src) {
            scaled.recycle()
        }
        return hash
    }

    /**
     * Computes 64-bit aHash (average hash) by resizing bitmap to 8x8 and comparing against mean luminance.
     */
    fun computeAHash(src: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(src, 8, 8, true)
        val grays = IntArray(64)
        var sum = 0L
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val gray = getGrayscalePixel(scaled.getPixel(x, y))
                grays[y * 8 + x] = gray
                sum += gray
            }
        }
        val avg = (sum / 64).toInt()
        var hash = 0L
        for (i in 0 until 64) {
            if (grays[i] >= avg) {
                hash = hash or (1L shl i)
            }
        }
        if (scaled != src) {
            scaled.recycle()
        }
        return hash
    }

    private fun getGrayscalePixel(pixel: Int): Int {
        val r = (pixel ushr 16) and 0xFF
        val g = (pixel ushr 8) and 0xFF
        val b = pixel and 0xFF
        return (r * 30 + g * 59 + b * 11) / 100
    }
}

