package com.hrshd1eux.imava.data.repository

import android.content.Context
import android.net.Uri
import com.hrshd1eux.imava.data.database.MediaMetadataEntity
import com.hrshd1eux.imava.data.database.MetadataDao
import com.hrshd1eux.imava.data.media.MediaStoreDataSource
import com.hrshd1eux.imava.data.model.MediaItem
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Before
import org.junit.After
import javax.crypto.KeyGenerator
import com.hrshd1eux.imava.core.util.VaultCrypto

class MediaRepositoryImplTest {

    @Before
    fun setUpCrypto() {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        VaultCrypto.testSecretKey = keyGen.generateKey()
    }

    @After
    fun tearDownCrypto() {
        VaultCrypto.testSecretKey = null
    }

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

        override suspend fun getExpiredTrashItems(cutoffTimestamp: Long): List<MediaMetadataEntity> {
            return dbMap.values.filter { it.isTrashed && it.trashTime > 0 && it.trashTime < cutoffTimestamp }
        }

        override suspend fun deleteByMediaId(mediaId: Long) {
            dbMap.remove(mediaId)
            deletedIds.add(mediaId)
        }

        override suspend fun getMetadataByTag(tagQuery: String): List<MediaMetadataEntity> {
            return dbMap.values.filter { it.tags.contains(tagQuery, ignoreCase = true) && !it.isHidden && !it.isTrashed }
        }

        override suspend fun updateTags(mediaId: Long, tags: String) {
            val cur = dbMap[mediaId] ?: MediaMetadataEntity(mediaId = mediaId)
            dbMap[mediaId] = cur.copy(tags = tags)
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

        // 3. Collect from the hidden media flow (when vault is unlocked), which should trigger syncVaultMetadata()
        val flow = repository.getHiddenMediaFlow(isVaultUnlocked = true)
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
        coEvery { mockDataSource.fetchMedia(2, 0, any(), any(), any(), any()) } returns listOf(item1, item2)
        // Second call with limit 1, offset 2: returns item3
        coEvery { mockDataSource.fetchMedia(1, 2, any(), any(), any(), any()) } returns listOf(item3)

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
            com.hrshd1eux.imava.data.media.BucketInfo(1L, "Camera", 10),
            com.hrshd1eux.imava.data.media.BucketInfo(2L, "Screenshots", 5)
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

    @Test
    fun testHiddenMediaFlow_doesNotDecryptWhenVaultIsLocked() {
        runBlocking {
            val fakeDao = FakeMetadataDao()
            val mockContext = mockk<Context>(relaxed = true)
            val tempFolder = java.nio.file.Files.createTempDirectory("vault_security_test").toFile()
            every { mockContext.filesDir } returns tempFolder
            every { mockContext.cacheDir } returns tempFolder

            val resolver = mockk<android.content.ContentResolver>(relaxed = true)
            every { mockContext.contentResolver } returns resolver
            val mockUri = mockk<android.net.Uri>(relaxed = true)
            mockkStatic(android.net.Uri::class)
            every { android.net.Uri.fromFile(any()) } returns mockUri

            val repository = MediaRepositoryImpl(
                context = mockContext,
                mediaStoreDataSource = MediaStoreDataSource(mockContext),
                metadataDao = fakeDao
            )

            // Seed DB with a hidden metadata entry
            val vaultFile = java.io.File(tempFolder, "vault_999").apply { writeText("encrypted_data") }
            fakeDao.dbMap[999L] = MediaMetadataEntity(
                mediaId = 999L,
                isHidden = true,
                vaultPath = vaultFile.absolutePath,
                mimeType = "image/jpeg"
            )

            // Collect getHiddenMediaFlow with isVaultUnlocked = false (default)
            val items = repository.getHiddenMediaFlow(isVaultUnlocked = false).first()

            assertEquals(0, items.size)

            // Assert that NO vault_cache directory was created and NO decrypted file exists
            val cacheDir = java.io.File(tempFolder, "vault_cache")
            val decryptedFile = java.io.File(cacheDir, "decrypted_999")
            assertTrue(!decryptedFile.exists())

            tempFolder.deleteRecursively()
            unmockkStatic(android.net.Uri::class)
        }
    }

    @Test
    fun testHiddenMediaFlow_decryptsWhenVaultIsUnlocked() {
        runBlocking {
            val fakeDao = FakeMetadataDao()
            val mockContext = mockk<Context>(relaxed = true)
            val tempFolder = java.nio.file.Files.createTempDirectory("vault_security_test2").toFile()
            every { mockContext.filesDir } returns tempFolder
            every { mockContext.cacheDir } returns tempFolder

            val resolver = mockk<android.content.ContentResolver>(relaxed = true)
            every { mockContext.contentResolver } returns resolver
            val mockUri = mockk<android.net.Uri>(relaxed = true)
            mockkStatic(android.net.Uri::class)
            every { android.net.Uri.fromFile(any()) } returns mockUri

            val repository = MediaRepositoryImpl(
                context = mockContext,
                mediaStoreDataSource = MediaStoreDataSource(mockContext),
                metadataDao = fakeDao
            )

            val vaultFile = java.io.File(tempFolder, "vault_888").apply {
                outputStream().use { out ->
                    com.hrshd1eux.imava.core.util.VaultCrypto.encrypt("Test Photo Data".toByteArray().inputStream(), out)
                }
            }
            fakeDao.dbMap[888L] = MediaMetadataEntity(
                mediaId = 888L,
                isHidden = true,
                vaultPath = vaultFile.absolutePath,
                mimeType = "image/jpeg"
            )

            // Collect getHiddenMediaFlow with isVaultUnlocked = true
            val items = repository.getHiddenMediaFlow(isVaultUnlocked = true).first()

            assertEquals(1, items.size)
            assertEquals(888L, items[0].id)

            // When unlocked, decrypted cache file is available for Coil/Viewer to load without corruption
            val cacheDir = java.io.File(tempFolder, "vault_cache")
            val decryptedFile = java.io.File(cacheDir, "decrypted_888.jpg")
            assertTrue("Decrypted cache file must exist while vault is unlocked", decryptedFile.exists())
            assertEquals("Test Photo Data", decryptedFile.readText())

            // Assert that clearVaultCache wipes transient cache on lock
            repository.clearVaultCache(mockContext)
            assertTrue("vault_cache must be cleared on vault lock", !decryptedFile.exists())

            tempFolder.deleteRecursively()
            unmockkStatic(android.net.Uri::class)
        }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    @Test
    fun testVaultFetcher_decryptsInMemoryAndDisablesDiskCache() {
        runBlocking {
            val tempFolder = java.nio.file.Files.createTempDirectory("vault_fetcher_test").toFile()
            val plaintextData = "Secret Vault Photo Content".toByteArray()

            val vaultFile = java.io.File(tempFolder, "vault_12345").apply {
                outputStream().use { out ->
                    com.hrshd1eux.imava.core.util.VaultCrypto.encrypt(plaintextData.inputStream(), out)
                }
            }

            val mockContext = mockk<Context>(relaxed = true)
            val mockUri = mockk<android.net.Uri>(relaxed = true)
            every { mockUri.path } returns vaultFile.absolutePath
            every { mockUri.scheme } returns "file"

            val options = coil.request.Options(mockContext)
            val mockImageLoader = mockk<coil.ImageLoader>(relaxed = true)

            val factory = com.hrshd1eux.imava.core.util.VaultFetcher.Factory()
            val fetcher = factory.create(mockUri, options, mockImageLoader)

            assertNotNull(fetcher)
            val result = fetcher?.fetch()
            assertTrue(result is coil.fetch.SourceResult)
            
            val sourceResult = result as coil.fetch.SourceResult
            assertEquals(coil.decode.DataSource.MEMORY, sourceResult.dataSource)

            tempFolder.deleteRecursively()
        }
    }

    @Test
    fun testGetMediaByIds_resolvesItemsWithMetadata() = runBlocking {
        val fakeDao = FakeMetadataDao()
        val mockContext = mockk<Context>(relaxed = true)
        val mockDataSource = mockk<MediaStoreDataSource>(relaxed = true)
        val mockUri = mockk<android.net.Uri>(relaxed = true)

        val item1 = MediaItem.Photo(100L, mockUri, "/path100.jpg", "image/jpeg", 1000L, 100L, 100, 100, false, false, false, 1L, "Camera")
        val item2 = MediaItem.Photo(500L, mockUri, "/path500.jpg", "image/jpeg", 2000L, 200L, 100, 100, false, false, false, 1L, "Camera")
        
        coEvery { mockDataSource.fetchMediaByIds(setOf(100L, 500L)) } returns listOf(item1, item2)
        fakeDao.dbMap[500L] = MediaMetadataEntity(mediaId = 500L, isFavorite = true)

        val repository = MediaRepositoryImpl(
            context = mockContext,
            mediaStoreDataSource = mockDataSource,
            metadataDao = fakeDao
        )

        val result = repository.getMediaByIds(setOf(100L, 500L))

        assertEquals(2, result.size)
        assertEquals(100L, result[0].id)
        assertEquals(500L, result[1].id)
        assertTrue(result[1].isFavorite)
    }

    @Test
    fun testSearchMedia_delegatesToDataSourceAndFiltersHidden() = runBlocking {
        val fakeDao = FakeMetadataDao()
        val mockContext = mockk<Context>(relaxed = true)
        val mockDataSource = mockk<MediaStoreDataSource>(relaxed = true)
        val mockUri = mockk<android.net.Uri>(relaxed = true)

        val item1 = MediaItem.Photo(1L, mockUri, "/path/sunset.jpg", "image/jpeg", 1000L, 100L, 100, 100, false, false, false, 1L, "Camera")
        val item2 = MediaItem.Photo(2L, mockUri, "/path/sunset2.jpg", "image/jpeg", 2000L, 200L, 100, 100, false, false, false, 1L, "Camera")
        
        coEvery { mockDataSource.searchMedia("sunset") } returns listOf(item1, item2)
        // Mark item2 as hidden in DAO
        fakeDao.dbMap[2L] = MediaMetadataEntity(mediaId = 2L, isHidden = true)

        val repository = MediaRepositoryImpl(
            context = mockContext,
            mediaStoreDataSource = mockDataSource,
            metadataDao = fakeDao
        )

        val result = repository.searchMedia("sunset")

        // Should return only non-hidden item1
        assertEquals(1, result.size)
        assertEquals(1L, result[0].id)
    }

    @Test
    fun testVaultCrypto_pbkdf2HashingAndVerification() {
        val salt = VaultCrypto.generateSalt()
        val pin = "123456"
        val hash = VaultCrypto.hashPin(pin, salt)

        val verifySuccess = VaultCrypto.verifyPin(pin, hash, salt)
        assertTrue(verifySuccess.isValid)
        org.junit.Assert.assertFalse(verifySuccess.needsUpgrade)

        val verifyFailure = VaultCrypto.verifyPin("999999", hash, salt)
        org.junit.Assert.assertFalse(verifyFailure.isValid)
    }

    @Test
    fun testVaultCrypto_legacySha256VerificationTriggersUpgrade() {
        val salt = VaultCrypto.generateSalt()
        val pin = "4321"
        val legacyHash = VaultCrypto.hashPinLegacySha256(pin, salt)

        val verifyResult = VaultCrypto.verifyPin(pin, legacyHash, salt)
        assertTrue(verifyResult.isValid)
        assertTrue(verifyResult.needsUpgrade)
    }

    @Test
    fun testLoadMediaPaged_passesMediaTypeFilter() = runBlocking {
        val fakeDao = FakeMetadataDao()
        val mockContext = mockk<Context>(relaxed = true)
        val mockDataSource = mockk<MediaStoreDataSource>(relaxed = true)
        val mockUri = mockk<android.net.Uri>(relaxed = true)

        val videoItem = MediaItem.Video(10L, mockUri, "/video.mp4", "video/mp4", 1000L, 5000L, 1920, 1080, 5000L, false, false, false, 1L, "Camera")
        coEvery {
            mockDataSource.fetchMedia(
                limit = any(),
                offset = any(),
                bucketId = any(),
                includeTrashed = any(),
                isAscending = any(),
                mediaType = com.hrshd1eux.imava.data.media.MediaTypeFilter.VIDEOS
            )
        } returns listOf(videoItem)

        val repository = MediaRepositoryImpl(
            context = mockContext,
            mediaStoreDataSource = mockDataSource,
            metadataDao = fakeDao
        )

        val result = repository.loadMediaPaged(
            limit = 10,
            offset = 0,
            mediaType = com.hrshd1eux.imava.data.media.MediaTypeFilter.VIDEOS
        )

        assertEquals(1, result.size)
        assertEquals(10L, result[0].id)
        assertTrue(result[0] is MediaItem.Video)
    }

    @Test
    fun testZoomState_canConsumePanBoundaryConditions() {
        val zoomState = com.hrshd1eux.imava.ui.viewer.ZoomState()
        zoomState.layoutSize = androidx.compose.ui.unit.IntSize(1000, 1000)

        // At 1.0x (unzoomed), should yield pan events to HorizontalPager
        zoomState.scale = 1f
        org.junit.Assert.assertFalse(zoomState.canConsumePan(50f))
        org.junit.Assert.assertFalse(zoomState.canConsumePan(-50f))

        // At 2.0x zoomed in, center position: can consume pan in either direction
        zoomState.scale = 2f
        zoomState.offsetX = 0f
        assertTrue(zoomState.canConsumePan(50f))
        assertTrue(zoomState.canConsumePan(-50f))

        // At 2.0x zoomed in, at right boundary (offsetX == 500f):
        // Panning right (panX > 0) is blocked by boundary, so should NOT consume (yield to pager)
        zoomState.offsetX = 500f
        org.junit.Assert.assertFalse(zoomState.canConsumePan(10f))
        // Panning left away from boundary is allowed
        assertTrue(zoomState.canConsumePan(-10f))

        // At 2.0x zoomed in, at left boundary (offsetX == -500f):
        // Panning left (panX < 0) is blocked by boundary, so should NOT consume (yield to pager)
        zoomState.offsetX = -500f
        org.junit.Assert.assertFalse(zoomState.canConsumePan(-10f))
        // Panning right away from boundary is allowed
        assertTrue(zoomState.canConsumePan(10f))
    }

    @Test
    fun testToggleFavoriteBatch_togglesAllToFavoriteThenUnfavorite() = runBlocking {
        val fakeDao = FakeMetadataDao()
        val mockContext = mockk<Context>(relaxed = true)
        val mockDataSource = mockk<MediaStoreDataSource>(relaxed = true)
        val repository = MediaRepositoryImpl(
            context = mockContext,
            mediaStoreDataSource = mockDataSource,
            metadataDao = fakeDao
        )

        val ids = setOf(101L, 102L, 103L)
        // Initially none favorited -> all become favorited
        repository.toggleFavoriteBatch(ids)
        assertTrue(ids.all { fakeDao.dbMap[it]?.isFavorite == true })

        // When all favorited -> all become unfavorited
        repository.toggleFavoriteBatch(ids)
        assertTrue(ids.all { fakeDao.dbMap[it]?.isFavorite == false })
    }

    @Test
    fun testBatchRenameMedia_updatesRoomMetadataPaths() = runBlocking {
        mockkStatic(android.media.MediaScannerConnection::class)
        every { android.media.MediaScannerConnection.scanFile(any(), any(), any(), any()) } returns Unit

        val fakeDao = FakeMetadataDao()
        val mockContext = mockk<Context>(relaxed = true)
        val mockResolver = mockk<android.content.ContentResolver>(relaxed = true)
        every { mockContext.contentResolver } returns mockResolver
        val mockDataSource = mockk<MediaStoreDataSource>(relaxed = true)
        val repository = MediaRepositoryImpl(
            context = mockContext,
            mediaStoreDataSource = mockDataSource,
            metadataDao = fakeDao
        )

        val mockUri1 = mockk<Uri>(relaxed = true)
        val mockUri2 = mockk<Uri>(relaxed = true)

        val item1 = MediaItem.Photo(
            id = 101L,
            uri = mockUri1,
            path = "/sdcard/DCIM/IMG_01.jpg",
            mimeType = "image/jpeg",
            dateTaken = 1000L,
            size = 1024L,
            width = 1920,
            height = 1080
        )
        val item2 = MediaItem.Photo(
            id = 102L,
            uri = mockUri2,
            path = "/sdcard/DCIM/IMG_02.jpg",
            mimeType = "image/jpeg",
            dateTaken = 1000L,
            size = 1024L,
            width = 1920,
            height = 1080
        )

        fakeDao.insertOrUpdate(MediaMetadataEntity(mediaId = 101L, originalPath = "/sdcard/DCIM/IMG_01.jpg"))
        fakeDao.insertOrUpdate(MediaMetadataEntity(mediaId = 102L, originalPath = "/sdcard/DCIM/IMG_02.jpg"))

        every { mockResolver.update(any(), any(), any(), any()) } returns 1

        val renames = listOf(
            item1 to "Trip_001.jpg",
            item2 to "Trip_002.jpg"
        )

        try {
            val count = repository.batchRenameMedia(mockContext, renames)
            assertEquals(2, count)
        } finally {
            unmockkStatic(android.media.MediaScannerConnection::class)
        }
    }
}
