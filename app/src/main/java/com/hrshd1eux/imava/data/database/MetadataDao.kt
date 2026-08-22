package com.hrshd1eux.imava.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MetadataDao {
    @Query("SELECT * FROM media_metadata WHERE mediaId = :mediaId")
    suspend fun getMetadataForMedia(mediaId: Long): MediaMetadataEntity?

    @Query("SELECT * FROM media_metadata")
    fun getAllMetadataFlow(): Flow<List<MediaMetadataEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(metadata: MediaMetadataEntity)

    @Query("UPDATE media_metadata SET isFavorite = :isFavorite WHERE mediaId = :mediaId")
    suspend fun updateFavorite(mediaId: Long, isFavorite: Boolean)

    @Query("UPDATE media_metadata SET isHidden = :isHidden WHERE mediaId = :mediaId")
    suspend fun updateHidden(mediaId: Long, isHidden: Boolean)

    @Query("UPDATE media_metadata SET isTrashed = :isTrashed, trashTime = :trashTime WHERE mediaId = :mediaId")
    suspend fun updateTrashed(mediaId: Long, isTrashed: Boolean, trashTime: Long)

    @Delete
    suspend fun delete(metadata: MediaMetadataEntity)
    
    @Query("SELECT mediaId FROM media_metadata WHERE isHidden = 0")
    suspend fun getTrackedNonHiddenIds(): List<Long>

    @Query("DELETE FROM media_metadata WHERE mediaId IN (:ids)")
    suspend fun deleteMetadataByIds(ids: List<Long>)

    @Query("SELECT * FROM media_metadata")
    suspend fun getAllMetadata(): List<MediaMetadataEntity>

    @Query("SELECT * FROM media_metadata WHERE isHidden = 1 OR isTrashed = 1")
    suspend fun getHiddenOrTrashedMetadata(): List<MediaMetadataEntity>

    @Query("SELECT * FROM media_metadata WHERE mediaId IN (:ids)")
    suspend fun getMetadataForMediaIds(ids: List<Long>): List<MediaMetadataEntity>

    @Query("SELECT mediaId FROM media_metadata WHERE isFavorite = 1 AND isHidden = 0 AND isTrashed = 0")
    fun getFavoriteIdsFlow(): Flow<List<Long>>

    @Query("SELECT mediaId FROM media_metadata WHERE isTrashed = 1")
    fun getTrashedIdsFlow(): Flow<List<Long>>

    @Query("SELECT * FROM media_metadata WHERE isTrashed = 1 AND trashTime > 0 AND trashTime < :cutoffTimestamp")
    suspend fun getExpiredTrashItems(cutoffTimestamp: Long): List<MediaMetadataEntity>

    @Query("DELETE FROM media_metadata WHERE mediaId = :mediaId")
    suspend fun deleteByMediaId(mediaId: Long)
}
