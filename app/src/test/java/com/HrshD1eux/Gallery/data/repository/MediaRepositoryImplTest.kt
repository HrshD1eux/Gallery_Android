package com.HrshD1eux.Gallery.data.repository

import android.content.Context
import com.HrshD1eux.Gallery.data.database.MediaMetadataEntity
import com.HrshD1eux.Gallery.data.database.MetadataDao
import com.HrshD1eux.Gallery.data.media.MediaStoreDataSource
import com.HrshD1eux.Gallery.data.model.MediaItem
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaRepositoryImplTest {

    private class FakeMetadataDao : MetadataDao {
        val dbMap = mutableMapOf<Long, MediaMetadataEntity>()
        var trackedIds = mutableListOf<Long>()
        val deletedIds = mutableListOf<Long>()
        val deletedChunks = mutableListOf<List<Long>>()

        override suspend fun getMetadataForMedia(mediaId: Long): MediaMetadataEntity? {
            return dbMap[mediaId]
        }

        override fun getAllMetadataFlow(): Flow<List<MediaMetadataEntity>> = flow {
            emit(dbMap.values.toList())
        }

        override suspend fun insertOrUpdate(metadata: MediaMetadataEntity) {
            dbMap[metadata.mediaId] = metadata
        }

        override suspend fun updateFavorite(mediaId: Long, isFavorite: Boolean) {}
        override suspend fun updateHidden(mediaId: Long, isHidden: Boolean) {}
        override suspend fun updateTrashed(mediaId: Long, isTrashed: Boolean, trashTime: Long) {}
        
        override suspend fun delete(metadata: MediaMetadataEntity) {
            dbMap.remove(metadata.mediaId)
        }

        override suspend fun getTrackedNonHiddenIds(): List<Long> {
            return trackedIds
        }

        override suspend fun deleteMetadataByIds(ids: List<Long>) {
            deletedChunks.add(ids)
            deletedIds.addAll(ids)
        }

        override suspend fun getAllMetadata(): List<MediaMetadataEntity> {
            return dbMap.values.toList()
        }

        override suspend fun getHiddenOrTrashedMetadata(): List<MediaMetadataEntity> {
            return dbMap.values.filter { it.isHidden || it.isTrashed }
        }

        override suspend fun getMetadataForMediaIds(ids: List<Long>): List<MediaMetadataEntity> {
            return ids.mapNotNull { dbMap[it] }
        }

        override fun getFavoriteIdsFlow(): Flow<List<Long>> = flow {
            emit(dbMap.values.filter { it.isFavorite && !it.isHidden && !it.isTrashed }.map { it.mediaId })
        }

        override fun getTrashedIdsFlow(): Flow<List<Long>> = flow {
            emit(dbMap.values.filter { it.isTrashed }.map { it.mediaId })
        }
    }

    @Test
    fun testDeleteOrphanedMetadata_deletesOnlyOrphansInChunksOf500() = runBlocking {
        val fakeDao = FakeMetadataDao()
        
        // Seed the fake DAO with 1200 IDs (0 to 1199)
        fakeDao.trackedIds = (0L until 1200L).toMutableList()

        // Active IDs in MediaStore: only keep even IDs (0, 2, 4, ...), meaning odd IDs are orphaned
        // Keep 0, 2, ..., 1198. (600 active IDs)
        // This leaves 600 orphaned IDs (1, 3, 5, ..., 1199)
        val activeIds = (0L until 1200L).filter { it % 2 == 0L }

        // Create repository instance with dummy/mock MediaStoreDataSource
        val mockContext = mockk<Context>(relaxed = true)
        val repository = MediaRepositoryImpl(
            context = mockContext,
            mediaStoreDataSource = MediaStoreDataSource(mockContext),
            metadataDao = fakeDao
        )

        // Execute cleanup
        repository.deleteOrphanedMetadata(activeIds)

        // Verify that exactly 600 IDs were deleted
        assertEquals(600, fakeDao.deletedIds.size)

        // Verify that deleted IDs are indeed the odd IDs (the ones not present in activeIds)
        fakeDao.deletedIds.forEach { id ->
            assertTrue(id % 2 == 1L)
        }

        // Verify that chunking was executed in size of 500
        // 600 items should be chunked into:
        // Chunk 1: 500 items
        // Chunk 2: 100 items
        assertEquals(2, fakeDao.deletedChunks.size)
        assertEquals(500, fakeDao.deletedChunks[0].size)
        assertEquals(100, fakeDao.deletedChunks[1].size)
    }

    @Test
    fun testVaultMetadataSync_selfHealsRoomFromSidecar() {
        runBlocking {
            val fakeDao = FakeMetadataDao()
        val mockContext = mockk<Context>(relaxed = true)
        val tempFolder = java.nio.file.Files.createTempDirectory("vault_test").toFile()
        every { mockContext.filesDir } returns tempFolder
        every { mockContext.cacheDir } returns tempFolder

        val resolver = mockk<android.content.ContentResolver>(relaxed = true)
        every { mockContext.contentResolver } returns resolver
        val mockUri = mockk<android.net.Uri>(relaxed = true)
        mockkStatic(android.net.Uri::class)
        every { android.net.Uri.fromFile(any()) } returns mockUri
        val inputStream = "dummy content".byteInputStream()
        every { resolver.openInputStream(mockUri) } returns inputStream

        val repository = MediaRepositoryImpl(
            context = mockContext,
            mediaStoreDataSource = MediaStoreDataSource(mockContext),
            metadataDao = fakeDao
        )

        val itemToHide = MediaItem.Photo(
            id = 123L,
            uri = mockUri,
            path = "/original/path.jpg",
            mimeType = "image/jpeg",
            dateTaken = 1000L,
            size = 5000L,
            width = 800,
            height = 600,
            bucketId = 1L,
            bucketName = "Camera"
        )

        // 1. Hide the item
        repository.toggleHidden(mockContext, itemToHide)

        // Verify sidecar was created
        val vaultDir = java.io.File(tempFolder, "vault")
        val vaultFile = java.io.File(vaultDir, "vault_123")
        val metaFile = java.io.File(vaultDir, "vault_123.meta")
        assertTrue(vaultFile.exists())
        assertTrue(metaFile.exists())

        // Verify database entry exists
        var dbEntity = fakeDao.getMetadataForMedia(123L)
        assertTrue(dbEntity != null)
        assertEquals("/original/path.jpg", dbEntity?.originalPath)

        // 2. Simulate database wipe (Room migration or data clear) by removing the entity from DAO
        fakeDao.dbMap.clear()
        assertTrue(fakeDao.getMetadataForMedia(123L) == null)

        // 3. Collect from the hidden media flow, which should trigger syncVaultMetadata()
        val flow = repository.getHiddenMediaFlow()
        val itemsList = flow.first()

        // 4. Verify the database record has been self-healed and restored from sidecar!
        dbEntity = fakeDao.getMetadataForMedia(123L)
        assertTrue(dbEntity != null)
        assertEquals("/original/path.jpg", dbEntity?.originalPath)
        assertEquals(vaultFile.absolutePath, dbEntity?.vaultPath)
        
        // Cleanup temp folder
        tempFolder.deleteRecursively()
        unmockkStatic(android.net.Uri::class)
        }
    }

    @Test
    fun testLoadMediaPaged_filtersHiddenItemsAndLoops() = runBlocking {
        val fakeDao = FakeMetadataDao()
        val mockContext = mockk<Context>(relaxed = true)
        val mockDataSource = mockk<MediaStoreDataSource>(relaxed = true)
        
        val mockUri1 = mockk<android.net.Uri>(relaxed = true)
        val item1 = MediaItem.Photo(1L, mockUri1, "/path1.jpg", "image/jpeg", 1000L, 100L, 100, 100, false, false, false, 1L, "Camera")
        val item2 = MediaItem.Photo(2L, mockUri1, "/path2.jpg", "image/jpeg", 1000L, 100L, 100, 100, false, false, false, 1L, "Camera")
        val item3 = MediaItem.Photo(3L, mockUri1, "/path3.jpg", "image/jpeg", 1000L, 100L, 100, 100, false, false, false, 1L, "Camera")
        
        // Mark item1 as hidden in the DB
        fakeDao.dbMap[1L] = MediaMetadataEntity(mediaId = 1L, isHidden = true)
        
        // Mock datasource fetchMedia:
        // First call with limit 2, offset 0: returns item1 and item2
        coEvery { mockDataSource.fetchMedia(2, 0, any()) } returns listOf(item1, item2)
        // Second call with limit 1, offset 2: returns item3
        coEvery { mockDataSource.fetchMedia(1, 2, any()) } returns listOf(item3)

        val repository = MediaRepositoryImpl(
            context = mockContext,
            mediaStoreDataSource = mockDataSource,
            metadataDao = fakeDao
        )
        
        // Request limit 2, starting at offset 0
        // It should fetch 2 items (item1, item2), filter out item1 (hidden), and then loop to fetch 1 more item (item3)
        val result = repository.loadMediaPaged(limit = 2, offset = 0)
        
        assertEquals(2, result.size)
        assertEquals(2L, result[0].id)
        assertEquals(3L, result[1].id)
    }

    @Test
    fun testGetBuckets_deductsHiddenAndTrashedCounts() = runBlocking {
        val fakeDao = FakeMetadataDao()
        val mockContext = mockk<Context>(relaxed = true)
        val mockDataSource = mockk<MediaStoreDataSource>(relaxed = true)
        
        val rawBuckets = listOf(
            com.HrshD1eux.Gallery.data.media.BucketInfo(1L, "Camera", 10),
            com.HrshD1eux.Gallery.data.media.BucketInfo(2L, "Screenshots", 5)
        )
        coEvery { mockDataSource.fetchBuckets() } returns rawBuckets
        
        fakeDao.dbMap[101L] = MediaMetadataEntity(mediaId = 101L, isHidden = true, bucketId = 1L)
        fakeDao.dbMap[102L] = MediaMetadataEntity(mediaId = 102L, isHidden = true, bucketId = 1L)
        fakeDao.dbMap[103L] = MediaMetadataEntity(mediaId = 103L, isHidden = true, bucketId = 1L)
        fakeDao.dbMap[104L] = MediaMetadataEntity(mediaId = 104L, isTrashed = true, bucketId = 1L)
        
        fakeDao.dbMap[201L] = MediaMetadataEntity(mediaId = 201L, isHidden = true, bucketId = 2L)
        fakeDao.dbMap[202L] = MediaMetadataEntity(mediaId = 202L, isHidden = true, bucketId = 2L)
        fakeDao.dbMap[203L] = MediaMetadataEntity(mediaId = 203L, isHidden = true, bucketId = 2L)
        fakeDao.dbMap[204L] = MediaMetadataEntity(mediaId = 204L, isHidden = true, bucketId = 2L)
        fakeDao.dbMap[205L] = MediaMetadataEntity(mediaId = 205L, isHidden = true, bucketId = 2L)

        val repository = MediaRepositoryImpl(
            context = mockContext,
            mediaStoreDataSource = mockDataSource,
            metadataDao = fakeDao
        )
        
        val result = repository.getBuckets()
        
        assertEquals(1, result.size)
        assertEquals(1L, result[0].id)
        assertEquals("Camera", result[0].name)
        assertEquals(6, result[0].count)
    }
}
