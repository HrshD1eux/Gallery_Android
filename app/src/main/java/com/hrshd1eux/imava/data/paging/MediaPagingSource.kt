package com.hrshd1eux.imava.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.hrshd1eux.imava.data.media.MediaTypeFilter
import com.hrshd1eux.imava.data.model.MediaItem
import com.hrshd1eux.imava.data.repository.MediaRepository

class MediaPagingSource(
    private val repository: MediaRepository,
    private val bucketId: Long? = null,
    private val sortOrder: com.hrshd1eux.imava.ui.SortOrder = com.hrshd1eux.imava.ui.SortOrder.NEWEST_FIRST,
    private val mediaType: MediaTypeFilter = MediaTypeFilter.ALL
) : PagingSource<Int, MediaItem>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> {
        val offset = params.key ?: 0
        val limit = params.loadSize

        return try {
            val totalCount = repository.getTotalMediaCount(bucketId, mediaType)
            val items = repository.loadMediaPaged(limit = limit, offset = offset, bucketId = bucketId, sortOrder = sortOrder, mediaType = mediaType)
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
