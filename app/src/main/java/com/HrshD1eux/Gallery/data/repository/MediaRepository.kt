package com.HrshD1eux.Gallery.data.repository

import com.HrshD1eux.Gallery.data.media.BucketInfo
import com.HrshD1eux.Gallery.data.model.MediaItem
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun getMediaFlow(bucketId: Long? = null): Flow<List<MediaItem>>
    suspend fun loadMediaPaged(limit: Int, offset: Int, bucketId: Long? = null): List<MediaItem>
    suspend fun getBuckets(): List<BucketInfo>
    fun getBucketsFlow(): Flow<List<BucketInfo>>
    
    suspend fun toggleFavorite(mediaItem: MediaItem)
    suspend fun toggleHidden(context: android.content.Context, mediaItem: MediaItem)
    suspend fun toggleTrashed(mediaItem: MediaItem)
    
    fun getFavoriteMediaFlow(): Flow<List<MediaItem>>
    fun getTrashedMediaFlow(): Flow<List<MediaItem>>
    fun getHiddenMediaFlow(): Flow<List<MediaItem>>
    suspend fun deleteMetadataPermanently(mediaId: Long)
    suspend fun deleteOrphanedMetadata(activeIds: List<Long>)
    suspend fun getActiveMediaIds(): List<Long>
    suspend fun clearVaultCache(context: android.content.Context)
    fun observeMediaChanges(): Flow<Unit>
}
