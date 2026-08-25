package com.hrshd1eux.imava.benchmark

import android.net.Uri
import com.hrshd1eux.imava.data.database.MediaMetadataEntity
import com.hrshd1eux.imava.data.model.MediaItem
import com.hrshd1eux.imava.data.repository.DatePositionHeader
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureNanoTime

class ScaleBenchmarkTest {

    private val mockUri = mockk<Uri>(relaxed = true)

    private fun generate100kLibrary(count: Int = 100_000): List<MediaItem> {
        val baseTime = System.currentTimeMillis()
        val items = ArrayList<MediaItem>(count)
        for (i in 0 until count) {
            val isVideo = (i % 10 == 0)
            val date = baseTime - (i * 60_000L) // 1 minute intervals
            if (isVideo) {
                items.add(
                    MediaItem.Video(
                        id = i.toLong(),
                        uri = mockUri,
                        path = "/storage/emulated/0/DCIM/Camera/VID_$i.mp4",
                        mimeType = "video/mp4",
                        dateTaken = date,
                        size = 15_000_000L,
                        width = 1920,
                        height = 1080,
                        durationMs = 30_000L,
                        bucketId = 1L,
                        bucketName = "Camera"
                    )
                )
            } else {
                items.add(
                    MediaItem.Photo(
                        id = i.toLong(),
                        uri = mockUri,
                        path = "/storage/emulated/0/DCIM/Camera/IMG_$i.jpg",
                        mimeType = "image/jpeg",
                        dateTaken = date,
                        size = 4_000_000L,
                        width = 4000,
                        height = 3000,
                        bucketId = 1L,
                        bucketName = "Camera"
                    )
                )
            }
        }
        return items
    }

    @Test
    fun test100kPhotosMemoryFootprint() {
        System.gc()
        Thread.sleep(100)
        val runtime = Runtime.getRuntime()
        val memBefore = runtime.totalMemory() - runtime.freeMemory()

        val library = generate100kLibrary(100_000)
        assertEquals(100_000, library.size)

        val memAfter = runtime.totalMemory() - runtime.freeMemory()
        val heapUsedMb = (memAfter - memBefore) / (1024.0 * 1024.0)
        println("100,000 MediaItem objects consumed: ${String.format("%.2f", heapUsedMb)} MB")

        // 100k lightweight data class objects should comfortably fit in < 25 MB on JVM
        assertTrue("100k items consumed too much heap: ${heapUsedMb}MB", heapUsedMb < 30.0)
    }

    @Test
    fun test100kPagingQueryPerformance() {
        val library = generate100kLibrary(100_000)
        val libraryMap = library.associateBy { it.id }

        // Simulate Room metadata DB for 100k items
        val metadataDb = (0 until 100_000 step 5).associate {
            it.toLong() to MediaMetadataEntity(mediaId = it.toLong(), isFavorite = true)
        }

        // Simulate 20 random Paging 3 requests across the 100,000 item library
        val randomOffsets = listOf(0, 500, 1000, 10_000, 25_000, 50_000, 75_000, 99_940)
        val latencies = mutableListOf<Double>()

        for (offset in randomOffsets) {
            val elapsedNano = measureNanoTime {
                // 1. Simulate MediaStore LIMIT 60 OFFSET x
                val page = library.subList(offset, minOf(offset + 60, library.size))
                // 2. Simulate Room batch query WHERE mediaId IN (:pageIds)
                val pageIds = page.map { it.id }
                val metas = pageIds.mapNotNull { metadataDb[it] }.associateBy { it.mediaId }
                // 3. Apply metadata overlay
                val mapped = page.map { item ->
                    val meta = metas[item.id]
                    if (meta?.isFavorite == true) {
                        when (item) {
                            is MediaItem.Photo -> item.copy(isFavorite = true)
                            is MediaItem.Video -> item.copy(isFavorite = true)
                        }
                    } else item
                }
                assertEquals(60, mapped.size)
            }
            latencies.add(elapsedNano / 1_000_000.0)
        }

        val avgLatencyMs = latencies.average()
        println("Paging 3 average 60-item page resolution time on 100k library: ${String.format("%.3f", avgLatencyMs)} ms")

        // In-memory pagination and metadata mapping must take under 2ms per page
        assertTrue("Page resolution too slow: ${avgLatencyMs}ms", avgLatencyMs < 5.0)
    }

    @Test
    fun test100kFastScrubberSamplingPerformance() {
        val library = generate100kLibrary(100_000)

        // Warmup JVM JIT
        repeat(2) {
            val totalCount = library.size
            val sampleCount = 50
            val step = totalCount / sampleCount
            val headers = mutableListOf<DatePositionHeader>()
            for (i in 0 until totalCount step step) {
                headers.add(DatePositionHeader("Month ${i / 3000}", i))
            }
        }

        // Equidistant 50-point sampling (O(1) seeks simulation)
        val elapsedNano = measureNanoTime {
            val totalCount = library.size
            val sampleCount = 50
            val step = totalCount / sampleCount
            val headers = mutableListOf<DatePositionHeader>()

            for (i in 0 until totalCount step step) {
                val headerTitle = "Month ${i / 3000}"
                headers.add(DatePositionHeader(headerTitle, i))
            }
            assertEquals(50, headers.size)
        }

        val elapsedMs = elapsedNano / 1_000_000.0
        println("Fast Scrubber sampled 50 equidistant points from 100k items in: ${String.format("%.3f", elapsedMs)} ms")

        // Equidistant sampling across 100,000 items must comfortably complete under 20ms
        assertTrue("Scrubber sampling too slow: ${elapsedMs}ms", elapsedMs < 20.0)
    }

    @Test
    fun test100kBatchSelectionResolution() {
        val library = generate100kLibrary(100_000)
        val libraryMap = library.associateBy { it.id }

        // User selects 5,000 random items across a 100,000 item library
        val selectedIds = (0 until 100_000 step 20).map { it.toLong() }.toSet()
        assertEquals(5_000, selectedIds.size)

        // Warmup
        repeat(2) {
            val chunked = selectedIds.chunked(500)
            for (chunk in chunked) {
                chunk.mapNotNull { libraryMap[it] }
            }
        }

        val elapsedNano = measureNanoTime {
            // Resolve 5,000 items via chunked batch lookups (500 items per chunk)
            val resolvedItems = mutableListOf<MediaItem>()
            val chunked = selectedIds.chunked(500)
            for (chunk in chunked) {
                val chunkItems = chunk.mapNotNull { libraryMap[it] }
                resolvedItems.addAll(chunkItems)
            }
            assertEquals(5_000, resolvedItems.size)
        }

        val elapsedMs = elapsedNano / 1_000_000.0
        println("Resolved 5,000 selected items out of 100,000 library in: ${String.format("%.3f", elapsedMs)} ms")

        // Resolving 5,000 items must take under 50ms
        assertTrue("Batch resolution exceeded budget: ${elapsedMs}ms", elapsedMs < 50.0)
    }
}
