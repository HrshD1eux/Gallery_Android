package com.HrshD1eux.Gallery.data.repository

import com.HrshD1eux.Gallery.data.media.BucketInfo
import com.HrshD1eux.Gallery.data.model.MediaItem
import kotlinx.coroutines.flow.Flow

data class DatePositionHeader(
    val title: String,
    val positionIndex: Int
)

interface MediaRepository {
    fun getMediaFlow(bucketId: Long? = null, sortOrder: com.HrshD1eux.Gallery.ui.SortOrder = com.HrshD1eux.Gallery.ui.SortOrder.NEWEST_FIRST): Flow<List<MediaItem>>
    suspend fun loadMediaPaged(limit: Int, offset: Int, bucketId: Long? = null, sortOrder: com.HrshD1eux.Gallery.ui.SortOrder = com.HrshD1eux.Gallery.ui.SortOrder.NEWEST_FIRST): List<MediaItem>
    suspend fun getBuckets(): List<BucketInfo>
    fun getBucketsFlow(): Flow<List<BucketInfo>>
    
    suspend fun toggleFavorite(mediaItem: MediaItem)
    suspend fun toggleHidden(context: android.content.Context, mediaItem: MediaItem)
    suspend fun toggleTrashed(mediaItem: MediaItem)
    
    fun getFavoriteMediaFlow(): Flow<List<MediaItem>>
    fun getTrashedMediaFlow(): Flow<List<MediaItem>>
    fun getHiddenMediaFlow(isVaultUnlocked: Boolean = false): Flow<List<MediaItem>>
    suspend fun deleteMetadataPermanently(mediaId: Long)
    suspend fun deleteOrphanedMetadata(activeIds: List<Long>)
    suspend fun getActiveMediaIds(): List<Long>
    suspend fun getMediaByIds(ids: Set<Long>): List<MediaItem>
    suspend fun searchMedia(query: String): List<MediaItem>
    suspend fun scanSecondaryMediaDirectories(): Int
    suspend fun getDatePositionIndex(bucketId: Long? = null, sortOrder: com.HrshD1eux.Gallery.ui.SortOrder = com.HrshD1eux.Gallery.ui.SortOrder.NEWEST_FIRST): List<DatePositionHeader>
    suspend fun renameMedia(context: android.content.Context, mediaItem: MediaItem, newDisplayName: String): Boolean
    suspend fun purgeExpiredTrashMedia(): Int
    suspend fun clearVaultCache(context: android.content.Context)
    fun observeMediaChanges(): Flow<Unit>
}
