package com.HrshD1eux.Gallery.ui.viewer

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.AspectRatio
import androidx.media3.ui.AspectRatioFrameLayout
import com.HrshD1eux.Gallery.core.util.FormatUtils
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.Photo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.HrshD1eux.Gallery.data.model.MediaItem
import com.HrshD1eux.Gallery.data.model.isVideo
import com.HrshD1eux.Gallery.ui.MainViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val visibleMediaItems by viewModel.visibleMediaItems.collectAsState()

    val activeItem = viewModel.activeMediaItem ?: return

    val mediaItems = remember(visibleMediaItems, activeItem.id) {
        if (visibleMediaItems.none { it.id == activeItem.id }) {
            listOf(activeItem) + visibleMediaItems
        } else {
            visibleMediaItems
        }
    }

    val initialIndex = remember(activeItem.id) {
        mediaItems.indexOfFirst { it.id == activeItem.id }.coerceAtLeast(0)
    }
    
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { mediaItems.size }
    )

    // Scroll to the tapped media item whenever activeItem changes
    LaunchedEffect(activeItem.id) {
        val targetIndex = mediaItems.indexOfFirst { it.id == activeItem.id }
        if (targetIndex >= 0 && pagerState.currentPage != targetIndex) {
            pagerState.scrollToPage(targetIndex)
        }
    }

    // Sync active item state in ViewModel only when user finishes swiping to a settled page
    LaunchedEffect(pagerState.settledPage) {
        val currentMedia = mediaItems.getOrNull(pagerState.settledPage)
        if (currentMedia != null && currentMedia.id != viewModel.activeMediaItem?.id) {
            viewModel.activeMediaItem = currentMedia
        }
    }

    var showChrome by remember { mutableStateOf(true) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var stripMetadataOnShare by remember { mutableStateOf(true) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDeletePermanentlyConfirmDialog by remember { mutableStateOf(false) }
    var showMoveToAlbumDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInputText by remember { mutableStateOf("") }
    var showVaultConfirmDialog by remember { mutableStateOf(false) }
    var showSetAsDialog by remember { mutableStateOf(false) }
    var showVideoTrimDialog by remember { mutableStateOf(false) }

    var isMotionPhoto by remember { mutableStateOf(false) }
    var isPlayingMotionPhoto by remember { mutableStateOf(false) }
    var motionVideoFile by remember { mutableStateOf<java.io.File?>(null) }
    val scope = rememberCoroutineScope()

    val currentItem = mediaItems.getOrNull(pagerState.currentPage) ?: activeItem

    LaunchedEffect(currentItem.id) {
        isPlayingMotionPhoto = false
        motionVideoFile = null
        if (currentItem is com.HrshD1eux.Gallery.data.model.MediaItem.Photo) {
            val info = com.HrshD1eux.Gallery.core.util.MotionPhotoUtil.checkMotionPhoto(context, currentItem.uri)
            isMotionPhoto = info.isMotionPhoto
        } else {
            isMotionPhoto = false
        }
    }

    var isSlideshowActive by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var targetKbInput by remember { mutableStateOf("15") }
    var videoResizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    LaunchedEffect(isSlideshowActive) {
        if (isSlideshowActive) {
            showChrome = false
        }
    }

    LaunchedEffect(isSlideshowActive, pagerState.currentPage) {
        if (isSlideshowActive && mediaItems.isNotEmpty() && !pagerState.isScrollInProgress) {
            kotlinx.coroutines.delay(3500L)
            if (isSlideshowActive) {
                val nextPage = (pagerState.currentPage + 1) % mediaItems.size
                pagerState.animateScrollToPage(
                    page = nextPage,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 800)
                )
            }
        }
    }

    val infoSheetState = rememberModalBottomSheetState()

    // Track whether the current page is zoomed (each page manages its own ZoomState internally)
    var isCurrentPageZoomed by remember { mutableStateOf(false) }

    // Swipe down to dismiss state
    var dragOffsetY by remember { mutableStateOf(0f) }
    
    // Disable drag dismiss if image is zoomed in to avoid gesture collision.
    val swipeDismissModifier = if (!isCurrentPageZoomed) {
        Modifier.draggable(
            state = rememberDraggableState { delta ->
                if (delta > 0 || dragOffsetY > 0) {
                    dragOffsetY = (dragOffsetY + delta).coerceAtLeast(0f)
                }
            },
            orientation = Orientation.Vertical,
            onDragStopped = { velocity ->
                if (dragOffsetY > 220f || velocity > 800f) {
                    viewModel.activeMediaItem = null // Trigger close
                } else {
                    dragOffsetY = 0f // Bounce back
                }
            }
        )
    } else {
        Modifier
    }

    val containerScale = (1f - (dragOffsetY / 1600f)).coerceIn(0.7f, 1f)
    val containerAlpha = (1f - (dragOffsetY / 800f)).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = containerAlpha))
    ) {
        // Main Horizontal Pager
        HorizontalPager(
            state = pagerState,
            key = { page -> mediaItems.getOrNull(page)?.id ?: page },
            userScrollEnabled = !isCurrentPageZoomed && !isSlideshowActive,
            beyondBoundsPageCount = 1,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    translationY = dragOffsetY,
                    scaleX = containerScale,
                    scaleY = containerScale
                )
                .then(swipeDismissModifier)
        ) { page ->
            val item = mediaItems.getOrNull(page)
            if (item != null) {
                val imageRequest = remember(item.uri, item.width, item.height) {
                    val maxTextureDim = 4096
                    val builder = coil.request.ImageRequest.Builder(context)
                        .data(item.uri)
                        .crossfade(true)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .allowHardware(true)
                        .error(android.R.drawable.ic_menu_report_image)
                        .fallback(android.R.drawable.ic_menu_report_image)

                    if (item.width > 0 && item.height > 0) {
                        val maxOriginal = maxOf(item.width, item.height)
                        if (maxOriginal > maxTextureDim) {
                            val scale = maxTextureDim.toFloat() / maxOriginal.toFloat()
                            val targetW = (item.width * scale).toInt().coerceAtLeast(1)
                            val targetH = (item.height * scale).toInt().coerceAtLeast(1)
                            builder.size(targetW, targetH)
                                .precision(coil.size.Precision.INEXACT)
                        } else {
                            builder.size(coil.size.Size.ORIGINAL)
                        }
                    } else {
                        builder.size(maxTextureDim, maxTextureDim)
                            .precision(coil.size.Precision.INEXACT)
                    }
                    builder.build()
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.isVideo) {
                        VideoPlayerContainer(
                            uri = item.uri,
                            title = item.path.substringAfterLast('/'),
                            isSelectedPage = (page == pagerState.currentPage),
                            showChrome = showChrome,
                            onTap = { showChrome = !showChrome },
                            resizeMode = videoResizeMode,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (isPlayingMotionPhoto && motionVideoFile != null && page == pagerState.currentPage) {
                        VideoPlayerContainer(
                            uri = android.net.Uri.fromFile(motionVideoFile),
                            title = "Motion Photo",
                            isSelectedPage = true,
                            showChrome = showChrome,
                            onTap = { showChrome = !showChrome },
                            resizeMode = videoResizeMode,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Each page gets its own independent zoom state
                        val pageZoomState = rememberZoomState()

                        // Reset zoom when this page is no longer the active page
                        LaunchedEffect(pagerState.currentPage) {
                            if (page != pagerState.currentPage) {
                                pageZoomState.reset()
                            }
                        }

                        // Report zoom state to the parent for pager scroll locking
                        LaunchedEffect(pageZoomState.scale) {
                            if (page == pagerState.currentPage) {
                                isCurrentPageZoomed = pageZoomState.scale > 1.05f
                            }
                        }

                        AsyncImage(
                            model = imageRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .zoomable(
                                    state = pageZoomState,
                                    onTap = { showChrome = !showChrome }
                                )
                        )
                    }
                }
            }
        }

        // Top App Bar Chrome overlay (Zero Overlap with filename constrained and ellipsized)
        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            val currentItem = mediaItems.getOrNull(pagerState.currentPage) ?: activeItem
            val fileName = currentItem.path.substringAfterLast('/')

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.activeMediaItem = null }
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                )

                // Motion Photo Badge / Player Button
                if (isMotionPhoto) {
                    FilterChip(
                        selected = isPlayingMotionPhoto,
                        onClick = {
                            scope.launch {
                                if (isPlayingMotionPhoto) {
                                    isPlayingMotionPhoto = false
                                } else {
                                    val info = com.HrshD1eux.Gallery.core.util.MotionPhotoUtil.checkMotionPhoto(context, currentItem.uri)
                                    val file = com.HrshD1eux.Gallery.core.util.MotionPhotoUtil.extractMotionVideo(context, currentItem.uri, info)
                                    if (file != null) {
                                        motionVideoFile = file
                                        isPlayingMotionPhoto = true
                                    } else {
                                        android.widget.Toast.makeText(context, "Motion video not available", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        label = { Text(if (isPlayingMotionPhoto) "Playing ⏸️" else "Motion 🎞️", color = Color.White) },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }

                IconButton(onClick = { showInfoSheet = true }) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = "Info", tint = Color.White)
                }

                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More options", tint = Color.White)
                    }

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        val item = mediaItems.getOrNull(pagerState.currentPage) ?: activeItem
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                renameInputText = java.io.File(item.path).nameWithoutExtension
                                showRenameDialog = true
                            }
                        )

                        DropdownMenuItem(
                            text = { Text(if (item.isHidden) "Unhide from Vault" else "Move to Vault") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (item.isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                if (item.isHidden) {
                                    viewModel.toggleHidden(context, item)
                                } else {
                                    showVaultConfirmDialog = true
                                }
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Start Slideshow 🎞️") },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                isSlideshowActive = true
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Set as Wallpaper / Contact") },
                            leadingIcon = { Icon(Icons.Default.Wallpaper, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                showSetAsDialog = true
                            }
                        )

                        if (item is com.HrshD1eux.Gallery.data.model.MediaItem.Video) {
                            DropdownMenuItem(
                                text = { Text("Trim Video / Make GIF 🎬") },
                                leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    showVideoTrimDialog = true
                                }
                            )

                            val aspectLabel = when (videoResizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Aspect Ratio: Fit (Tap to Zoom)"
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Aspect Ratio: Zoom (Tap to Fill)"
                                else -> "Aspect Ratio: Fill (Tap to Fit)"
                            }
                            DropdownMenuItem(
                                text = { Text(aspectLabel) },
                                leadingIcon = { Icon(Icons.Default.AspectRatio, contentDescription = null) },
                                onClick = {
                                    videoResizeMode = when (videoResizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                    showMoreMenu = false
                                }
                            )
                        }

                        if (item is com.HrshD1eux.Gallery.data.model.MediaItem.Photo) {
                            DropdownMenuItem(
                                text = { Text("Compress Image 📉") },
                                leadingIcon = { Icon(Icons.Default.Compress, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    showCompressDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }

        // Bottom Navigation Actions overlay
        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                val item = mediaItems.getOrNull(pagerState.currentPage) ?: activeItem
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.isTrashed) {
                        // Actions for trashed items: Restore or Delete permanently
                        IconButton(onClick = {
                            com.HrshD1eux.Gallery.core.util.HapticUtil.performSuccess(context)
                            viewModel.toggleTrashed(context, item)
                        }) {
                            Icon(
                                imageVector = Icons.Default.RestoreFromTrash,
                                contentDescription = "Restore",
                                tint = Color.White
                            )
                        }

                        IconButton(onClick = {
                            com.HrshD1eux.Gallery.core.util.HapticUtil.performClick(context)
                            showDeletePermanentlyConfirmDialog = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = "Delete Permanently",
                                tint = Color.Red
                            )
                        }
                    } else {
                        // Standard actions
                        IconButton(onClick = {
                            com.HrshD1eux.Gallery.core.util.HapticUtil.performSelection(context)
                            viewModel.toggleFavorite(item)
                        }) {
                            Icon(
                                imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (item.isFavorite) Color.Red else Color.White
                            )
                        }

                        if (item is com.HrshD1eux.Gallery.data.model.MediaItem.Photo) {
                            IconButton(onClick = {
                                com.HrshD1eux.Gallery.core.util.HapticUtil.performClick(context)
                                viewModel.editingMediaItem = item
                            }) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = Color.White)
                            }
                        }

                        IconButton(onClick = {
                            com.HrshD1eux.Gallery.core.util.HapticUtil.performClick(context)
                            showShareDialog = true
                        }) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                        }

                        IconButton(onClick = {
                            com.HrshD1eux.Gallery.core.util.HapticUtil.performClick(context)
                            showMoveToAlbumDialog = true
                        }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move to Album", tint = Color.White)
                        }

                        IconButton(onClick = {
                            com.HrshD1eux.Gallery.core.util.HapticUtil.performLongPress(context)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                viewModel.toggleTrashed(context, item)
                            } else {
                                showDeleteConfirmDialog = true
                            }
                        }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                        }
                    }
                }
            }
        }

        // Intercepting share details prompt
        if (showShareDialog) {
            val item = mediaItems.getOrNull(pagerState.currentPage) ?: activeItem
            AlertDialog(
                onDismissRequest = { showShareDialog = false },
                title = { Text("Share Privately") },
                text = {
                    Column {
                        Text("Removes location markers, camera details, and personal info before sharing.")
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = stripMetadataOnShare,
                                onCheckedChange = { stripMetadataOnShare = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Remove location & camera info")
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showShareDialog = false
                            viewModel.shareSingleMedia(context, item, stripMetadataOnShare)
                        }
                    ) {
                        Text("Share Cleaned")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showShareDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Draggable info overlay sheet
        if (showInfoSheet) {
            val item = mediaItems.getOrNull(pagerState.currentPage) ?: activeItem
            InfoBottomSheet(
                item = item,
                sheetState = infoSheetState,
                onDismissRequest = { showInfoSheet = false },
                onUpdateDateTaken = { newDate ->
                    viewModel.updateMediaDateTaken(context, item, newDate)
                }
            )
        }

        if (showDeleteConfirmDialog) {
            val item = mediaItems.getOrNull(pagerState.currentPage) ?: activeItem
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text("Move to Trash?") },
                text = { Text("Are you sure you want to move this photo to Trash?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirmDialog = false
                            viewModel.toggleTrashed(context, item)
                        }
                    ) {
                        Text("Move to Trash")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showDeletePermanentlyConfirmDialog) {
            val item = mediaItems.getOrNull(pagerState.currentPage) ?: activeItem
            AlertDialog(
                onDismissRequest = { showDeletePermanentlyConfirmDialog = false },
                title = { Text("Delete Permanently?") },
                text = { Text("Are you sure? This action is irreversible and will erase the file permanently from your device storage.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeletePermanentlyConfirmDialog = false
                            viewModel.deletePermanently(context, item)
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeletePermanentlyConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showRenameDialog) {
            val item = mediaItems.getOrNull(pagerState.currentPage) ?: activeItem
            val rawFullName = item.path.substringAfterLast('/')
            val ext = if (rawFullName.contains('.')) "." + rawFullName.substringAfterLast('.') else ""
            val nameWithoutExt = rawFullName.removeSuffix(ext)
            
            var customRenameText by remember(item.id, showRenameDialog) { mutableStateOf(nameWithoutExt) }

            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.DriveFileRenameOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "Rename File",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Media preview snippet card
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = item.uri,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(MaterialTheme.shapes.small),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rawFullName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${FormatUtils.formatFileSize(item.size)} • ${if (item.width > 0) "${item.width}×${item.height}" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = customRenameText,
                            onValueChange = { customRenameText = it },
                            label = { Text("New File Name") },
                            placeholder = { Text("Enter name") },
                            singleLine = true,
                            trailingIcon = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    if (customRenameText.isNotEmpty()) {
                                        IconButton(
                                            onClick = { customRenameText = "" },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    if (ext.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    MaterialTheme.colorScheme.primaryContainer,
                                                    MaterialTheme.shapes.extraSmall
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = ext,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            },
                            supportingText = {
                                Text(
                                    text = if (ext.isNotEmpty()) "Extension $ext will be preserved automatically" else "",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        enabled = customRenameText.isNotBlank() && customRenameText.trim() != nameWithoutExt,
                        onClick = {
                            if (customRenameText.isNotBlank()) {
                                showRenameDialog = false
                                val finalName = customRenameText.trim() + ext
                                com.HrshD1eux.Gallery.core.util.HapticUtil.performClick(context)
                                viewModel.renameMedia(context, item, finalName)
                            }
                        }
                    ) {
                        Text("Rename")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showMoveToAlbumDialog) {
            val item = mediaItems.getOrNull(pagerState.currentPage) ?: activeItem
            var createNewAlbumInMove by remember { mutableStateOf(false) }
            var tempNewAlbumName by remember { mutableStateOf("") }
            val bucketList by viewModel.buckets.collectAsState()
            
            AlertDialog(
                onDismissRequest = { 
                    showMoveToAlbumDialog = false 
                    createNewAlbumInMove = false
                    tempNewAlbumName = ""
                },
                title = { Text("Move to Album") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (createNewAlbumInMove) {
                            OutlinedTextField(
                                value = tempNewAlbumName,
                                onValueChange = { tempNewAlbumName = it },
                                label = { Text("New Album Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                                item {
                                    TextButton(
                                        onClick = { createNewAlbumInMove = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("+ Create New Album")
                                    }
                                }
                                items(bucketList) { bucket ->
                                    TextButton(
                                        onClick = {
                                            viewModel.moveMediaToFolder(context, listOf(item), bucket.name)
                                            showMoveToAlbumDialog = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(bucket.name)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (createNewAlbumInMove) {
                        Button(
                            onClick = {
                                if (tempNewAlbumName.isNotBlank()) {
                                    viewModel.moveMediaToFolder(context, listOf(item), tempNewAlbumName.trim())
                                    showMoveToAlbumDialog = false
                                    createNewAlbumInMove = false
                                    tempNewAlbumName = ""
                                }
                            }
                        ) {
                            Text("Move")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showMoveToAlbumDialog = false
                            createNewAlbumInMove = false
                            tempNewAlbumName = ""
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showVaultConfirmDialog) {
            val item = mediaItems.getOrNull(pagerState.currentPage) ?: activeItem
            AlertDialog(
                onDismissRequest = { showVaultConfirmDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = { Text("Move to Hidden Vault?") },
                text = {
                    Column {
                        Text(
                            text = "This item will be safely locked inside your private Vault.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Note: The original item will be removed from your main gallery so other apps can't access it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showVaultConfirmDialog = false
                            viewModel.toggleHidden(context, item)
                        }
                    ) {
                        Text("Move to Vault")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showVaultConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (isSlideshowActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp)
                    .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                    .clickable { isSlideshowActive = false }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Pause Slideshow ⏸️", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (showCompressDialog) {
            val item = mediaItems.getOrNull(pagerState.currentPage) ?: activeItem
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            var isCompressing by remember { mutableStateOf(false) }

            val originalKb = (item.size / 1024).coerceAtLeast(1)
            val originalFormatted = com.HrshD1eux.Gallery.core.util.FormatUtils.formatFileSize(item.size)

            AlertDialog(
                onDismissRequest = { if (!isCompressing) showCompressDialog = false },
                icon = { Icon(Icons.Default.Compress, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Compress Image", style = MaterialTheme.typography.titleLarge) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        // Original file information card
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = java.io.File(item.path).name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Original Size: $originalFormatted",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (item.width > 0 && item.height > 0) {
                                        Text(
                                            text = "${item.width} × ${item.height}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Target File Size (in KB):", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = targetKbInput,
                            onValueChange = { targetKbInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Target KB") },
                            placeholder = { Text("e.g. 100") },
                            singleLine = true,
                            enabled = !isCompressing,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Quick Presets:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))

                        val presets = listOf(25, 50, 100, 250, 500, 1000, 2000)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presets.forEach { presetKb ->
                                val isSelected = targetKbInput == presetKb.toString()
                                val label = if (presetKb >= 1000) "${presetKb / 1000} MB" else "$presetKb KB"
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { targetKbInput = presetKb.toString() },
                                    label = { Text(label) },
                                    enabled = !isCompressing
                                )
                            }
                        }

                        val targetKb = targetKbInput.toLongOrNull() ?: 0L
                        if (targetKb in 1 until originalKb) {
                            val savedPercent = (((originalKb - targetKb).toDouble() / originalKb.toDouble()) * 100).toInt()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Estimated space saved: ~$savedPercent%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (isCompressing) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Compressing image...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = !isCompressing && (targetKbInput.toIntOrNull() ?: 0) > 0,
                        onClick = {
                            val kb = targetKbInput.toIntOrNull() ?: 15
                            isCompressing = true
                            scope.launch {
                                val resultUri = com.HrshD1eux.Gallery.core.util.ImageCompressor.compressToTargetKb(context, item, kb)
                                isCompressing = false
                                showCompressDialog = false
                                if (resultUri != null) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Saved to Pictures/Compressed (${kb} KB target)",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    viewModel.refreshAll()
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Failed to compress image",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    ) {
                        Text("Compress & Save")
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !isCompressing,
                        onClick = { showCompressDialog = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Set As Wallpaper / Contact Photo Dialog
        if (showSetAsDialog) {
            val item = mediaItems.getOrNull(pagerState.currentPage) ?: activeItem
            val activity = context as? android.app.Activity
            AlertDialog(
                onDismissRequest = { showSetAsDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Wallpaper,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = { Text("Set Image As...") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = {
                                showSetAsDialog = false
                                if (activity != null) {
                                    com.HrshD1eux.Gallery.core.util.WallpaperUtil.openSystemWallpaperCropper(activity, item.uri)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Wallpaper, contentDescription = null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Wallpaper (Lock / Home Screen)")
                            }
                        }

                        TextButton(
                            onClick = {
                                showSetAsDialog = false
                                if (activity != null) {
                                    com.HrshD1eux.Gallery.core.util.WallpaperUtil.setAsContactPhoto(activity, item.uri)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Photo, contentDescription = null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Contact Photo / Profile")
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showSetAsDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Video Trimmer & GIF Generator Dialog
        if (showVideoTrimDialog) {
            val item = mediaItems.getOrNull(pagerState.currentPage) ?: activeItem
            if (item is com.HrshD1eux.Gallery.data.model.MediaItem.Video) {
                VideoTrimDialog(
                    mediaItem = item,
                    onDismiss = { showVideoTrimDialog = false },
                    onSuccess = { viewModel.refreshAll() }
                )
            }
        }
    }
}
