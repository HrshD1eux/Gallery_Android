package com.HrshD1eux.Gallery.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.HrshD1eux.Gallery.data.model.MediaItem
import com.HrshD1eux.Gallery.data.repository.MediaRepository

class MediaPagingSource(
    private val repository: MediaRepository,
    private val bucketId: Long? = null,
    private val sortOrder: com.HrshD1eux.Gallery.ui.SortOrder = com.HrshD1eux.Gallery.ui.SortOrder.NEWEST_FIRST
) : PagingSource<Int, MediaItem>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> {
        val offset = params.key ?: 0
        val limit = params.loadSize

        return try {
            val totalCount = repository.getTotalMediaCount(bucketId)
            val items = repository.loadMediaPaged(limit = limit, offset = offset, bucketId = bucketId, sortOrder = sortOrder)
            val nextKey = if (items.size < limit || offset + items.size >= totalCount) null else offset + items.size
            val prevKey = if (offset == 0) null else (offset - limit).coerceAtLeast(0)
            val itemsBefore = offset.coerceAtMost(totalCount)
            val itemsAfter = (totalCount - (offset + items.size)).coerceAtLeast(0)

            LoadResult.Page(
                data = items,
                prevKey = prevKey,
                nextKey = nextKey,
                itemsBefore = itemsBefore,
                itemsAfter = itemsAfter
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(state.config.pageSize)
                ?: anchorPage?.nextKey?.minus(state.config.pageSize)
        }
    }
}
