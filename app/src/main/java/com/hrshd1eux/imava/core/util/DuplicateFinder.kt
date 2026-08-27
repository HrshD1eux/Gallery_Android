package com.hrshd1eux.imava.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hrshd1eux.imava.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
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

    suspend fun findDuplicates(
        context: Context,
        items: List<MediaItem>,
        onProgress: ((scanned: Int, total: Int) -> Unit)? = null
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        if (items.size < 2) return@withContext emptyList()

        val resultGroups = mutableListOf<DuplicateGroup>()
        val groupedIds = mutableSetOf<Long>()

        val exactBuckets = items.groupBy { "${it.size}_${it.width}_${it.height}_${if (it is MediaItem.Video) it.durationMs else 0}" }
        for ((_, bucket) in exactBuckets) {
            if (bucket.size > 1 && bucket.first().size > 0L) {
                val best = bucket.maxWithOrNull(
                    compareBy<MediaItem> { it.width * it.height }
                        .thenBy { it.size }
                        .thenByDescending { it.dateTaken }
                ) ?: bucket.first()

                resultGroups.add(
                    DuplicateGroup(
                        id = "exact_${bucket.first().id}",
                        items = bucket,
                        bestItem = best
                    )
                )
                bucket.forEach { groupedIds.add(it.id) }
            }
        }

        val remainingPhotos = items.filterIsInstance<MediaItem.Photo>()
            .filter { !groupedIds.contains(it.id) }

        if (remainingPhotos.size < 2) {
            return@withContext resultGroups
        }

        val totalPhotos = remainingPhotos.size
        var scannedCount = 0

        val semaphore = Semaphore(4)
        val signatures = mutableListOf<PhotoSignature>()
        val resolver = context.contentResolver

        for (item in remainingPhotos) {
            semaphore.withPermit {
                try {
                    val bitmap = decodeMicroThumbnail(context, item)
                    if (bitmap != null) {
                        val dHash = computeDHash(bitmap)
                        val aHash = computeAHash(bitmap)
                        signatures.add(PhotoSignature(item, dHash, aHash))
                        bitmap.recycle()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    scannedCount++
                    onProgress?.invoke(scannedCount, totalPhotos)
                }
            }
        }

        val visitedSignatures = mutableSetOf<Long>()

        for (i in signatures.indices) {
            val sigA = signatures[i]
            if (visitedSignatures.contains(sigA.item.id)) continue

            val cluster = mutableListOf<MediaItem.Photo>()
            cluster.add(sigA.item)

            for (j in i + 1 until signatures.size) {
                val sigB = signatures[j]
                if (visitedSignatures.contains(sigB.item.id)) continue

                if (arePerceptualDuplicates(sigA, sigB)) {
                    cluster.add(sigB.item)
                    visitedSignatures.add(sigB.item.id)
                }
            }

            if (cluster.size > 1) {
                visitedSignatures.add(sigA.item.id)
                val best = cluster.maxWithOrNull(
                    compareBy<MediaItem.Photo> { it.width * it.height }
                        .thenBy { it.size }
                        .thenByDescending { it.dateTaken }
                ) ?: sigA.item

                resultGroups.add(
                    DuplicateGroup(
                        id = "perceptual_${sigA.item.id}",
                        items = cluster,
                        bestItem = best
                    )
                )
            }
        }

        resultGroups
    }

    private fun decodeMicroThumbnail(context: Context, item: MediaItem.Photo): Bitmap? {
        val resolver = context.contentResolver
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565 // 16-bit to halve memory footprint
            inSampleSize = if (item.width > 0 && item.height > 0) {
                val maxDim = maxOf(item.width, item.height)
                if (maxDim > 64) maxDim / 32 else 1
            } else {
                8
            }
        }

        // try MediaStore
        try {
            resolver.openInputStream(item.uri)?.use { stream ->
                val bmp = BitmapFactory.decodeStream(stream, null, options)
                if (bmp != null) return bmp
            }
        } catch (_: Exception) {}

        // file fallback
        if (item.path.isNotBlank()) {
            try {
                val file = File(item.path)
                if (file.exists() && file.canRead()) {
                    return BitmapFactory.decodeFile(file.absolutePath, options)
                }
            } catch (_: Exception) {}
        }

        return null
    }

    private fun arePerceptualDuplicates(a: PhotoSignature, b: PhotoSignature): Boolean {
        val timeDiffMs = abs(a.item.dateTaken - b.item.dateTaken)
        val dDist = java.lang.Long.bitCount(a.dHash xor b.dHash)
        val aDist = java.lang.Long.bitCount(a.aHash xor b.aHash)

        if (timeDiffMs <= 5000L && (dDist <= 6 && aDist <= 6)) {
            return true
        }

        return dDist <= 4 || (dDist <= 6 && aDist <= 4)
    }

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
