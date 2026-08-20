package com.HrshD1eux.Gallery.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.HrshD1eux.Gallery.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

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
                var bitmap: Bitmap? = null

                // 1. Try decoding from MediaStore content URI
                try {
                    resolver.openInputStream(item.uri)?.use { input ->
                        val options = BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                            inSampleSize = if (item.width > 0 && item.height > 0) {
                                val maxDim = maxOf(item.width, item.height)
                                if (maxDim > 128) maxDim / 64 else 1
                            } else {
                                4
                            }
                        }
                        bitmap = BitmapFactory.decodeStream(input, null, options)
                    }
                } catch (_: Exception) {}

                // 2. Fallback to direct file path if stream decode was null
                if (bitmap == null && item.path.isNotBlank()) {
                    try {
                        val file = java.io.File(item.path)
                        if (file.exists() && file.canRead()) {
                            val options = BitmapFactory.Options().apply {
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                                inSampleSize = 4
                            }
                            bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
                        }
                    } catch (_: Exception) {}
                }

                if (bitmap != null) {
                    val rawBmp = bitmap!!
                    val softwareBitmap = if (rawBmp.config == Bitmap.Config.HARDWARE) {
                        rawBmp.copy(Bitmap.Config.ARGB_8888, false) ?: rawBmp
                    } else {
                        rawBmp
                    }
                    val dHash = computeDHash(softwareBitmap)
                    val aHash = computeAHash(softwareBitmap)
                    signatures.add(PhotoSignature(item, dHash, aHash))
                    if (softwareBitmap != rawBmp) {
                        softwareBitmap.recycle()
                    }
                    rawBmp.recycle()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

        // 2. Near-exact attribute match (e.g. re-saved screenshot)
        if (a.item.width > 0 && a.item.width == b.item.width && a.item.height == b.item.height && abs(a.item.size - b.item.size) < 1024) {
            val dDist = java.lang.Long.bitCount(a.dHash xor b.dHash)
            if (dDist <= 4) return true
        }

        // 3. Perceptual hash comparison
        val dDist = java.lang.Long.bitCount(a.dHash xor b.dHash)
        val aDist = java.lang.Long.bitCount(a.aHash xor b.aHash)

        // Threshold of <= 10 bits out of 64 bits detects identical screenshots, bursts, and re-saved images
        return dDist <= 10 || aDist <= 6 || (dDist <= 14 && aDist <= 10)
    }

    /**
     * Computes 64-bit dHash (difference hash) by resizing bitmap to 9x8 and tracking horizontal gradients.
     */
    fun computeDHash(src: Bitmap): Long {
        val softwareSrc = if (src.config == Bitmap.Config.HARDWARE) {
            src.copy(Bitmap.Config.ARGB_8888, false) ?: src
        } else {
            src
        }
        val scaled = Bitmap.createScaledBitmap(softwareSrc, 9, 8, false)
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
        if (scaled != softwareSrc && scaled != src) {
            scaled.recycle()
        }
        if (softwareSrc != src) {
            softwareSrc.recycle()
        }
        return hash
    }

    /**
     * Computes 64-bit aHash (average hash) by resizing bitmap to 8x8 and comparing against mean luminance.
     */
    fun computeAHash(src: Bitmap): Long {
        val softwareSrc = if (src.config == Bitmap.Config.HARDWARE) {
            src.copy(Bitmap.Config.ARGB_8888, false) ?: src
        } else {
            src
        }
        val scaled = Bitmap.createScaledBitmap(softwareSrc, 8, 8, false)
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
        if (scaled != softwareSrc && scaled != src) {
            scaled.recycle()
        }
        if (softwareSrc != src) {
            softwareSrc.recycle()
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
