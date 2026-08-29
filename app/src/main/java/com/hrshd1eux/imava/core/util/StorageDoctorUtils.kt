package com.hrshd1eux.imava.core.util

import com.hrshd1eux.imava.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

object StorageDoctorUtils {

    data class BurstGroup(
        val representative: MediaItem,
        val burstItems: List<MediaItem>,
        val totalSizeBytes: Long
    )

    data class StorageReport(
        val largeVideos: List<MediaItem.Video>,
        val staleScreenshots: List<MediaItem>,
        val burstGroups: List<BurstGroup>,
        val totalPotentialReclaimBytes: Long
    )

    suspend fun analyzeStorage(
        allItems: List<MediaItem>
    ): StorageReport = withContext(Dispatchers.Default) {
        val nonTrashed = allItems.filter { !it.isTrashed }

        // Large Videos (> 50 MB)
        val largeVideos = nonTrashed.filterIsInstance<MediaItem.Video>()
            .filter { it.size > 50L * 1024 * 1024 }
            .sortedByDescending { it.size }

        // Stale Screenshots (> 30 days old)
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        val staleScreenshots = nonTrashed.filter { item ->
            val filename = item.path.substringAfterLast("/")
            val isScreenshotBucket = item.bucketName.contains("screenshot", ignoreCase = true) ||
                    filename.contains("screenshot", ignoreCase = true)
            isScreenshotBucket && item.dateTaken < thirtyDaysAgo
        }.sortedByDescending { it.dateTaken }

        // Rapid / Burst Photo Groups (Photos within 2.5 seconds of each other in same bucket)
        val photosSorted = nonTrashed.filterIsInstance<MediaItem.Photo>()
            .sortedWith(compareBy({ it.bucketName }, { it.dateTaken }))

        val burstGroups = mutableListOf<BurstGroup>()
        var currentGroup = mutableListOf<MediaItem.Photo>()

        for (i in photosSorted.indices) {
            val photo = photosSorted[i]
            if (currentGroup.isEmpty()) {
                currentGroup.add(photo)
            } else {
                val prev = currentGroup.last()
                val isSameBucket = prev.bucketName == photo.bucketName
                val isRapid = abs(photo.dateTaken - prev.dateTaken) <= 2500L

                if (isSameBucket && isRapid) {
                    currentGroup.add(photo)
                } else {
                    if (currentGroup.size >= 3) {
                        val totalSize = currentGroup.sumOf { it.size }
                        burstGroups.add(BurstGroup(currentGroup.first(), currentGroup.toList(), totalSize))
                    }
                    currentGroup.clear()
                    currentGroup.add(photo)
                }
            }
        }
        if (currentGroup.size >= 3) {
            val totalSize = currentGroup.sumOf { it.size }
            burstGroups.add(BurstGroup(currentGroup.first(), currentGroup.toList(), totalSize))
        }

        val largeVideosSize = largeVideos.sumOf { it.size }
        val staleScreenshotsSize = staleScreenshots.sumOf { it.size }
        val burstRedundantSize = burstGroups.sumOf { group ->
            group.burstItems.drop(1).sumOf { it.size }
        }

        val totalReclaim = largeVideosSize + staleScreenshotsSize + burstRedundantSize

        StorageReport(
            largeVideos = largeVideos,
            staleScreenshots = staleScreenshots,
            burstGroups = burstGroups,
            totalPotentialReclaimBytes = totalReclaim
        )
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.2f GB", gb)
    }
}
