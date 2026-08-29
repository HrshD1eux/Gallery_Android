package com.hrshd1eux.imava.ui

import android.app.Activity
import com.hrshd1eux.imava.data.model.MediaItem
import com.hrshd1eux.imava.data.repository.MediaRepository
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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
    private val mockApplication = mockk<android.app.Application>(relaxed = true)
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        
        every { mockApplication.getSharedPreferences(any(), any()) } returns mockk(relaxed = true)
        every { mockRepository.getFavoriteMediaFlow() } returns flowOf(emptyList())
        every { mockRepository.getHiddenMediaFlow() } returns flowOf(emptyList())
        every { mockRepository.getTrashedMediaFlow() } returns flowOf(emptyList())
        every { mockRepository.observeMediaChanges() } returns kotlinx.coroutines.flow.emptyFlow()
        every { mockRepository.getMediaFlow(any(), any(), any()) } returns flowOf(emptyList())
        coEvery { mockRepository.loadMediaPaged(any(), any(), any(), any(), any()) } returns emptyList()
        
        viewModel = MainViewModel(mockApplication, mockRepository, SavedStateHandle())
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

        viewModel.pendingActionItem = mockItem

        viewModel.handleActivityResult(1001, Activity.RESULT_OK)
        
        testDispatcher.scheduler.advanceUntilIdle()

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

        viewModel.pendingActionItem = mockItem

        viewModel.handleActivityResult(1001, Activity.RESULT_CANCELED)
        
        testDispatcher.scheduler.advanceUntilIdle()

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

        viewModel.pendingActionItem = mockItem

        viewModel.handleActivityResult(1004, Activity.RESULT_CANCELED)
        
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockRepository.deleteMetadataPermanently(55L) }
        assertNull(viewModel.pendingActionItem)
    }

    @Test
    fun testLoadNextPage_loadsCorrectly() {
        val mockUri = mockk<android.net.Uri>(relaxed = true)
        val item1 = MediaItem.Photo(1L, mockUri, "/path1.jpg", "image/jpeg", 1000L, 100L, 100, 100, false, false, false, 1L, "Camera")
        
        val firstPageList = List(200) { item1 }
        coEvery { mockRepository.loadMediaPaged(limit = any(), offset = any(), bucketId = any(), sortOrder = any()) } returns firstPageList

        viewModel.loadNextPage()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(200, viewModel.mediaItems.value.size)
        assertEquals(1L, viewModel.mediaItems.value[0].id)
    }

    @Test
    fun testDeleteSelectedMedia_resolvesSelectedItemsByIdBeyondFirst200() {
        val mockUri = mockk<android.net.Uri>(relaxed = true)
        val itemBeyond200 = MediaItem.Photo(999L, mockUri, "/path999.jpg", "image/jpeg", 1000L, 100L, 100, 100, false, false, false, 1L, "Camera")
        
        coEvery { mockRepository.getMediaByIds(setOf(999L)) } returns listOf(itemBeyond200)

        val mockContext = mockk<android.content.Context>(relaxed = true)

        viewModel.selectionState.select(999L)

        viewModel.deleteSelectedMedia(mockContext)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockRepository.getMediaByIds(setOf(999L)) }
    }

    @Test
    fun testSearchQuery_triggersRepositorySearch() = runTest {
        val mockUri = mockk<android.net.Uri>(relaxed = true)
        val searchResultItem = MediaItem.Photo(500L, mockUri, "/path/vacation.jpg", "image/jpeg", 1000L, 100L, 100, 100, false, false, false, 1L, "Camera")
        
        coEvery { mockRepository.searchMedia("vacation") } returns listOf(searchResultItem)

        val job = backgroundScope.launch { viewModel.searchResults.collect {} }

        viewModel.setSearchQuery("vacation")
        testDispatcher.scheduler.advanceTimeBy(350)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockRepository.searchMedia("vacation") }
        assertEquals(1, viewModel.searchResults.value.size)
        assertEquals(500L, viewModel.searchResults.value[0].id)

        viewModel.setSearchQuery("")
        testDispatcher.scheduler.advanceTimeBy(350)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, viewModel.searchResults.value.size)

        job.cancel()
    }

    @Test
    fun testDismissMemoriesFor24Hours_setsTimestamp() = runTest {
        assertEquals(0L, viewModel.memoriesDismissedTimestamp.value)
        viewModel.dismissMemoriesFor24Hours()
        org.junit.Assert.assertTrue(viewModel.memoriesDismissedTimestamp.value > 0L)
    }
}
