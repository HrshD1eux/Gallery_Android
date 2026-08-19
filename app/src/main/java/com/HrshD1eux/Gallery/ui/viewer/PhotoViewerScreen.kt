package com.HrshD1eux.Gallery.ui.viewer

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

    val infoSheetState = rememberModalBottomSheetState()
    val zoomState = rememberZoomState()

    // Reset zoom state when user pages to another image
    LaunchedEffect(pagerState.currentPage) {
        zoomState.reset()
    }

    // Swipe down to dismiss state
    var dragOffsetY by remember { mutableStateOf(0f) }
    
    // Disable drag dismiss if image is zoomed in to avoid gesture collision.
    // Uses draggable to track drag offsets and swipe velocity (flinging downwards closes the viewer)
    val swipeDismissModifier = if (zoomState.scale == 1f) {
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
                val imageRequest = remember(item.uri) {
                    coil.request.ImageRequest.Builder(context)
                        .data(item.uri)
                        .crossfade(true)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .error(android.R.drawable.ic_menu_report_image)
                        .fallback(android.R.drawable.ic_menu_report_image)
                        .build()
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
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .zoomable(
                                    state = zoomState,
                                    onTap = { showChrome = !showChrome }
                                )
                        )
                    }
                }
            }
        }

        // Top App Bar Chrome overlay
        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                IconButton(
                    onClick = { viewModel.activeMediaItem = null },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                        IconButton(onClick = { viewModel.toggleTrashed(context, item) }) {
                            Icon(
                                imageVector = Icons.Default.RestoreFromTrash,
                                contentDescription = "Restore",
                                tint = Color.White
                            )
                        }

                        IconButton(onClick = { showDeletePermanentlyConfirmDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = "Delete Permanently",
                                tint = Color.Red
                            )
                        }
                    } else {
                        // Standard actions
                        IconButton(onClick = { viewModel.toggleFavorite(item) }) {
                            Icon(
                                imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (item.isFavorite) Color.Red else Color.White
                            )
                        }

                        if (item is com.HrshD1eux.Gallery.data.model.MediaItem.Photo) {
                            IconButton(onClick = { viewModel.editingMediaItem = item }) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = Color.White)
                            }
                        }

                        IconButton(onClick = { showShareDialog = true }) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                        }

                        IconButton(onClick = { showMoveToAlbumDialog = true }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move to Album", tint = Color.White)
                        }

                        IconButton(onClick = {
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
                title = { Text("Share Privacy Protection") },
                text = {
                    Column {
                        Text("Strips device manufacturer details, location markers, and metadata tags from files before sharing.")
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
                            Text("Remove GPS & metadata tags")
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
                onDismissRequest = { showInfoSheet = false }
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
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename Media") },
                text = {
                    OutlinedTextField(
                        value = renameInputText,
                        onValueChange = { renameInputText = it },
                        label = { Text("File Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (renameInputText.isNotBlank()) {
                                showRenameDialog = false
                                viewModel.renameMedia(context, item, renameInputText.trim())
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
                title = { Text("Move to Encrypted Vault?") },
                text = {
                    Column {
                        Text(
                            text = "This photo/video will be encrypted with hardware AES-256-GCM and stored safely in your private Vault.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "⚠️ Notice: The original unencrypted file will be removed from public device storage so other apps cannot see it.",
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
    }
}
