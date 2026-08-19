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

object DuplicateFinder {

    /**
     * Scans list of media items and groups duplicates based on 64-bit dHash perceptual hashing.
     */
    suspend fun findDuplicates(
        context: Context,
        items: List<MediaItem>
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val photos = items.filterIsInstance<MediaItem.Photo>()
        if (photos.size < 2) return@withContext emptyList()

        val hashedItems = mutableListOf<Pair<MediaItem, Long>>()
        val resolver = context.contentResolver

        photos.forEach { item ->
            try {
                resolver.openInputStream(item.uri)?.use { input ->
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 4 // Downsample for fast hashing
                    }
                    val original = BitmapFactory.decodeStream(input, null, options)
                    if (original != null) {
                        val hash = computeDHash(original)
                        hashedItems.add(Pair(item, hash))
                        original.recycle()
                    }
                }
            } catch (_: Exception) {}
        }

        val visited = mutableSetOf<Long>()
        val resultGroups = mutableListOf<DuplicateGroup>()

        for (i in hashedItems.indices) {
            val (itemA, hashA) = hashedItems[i]
            if (visited.contains(itemA.id)) continue

            val cluster = mutableListOf<MediaItem>()
            cluster.add(itemA)

            for (j in i + 1 until hashedItems.size) {
                val (itemB, hashB) = hashedItems[j]
                if (visited.contains(itemB.id)) continue

                val distance = java.lang.Long.bitCount(hashA xor hashB)
                if (distance <= 3) {
                    cluster.add(itemB)
                    visited.add(itemB.id)
                }
            }

            if (cluster.size > 1) {
                visited.add(itemA.id)
                // Pick the item with largest resolution as best
                val best = cluster.maxByOrNull { it.width * it.height } ?: itemA
                resultGroups.add(
                    DuplicateGroup(
                        id = "group_${itemA.id}",
                        items = cluster,
                        bestItem = best
                    )
                )
            }
        }

        resultGroups
    }

    /**
     * Computes 64-bit dHash by resizing bitmap to 9x8 grayscale and comparing adjacent pixels.
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
        scaled.recycle()
        return hash
    }

    private fun getGrayscalePixel(pixel: Int): Int {
        val r = (pixel ushr 16) and 0xFF
        val g = (pixel ushr 8) and 0xFF
        val b = pixel and 0xFF
        return (r * 30 + g * 59 + b * 11) / 100
    }
}
