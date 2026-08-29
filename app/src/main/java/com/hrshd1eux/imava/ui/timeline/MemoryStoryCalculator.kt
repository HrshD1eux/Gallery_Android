package com.hrshd1eux.imava.ui.timeline

import com.hrshd1eux.imava.data.model.MediaItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object MemoryStoryCalculator {

    fun generateStories(items: List<MediaItem>): List<MemoryStory> {
        if (items.isEmpty()) return emptyList()

        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val todayMonth = today.monthValue
        val todayDay = today.dayOfMonth
        val formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault())
        val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

        val stories = mutableListOf<MemoryStory>()

        val exactDayItems = items.filter { item ->
            if (item.dateTaken <= 0) return@filter false
            val itemDate = Instant.ofEpochMilli(item.dateTaken).atZone(zoneId).toLocalDate()
            itemDate.monthValue == todayMonth && itemDate.dayOfMonth == todayDay && itemDate.year < today.year
        }
        if (exactDayItems.isNotEmpty()) {
            val groupedByYear = exactDayItems.groupBy { item ->
                Instant.ofEpochMilli(item.dateTaken).atZone(zoneId).toLocalDate().year
            }
            groupedByYear.forEach { (year, yearItems) ->
                val yearsAgo = today.year - year
                val title = if (yearsAgo == 1) "1 Year Ago Today" else "$yearsAgo Years Ago"
                val sampleDate = Instant.ofEpochMilli(yearItems.first().dateTaken).atZone(zoneId).toLocalDate().format(formatter)
                val cover = yearItems.firstOrNull()
                if (cover != null) {
                    stories.add(
                        MemoryStory(
                            id = "memory_day_$year",
                            title = title,
                            dateSubtitle = sampleDate,
                            coverItem = cover,
                            items = yearItems
                        )
                    )
                }
            }
        }

        val sameMonthPastYears = items.filter { item ->
            if (item.dateTaken <= 0) return@filter false
            val itemDate = Instant.ofEpochMilli(item.dateTaken).atZone(zoneId).toLocalDate()
            itemDate.monthValue == todayMonth && itemDate.year < today.year && !exactDayItems.contains(item)
        }
        if (sameMonthPastYears.isNotEmpty()) {
            val groupedByYear = sameMonthPastYears.groupBy { item ->
                Instant.ofEpochMilli(item.dateTaken).atZone(zoneId).toLocalDate().year
            }
            groupedByYear.forEach { (year, yearItems) ->
                val sampleDate = Instant.ofEpochMilli(yearItems.first().dateTaken).atZone(zoneId).toLocalDate().format(monthFormatter)
                val cover = yearItems.firstOrNull()
                if (cover != null && yearItems.size >= 2) {
                    stories.add(
                        MemoryStory(
                            id = "memory_month_$year",
                            title = "$sampleDate Memories",
                            dateSubtitle = "${yearItems.size} moments",
                            coverItem = cover,
                            items = yearItems
                        )
                    )
                }
            }
        }

        if (stories.isEmpty()) {
            val validItems = items.filter { it.dateTaken > 0 }
            val groupedByMonth = validItems.groupBy { item ->
                val date = Instant.ofEpochMilli(item.dateTaken).atZone(zoneId).toLocalDate()
                "${date.year}-${date.monthValue}"
            }
            groupedByMonth.entries.take(4).forEach { (_, monthItems) ->
                val sampleDate = Instant.ofEpochMilli(monthItems.first().dateTaken).atZone(zoneId).toLocalDate().format(monthFormatter)
                val cover = monthItems.firstOrNull()
                if (cover != null) {
                    stories.add(
                        MemoryStory(
                            id = "memory_fallback_${cover.id}",
                            title = "$sampleDate Highlights",
                            dateSubtitle = "${monthItems.size} moments",
                            coverItem = cover,
                            items = monthItems
                        )
                    )
                }
            }
        }

        return stories.sortedByDescending { it.items.firstOrNull()?.dateTaken ?: 0L }
    }
}
