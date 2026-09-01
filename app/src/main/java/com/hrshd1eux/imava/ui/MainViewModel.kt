package com.hrshd1eux.imava.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrshd1eux.imava.core.util.SharingUtils
import com.hrshd1eux.imava.data.media.BucketInfo
import com.hrshd1eux.imava.data.model.MediaItem
import com.hrshd1eux.imava.data.repository.MediaRepository
import com.hrshd1eux.imava.ui.selection.SelectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import com.hrshd1eux.imava.data.model.TimelineItem
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import com.hrshd1eux.imava.data.model.isVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import javax.inject.Inject

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import androidx.paging.filter
import com.hrshd1eux.imava.data.paging.MediaPagingSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

private data class PagingParams(
    val bucketId: Long?,
    val category: String?,
    val sortMode: TimelineSortMode,
    val favs: List<MediaItem>,
    val trash: List<MediaItem>,
    val vault: List<MediaItem>
)

enum class TimelineSortMode {
    DATE_GROUPED,
    FLAT_NEWEST_FIRST
}

enum class GridStyle {
    SQUARE,
    NATURAL
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val application: android.app.Application,
    private val repository: MediaRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val prefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    val selectionState = SelectionState()
    var pendingActionItem: MediaItem? = null
    
    var editingMediaItem by mutableStateOf<MediaItem.Photo?>(null)

    suspend fun saveEditedPhoto(context: Context, originalItem: MediaItem.Photo, editedBitmap: Bitmap) = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val filename = "Edited_${System.currentTimeMillis()}.jpg"
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Edited")
            }
            val targetUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (targetUri != null) {
                resolver.openOutputStream(targetUri)?.use { output ->
                    editedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
                }

                com.hrshd1eux.imava.core.util.PhotoEditorUtils.copyExifAttributes(
                    context,
                    originalItem.uri,
                    targetUri
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val refreshTrigger = MutableStateFlow(0L)

    private val vaultState = com.hrshd1eux.imava.ui.vault.VaultStateHolder()

    val vaultConfigVersion: StateFlow<Int> = vaultState.vaultConfigVersion
    val isVaultUnlocked: StateFlow<Boolean> = vaultState.isVaultUnlocked
    val isDecoyVault: Boolean get() = vaultState.isDecoyVault

    fun notifyVaultConfigChanged() = vaultState.notifyVaultConfigChanged()
    fun unlockVault() = vaultState.unlockVault()
    fun unlockDecoyVault() = vaultState.unlockDecoyVault()

    fun lockVault(context: Context) {
        if (currentCategoryName == "Hidden Vault") {
            currentCategoryName = null
            currentScreen = Screen.Albums
        }
        vaultState.lockVault(context)
    }

    fun onAppBackgrounded() {
        vaultState.onAppBackgrounded()
    }

    fun onAppForegrounded(context: Context) {
        vaultState.onAppForegrounded(context)
    }

    private var _appThemeState = mutableStateOf(prefs.getString("app_theme", "system") ?: "system")
    var appTheme: String
        get() = _appThemeState.value
        set(value) {
            _appThemeState.value = value
            prefs.edit().putString("app_theme", value).apply()
            savedStateHandle["app_theme"] = value
        }

    suspend fun scanSecondaryMediaDirectories(): Int {
        return repository.scanSecondaryMediaDirectories()
    }

    private var _currentScreenState = mutableStateOf(savedStateHandle.get<Screen>("current_screen") ?: Screen.Photos)
    var currentScreen: Screen
        get() = _currentScreenState.value
        set(value) {
            _currentScreenState.value = value
            savedStateHandle["current_screen"] = value
        }

    private var _activeMediaIdState = mutableStateOf(savedStateHandle.get<Long>("active_media_id"))
    var activeMediaId: Long?
        get() = _activeMediaIdState.value
        set(value) {
            _activeMediaIdState.value = value
            savedStateHandle["active_media_id"] = value
        }

    private var _activeMediaItemState = mutableStateOf<MediaItem?>(null)
    var activeMediaItem: MediaItem?
        get() = _activeMediaItemState.value
        set(value) {
            _activeMediaItemState.value = value
            _activeMediaIdState.value = value?.id
            savedStateHandle["active_media_id"] = value?.id
        }
    
    private var _currentBucketIdState = mutableStateOf(savedStateHandle.get<Long>("current_bucket_id"))
    var currentBucketId: Long?
        get() = _currentBucketIdState.value
        set(value) {
            _currentBucketIdState.value = value
            savedStateHandle["current_bucket_id"] = value
        }

    private var _currentBucketNameState = mutableStateOf(savedStateHandle.get<String>("current_bucket_name"))
    var currentBucketName: String?
        get() = _currentBucketNameState.value
        set(value) {
            _currentBucketNameState.value = value
            savedStateHandle["current_bucket_name"] = value
        }

    private val _currentCategoryName = MutableStateFlow<String?>(savedStateHandle.get<String>("current_category_name")?.takeIf { it != "Hidden Vault" })
    val currentCategoryNameFlow: StateFlow<String?> = _currentCategoryName.asStateFlow()
    var currentCategoryName: String?
        get() = _currentCategoryName.value
        set(value) {
            _currentCategoryName.value = value
            savedStateHandle["current_category_name"] = value
        }

    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems: StateFlow<List<MediaItem>> = _mediaItems.asStateFlow()

    private val _buckets = MutableStateFlow<List<BucketInfo>>(emptyList())
    val buckets: StateFlow<List<BucketInfo>> = _buckets.asStateFlow()

    private val _excludedBucketIds = MutableStateFlow<Set<String>>(
        (prefs.getStringSet("excluded_buckets", null)
            ?: application.getSharedPreferences("album_prefs", Context.MODE_PRIVATE).getStringSet("excluded_buckets", emptySet())
            ?: emptySet())
    )
    val excludedBucketIds: StateFlow<Set<String>> = _excludedBucketIds.asStateFlow()

    enum class AlbumSortOrder {
        NAME_ASC,
        COUNT_DESC,
        RECENT
    }

    enum class AlbumLayoutMode {
        LIST,
        GRID_2, // Large
        GRID_3, // Medium
        GRID_4  // Small
    }

    private val _albumSortOrder = MutableStateFlow(
        try {
            AlbumSortOrder.valueOf(prefs.getString("album_sort_order", AlbumSortOrder.NAME_ASC.name) ?: AlbumSortOrder.NAME_ASC.name)
        } catch (_: Exception) {
            AlbumSortOrder.NAME_ASC
        }
    )
    val albumSortOrder: StateFlow<AlbumSortOrder> = _albumSortOrder.asStateFlow()

    fun setAlbumSortOrder(order: AlbumSortOrder) {
        _albumSortOrder.value = order
        prefs.edit().putString("album_sort_order", order.name).apply()
    }

    private val _albumLayoutMode = MutableStateFlow(
        try {
            AlbumLayoutMode.valueOf(prefs.getString("album_layout_mode", AlbumLayoutMode.GRID_2.name) ?: AlbumLayoutMode.GRID_2.name)
        } catch (_: Exception) {
            AlbumLayoutMode.GRID_2
        }
    )
    val albumLayoutMode: StateFlow<AlbumLayoutMode> = _albumLayoutMode.asStateFlow()

    fun setAlbumLayoutMode(mode: AlbumLayoutMode) {
        _albumLayoutMode.value = mode
        prefs.edit().putString("album_layout_mode", mode.name).apply()
    }

    private val _customAlbumCovers = MutableStateFlow<Map<Long, Long>>(loadCustomAlbumCovers())
    val customAlbumCovers: StateFlow<Map<Long, Long>> = _customAlbumCovers.asStateFlow()

    private fun loadCustomAlbumCovers(): Map<Long, Long> {
        val map = mutableMapOf<Long, Long>()
        val allPrefs = prefs.all
        for ((k, v) in allPrefs) {
            if (k.startsWith("custom_cover_")) {
                val bucketId = k.removePrefix("custom_cover_").toLongOrNull()
                val mediaId = (v as? Long) ?: (v as? String)?.toLongOrNull()
                if (bucketId != null && mediaId != null) {
                    map[bucketId] = mediaId
                }
            }
        }
        return map
    }

    fun setCustomAlbumCover(bucketId: Long, mediaId: Long) {
        val newMap = _customAlbumCovers.value.toMutableMap()
        newMap[bucketId] = mediaId
        _customAlbumCovers.value = newMap
        prefs.edit().putLong("custom_cover_$bucketId", mediaId).apply()
        refreshAll()
    }

    private val _pinnedBucketIds = MutableStateFlow<Set<String>>(
        (prefs.getStringSet("pinned_buckets", null)
            ?: application.getSharedPreferences("album_prefs", Context.MODE_PRIVATE).getStringSet("pinned_buckets", emptySet())
            ?: emptySet())
    )
    val pinnedBucketIds: StateFlow<Set<String>> = _pinnedBucketIds.asStateFlow()

    val visibleBuckets: StateFlow<List<BucketInfo>> = combine(
        _buckets, _excludedBucketIds, _albumSortOrder
    ) { list, excluded, sortOrder ->
        val filtered = list.filter { !excluded.contains(it.id.toString()) }
        when (sortOrder) {
            AlbumSortOrder.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            AlbumSortOrder.COUNT_DESC -> filtered.sortedByDescending { it.count }
            AlbumSortOrder.RECENT -> filtered
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun excludeBucket(bucketId: Long) {
        val newSet = _excludedBucketIds.value.toMutableSet().apply { add(bucketId.toString()) }
        _excludedBucketIds.value = newSet
        prefs.edit().putStringSet("excluded_buckets", newSet).apply()
        application.getSharedPreferences("album_prefs", Context.MODE_PRIVATE).edit().putStringSet("excluded_buckets", newSet).apply()
        refreshAll()
    }

    fun restoreExcludedBucket(bucketId: String) {
        val newSet = _excludedBucketIds.value.toMutableSet().apply { remove(bucketId) }
        _excludedBucketIds.value = newSet
        prefs.edit().putStringSet("excluded_buckets", newSet).apply()
        application.getSharedPreferences("album_prefs", Context.MODE_PRIVATE).edit().putStringSet("excluded_buckets", newSet).apply()
        refreshAll()
    }

    fun togglePinBucket(bucketId: Long) {
        val isPinned = _pinnedBucketIds.value.contains(bucketId.toString())
        val newSet = _pinnedBucketIds.value.toMutableSet().apply {
            if (isPinned) remove(bucketId.toString()) else add(bucketId.toString())
        }
        _pinnedBucketIds.value = newSet
        prefs.edit().putStringSet("pinned_buckets", newSet).apply()
        application.getSharedPreferences("album_prefs", Context.MODE_PRIVATE).edit().putStringSet("pinned_buckets", newSet).apply()
    }

    private val _favorites = MutableStateFlow<List<MediaItem>>(emptyList())
    val favorites: StateFlow<List<MediaItem>> = _favorites.asStateFlow()

    private val _trashed = MutableStateFlow<List<MediaItem>>(emptyList())
    val trashed: StateFlow<List<MediaItem>> = _trashed.asStateFlow()

    private val _hidden = MutableStateFlow<List<MediaItem>>(emptyList())
    val hidden: StateFlow<List<MediaItem>> = _hidden.asStateFlow()

    data class StorageStats(
        val photosCount: Int = 0,
        val videosCount: Int = 0,
        val vaultCount: Int = 0,
        val trashCount: Int = 0,
        val photosBytes: Long = 0L,
        val videosBytes: Long = 0L,
        val vaultBytes: Long = 0L,
        val trashBytes: Long = 0L
    ) {
        val totalBytes: Long get() = photosBytes + videosBytes + vaultBytes + trashBytes
        val formattedTotal: String get() = com.hrshd1eux.imava.core.util.FormatUtils.formatFileSize(totalBytes)
        val formattedPhotos: String get() = com.hrshd1eux.imava.core.util.FormatUtils.formatFileSize(photosBytes)
        val formattedVideos: String get() = com.hrshd1eux.imava.core.util.FormatUtils.formatFileSize(videosBytes)
        val formattedVault: String get() = com.hrshd1eux.imava.core.util.FormatUtils.formatFileSize(vaultBytes)
        val formattedTrash: String get() = com.hrshd1eux.imava.core.util.FormatUtils.formatFileSize(trashBytes)
    }

    val storageBreakdown: StateFlow<StorageStats> = combine(
        mediaItems, hidden, trashed, _excludedBucketIds
    ) { raw, vault, trash, excluded ->
        var photosBytes = 0L
        var videosBytes = 0L
        var photosCount = 0
        var videosCount = 0
        for (item in raw) {
            if (!excluded.contains(item.bucketId.toString())) {
                if (item.isVideo) {
                    videosBytes += item.size
                    videosCount++
                } else {
                    photosBytes += item.size
                    photosCount++
                }
            }
        }
        val vaultBytes = vault.sumOf { it.size }
        val trashBytes = trash.sumOf { it.size }
        StorageStats(
            photosCount = photosCount,
            videosCount = videosCount,
            vaultCount = vault.size,
            trashCount = trash.size,
            photosBytes = photosBytes,
            videosBytes = videosBytes,
            vaultBytes = vaultBytes,
            trashBytes = trashBytes
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StorageStats())

    val videosCount: StateFlow<Int> = mediaItems.map { list ->
        list.count { it is MediaItem.Video }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val visibleMediaItems: StateFlow<List<MediaItem>> = combine(
        combine(mediaItems, favorites) { m, f -> Pair(m, f) },
        combine(trashed, hidden) { t, h -> Pair(t, h) },
        _currentCategoryName,
        _excludedBucketIds
    ) { (raw, favs), (trash, vault), category, excluded ->
        val list = when (category) {
            "Favorites" -> favs
            "Trash" -> trash
            "Hidden Vault" -> if (isDecoyVault) emptyList() else vault
            "Videos" -> raw.filterIsInstance<MediaItem.Video>().let { if (currentBucketId != null) it else it.filter { item -> !excluded.contains(item.bucketId.toString()) } }
            else -> if (currentBucketId != null) raw else raw.filter { !excluded.contains(it.bucketId.toString()) }
        }
        if (sortOrder == SortOrder.OLDEST_FIRST) {
            list.sortedBy { it.dateTaken }
        } else {
            list.sortedByDescending { it.dateTaken }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var activeMemoryStory by mutableStateOf<com.hrshd1eux.imava.ui.timeline.MemoryStory?>(null)

    private val _memoriesDismissedTimestamp = MutableStateFlow(prefs.getLong("memories_dismissed_at", 0L))
    val memoriesDismissedTimestamp: StateFlow<Long> = _memoriesDismissedTimestamp.asStateFlow()

    fun dismissMemoriesFor24Hours() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong("memories_dismissed_at", now).apply()
        _memoriesDismissedTimestamp.value = now
    }

    val throwbackMemories: StateFlow<List<com.hrshd1eux.imava.ui.timeline.MemoryStory>> = combine(
        visibleMediaItems,
        _memoriesDismissedTimestamp
    ) { items, dismissedTimestamp ->
        val now = System.currentTimeMillis()
        if (dismissedTimestamp > 0L && (now - dismissedTimestamp) < 24 * 60 * 60 * 1000L) {
            emptyList()
        } else {
            com.hrshd1eux.imava.ui.timeline.MemoryStoryCalculator.generateStories(items)
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDistinctTags: StateFlow<List<String>> = visibleMediaItems
        .map { items ->
            items.flatMap { it.tags }
                .map { it.trim().removePrefix("#").lowercase() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val searchResults: StateFlow<List<MediaItem>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            flow {
                if (query.isBlank()) {
                    emit(emptyList())
                } else {
                    emit(repository.searchMedia(query))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var _sortModeState = mutableStateOf(
        try {
            TimelineSortMode.valueOf(prefs.getString("sort_mode", TimelineSortMode.DATE_GROUPED.name) ?: TimelineSortMode.DATE_GROUPED.name)
        } catch (_: Exception) { TimelineSortMode.DATE_GROUPED }
    )
    var sortMode: TimelineSortMode
        get() = _sortModeState.value
        set(value) {
            _sortModeState.value = value
            prefs.edit().putString("sort_mode", value.name).apply()
        }

    fun toggleSortMode() {
        sortMode = if (sortMode == TimelineSortMode.DATE_GROUPED) {
            TimelineSortMode.FLAT_NEWEST_FIRST
        } else {
            TimelineSortMode.DATE_GROUPED
        }
    }

    private var _sortOrderState = mutableStateOf(
        try {
            SortOrder.valueOf(prefs.getString("sort_order", SortOrder.NEWEST_FIRST.name) ?: SortOrder.NEWEST_FIRST.name)
        } catch (_: Exception) { SortOrder.NEWEST_FIRST }
    )
    var sortOrder: SortOrder
        get() = _sortOrderState.value
        set(value) {
            _sortOrderState.value = value
            prefs.edit().putString("sort_order", value.name).apply()
            refreshTrigger.value++
        }

    fun toggleSortOrder() {
        sortOrder = if (sortOrder == SortOrder.NEWEST_FIRST) SortOrder.OLDEST_FIRST else SortOrder.NEWEST_FIRST
    }

    private var _gridStyleState = mutableStateOf(
        try {
            GridStyle.valueOf(prefs.getString("grid_style", GridStyle.NATURAL.name) ?: GridStyle.NATURAL.name)
        } catch (_: Exception) { GridStyle.NATURAL }
    )
    var gridStyle: GridStyle
        get() = _gridStyleState.value
        set(value) {
            _gridStyleState.value = value
            prefs.edit().putString("grid_style", value.name).apply()
        }

    fun toggleGridStyle() {
        gridStyle = if (gridStyle == GridStyle.NATURAL) GridStyle.SQUARE else GridStyle.NATURAL
    }

    private var _gridColumnCountState = mutableStateOf(
        prefs.getInt("grid_column_count", 3).coerceIn(1, 6)
    )
    var gridColumnCount: Int
        get() = _gridColumnCountState.value
        set(value) {
            val clamped = value.coerceIn(1, 6)
            if (_gridColumnCountState.value != clamped) {
                _gridColumnCountState.value = clamped
                prefs.edit().putInt("grid_column_count", clamped).apply()
            }
        }

    fun setGridColumns(columns: Int) {
        gridColumnCount = columns
    }

    fun increaseZoom() {
        if (gridColumnCount > 1) {
            gridColumnCount--
        }
    }

    fun decreaseZoom() {
        if (gridColumnCount < 6) {
            gridColumnCount++
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val datePositionHeaders: StateFlow<List<com.hrshd1eux.imava.data.repository.DatePositionHeader>> = combine(
        snapshotFlow { currentBucketId },
        snapshotFlow { sortOrder },
        refreshTrigger
    ) { bucketId, order, _ ->
        Pair(bucketId, order)
    }.flatMapLatest { (bucketId, order) ->
        flow {
            emit(repository.getDatePositionIndex(bucketId, order))
        }
    }.flowOn(Dispatchers.IO)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagingDataFlow: Flow<PagingData<TimelineItem>> = combine(
        combine(snapshotFlow { currentBucketId }, _currentCategoryName) { bId, cat -> Pair(bId, cat) },
        combine(snapshotFlow { sortMode }, snapshotFlow { sortOrder }) { mode, order -> Pair(mode, order) },
        _excludedBucketIds,
        refreshTrigger
    ) { (bucketId, category), (mode, order), excluded, _ ->
        val itemsFlow: Flow<PagingData<MediaItem>> = when (category) {
            "Favorites" -> favorites.map { list ->
                val filtered = if (bucketId != null) list else list.filter { !excluded.contains(it.bucketId.toString()) }
                val sorted = if (order == SortOrder.OLDEST_FIRST) filtered.sortedBy { it.dateTaken } else filtered.sortedByDescending { it.dateTaken }
                PagingData.from(sorted)
            }
            "Trash" -> trashed.map { list ->
                val sorted = if (order == SortOrder.OLDEST_FIRST) list.sortedBy { it.dateTaken } else list.sortedByDescending { it.dateTaken }
                PagingData.from(sorted)
            }
            "Hidden Vault" -> hidden.map { list ->
                val sorted = if (order == SortOrder.OLDEST_FIRST) list.sortedBy { it.dateTaken } else list.sortedByDescending { it.dateTaken }
                PagingData.from(sorted)
            }
            "Videos" -> Pager(
                config = PagingConfig(pageSize = 60, prefetchDistance = 40, enablePlaceholders = false),
                pagingSourceFactory = { MediaPagingSource(repository, bucketId, order, com.hrshd1eux.imava.data.media.MediaTypeFilter.VIDEOS) }
            ).flow.map { pagingData ->
                if (bucketId != null) pagingData else pagingData.filter { !excluded.contains(it.bucketId.toString()) }
            }
            else -> Pager(
                config = PagingConfig(pageSize = 60, prefetchDistance = 40, enablePlaceholders = false),
                pagingSourceFactory = { MediaPagingSource(repository, bucketId, order) }
            ).flow.map { pagingData ->
                if (bucketId != null) pagingData else pagingData.filter { !excluded.contains(it.bucketId.toString()) }
            }
        }
        itemsFlow.map { pagingData ->
            val mapped: PagingData<TimelineItem> = pagingData.map { TimelineItem.Media(it) }
            if (mode == TimelineSortMode.DATE_GROUPED) {
                mapped.insertSeparators { before: TimelineItem?, after: TimelineItem? ->
                    val zoneId = ZoneId.systemDefault()
                    val today = LocalDate.now(zoneId)
                    val yesterday = today.minusDays(1)
                    val sameYearFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
                    val otherYearFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())

                    fun getHeaderTitle(dateMs: Long): String {
                        val ms = if (dateMs > 0) dateMs else System.currentTimeMillis()
                        val localDate = Instant.ofEpochMilli(ms).atZone(zoneId).toLocalDate()
                        return when (localDate) {
                            today -> "Today"
                            yesterday -> "Yesterday"
                            else -> if (localDate.year == today.year) {
                                localDate.format(sameYearFormatter)
                            } else {
                                localDate.format(otherYearFormatter)
                            }
                        }
                    }

                    if (before == null && after is TimelineItem.Media) {
                        TimelineItem.Header(getHeaderTitle(after.item.dateTaken))
                    } else if (before is TimelineItem.Media && after is TimelineItem.Media) {
                        val beforeTitle = getHeaderTitle(before.item.dateTaken)
                        val afterTitle = getHeaderTitle(after.item.dateTaken)
                        if (beforeTitle != afterTitle) {
                            TimelineItem.Header(afterTitle)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
            } else {
                mapped
            }
        }
    }.flatMapLatest { it }.cachedIn(viewModelScope)

    private val PAGE_SIZE = 200

    init {
        loadNextPage()
        loadBuckets()
        
        viewModelScope.launch {
            combine(
                snapshotFlow { currentBucketId },
                snapshotFlow { sortOrder },
                refreshTrigger
            ) { bucketId, order, _ ->
                Pair(bucketId, order)
            }.flatMapLatest { (bucketId, order) ->
                repository.getMediaFlow(bucketId, order)
            }.collect { items ->
                _mediaItems.value = items
            }
        }

        viewModelScope.launch {
            repository.getBucketsFlow().collect {
                _buckets.value = it
            }
        }
        viewModelScope.launch {
            repository.getFavoriteMediaFlow().collect {
                _favorites.value = it
            }
        }
        viewModelScope.launch {
            repository.getTrashedMediaFlow().collect {
                _trashed.value = it
            }
        }
        viewModelScope.launch {
            isVaultUnlocked.flatMapLatest { unlocked ->
                repository.getHiddenMediaFlow(unlocked)
            }.collect {
                _hidden.value = it
            }
        }
        

        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            repository.observeMediaChanges()
                .debounce(300)
                .collectLatest {
                    refreshAll()
                }
        }

        // cleanup after boot
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.purgeExpiredTrashMedia()
                kotlinx.coroutines.delay(2000) // defer scan for cold boot performance
                repository.scanSecondaryMediaDirectories()
                val activeIds = repository.getActiveMediaIds()
                if (activeIds.isNotEmpty()) {
                    repository.deleteOrphanedMetadata(activeIds)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    var pendingRenameItem: MediaItem? = null
    var pendingRenameName: String? = null

    fun renameMedia(context: Context, item: MediaItem, newName: String) {
        viewModelScope.launch {
            try {
                val success = repository.renameMedia(context, item, newName)
                if (success) {
                    applyRenamedState(item, newName)
                }
            } catch (e: Exception) {
                val recoverable = e as? android.app.RecoverableSecurityException
                    ?: e.cause as? android.app.RecoverableSecurityException
                val isSec = e is SecurityException || e.cause is SecurityException
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && (isSec || recoverable != null)) {
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        pendingRenameItem = item
                        pendingRenameName = newName
                        val pendingIntent = android.provider.MediaStore.createWriteRequest(
                            context.contentResolver,
                            listOf(item.uri)
                        )
                        activity.startIntentSenderForResult(
                            pendingIntent.intentSender,
                            1006,
                            null,
                            0,
                            0,
                            0
                        )
                    }
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && recoverable != null) {
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        pendingRenameItem = item
                        pendingRenameName = newName
                        activity.startIntentSenderForResult(
                            recoverable.userAction.actionIntent.intentSender,
                            1006,
                            null,
                            0,
                            0,
                            0
                        )
                    }
                } else {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun applyRenamedState(item: MediaItem, newName: String) {
        val file = java.io.File(item.path)
        val ext = file.extension.ifEmpty { "jpg" }
        val finalName = if (newName.contains(".")) newName else "$newName.$ext"
        val newPath = if (file.parentFile != null) java.io.File(file.parentFile, finalName).absolutePath else item.path

        val currentActive = activeMediaItem
        if (currentActive?.id == item.id) {
            activeMediaItem = when (currentActive) {
                is MediaItem.Photo -> currentActive.copy(path = newPath)
                is MediaItem.Video -> currentActive.copy(path = newPath)
            }
        }
        refreshAll()
    }

    fun updateMediaDateTaken(context: Context, item: MediaItem, newDateMs: Long) {
        viewModelScope.launch {
            val success = repository.updateMediaDateTaken(context, item, newDateMs)
            if (success) {
                val currentActive = activeMediaItem
                if (currentActive?.id == item.id) {
                    activeMediaItem = when (currentActive) {
                        is MediaItem.Photo -> currentActive.copy(dateTaken = newDateMs)
                        is MediaItem.Video -> currentActive.copy(dateTaken = newDateMs)
                    }
                }
                refreshAll()
            }
        }
    }

    fun loadNextPage() {
        viewModelScope.launch {
            val items = repository.loadMediaPaged(
                limit = Int.MAX_VALUE,
                offset = 0,
                bucketId = currentBucketId,
                sortOrder = sortOrder
            )
            _mediaItems.value = if (currentBucketId != null) items else items.filter { !_excludedBucketIds.value.contains(it.bucketId.toString()) }
        }
    }

    fun selectBucket(bucketId: Long?, bucketName: String?) {
        currentCategoryName = null // Reset category filter when selecting a folder
        currentBucketId = bucketId
        currentBucketName = bucketName
        currentScreen = Screen.Photos
        loadNextPage()
    }

    fun clearVaultCache(context: Context) {
        viewModelScope.launch {
            repository.clearVaultCache(context)
        }
    }

    fun loadMediaStream() {
        refreshAll()
    }

    suspend fun getAllPhotosForDuplicateScan(): List<MediaItem.Photo> = withContext(Dispatchers.IO) {
        val allMedia = repository.loadMediaPaged(limit = 5000, offset = 0, bucketId = null)
        allMedia.filterIsInstance<MediaItem.Photo>()
    }

    fun loadBuckets() {
        viewModelScope.launch(Dispatchers.IO) {
            _buckets.value = repository.getBuckets()
        }
    }

    fun refreshAll() {
        refreshTrigger.value++
        loadBuckets()
        loadNextPage()
    }

    fun moveMediaToFolder(context: Context, items: List<MediaItem>, folderName: String) {
        val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
        val targetDir = java.io.File(picturesDir, folderName)
        moveOrCopyMedia(context, items, targetDir, isCopy = false) {}
    }

    fun createEmptyAlbum(context: Context, albumName: String) {
        viewModelScope.launch {
            try {
                val userPrefs = context.getSharedPreferences("user_albums", Context.MODE_PRIVATE)
                val currentSet = userPrefs.getStringSet("created_albums", emptySet()) ?: emptySet()
                val updatedSet = currentSet.toMutableSet().apply { add(albumName) }
                userPrefs.edit().putStringSet("created_albums", updatedSet).commit()

                val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                val newAlbumDir = java.io.File(picturesDir, albumName)
                if (!newAlbumDir.exists()) {
                    newAlbumDir.mkdirs()
                }
                refreshAll()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteAlbum(context: Context, bucketId: Long, bucketName: String) {
        viewModelScope.launch {
            try {
                val userPrefs = context.getSharedPreferences("user_albums", Context.MODE_PRIVATE)
                val currentSet = userPrefs.getStringSet("created_albums", emptySet()) ?: emptySet()
                if (currentSet.contains(bucketName)) {
                    val updatedSet = currentSet.toMutableSet().apply { remove(bucketName) }
                    userPrefs.edit().putStringSet("created_albums", updatedSet).commit()
                }

                val itemsInAlbum = repository.loadMediaPaged(limit = 2000, offset = 0, bucketId = bucketId)
                if (itemsInAlbum.isNotEmpty()) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        pendingBatchActionItems = itemsInAlbum
                        val pendingIntent = android.provider.MediaStore.createTrashRequest(
                            context.contentResolver,
                            itemsInAlbum.map { it.uri },
                            true
                        )
                        val activity = context as? android.app.Activity
                        activity?.startIntentSenderForResult(pendingIntent.intentSender, 1005, null, 0, 0, 0)
                    } else {
                        itemsInAlbum.forEach { item ->
                            repository.toggleTrashed(item)
                        }
                    }
                }
                val albumFolder = java.io.File(
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES),
                    bucketName
                )
                if (albumFolder.exists()) {
                    albumFolder.deleteRecursively()
                }
                refreshAll()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleFavorite(item: MediaItem) {
        viewModelScope.launch {
            repository.toggleFavorite(item)
            // Sync current active view state
            if (activeMediaItem?.id == item.id) {
                activeMediaItem = when (val active = activeMediaItem) {
                    is MediaItem.Photo -> active.copy(isFavorite = !active.isFavorite)
                    is MediaItem.Video -> active.copy(isFavorite = !active.isFavorite)
                    null -> null
                }
            }
            refreshAll()
        }
    }

    fun updateMediaTags(item: MediaItem, tags: List<String>) {
        viewModelScope.launch {
            repository.updateMediaTags(item.id, tags)
            if (activeMediaItem?.id == item.id) {
                activeMediaItem = when (val active = activeMediaItem) {
                    is MediaItem.Photo -> active.copy(tags = tags)
                    is MediaItem.Video -> active.copy(tags = tags)
                    null -> null
                }
            }
            refreshAll()
        }
    }

    fun toggleFavoriteSelectedMedia(selectedIds: Set<Long>) {
        if (selectedIds.isEmpty()) return
        viewModelScope.launch {
            repository.toggleFavoriteBatch(selectedIds)
            selectionState.clear()
            refreshAll()
        }
    }

    private var pendingBatchRenames: List<Pair<MediaItem, String>>? = null

    fun batchRenameSelectedMedia(
        context: Context,
        renames: List<Pair<MediaItem, String>>
    ) {
        if (renames.isEmpty()) return
        viewModelScope.launch {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val activity = context as? android.app.Activity
                if (activity != null) {
                    try {
                        pendingBatchRenames = renames
                        val uris = renames.map { it.first.uri }
                        val pendingIntent = android.provider.MediaStore.createWriteRequest(
                            context.contentResolver,
                            uris
                        )
                        activity.startIntentSenderForResult(
                            pendingIntent.intentSender,
                            1008,
                            null,
                            0,
                            0,
                            0
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                        val count = repository.batchRenameMedia(context, renames)
                        selectionState.clear()
                        refreshAll()
                        if (count > 0) {
                            com.hrshd1eux.imava.core.util.HapticUtil.performSuccess(context)
                            android.widget.Toast.makeText(context, "Renamed $count items", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    val count = repository.batchRenameMedia(context, renames)
                    selectionState.clear()
                    refreshAll()
                    if (count > 0) {
                        com.hrshd1eux.imava.core.util.HapticUtil.performSuccess(context)
                        android.widget.Toast.makeText(context, "Renamed $count items", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                val count = repository.batchRenameMedia(context, renames)
                selectionState.clear()
                refreshAll()
                if (count > 0) {
                    com.hrshd1eux.imava.core.util.HapticUtil.performSuccess(context)
                    android.widget.Toast.makeText(context, "Renamed $count items", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private var pendingMoveSourceItems: List<MediaItem>? = null
    private var pendingMoveRollbackTargets: List<java.io.File>? = null

    fun moveOrCopyMedia(
        context: Context,
        items: List<MediaItem>,
        targetDirectory: java.io.File,
        isCopy: Boolean,
        onComplete: (Int) -> Unit
    ) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val result = repository.moveOrCopyMedia(context, items, targetDirectory, isCopy)
            val moveResult = result.getOrNull()
            val count = moveResult?.successCount ?: 0

            if (moveResult != null && moveResult.failedDeleteItems.isNotEmpty()) {
                pendingMoveSourceItems = moveResult.failedDeleteItems
                pendingMoveRollbackTargets = moveResult.createdTargetsForFailedDeletes

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        try {
                            val uris = moveResult.failedDeleteItems.map { it.uri }
                            val pendingIntent = android.provider.MediaStore.createDeleteRequest(
                                context.contentResolver,
                                uris
                            )
                            activity.startIntentSenderForResult(
                                pendingIntent.intentSender,
                                1007,
                                null,
                                0,
                                0,
                                0
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                            moveResult.createdTargetsForFailedDeletes.forEach { it.delete() }
                            pendingMoveSourceItems = null
                            pendingMoveRollbackTargets = null
                        }
                    }
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    loadBuckets()
                    refreshAll()
                }
            } else {
                loadBuckets()
                refreshAll()
                if (count > 0) {
                    com.hrshd1eux.imava.core.util.HapticUtil.performSuccess(context)
                    val actionName = if (isCopy) "Copied" else "Moved"
                    android.widget.Toast.makeText(context, "$actionName $count items to ${targetDirectory.name}", android.widget.Toast.LENGTH_SHORT).show()
                }
                onComplete(count)
            }
        }
    }

    fun moveOrCopySelectedMedia(
        context: Context,
        targetDirectory: java.io.File,
        isCopy: Boolean,
        onComplete: (Int) -> Unit
    ) {
        val selectedIds = selectionState.selectedIds.toSet()
        if (selectedIds.isEmpty()) return
        viewModelScope.launch {
            val items = getSelectedMediaItems(selectedIds)
            moveOrCopyMedia(context, items, targetDirectory, isCopy) { count ->
                selectionState.clear()
                onComplete(count)
            }
        }
    }

    fun shiftSelectedMediaTimestamp(
        context: Context,
        offsetMillis: Long,
        exactTimestamp: Long? = null,
        onComplete: (Int) -> Unit
    ) {
        val selectedIds = selectionState.selectedIds.toSet()
        if (selectedIds.isEmpty()) return
        viewModelScope.launch {
            val items = getSelectedMediaItems(selectedIds)
            val result = repository.shiftMediaTimestamps(context, items, offsetMillis, exactTimestamp)
            val count = result.getOrDefault(0)
            selectionState.clear()
            refreshAll()
            if (count > 0) {
                com.hrshd1eux.imava.core.util.HapticUtil.performSuccess(context)
                android.widget.Toast.makeText(context, "Adjusted date/time for $count items", android.widget.Toast.LENGTH_SHORT).show()
            }
            onComplete(count)
        }
    }

    fun disableVault(context: Context) {
        val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("vault_disabled", true)
            .remove("vault_pin_hash")
            .remove("vault_salt")
            .remove("vault_pin")
            .remove("vault_biometric_enabled")
            .remove("vault_stealth_mode")
            .apply()
        vaultState.setUnlocked(true)
        refreshAll()
    }

    fun deleteVault(context: Context, restoreMedia: Boolean, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (restoreMedia) {
                repository.restoreAllVaultMedia(context)
            } else {
                repository.deleteAllVaultData(context)
            }
            val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            vaultState.setUnlocked(false)
            currentCategoryName = null
            refreshAll()
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    val isVaultDisabled: Boolean
        get() {
            val prefs = application.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
            return prefs.getBoolean("vault_disabled", false)
        }

    fun toggleHidden(context: Context, item: MediaItem) {
        viewModelScope.launch {
            try {
                repository.toggleHidden(context, item)
                refreshAll()
            } catch (e: Exception) {
                val recoverable = e as? android.app.RecoverableSecurityException
                    ?: e.cause as? android.app.RecoverableSecurityException
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        pendingActionItem = item
                        val pendingIntent = android.provider.MediaStore.createDeleteRequest(
                            context.contentResolver,
                            listOf(item.uri)
                        )
                        activity.startIntentSenderForResult(
                            pendingIntent.intentSender,
                            1004,
                            null,
                            0,
                            0,
                            0
                        )
                    }
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && recoverable != null) {
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        pendingActionItem = item
                        activity.startIntentSenderForResult(
                            recoverable.userAction.actionIntent.intentSender,
                            1004,
                            null,
                            0,
                            0,
                            0
                        )
                    }
                }
            }
            if (activeMediaItem?.id == item.id) {
                activeMediaItem = null
            }
        }
    }

    fun toggleTrashed(context: Context, item: MediaItem) {
        viewModelScope.launch {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        pendingActionItem = item
                        val pendingIntent = android.provider.MediaStore.createTrashRequest(
                            context.contentResolver,
                            listOf(item.uri),
                            !item.isTrashed
                        )
                        activity.startIntentSenderForResult(
                            pendingIntent.intentSender, 1002, null, 0, 0, 0
                        )
                    }
                } else {
                    repository.toggleTrashed(item)
                    if (activeMediaItem?.id == item.id) {
                        activeMediaItem = null
                    }
                    refreshAll()
                }
            } catch (e: Exception) {
                val recoverable = e as? android.app.RecoverableSecurityException
                    ?: e.cause as? android.app.RecoverableSecurityException
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && recoverable != null) {
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        pendingActionItem = item
                        activity.startIntentSenderForResult(
                            recoverable.userAction.actionIntent.intentSender,
                            1002,
                            null,
                            0,
                            0,
                            0
                        )
                    }
                } else {
                    e.printStackTrace()
                }
            }
        }
    }

    fun emptyTrash(context: Context) {
        viewModelScope.launch {
            val trashedItems = trashed.value
            if (trashedItems.isNotEmpty()) {
                trashedItems.forEach { item ->
                    repository.deleteMetadataPermanently(item.id)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    pendingBatchActionItems = trashedItems
                    val pendingIntent = android.provider.MediaStore.createDeleteRequest(
                        context.contentResolver,
                        trashedItems.map { it.uri }
                    )
                    val activity = context as? android.app.Activity
                    activity?.startIntentSenderForResult(pendingIntent.intentSender, 1003, null, 0, 0, 0)
                }
                refreshAll()
            }
        }
    }

    suspend fun getSelectedMediaItems(selectedIds: Set<Long>): List<MediaItem> = withContext(Dispatchers.IO) {
        if (selectedIds.isEmpty()) return@withContext emptyList()
        val repoItems = repository.getMediaByIds(selectedIds)
        if (repoItems.isNotEmpty()) return@withContext repoItems
        val visible = visibleMediaItems.value.filter { selectedIds.contains(it.id) }
        if (visible.isNotEmpty()) return@withContext visible
        val all = mediaItems.value.filter { selectedIds.contains(it.id) }
        if (all.isNotEmpty()) return@withContext all
        hidden.value.filter { selectedIds.contains(it.id) }
    }

    fun shareSelectedMedia(context: Context, stripMetadata: Boolean) {
        val selectedIds = selectionState.selectedIds.toSet()
        if (selectedIds.isNotEmpty()) {
            viewModelScope.launch {
                val selectedList = getSelectedMediaItems(selectedIds)
                if (selectedList.isNotEmpty()) {
                    SharingUtils.shareMedia(context, selectedList, stripMetadata)
                    selectionState.clear()
                }
            }
        }
    }

    fun hideSelectedMedia(context: Context) {
        val selectedIds = selectionState.selectedIds.toSet()
        if (selectedIds.isEmpty()) return
        viewModelScope.launch {
            val selectedItems = getSelectedMediaItems(selectedIds)
            selectedItems.forEach { item ->
                toggleHidden(context, item)
            }
            selectionState.clear()
            refreshAll()
        }
    }

    fun moveSelectedMediaToFolder(context: Context, folderName: String) {
        val selectedIds = selectionState.selectedIds.toSet()
        if (selectedIds.isEmpty()) return
        viewModelScope.launch {
            val selectedItems = repository.getMediaByIds(selectedIds)
            if (selectedItems.isNotEmpty()) {
                moveMediaToFolder(context, selectedItems, folderName)
                selectionState.clear()
                refreshAll()
            }
        }
    }

    fun shareSingleMedia(context: Context, item: MediaItem, stripMetadata: Boolean) {
        viewModelScope.launch {
            SharingUtils.shareMedia(context, listOf(item), stripMetadata)
        }
    }

    fun deletePermanently(context: Context, item: MediaItem) {
        viewModelScope.launch {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        pendingActionItem = item
                        val pendingIntent = android.provider.MediaStore.createDeleteRequest(context.contentResolver, listOf(item.uri))
                        activity.startIntentSenderForResult(
                            pendingIntent.intentSender,
                            1001,
                            null,
                            0,
                            0,
                            0
                        )
                    }
                } else {
                    context.contentResolver.delete(item.uri, null, null)
                    repository.deleteMetadataPermanently(item.id)
                    if (activeMediaItem?.id == item.id) {
                        activeMediaItem = null
                    }
                    refreshAll()
                }
            } catch (e: Exception) {
                val recoverable = e as? android.app.RecoverableSecurityException
                    ?: e.cause as? android.app.RecoverableSecurityException
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && recoverable != null) {
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        pendingActionItem = item
                        activity.startIntentSenderForResult(
                            recoverable.userAction.actionIntent.intentSender,
                            1001,
                            null,
                            0,
                            0,
                            0
                        )
                    }
                } else {
                    e.printStackTrace()
                }
            }
        }
    }

    var pendingBatchActionItems: List<MediaItem>? = null

    fun deleteSelectedMedia(context: Context) {
        val selectedIds = selectionState.selectedIds.toSet()
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            val selectedItems = repository.getMediaByIds(selectedIds)
            if (selectedItems.isEmpty()) return@launch

            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        pendingBatchActionItems = selectedItems
                        val uris = selectedItems.map { it.uri }
                        val pendingIntent = android.provider.MediaStore.createTrashRequest(
                            context.contentResolver,
                            uris,
                            true
                        )
                        activity.startIntentSenderForResult(
                            pendingIntent.intentSender,
                            1005,
                            null,
                            0,
                            0,
                            0
                        )
                    }
                } else {
                    selectedItems.forEach { item ->
                        repository.toggleTrashed(item)
                    }
                    selectionState.clear()
                    refreshAll()
                }
            } catch (e: Exception) {
                val recoverable = e as? android.app.RecoverableSecurityException
                    ?: e.cause as? android.app.RecoverableSecurityException
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && recoverable != null) {
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        pendingBatchActionItems = selectedItems
                        activity.startIntentSenderForResult(
                            recoverable.userAction.actionIntent.intentSender,
                            1005,
                            null,
                            0,
                            0,
                            0
                        )
                    }
                } else {
                    e.printStackTrace()
                }
            }
        }
    }

    fun deleteMediaItems(context: Context, items: List<MediaItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        pendingBatchActionItems = items
                        val uris = items.map { it.uri }
                        val pendingIntent = android.provider.MediaStore.createTrashRequest(
                            context.contentResolver,
                            uris,
                            true
                        )
                        activity.startIntentSenderForResult(
                            pendingIntent.intentSender,
                            1005,
                            null,
                            0,
                            0,
                            0
                        )
                    }
                } else {
                    items.forEach { item ->
                        repository.toggleTrashed(item)
                    }
                    refreshAll()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun restoreSelectedMedia(context: Context) {
        val selectedIds = selectionState.selectedIds.toSet()
        if (selectedIds.isEmpty()) return
        val trashedItems = trashed.value.filter { selectedIds.contains(it.id) }
        if (trashedItems.isEmpty()) return
        viewModelScope.launch {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        pendingBatchActionItems = trashedItems
                        val uris = trashedItems.map { it.uri }
                        val pendingIntent = android.provider.MediaStore.createTrashRequest(
                            context.contentResolver,
                            uris,
                            false
                        )
                        activity.startIntentSenderForResult(
                            pendingIntent.intentSender,
                            1005,
                            null,
                            0,
                            0,
                            0
                        )
                    }
                } else {
                    trashedItems.forEach { item ->
                        repository.toggleTrashed(item)
                    }
                    selectionState.clear()
                    refreshAll()
                    com.hrshd1eux.imava.core.util.HapticUtil.performSuccess(context)
                    android.widget.Toast.makeText(context, "Restored ${trashedItems.size} items", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteSelectedMediaPermanently(context: Context) {
        val selectedItems = trashed.value.filter { selectionState.selectedIds.contains(it.id) }
        if (selectedItems.isEmpty()) return
        viewModelScope.launch {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        pendingBatchActionItems = selectedItems
                        val uris = selectedItems.map { it.uri }
                        val pendingIntent = android.provider.MediaStore.createDeleteRequest(
                            context.contentResolver,
                            uris
                        )
                        activity.startIntentSenderForResult(
                            pendingIntent.intentSender,
                            1001,
                            null,
                            0,
                            0,
                            0
                        )
                    }
                } else {
                    selectedItems.forEach { item ->
                        try {
                            context.contentResolver.delete(item.uri, null, null)
                            repository.deleteMetadataPermanently(item.id)
                        } catch (e: Exception) {
                            val recoverable = e as? android.app.RecoverableSecurityException
                                ?: e.cause as? android.app.RecoverableSecurityException
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && recoverable != null) {
                                val activity = context as? android.app.Activity
                                if (activity != null) {
                                    pendingBatchActionItems = selectedItems
                                    activity.startIntentSenderForResult(
                                        recoverable.userAction.actionIntent.intentSender,
                                        1001,
                                        null,
                                        0,
                                        0,
                                        0
                                    )
                                }
                            } else {
                                e.printStackTrace()
                            }
                        }
                    }
                    selectionState.clear()
                    refreshAll()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, context: Context? = null) {
        val item = pendingActionItem
        val batchItems = pendingBatchActionItems

        if (resultCode == android.app.Activity.RESULT_OK) {
            when (requestCode) {
                1001 -> {
                    if (batchItems != null) {
                        viewModelScope.launch {
                            batchItems.forEach { batchItem ->
                                repository.deleteMetadataPermanently(batchItem.id)
                            }
                            selectionState.clear()
                            pendingBatchActionItems = null
                            refreshAll()
                        }
                    } else if (item != null) {
                        viewModelScope.launch {
                            repository.deleteMetadataPermanently(item.id)
                            if (activeMediaItem?.id == item.id) {
                                activeMediaItem = null
                            }
                            pendingActionItem = null
                            refreshAll()
                        }
                    }
                }
                1002 -> {
                    if (item != null) {
                        viewModelScope.launch {
                            repository.toggleTrashed(item)
                            if (activeMediaItem?.id == item.id) {
                                activeMediaItem = null
                            }
                            pendingActionItem = null
                            refreshAll()
                        }
                    }
                }
                1003 -> {
                    if (batchItems != null) {
                        viewModelScope.launch {
                            batchItems.forEach { batchItem ->
                                repository.deleteMetadataPermanently(batchItem.id)
                            }
                            selectionState.clear()
                            pendingBatchActionItems = null
                            refreshAll()
                        }
                    } else {
                        refreshAll()
                    }
                }
                1004 -> {
                    pendingActionItem = null
                    refreshAll()
                }
                1005 -> {
                    if (batchItems != null) {
                        viewModelScope.launch {
                            val count = batchItems.size
                            val isCurrentlyTrashed = batchItems.firstOrNull()?.isTrashed == true
                            batchItems.forEach { batchItem ->
                                repository.toggleTrashed(batchItem)
                            }
                            selectionState.clear()
                            pendingBatchActionItems = null
                            refreshAll()
                            if (context != null) {
                                com.hrshd1eux.imava.core.util.HapticUtil.performSuccess(context)
                                val msg = if (isCurrentlyTrashed) "Restored $count items" else "Moved $count items to Trash"
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        refreshAll()
                    }
                }
                1006 -> {
                    val renameItem = pendingRenameItem
                    val renameName = pendingRenameName
                    if (renameItem != null && renameName != null && context != null) {
                        viewModelScope.launch {
                            try {
                                val success = repository.renameMedia(context, renameItem, renameName)
                                if (success) {
                                    applyRenamedState(renameItem, renameName)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                pendingRenameItem = null
                                pendingRenameName = null
                            }
                        }
                    } else {
                        pendingRenameItem = null
                        pendingRenameName = null
                    }
                }
                1007 -> {
                    val sourceItems = pendingMoveSourceItems
                    val rollbackTargets = pendingMoveRollbackTargets
                    if (sourceItems != null) {
                        viewModelScope.launch {
                            val scanned = mutableListOf<String>()
                            sourceItems.forEach { src ->
                                repository.deleteMetadataPermanently(src.id)
                                scanned.add(src.path)
                            }
                            rollbackTargets?.forEach { tgt ->
                                scanned.add(tgt.absolutePath)
                            }
                            if (context != null && scanned.isNotEmpty()) {
                                android.media.MediaScannerConnection.scanFile(
                                    context,
                                    scanned.toTypedArray(),
                                    null,
                                    null
                                )
                            }
                            pendingMoveSourceItems = null
                            pendingMoveRollbackTargets = null
                            loadBuckets()
                            refreshAll()
                            if (context != null) {
                                com.hrshd1eux.imava.core.util.HapticUtil.performSuccess(context)
                                android.widget.Toast.makeText(context, "Moved ${sourceItems.size} items successfully", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                1008 -> {
                    val renames = pendingBatchRenames
                    if (renames != null && context != null) {
                        viewModelScope.launch {
                            val count = repository.batchRenameMedia(context, renames)
                            selectionState.clear()
                            pendingBatchRenames = null
                            refreshAll()
                            if (count > 0) {
                                com.hrshd1eux.imava.core.util.HapticUtil.performSuccess(context)
                                android.widget.Toast.makeText(context, "Renamed $count items", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        pendingBatchRenames = null
                        selectionState.clear()
                        refreshAll()
                    }
                }
            }
        } else {
            // Cancelled flow
            if (requestCode == 1004 && item != null) {
                // Rollback: delete vault file and Room entry
                viewModelScope.launch {
                    repository.deleteMetadataPermanently(item.id)
                    pendingActionItem = null
                    refreshAll()
                }
            } else if (requestCode == 1007) {
                // Rollback: delete newly copied target files so no duplicate is created
                viewModelScope.launch {
                    pendingMoveRollbackTargets?.forEach { tgt ->
                        try { tgt.delete() } catch (_: Exception) {}
                    }
                    pendingMoveSourceItems = null
                    pendingMoveRollbackTargets = null
                    loadBuckets()
                    refreshAll()
                }
            } else {
                pendingActionItem = null
                pendingBatchActionItems = null
                pendingRenameItem = null
                pendingRenameName = null
                pendingBatchRenames = null
                refreshAll()
            }
        }
    }
}

enum class Screen {
    Photos, Albums, Search, Settings, DuplicateFinder
}

enum class SortOrder {
    NEWEST_FIRST, OLDEST_FIRST
}

private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
