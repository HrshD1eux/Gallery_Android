package com.HrshD1eux.Gallery.ui.selection

import android.net.Uri
import com.HrshD1eux.Gallery.data.model.MediaItem
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionStateTest {

    private val mockUri = mockk<Uri>(relaxed = true)

    private val items = listOf(
        MediaItem.Photo(1L, mockUri, "/path1", "image/jpeg", 1000L, 100L, 100, 100, bucketId = 1L, bucketName = "Camera"),
        MediaItem.Photo(2L, mockUri, "/path2", "image/jpeg", 2000L, 200L, 100, 100, bucketId = 1L, bucketName = "Camera"),
        MediaItem.Photo(3L, mockUri, "/path3", "image/jpeg", 3000L, 300L, 100, 100, bucketId = 1L, bucketName = "Camera"),
        MediaItem.Photo(4L, mockUri, "/path4", "image/jpeg", 4000L, 400L, 100, 100, bucketId = 1L, bucketName = "Camera"),
        MediaItem.Photo(5L, mockUri, "/path5", "image/jpeg", 5000L, 500L, 100, 100, bucketId = 1L, bucketName = "Camera")
    )

    @Test
    fun testToggleSelection() {
        val state = SelectionState()
        assertFalse(state.inSelectionMode)

        state.toggle(1L)
        assertTrue(state.inSelectionMode)
        assertTrue(state.selectedIds.contains(1L))

        state.toggle(1L)
        assertFalse(state.inSelectionMode)
        assertTrue(state.selectedIds.isEmpty())
    }

    @Test
    fun testSelectRangeForward() {
        val state = SelectionState()
        // Select range from 2L to 4L
        state.selectRange(2L, 4L, items)

        assertEquals(3, state.selectedIds.size)
        assertTrue(state.selectedIds.contains(2L))
        assertTrue(state.selectedIds.contains(3L))
        assertTrue(state.selectedIds.contains(4L))
    }

    @Test
    fun testSelectRangeBackward() {
        val state = SelectionState()
        // Select range from 4L to 2L (drag backward)
        state.selectRange(4L, 2L, items)

        assertEquals(3, state.selectedIds.size)
        assertTrue(state.selectedIds.contains(2L))
        assertTrue(state.selectedIds.contains(3L))
        assertTrue(state.selectedIds.contains(4L))
    }

    @Test
    fun testClearSelection() {
        val state = SelectionState()
        state.select(1L)
        state.select(2L)
        assertTrue(state.inSelectionMode)

        state.clear()
        assertFalse(state.inSelectionMode)
        assertTrue(state.selectedIds.isEmpty())
    }
}
