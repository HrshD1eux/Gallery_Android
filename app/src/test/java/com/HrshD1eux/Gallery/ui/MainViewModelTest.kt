package com.HrshD1eux.Gallery.ui

import android.app.Activity
import com.HrshD1eux.Gallery.data.model.MediaItem
import com.HrshD1eux.Gallery.data.repository.MediaRepository
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import androidx.lifecycle.SavedStateHandle

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockRepository = mockk<MediaRepository>(relaxed = true)
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        
        every { mockRepository.getFavoriteMediaFlow() } returns flowOf(emptyList())
        every { mockRepository.getHiddenMediaFlow() } returns flowOf(emptyList())
        every { mockRepository.getTrashedMediaFlow() } returns flowOf(emptyList())
        every { mockRepository.observeMediaChanges() } returns kotlinx.coroutines.flow.emptyFlow()
        coEvery { mockRepository.loadMediaPaged(any(), any()) } returns emptyList()
        
        viewModel = MainViewModel(mockRepository, SavedStateHandle())
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testHandleActivityResult_deleteConfirmed_executesDBDeletion() {
        val mockUri = mockk<android.net.Uri>(relaxed = true)
        val mockItem = MediaItem.Photo(
            id = 55L,
            uri = mockUri,
            path = "/original/photo.jpg",
            mimeType = "image/jpeg",
            dateTaken = 1000L,
            size = 100L,
            width = 100,
            height = 100,
            bucketId = 1L,
            bucketName = "Camera"
        )

        // Set pending action item
        viewModel.pendingActionItem = mockItem

        // Trigger RESULT_OK for deletion (1001)
        viewModel.handleActivityResult(1001, Activity.RESULT_OK)
        
        // Wait for coroutine to complete
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify repository permanent delete was called
        coVerify(exactly = 1) { mockRepository.deleteMetadataPermanently(55L) }
        assertNull(viewModel.pendingActionItem)
    }

    @Test
    fun testHandleActivityResult_deleteCanceled_doesNotExecuteDBDeletion() {
        val mockUri = mockk<android.net.Uri>(relaxed = true)
        val mockItem = MediaItem.Photo(
            id = 55L,
            uri = mockUri,
            path = "/original/photo.jpg",
            mimeType = "image/jpeg",
            dateTaken = 1000L,
            size = 100L,
            width = 100,
            height = 100,
            bucketId = 1L,
            bucketName = "Camera"
        )

        // Set pending action item
        viewModel.pendingActionItem = mockItem

        // Trigger RESULT_CANCELED for deletion (1001)
        viewModel.handleActivityResult(1001, Activity.RESULT_CANCELED)
        
        // Wait for coroutine to complete
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify repository permanent delete was NOT called
        coVerify(exactly = 0) { mockRepository.deleteMetadataPermanently(any()) }
        assertNull(viewModel.pendingActionItem)
    }

    @Test
    fun testHandleActivityResult_vaultHideCanceled_performsRollback() {
        val mockUri = mockk<android.net.Uri>(relaxed = true)
        val mockItem = MediaItem.Photo(
            id = 55L,
            uri = mockUri,
            path = "/original/photo.jpg",
            mimeType = "image/jpeg",
            dateTaken = 1000L,
            size = 100L,
            width = 100,
            height = 100,
            bucketId = 1L,
            bucketName = "Camera"
        )

        // Set pending action item
        viewModel.pendingActionItem = mockItem

        // Trigger RESULT_CANCELED for vault hide (1004)
        viewModel.handleActivityResult(1004, Activity.RESULT_CANCELED)
        
        // Wait for coroutine to complete
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify rollback was executed: calling deleteMetadataPermanently
        coVerify(exactly = 1) { mockRepository.deleteMetadataPermanently(55L) }
        assertNull(viewModel.pendingActionItem)
    }

    @Test
    fun testLoadNextPage_loadsCorrectly() {
        val mockUri = mockk<android.net.Uri>(relaxed = true)
        val item1 = MediaItem.Photo(1L, mockUri, "/path1.jpg", "image/jpeg", 1000L, 100L, 100, 100, false, false, false, 1L, "Camera")
        val item2 = MediaItem.Photo(2L, mockUri, "/path2.jpg", "image/jpeg", 1000L, 100L, 100, 100, false, false, false, 1L, "Camera")
        
        val firstPageList = List(200) { item1 }
        coEvery { mockRepository.loadMediaPaged(limit = 200, offset = 0, bucketId = null) } returns firstPageList
        coEvery { mockRepository.loadMediaPaged(limit = 200, offset = 200, bucketId = null) } returns listOf(item2)

        // Init loads first page (returns 200 items)
        viewModel.loadNextPage(reset = true)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(200, viewModel.mediaItems.value.size)
        assertEquals(1L, viewModel.mediaItems.value[0].id)

        // Load next page (returns item2)
        viewModel.loadNextPage(reset = false)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(201, viewModel.mediaItems.value.size)
        assertEquals(2L, viewModel.mediaItems.value[200].id)
    }
}
