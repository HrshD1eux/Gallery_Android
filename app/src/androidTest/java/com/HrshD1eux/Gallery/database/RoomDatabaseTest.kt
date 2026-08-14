package com.HrshD1eux.Gallery.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.HrshD1eux.Gallery.data.database.GalleryDatabase
import com.HrshD1eux.Gallery.data.database.MediaMetadataEntity
import com.HrshD1eux.Gallery.data.database.MetadataDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomDatabaseTest {

    private lateinit var database: GalleryDatabase
    private lateinit var metadataDao: MetadataDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, GalleryDatabase::class.java).build()
        metadataDao = database.metadataDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertAndGetMetadata() = runBlocking {
        val entity = MediaMetadataEntity(
            mediaId = 1001L,
            isFavorite = true,
            isHidden = false,
            isTrashed = false,
            originalPath = "/storage/emulated/0/DCIM/Photo.jpg"
        )
        metadataDao.insertOrUpdate(entity)

        val retrieved = metadataDao.getMetadataForMedia(1001L)
        assertNotNull(retrieved)
        assertEquals(1001L, retrieved?.mediaId)
        assertTrue(retrieved?.isFavorite == true)
        assertEquals("/storage/emulated/0/DCIM/Photo.jpg", retrieved?.originalPath)
    }

    @Test
    fun batchQueryAndDeletion() = runBlocking {
        val entities = (1L..5L).map { id ->
            MediaMetadataEntity(mediaId = id, isFavorite = (id % 2 == 0L))
        }
        entities.forEach { metadataDao.insertOrUpdate(it) }

        val queried = metadataDao.getMetadataForMediaIds(listOf(2L, 4L))
        assertEquals(2, queried.size)

        metadataDao.deleteMetadataByIds(listOf(1L, 2L, 3L))
        val remaining = metadataDao.getAllMetadata()
        assertEquals(2, remaining.size)
        assertNull(metadataDao.getMetadataForMedia(1L))
    }
}
