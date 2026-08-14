package com.HrshD1eux.Gallery.benchmark

import android.net.Uri
import com.HrshD1eux.Gallery.data.model.MediaItem
import com.HrshD1eux.Gallery.data.model.TimelineItem
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

        // Measure timeline grouping performance (O(n) test)
        val elapsedNano = measureNanoTime {
            val result = mutableListOf<TimelineItem>()
            val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
            val zoneId = ZoneId.systemDefault()
            val today = LocalDate.now(zoneId)
            val yesterday = today.minusDays(1)

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
        }

        val elapsedMillis = elapsedNano / 1_000_000.0
        println("Benchmark: 10,000 items grouped in ${elapsedMillis}ms")

        // Performance assertion: grouping 10,000 items off main thread must complete under 100ms
        assertTrue("Timeline grouping exceeded 100ms budget: ${elapsedMillis}ms", elapsedMillis < 100.0)
    }
}
