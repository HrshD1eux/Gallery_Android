package com.HrshD1eux.Gallery.data.repository

import com.HrshD1eux.Gallery.data.media.BucketInfo
import com.HrshD1eux.Gallery.data.media.MediaTypeFilter
import com.HrshD1eux.Gallery.data.model.MediaItem
import kotlinx.coroutines.flow.Flow

data class DatePositionHeader(
    val title: String,
    val positionIndex: Int
)

interface MediaRepository {
    fun getMediaFlow(bucketId: Long? = null, sortOrder: com.HrshD1eux.Gallery.ui.SortOrder = com.HrshD1eux.Gallery.ui.SortOrder.NEWEST_FIRST, mediaType: MediaTypeFilter = MediaTypeFilter.ALL): Flow<List<MediaItem>>
    suspend fun loadMediaPaged(limit: Int, offset: Int, bucketId: Long? = null, sortOrder: com.HrshD1eux.Gallery.ui.SortOrder = com.HrshD1eux.Gallery.ui.SortOrder.NEWEST_FIRST, mediaType: MediaTypeFilter = MediaTypeFilter.ALL): List<MediaItem>
    suspend fun getTotalMediaCount(bucketId: Long? = null, mediaType: MediaTypeFilter = MediaTypeFilter.ALL): Int
    suspend fun getBuckets(): List<BucketInfo>
    fun getBucketsFlow(): Flow<List<BucketInfo>>
    
    suspend fun toggleFavorite(mediaItem: MediaItem)
    suspend fun toggleFavoriteBatch(mediaIds: Set<Long>)
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
    suspend fun getDatePositionIndex(bucketId: Long? = null, sortOrder: com.HrshD1eux.Gallery.ui.SortOrder = com.HrshD1eux.Gallery.ui.SortOrder.NEWEST_FIRST, mediaType: MediaTypeFilter = MediaTypeFilter.ALL): List<DatePositionHeader>
    suspend fun renameMedia(context: android.content.Context, mediaItem: MediaItem, newDisplayName: String): Boolean
    suspend fun batchRenameMedia(context: android.content.Context, itemsWithNewNames: List<Pair<MediaItem, String>>): Int
    suspend fun updateMediaDateTaken(context: android.content.Context, mediaItem: MediaItem, newDateMs: Long): Boolean
    suspend fun purgeExpiredTrashMedia(): Int
    suspend fun clearVaultCache(context: android.content.Context)
    suspend fun restoreAllVaultMedia(context: android.content.Context): Int
    suspend fun deleteAllVaultData(context: android.content.Context): Int
    fun observeMediaChanges(): Flow<Unit>
}
