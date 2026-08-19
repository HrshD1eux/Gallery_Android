package com.HrshD1eux.Gallery.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.HrshD1eux.Gallery.core.util.SharingUtils
import com.HrshD1eux.Gallery.data.media.BucketInfo
import com.HrshD1eux.Gallery.data.model.MediaItem
import com.HrshD1eux.Gallery.data.repository.MediaRepository
import com.HrshD1eux.Gallery.ui.selection.SelectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import com.HrshD1eux.Gallery.data.model.TimelineItem
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import com.HrshD1eux.Gallery.data.model.isVideo
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
import com.HrshD1eux.Gallery.data.paging.MediaPagingSource
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
                // Copy original EXIF metadata (camera info, GPS, creation timestamps) to the newly saved photo
                com.HrshD1eux.Gallery.core.util.PhotoEditorUtils.copyExifAttributes(
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

    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    fun unlockVault() {
        _isVaultUnlocked.value = true
    }

    fun lockVault(context: Context) {
        _isVaultUnlocked.value = false
        if (currentCategoryName == "Hidden Vault") {
            currentCategoryName = null
            currentScreen = Screen.Albums
        }
        clearVaultCache(context)
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

    val buckets: StateFlow<List<BucketInfo>> = repository.getBucketsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _favorites = MutableStateFlow<List<MediaItem>>(emptyList())
    val favorites: StateFlow<List<MediaItem>> = _favorites.asStateFlow()

    private val _trashed = MutableStateFlow<List<MediaItem>>(emptyList())
    val trashed: StateFlow<List<MediaItem>> = _trashed.asStateFlow()

    private val _hidden = MutableStateFlow<List<MediaItem>>(emptyList())
    val hidden: StateFlow<List<MediaItem>> = _hidden.asStateFlow()

    val videosCount: StateFlow<Int> = mediaItems.map { list ->
        list.count { it is MediaItem.Video }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val visibleMediaItems: StateFlow<List<MediaItem>> = combine(
        mediaItems, favorites, trashed, hidden, _currentCategoryName
    ) { raw, favs, trash, vault, category ->
        val list = when (category) {
            "Favorites" -> favs
            "Trash" -> trash
            "Hidden Vault" -> vault
            "Videos" -> raw.filterIsInstance<MediaItem.Video>()
            else -> raw
        }
        if (sortOrder == SortOrder.OLDEST_FIRST) {
            list.sortedBy { it.dateTaken }
        } else {
            list.sortedByDescending { it.dateTaken }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    @OptIn(ExperimentalCoroutinesApi::class)
    val datePositionHeaders: StateFlow<List<com.HrshD1eux.Gallery.data.repository.DatePositionHeader>> = combine(
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
        snapshotFlow { currentBucketId },
        _currentCategoryName,
        snapshotFlow { sortMode },
        snapshotFlow { sortOrder },
        refreshTrigger
    ) { bucketId, category, mode, order, _ ->
        val itemsFlow: Flow<PagingData<MediaItem>> = when (category) {
            "Favorites" -> favorites.map { list ->
                val sorted = if (order == SortOrder.OLDEST_FIRST) list.sortedBy { it.dateTaken } else list.sortedByDescending { it.dateTaken }
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
                config = PagingConfig(pageSize = 100, enablePlaceholders = false),
                pagingSourceFactory = { MediaPagingSource(repository, bucketId, order) }
            ).flow.map { pagingData ->
                pagingData.filter { it is MediaItem.Video }
            }
            else -> Pager(
                config = PagingConfig(pageSize = 100, enablePlaceholders = false),
                pagingSourceFactory = { MediaPagingSource(repository, bucketId, order) }
            ).flow
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
            _isVaultUnlocked.flatMapLatest { unlocked ->
                repository.getHiddenMediaFlow(unlocked)
            }.collect {
                _hidden.value = it
            }
        }
        
        // Reactive MediaStore observer to reload current visible range in-place with debouncing
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            repository.observeMediaChanges()
                .debounce(1000)
                .collectLatest {
                    refreshTrigger.value++
                    val refreshedItems = repository.loadMediaPaged(
                        limit = PAGE_SIZE,
                        offset = 0,
                        bucketId = currentBucketId
                    )
                    _mediaItems.value = refreshedItems
                }
        }

        // Clean orphaned database metadata and scan secondary directories asynchronously after boot delay
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.purgeExpiredTrashMedia()
                kotlinx.coroutines.delay(2000) // Defer scan by 2s so cold boot media rendering completes instantly
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

    fun renameMedia(context: Context, item: MediaItem, newName: String) {
        viewModelScope.launch {
            val success = repository.renameMedia(context, item, newName)
            if (success) {
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
                refreshTrigger.value++
                loadNextPage()
            }
        }
    }

    fun loadNextPage() {
        viewModelScope.launch {
            val items = repository.loadMediaPaged(
                limit = PAGE_SIZE,
                offset = 0,
                bucketId = currentBucketId
            )
            _mediaItems.value = items
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
        loadNextPage()
    }

    fun loadBuckets() {
        // Handled reactively
    }

    fun moveMediaToFolder(context: Context, items: List<MediaItem>, folderName: String) {
        viewModelScope.launch {
            val sourceUris = mutableListOf<android.net.Uri>()
            val resolver = context.contentResolver
            
            // Cleanup any empty album placeholder image in target folder
            try {
                val placeholderSelection = "${android.provider.MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                val placeholderArgs = arrayOf("Pictures/$folderName%", ".placeholder.jpg")
                resolver.delete(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, placeholderSelection, placeholderArgs)
            } catch (_: Exception) {}

            items.forEach { item ->
                try {
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, java.io.File(item.path).name)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/$folderName")
                    }
                    
                    val collectionUri = if (item.isVideo) {
                        android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    } else {
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    
                    val targetUri = resolver.insert(collectionUri, contentValues)
                    if (targetUri != null) {
                        resolver.openInputStream(item.uri)?.use { input ->
                            resolver.openOutputStream(targetUri)?.use { output ->
                                input.copyTo(output)
                            }
                        }
                        sourceUris.add(item.uri)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            if (sourceUris.isNotEmpty()) {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        val activity = context as? android.app.Activity
                        if (activity != null) {
                            val pendingIntent = android.provider.MediaStore.createDeleteRequest(resolver, sourceUris)
                            activity.startIntentSenderForResult(
                                pendingIntent.intentSender,
                                1003,
                                null,
                                0,
                                0,
                                0
                            )
                        }
                    } else {
                        sourceUris.forEach { uri ->
                            try {
                                resolver.delete(uri, null, null)
                            } catch (e: Exception) {
                                val recoverable = e as? android.app.RecoverableSecurityException
                                    ?: e.cause as? android.app.RecoverableSecurityException
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && recoverable != null) {
                                    val activity = context as? android.app.Activity
                                    activity?.startIntentSenderForResult(
                                        recoverable.userAction.actionIntent.intentSender,
                                        1003,
                                        null,
                                        0,
                                        0,
                                        0
                                    )
                                } else {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun createEmptyAlbum(context: Context, albumName: String) {
        viewModelScope.launch {
            try {
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "album_cover.jpg")
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/$albumName")
                }
                val targetUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (targetUri != null) {
                    resolver.openOutputStream(targetUri)?.use { output ->
                        val dummyJpegBytes = byteArrayOf(
                            -1, -40, -1, -32, 0, 16, 74, 70, 73, 70, 0, 1, 1, 1, 0, 96, 0, 96, 0, 0,
                            -1, -37, 0, 67, 0, 8, 6, 6, 7, 6, 5, 8, 7, 7, 7, 9, 9, 8, 10, 12,
                            20, 13, 12, 11, 11, 12, 25, 18, 19, 15, 20, 29, 26, 31, 30, 29, 26, 28, 28, 32,
                            36, 46, 39, 32, 34, 44, 35, 28, 28, 40, 55, 41, 44, 48, 49, 52, 52, 52, 31, 39,
                            57, 61, 56, 50, 60, 46, 51, 52, 50, -1, -64, 0, 11, 8, 0, 1, 0, 1, 1, 1,
                            17, 0, -1, -60, 0, 20, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0,
                            0, 0, 0, 0, 0, 5, -1, -38, 0, 12, 1, 1, 0, 2, 17, 3, 17, 0, 63, 0,
                            -113, -128, -1, -39
                        )
                        output.write(dummyJpegBytes)
                    }
                }
                refreshTrigger.value++
                loadNextPage()
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
        }
    }

    fun toggleHidden(context: Context, item: MediaItem) {
        viewModelScope.launch {
            try {
                repository.toggleHidden(context, item)
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
                    val pendingIntent = android.provider.MediaStore.createDeleteRequest(
                        context.contentResolver,
                        trashedItems.map { it.uri }
                    )
                    val activity = context as? android.app.Activity
                    activity?.startIntentSenderForResult(pendingIntent.intentSender, 1003, null, 0, 0, 0)
                }
                refreshTrigger.value++
                loadNextPage()
            }
        }
    }

    fun shareSelectedMedia(context: Context, stripMetadata: Boolean) {
        val selectedIds = selectionState.selectedIds.toSet()
        if (selectedIds.isNotEmpty()) {
            viewModelScope.launch {
                val selectedList = repository.getMediaByIds(selectedIds)
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
            val selectedItems = repository.getMediaByIds(selectedIds)
            selectedItems.forEach { item ->
                toggleHidden(context, item)
            }
            selectionState.clear()
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

    fun restoreSelectedMedia() {
        val selectedIds = selectionState.selectedIds.toSet()
        if (selectedIds.isEmpty()) return
        viewModelScope.launch {
            val trashedItems = trashed.value.filter { selectedIds.contains(it.id) }
            trashedItems.forEach { item ->
                repository.toggleTrashed(item)
            }
            selectionState.clear()
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
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int) {
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
                        }
                    } else if (item != null) {
                        viewModelScope.launch {
                            repository.deleteMetadataPermanently(item.id)
                            if (activeMediaItem?.id == item.id) {
                                activeMediaItem = null
                            }
                            pendingActionItem = null
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
                        }
                    }
                }
                1004 -> {
                    pendingActionItem = null
                }
                1005 -> {
                    if (batchItems != null) {
                        viewModelScope.launch {
                            batchItems.forEach { batchItem ->
                                repository.toggleTrashed(batchItem)
                            }
                            selectionState.clear()
                            pendingBatchActionItems = null
                        }
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
                }
            } else {
                pendingActionItem = null
                pendingBatchActionItems = null
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
