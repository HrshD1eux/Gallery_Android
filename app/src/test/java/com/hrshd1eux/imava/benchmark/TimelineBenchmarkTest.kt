package com.hrshd1eux.imava.benchmark

import android.net.Uri
import com.hrshd1eux.imava.data.model.MediaItem
import com.hrshd1eux.imava.data.model.TimelineItem
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.system.measureNanoTime

class TimelineBenchmarkTest {

    @Test
    fun benchmark10kItemTimelineGroupingPerformance() {
        val mockUri = mockk<Uri>(relaxed = true)
        val startTime = System.currentTimeMillis()

        // Generate 10,000 synthetic media items spread across 30 days
        val items = (0 until 10_000).map { index ->
            MediaItem.Photo(
                id = index.toLong(),
                uri = mockUri,
                path = "/path/$index.jpg",
                mimeType = "image/jpeg",
                dateTaken = startTime - (index % 30) * 86_400_000L,
                size = 1000L,
                width = 1920,
                height = 1080,
                bucketId = 1L,
                bucketName = "Camera"
            )
        }

        val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val yesterday = today.minusDays(1)

        // Warmup JVM to prevent classloading/JIT spikes on 2-vCPU CI runners
        repeat(2) {
            items.groupBy { item ->
                val localDate = Instant.ofEpochMilli(item.dateTaken).atZone(zoneId).toLocalDate()
                when (localDate) {
                    today -> "Today"
                    yesterday -> "Yesterday"
                    else -> localDate.format(formatter)
                }
            }
        }

        // Measure timeline grouping performance (O(n) test)
        val elapsedNano = measureNanoTime {
            val result = mutableListOf<TimelineItem>()

            val grouped = items.groupBy { item ->
                val localDate = Instant.ofEpochMilli(item.dateTaken).atZone(zoneId).toLocalDate()
                when (localDate) {
                    today -> "Today"
                    yesterday -> "Yesterday"
                    else -> localDate.format(formatter)
                }
            }

            grouped.forEach { (header, list) ->
                result.add(TimelineItem.Header(header))
                list.forEach { item ->
                    result.add(TimelineItem.Media(item))
                }
            }
            assertTrue(result.isNotEmpty())
        }

        val elapsedMillis = elapsedNano / 1_000_000.0
        println("Benchmark: 10,000 items grouped in ${elapsedMillis}ms")

        // Performance assertion: grouping 10,000 items off main thread must complete under 500ms even on virtualized CI
        assertTrue("Timeline grouping exceeded budget: ${elapsedMillis}ms", elapsedMillis < 500.0)
    }
}
