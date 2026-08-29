package com.hrshd1eux.imava.core.util

import android.net.Uri
import com.hrshd1eux.imava.data.model.MediaItem
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class StorageDoctorUtilsTest {

    private val mockUri = mockk<Uri>(relaxed = true)

    private fun createPhoto(id: Long, size: Long, dateTaken: Long, bucketName: String = "Camera"): MediaItem.Photo {
        return MediaItem.Photo(
            id = id,
            uri = mockUri,
            path = "/storage/emulated/0/DCIM/Camera/IMG_$id.jpg",
            mimeType = "image/jpeg",
            dateTaken = dateTaken,
            size = size,
            width = 1920,
            height = 1080,
            bucketId = 1L,
            bucketName = bucketName
        )
    }

    private fun createVideo(id: Long, size: Long, dateTaken: Long): MediaItem.Video {
        return MediaItem.Video(
            id = id,
            uri = mockUri,
            path = "/storage/emulated/0/DCIM/Camera/VID_$id.mp4",
            mimeType = "video/mp4",
            dateTaken = dateTaken,
            size = size,
            width = 1920,
            height = 1080,
            durationMs = 30000L,
            bucketId = 1L,
            bucketName = "Camera"
        )
    }

    @Test
    fun testAnalyzeStorage_identifiesLargeVideos() = runBlocking {
        val smallVideo = createVideo(1L, 20L * 1024 * 1024, System.currentTimeMillis())
        val largeVideo = createVideo(2L, 60L * 1024 * 1024, System.currentTimeMillis())

        val report = StorageDoctorUtils.analyzeStorage(listOf(smallVideo, largeVideo))
        assertEquals(1, report.largeVideos.size)
        assertEquals(2L, report.largeVideos.first().id)
    }

    @Test
    fun testAnalyzeStorage_identifiesStaleScreenshots() = runBlocking {
        val now = System.currentTimeMillis()
        val oldDate = now - (40L * 24 * 60 * 60 * 1000)
        val recentDate = now - (5L * 24 * 60 * 60 * 1000)

        val oldScreenshot = createPhoto(10L, 2000L, oldDate, bucketName = "Screenshots")
        val recentScreenshot = createPhoto(11L, 2000L, recentDate, bucketName = "Screenshots")

        val report = StorageDoctorUtils.analyzeStorage(listOf(oldScreenshot, recentScreenshot))
        assertEquals(1, report.staleScreenshots.size)
        assertEquals(10L, report.staleScreenshots.first().id)
    }

    @Test
    fun testAnalyzeStorage_identifiesBurstGroups() = runBlocking {
        val baseTime = 1000000L
        val burst1 = createPhoto(20L, 2000L, baseTime)
        val burst2 = createPhoto(21L, 2000L, baseTime + 1000L)
        val burst3 = createPhoto(22L, 2000L, baseTime + 2000L)

        val report = StorageDoctorUtils.analyzeStorage(listOf(burst1, burst2, burst3))
        assertEquals(1, report.burstGroups.size)
        assertEquals(3, report.burstGroups.first().burstItems.size)
    }
}
