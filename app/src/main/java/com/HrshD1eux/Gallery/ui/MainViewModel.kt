package com.HrshD1eux.Gallery.ui

import android.content.Context
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import com.HrshD1eux.Gallery.data.model.TimelineItem
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import com.HrshD1eux.Gallery.data.model.isVideo
import kotlinx.coroutines.Dispatchers
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import javax.inject.Inject

import androidx.lifecycle.SavedStateHandle

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val selectionState = SelectionState()
    var pendingActionItem: MediaItem? = null

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

    private val _currentCategoryName = MutableStateFlow<String?>(savedStateHandle.get<String>("current_category_name"))
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
        when (category) {
            "Favorites" -> favs
            "Trash" -> trash
            "Hidden Vault" -> vault
            "Videos" -> raw.filterIsInstance<MediaItem.Video>()
            else -> raw
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val flatTimelineList: StateFlow<List<TimelineItem>> = visibleMediaItems.map { items ->
        val result = mutableListOf<TimelineItem>()
        val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val yesterday = today.minusDays(1)

        val grouped = items.groupBy { item ->
            val localDate = Instant.ofEpochMilli(item.dateTaken).atZone(zoneId).toLocalDate()
            when (localDate) {
                today -> "Today"
                yesterday -> "Yesterday"
                else -> localDate.format(formatter)
            }
        }

        grouped.forEach { (header, list) ->
            result.add(TimelineItem.Header(header))
            list.forEach { item ->
                result.add(TimelineItem.Media(item))
            }
        }
        result
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var currentOffset = 0
    private var isLastPage = false
    private var isLoadingPage = false
    private val PAGE_SIZE = 200

    init {
        loadNextPage(reset = true)
        
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
            repository.getHiddenMediaFlow().collect {
                _hidden.value = it
            }
        }
        
        // Reactive MediaStore observer to reload current visible range in-place
        viewModelScope.launch {
            repository.observeMediaChanges().collectLatest {
                val loadedCount = if (currentOffset > 0) currentOffset else PAGE_SIZE
                val refreshedItems = repository.loadMediaPaged(
                    limit = loadedCount,
                    offset = 0,
                    bucketId = currentBucketId
                )
                _mediaItems.value = refreshedItems
                
                // Clean orphaned database metadata in the background using full active ID list
                try {
                    val activeIds = repository.getActiveMediaIds()
                    if (activeIds.isNotEmpty()) {
                        repository.deleteOrphanedMetadata(activeIds)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private var loadPageJob: kotlinx.coroutines.Job? = null

    fun loadNextPage(reset: Boolean = false) {
        if (reset) {
            loadPageJob?.cancel()
            isLoadingPage = false
        } else {
            if (isLoadingPage || isLastPage) return
        }
        
        isLoadingPage = true
        loadPageJob = viewModelScope.launch {
            if (reset) {
                currentOffset = 0
                isLastPage = false
            }
            
            val newItems = repository.loadMediaPaged(
                limit = PAGE_SIZE,
                offset = currentOffset,
                bucketId = currentBucketId
            )
            
            if (!isActive) return@launch
            
            if (newItems.size < PAGE_SIZE) {
                isLastPage = true
            }
            
            if (reset) {
                _mediaItems.value = newItems
            } else {
                _mediaItems.value = _mediaItems.value + newItems
            }
            currentOffset += newItems.size
            isLoadingPage = false
        }
    }

    fun selectBucket(bucketId: Long?, bucketName: String?) {
        currentCategoryName = null // Reset category filter when selecting a folder
        currentBucketId = bucketId
        currentBucketName = bucketName
        currentScreen = Screen.Photos
        loadNextPage(reset = true)
    }

    fun clearVaultCache(context: Context) {
        viewModelScope.launch {
            repository.clearVaultCache(context)
        }
    }

    fun loadMediaStream() {
        loadNextPage(reset = true)
    }

    fun loadBuckets() {
        // Handled reactively
    }

    fun moveMediaToFolder(context: Context, items: List<MediaItem>, folderName: String) {
        viewModelScope.launch {
            val sourceUris = mutableListOf<android.net.Uri>()
            val resolver = context.contentResolver
            
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
                            resolver.delete(uri, null, null)
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
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, ".placeholder.jpg")
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
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && e is android.app.RecoverableSecurityException) {
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        pendingActionItem = item
                        activity.startIntentSenderForResult(
                            e.userAction.actionIntent.intentSender,
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
                e.printStackTrace()
            }
        }
    }

    fun shareSelectedMedia(context: Context, stripMetadata: Boolean) {
        val selectedList = mediaItems.value.filter { selectionState.selectedIds.contains(it.id) }
        if (selectedList.isNotEmpty()) {
            viewModelScope.launch {
                SharingUtils.shareMedia(context, selectedList, stripMetadata)
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
            } catch (e: SecurityException) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && e is android.app.RecoverableSecurityException) {
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        pendingActionItem = item
                        activity.startIntentSenderForResult(
                            e.userAction.actionIntent.intentSender,
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int) {
        val item = pendingActionItem ?: return
        if (resultCode == android.app.Activity.RESULT_OK) {
            when (requestCode) {
                1001 -> {
                    viewModelScope.launch {
                        repository.deleteMetadataPermanently(item.id)
                        if (activeMediaItem?.id == item.id) {
                            activeMediaItem = null
                        }
                        pendingActionItem = null
                    }
                }
                1002 -> {
                    viewModelScope.launch {
                        repository.toggleTrashed(item)
                        if (activeMediaItem?.id == item.id) {
                            activeMediaItem = null
                        }
                        pendingActionItem = null
                    }
                }
                1004 -> {
                    pendingActionItem = null
                }
            }
        } else {
            // Cancelled flow
            if (requestCode == 1004) {
                // Rollback: delete vault file and Room entry
                viewModelScope.launch {
                    repository.deleteMetadataPermanently(item.id)
                    pendingActionItem = null
                }
            } else {
                pendingActionItem = null
            }
        }
    }
}

enum class Screen {
    Photos, Albums, Search
}
