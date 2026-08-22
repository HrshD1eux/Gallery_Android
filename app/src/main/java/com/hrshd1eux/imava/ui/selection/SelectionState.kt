package com.hrshd1eux.imava.ui.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hrshd1eux.imava.data.model.MediaItem

class SelectionState {
    // Observable set of selected MediaItem IDs
    var selectedIds by mutableStateOf<Set<Long>>(emptySet())
        private set

    val inSelectionMode: Boolean
        get() = selectedIds.isNotEmpty()

    fun toggle(id: Long) {
        selectedIds = if (selectedIds.contains(id)) {
            selectedIds - id
        } else {
            selectedIds + id
        }
    }

    fun select(id: Long) {
        selectedIds = selectedIds + id
    }

    fun deselect(id: Long) {
        selectedIds = selectedIds - id
    }

    /**
     * Selects all items between startId and endId from the given ordered list of items.
     */
    fun selectRange(startId: Long, endId: Long, allItems: List<MediaItem>) {
        val startIndex = allItems.indexOfFirst { it.id == startId }
        val endIndex = allItems.indexOfFirst { it.id == endId }
        if (startIndex == -1 || endIndex == -1) return

        val minIndex = minOf(startIndex, endIndex)
        val maxIndex = maxOf(startIndex, endIndex)

        val newSelection = allItems.subList(minIndex, maxIndex + 1).map { it.id }.toSet()
        selectedIds = selectedIds + newSelection
    }

    fun clear() {
        selectedIds = emptySet()
    }
}
