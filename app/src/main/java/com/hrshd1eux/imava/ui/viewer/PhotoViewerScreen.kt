package com.hrshd1eux.imava.ui.viewer

import android.content.Intent
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Print
import androidx.media3.ui.AspectRatioFrameLayout
import com.hrshd1eux.imava.core.util.FormatUtils
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
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
import com.hrshd1eux.imava.data.model.MediaItem
import com.hrshd1eux.imava.data.model.isVideo
import com.hrshd1eux.imava.ui.MainViewModel

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


    LaunchedEffect(activeItem.id) {
        val targetIndex = mediaItems.indexOfFirst { it.id == activeItem.id }
        if (targetIndex >= 0 && pagerState.currentPage != targetIndex) {
            pagerState.scrollToPage(targetIndex)
        }
    }


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
    var showAlbumCoverChooserDialog by remember { mutableStateOf(false) }
    var showVideoTrimDialog by remember { mutableStateOf(false) }

    var isMotionPhoto by remember { mutableStateOf(false) }
    var isPlayingMotionPhoto by remember { mutableStateOf(false) }
    var motionVideoFile by remember { mutableStateOf<java.io.File?>(null) }
    val scope = rememberCoroutineScope()

    val currentItem = mediaItems.getOrNull(pagerState.currentPage) ?: activeItem

    LaunchedEffect(currentItem.id) {
        isPlayingMotionPhoto = false
        motionVideoFile = null
        if (currentItem is com.hrshd1eux.imava.data.model.MediaItem.Photo) {
            val info = com.hrshd1eux.imava.core.util.MotionPhotoUtil.checkMotionPhoto(context, currentItem.uri)
            isMotionPhoto = info.isMotionPhoto
        } else {
            isMotionPhoto = false
        }
    }

    var isSlideshowActive by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var showMotionExportDialog by remember { mutableStateOf(false) }
    var showOcrSheet by remember { mutableStateOf(false) }
    var ocrRecognizedText by remember { mutableStateOf("") }
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


    var isCurrentPageZoomed by remember { mutableStateOf(false) }


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
                    viewModel.activeMediaItem = null
                } else {
                    dragOffsetY = 0f
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

                        val pageZoomState = rememberZoomState()


                        LaunchedEffect(pagerState.currentPage) {
                            if (page != pagerState.currentPage) {
                                pageZoomState.reset()
                            }
                        }


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
                                    onTap = { showChrome = !showChrome },
                                    onDismiss = { viewModel.activeMediaItem = null }
                                )
                        )
                    }
                }
            }
        }


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


                if (isMotionPhoto) {
                    FilterChip(
                        selected = isPlayingMotionPhoto,
                        onClick = {
                            scope.launch {
                                if (isPlayingMotionPhoto) {
                                    isPlayingMotionPhoto = false
                                } else {
                                    val info = com.hrshd1eux.imava.core.util.MotionPhotoUtil.checkMotionPhoto(context, currentItem.uri)
                                    val file = com.hrshd1eux.imava.core.util.MotionPhotoUtil.extractMotionVideo(context, currentItem.uri, info)
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

                    IconButton(
                        onClick = {
                            scope.launch {
                                val info = com.hrshd1eux.imava.core.util.MotionPhotoUtil.checkMotionPhoto(context, currentItem.uri)
                                val file = com.hrshd1eux.imava.core.util.MotionPhotoUtil.extractMotionVideo(context, currentItem.uri, info)
                                if (file != null) {
                                    motionVideoFile = file
                                    showMotionExportDialog = true
                                } else {
                                    android.widget.Toast.makeText(context, "Motion video not available", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.MovieFilter, contentDescription = "Motion Tools", tint = Color.White)
                    }
                }

                if (currentItem is com.hrshd1eux.imava.data.model.MediaItem.Photo) {
                    IconButton(onClick = {
                        scope.launch {
                            android.widget.Toast.makeText(context, "Scanning image for text...", android.widget.Toast.LENGTH_SHORT).show()
                            val result = com.hrshd1eux.imava.core.util.OcrHelper.recognizeTextFromUri(context, currentItem.uri)
                            if (result != null && result.fullText.isNotBlank()) {
                                ocrRecognizedText = result.fullText
                                showOcrSheet = true
                                viewModel.saveOcrText(currentItem.id, result.fullText)
                            } else {
                                android.widget.Toast.makeText(context, "No readable text detected", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Icon(imageVector = Icons.Default.DocumentScanner, contentDescription = "Copy Text", tint = Color.White)
                    }
                }

                if (currentItem is com.hrshd1eux.imava.data.model.MediaItem.Video) {
                    IconButton(onClick = {
                        scope.launch {
                            android.widget.Toast.makeText(context, "Muting video losslessly...", android.widget.Toast.LENGTH_SHORT).show()
                            val mutedUri = com.hrshd1eux.imava.core.util.VideoMuterUtil.muteVideo(context, currentItem.uri)
                            if (mutedUri != null) {
                                com.hrshd1eux.imava.core.util.HapticUtil.performSuccess(context)
                                android.widget.Toast.makeText(context, "Muted video saved to Movies/Muted! 🔇", android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                com.hrshd1eux.imava.core.util.HapticUtil.performError(context)
                                android.widget.Toast.makeText(context, "Could not mute video", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Icon(imageVector = Icons.Default.VolumeOff, contentDescription = "Mute Video", tint = Color.White)
                    }
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
                            text = { Text("Tags & Hashtags 🏷️") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Style,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                showInfoSheet = true
                            }
                        )

                        if (!viewModel.isVaultDisabled || item.isHidden) {
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

                        DropdownMenuItem(
                            text = { Text("Start Slideshow 🎞️") },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                isSlideshowActive = true
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Set as Wallpaper 📱") },
                            leadingIcon = { Icon(Icons.Default.Wallpaper, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                showWallpaperDialog = true
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Set as Album Cover 🖼️") },
                            leadingIcon = { Icon(Icons.Default.Album, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                showAlbumCoverChooserDialog = true
                            }
                        )

                        if (item is com.hrshd1eux.imava.data.model.MediaItem.Photo) {
                            DropdownMenuItem(
                                text = { Text("Copy Text from Photo (OCR) 📝") },
                                leadingIcon = { Icon(Icons.Default.DocumentScanner, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    scope.launch {
                                        android.widget.Toast.makeText(context, "Scanning image for text...", android.widget.Toast.LENGTH_SHORT).show()
                                        val result = com.hrshd1eux.imava.core.util.OcrHelper.recognizeTextFromUri(context, item.uri)
                                        if (result != null && result.fullText.isNotBlank()) {
                                            ocrRecognizedText = result.fullText
                                            showOcrSheet = true
                                            viewModel.saveOcrText(item.id, result.fullText)
                                        } else {
                                            android.widget.Toast.makeText(context, "No readable text detected", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )

                            if (isMotionPhoto) {
                                DropdownMenuItem(
                                    text = { Text("Motion Photo Tools 🎞️") },
                                    leadingIcon = { Icon(Icons.Default.MovieFilter, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        scope.launch {
                                            val info = com.hrshd1eux.imava.core.util.MotionPhotoUtil.checkMotionPhoto(context, item.uri)
                                            val file = com.hrshd1eux.imava.core.util.MotionPhotoUtil.extractMotionVideo(context, item.uri, info)
                                            if (file != null) {
                                                motionVideoFile = file
                                                showMotionExportDialog = true
                                            } else {
                                                android.widget.Toast.makeText(context, "Motion video not available", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }

                            DropdownMenuItem(
                                text = { Text("Print Photo 🖨️") },
                                leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    scope.launch {
                                        com.hrshd1eux.imava.core.util.PrintUtil.printPhoto(context, item)
                                    }
                                }
                            )
                        }

                        if (item is com.hrshd1eux.imava.data.model.MediaItem.Video) {
                            DropdownMenuItem(
                                text = { Text("Extract Audio (.m4a) 🎵") },
                                leadingIcon = { Icon(Icons.Default.Audiotrack, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    scope.launch {
                                        android.widget.Toast.makeText(context, "Extracting audio...", android.widget.Toast.LENGTH_SHORT).show()
                                        val name = java.io.File(item.path).nameWithoutExtension
                                        val audioUri = com.hrshd1eux.imava.core.util.AudioExtractor.extractAudioFromVideo(context, item.uri, name)
                                        if (audioUri != null) {
                                            com.hrshd1eux.imava.core.util.HapticUtil.performSuccess(context)
                                            android.widget.Toast.makeText(context, "Audio saved to Music/Imava_Audio", android.widget.Toast.LENGTH_LONG).show()
                                        } else {
                                            com.hrshd1eux.imava.core.util.HapticUtil.performError(context)
                                            android.widget.Toast.makeText(context, "Could not extract audio", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Trim Video / Make GIF 🎬") },
                                leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    showVideoTrimDialog = true
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Mute Audio & Save Copy 🔇") },
                                leadingIcon = { Icon(Icons.Default.VolumeOff, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    scope.launch {
                                        android.widget.Toast.makeText(context, "Muting video losslessly...", android.widget.Toast.LENGTH_SHORT).show()
                                        val mutedUri = com.hrshd1eux.imava.core.util.VideoMuterUtil.muteVideo(context, item.uri)
                                        if (mutedUri != null) {
                                            com.hrshd1eux.imava.core.util.HapticUtil.performSuccess(context)
                                            android.widget.Toast.makeText(context, "Muted video saved to Movies/Muted! 🔇", android.widget.Toast.LENGTH_LONG).show()
                                        } else {
                                            com.hrshd1eux.imava.core.util.HapticUtil.performError(context)
                                            android.widget.Toast.makeText(context, "Could not mute video", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
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

                        if (item is com.hrshd1eux.imava.data.model.MediaItem.Photo) {
                            DropdownMenuItem(
                                text = { Text("Compress Image 📉") },
                                leadingIcon = { Icon(Icons.Default.Compress, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    showCompressDialog = true
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Copy Text from Image 📝") },
                                leadingIcon = { Icon(Icons.Default.DocumentScanner, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    scope.launch {
                                        android.widget.Toast.makeText(context, "Scanning image for text...", android.widget.Toast.LENGTH_SHORT).show()
                                        val result = com.hrshd1eux.imava.core.util.OcrHelper.recognizeTextFromUri(context, item.uri)
                                        if (result != null && result.fullText.isNotBlank()) {
                                            ocrRecognizedText = result.fullText
                                            showOcrSheet = true
                                            viewModel.saveOcrText(item.id, result.fullText)
                                        } else {
                                            android.widget.Toast.makeText(context, "No readable text detected", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )

                            if (isMotionPhoto) {
                                DropdownMenuItem(
                                    text = { Text("Motion Photo Tools (Scrub/GIF) 🎞️") },
                                    leadingIcon = { Icon(Icons.Default.MovieFilter, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        scope.launch {
                                            val info = com.hrshd1eux.imava.core.util.MotionPhotoUtil.checkMotionPhoto(context, item.uri)
                                            val file = com.hrshd1eux.imava.core.util.MotionPhotoUtil.extractMotionVideo(context, item.uri, info)
                                            if (file != null) {
                                                motionVideoFile = file
                                                showMotionExportDialog = true
                                            } else {
                                                android.widget.Toast.makeText(context, "Motion video not available", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }

                            if (item.mimeType.contains("gif", ignoreCase = true) || item.mimeType.contains("webp", ignoreCase = true)) {
                                DropdownMenuItem(
                                    text = { Text("Save Frame as JPEG") },
                                    leadingIcon = { Icon(Icons.Default.Photo, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                val loader = coil.ImageLoader(context)
                                                val req = coil.request.ImageRequest.Builder(context)
                                                    .data(item.uri)
                                                    .allowHardware(false)
                                                    .build()
                                                val result = loader.execute(req)
                                                val drawable = result.drawable
                                                val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                                                if (bitmap != null) {
                                                    val framesDir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "Frames")
                                                    framesDir.mkdirs()
                                                    val frameFile = java.io.File(framesDir, "Frame_${System.currentTimeMillis()}.jpg")
                                                    java.io.FileOutputStream(frameFile).use { out ->
                                                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                                                    }
                                                    android.media.MediaScannerConnection.scanFile(context, arrayOf(frameFile.absolutePath), null, null)
                                                    withContext(Dispatchers.Main) {
                                                        com.hrshd1eux.imava.core.util.HapticUtil.performSuccess(context)
                                                        android.widget.Toast.makeText(context, "Frame saved to Pictures/Frames", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }


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

                        IconButton(onClick = {
                            com.hrshd1eux.imava.core.util.HapticUtil.performSuccess(context)
                            viewModel.toggleTrashed(context, item)
                        }) {
                            Icon(
                                imageVector = Icons.Default.RestoreFromTrash,
                                contentDescription = "Restore",
                                tint = Color.White
                            )
                        }

                        IconButton(onClick = {
                            com.hrshd1eux.imava.core.util.HapticUtil.performClick(context)
                            showDeletePermanentlyConfirmDialog = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = "Delete Permanently",
                                tint = Color.Red
                            )
                        }
                    } else {

                        IconButton(onClick = {
                            com.hrshd1eux.imava.core.util.HapticUtil.performSelection(context)
                            viewModel.toggleFavorite(item)
                        }) {
                            Icon(
                                imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (item.isFavorite) Color.Red else Color.White
                            )
                        }

                        if (item is com.hrshd1eux.imava.data.model.MediaItem.Photo) {
                            IconButton(onClick = {
                                com.hrshd1eux.imava.core.util.HapticUtil.performClick(context)
                                viewModel.editingMediaItem = item
                            }) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = Color.White)
                            }
                        }

                        IconButton(onClick = {
                            com.hrshd1eux.imava.core.util.HapticUtil.performClick(context)
                            showShareDialog = true
                        }) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                        }

                        IconButton(onClick = {
                            com.hrshd1eux.imava.core.util.HapticUtil.performClick(context)
                            showMoveToAlbumDialog = true
                        }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move to Album", tint = Color.White)
                        }

                        IconButton(onClick = {
                            com.hrshd1eux.imava.core.util.HapticUtil.performLongPress(context)
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


        if (showInfoSheet) {
            val item = mediaItems.getOrNull(pagerState.currentPage) ?: activeItem
            InfoBottomSheet(
                item = item,
                sheetState = infoSheetState,
                onDismissRequest = { showInfoSheet = false },
                onUpdateDateTaken = { newDate ->
                    viewModel.updateMediaDateTaken(context, item, newDate)
                },
                onUpdateTags = { newTags ->
                    viewModel.updateMediaTags(item, newTags)
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .imePadding()
                    ) {

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
                                com.hrshd1eux.imava.core.util.HapticUtil.performClick(context)
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

        if (showAlbumCoverChooserDialog) {
            val item = mediaItems.getOrNull(pagerState.currentPage) ?: activeItem
            val buckets by viewModel.buckets.collectAsState()
            AlertDialog(
                onDismissRequest = { showAlbumCoverChooserDialog = false },
                icon = { Icon(Icons.Default.Album, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Set as Album Cover") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Select which album you want this photo to represent as its cover:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                            items(buckets) { bucket ->
                                val isCurrent = bucket.id == item.bucketId
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            viewModel.setCustomAlbumCover(bucket.id, item.id)
                                            showAlbumCoverChooserDialog = false
                                            com.hrshd1eux.imava.core.util.HapticUtil.performSuccess(context)
                                            android.widget.Toast.makeText(context, "Cover updated for '${bucket.name}'", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(bucket.name, style = MaterialTheme.typography.titleSmall)
                                            Text("${bucket.count} items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (isCurrent) {
                                            Text("(Current Album)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showAlbumCoverChooserDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showMoveToAlbumDialog) {
            val item = mediaItems.getOrNull(pagerState.currentPage) ?: activeItem
            val bucketList by viewModel.buckets.collectAsState()
            if (item != null) {
                com.hrshd1eux.imava.ui.common.MoveCopyAlbumDialog(
                    buckets = bucketList,
                    isCopy = false,
                    onDismiss = { showMoveToAlbumDialog = false },
                    onConfirm = { targetDir, isCopy ->
                        showMoveToAlbumDialog = false
                        viewModel.moveOrCopyMedia(context, listOf(item), targetDir, isCopy) { count ->
                            if (!isCopy && count > 0) {
                                viewModel.activeMediaItem = null
                            }
                        }
                    }
                )
            }
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
                title = { Text("Hide in Private Vault") },
                text = {
                    Column {
                        Text(
                            text = "This item will be safely locked in your private vault so only you can see it.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Next, your phone will ask to remove the original from your main gallery. Please tap Allow on the next screen.",
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
                        Text("Continue")
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
            val originalFormatted = com.hrshd1eux.imava.core.util.FormatUtils.formatFileSize(item.size)

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
                                val resultUri = com.hrshd1eux.imava.core.util.ImageCompressor.compressToTargetKb(context, item, kb)
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
                                showWallpaperDialog = true
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
                                    com.hrshd1eux.imava.core.util.WallpaperUtil.setAsContactPhoto(activity, item.uri)
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

        if (showWallpaperDialog) {
            val item = mediaItems.getOrNull(pagerState.currentPage) ?: activeItem
            val activity = context as? android.app.Activity
            var isSettingWallpaper by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { if (!isSettingWallpaper) showWallpaperDialog = false },
                icon = {
                    Icon(
                        Icons.Default.Wallpaper,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = { Text("Set Wallpaper 🎨", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isSettingWallpaper) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            FilledTonalButton(
                                onClick = {
                                    isSettingWallpaper = true
                                    scope.launch {
                                        val success = com.hrshd1eux.imava.core.util.WallpaperUtil.setWallpaperDirect(
                                            context,
                                            item.uri,
                                            com.hrshd1eux.imava.core.util.WallpaperUtil.WALLPAPER_HOME
                                        )
                                        isSettingWallpaper = false
                                        showWallpaperDialog = false
                                        if (success) {
                                            com.hrshd1eux.imava.core.util.HapticUtil.performSuccess(context)
                                            android.widget.Toast.makeText(context, "Home screen wallpaper applied! 🏠", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            android.widget.Toast.makeText(context, "Failed to apply wallpaper", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Home, contentDescription = null)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Home Screen 🏠")
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    isSettingWallpaper = true
                                    scope.launch {
                                        val success = com.hrshd1eux.imava.core.util.WallpaperUtil.setWallpaperDirect(
                                            context,
                                            item.uri,
                                            com.hrshd1eux.imava.core.util.WallpaperUtil.WALLPAPER_LOCK
                                        )
                                        isSettingWallpaper = false
                                        showWallpaperDialog = false
                                        if (success) {
                                            com.hrshd1eux.imava.core.util.HapticUtil.performSuccess(context)
                                            android.widget.Toast.makeText(context, "Lock screen wallpaper applied! 🔒", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            android.widget.Toast.makeText(context, "Failed to apply wallpaper", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Lock, contentDescription = null)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Lock Screen 🔒")
                                }
                            }

                            Button(
                                onClick = {
                                    isSettingWallpaper = true
                                    scope.launch {
                                        val success = com.hrshd1eux.imava.core.util.WallpaperUtil.setWallpaperDirect(
                                            context,
                                            item.uri,
                                            com.hrshd1eux.imava.core.util.WallpaperUtil.WALLPAPER_BOTH
                                        )
                                        isSettingWallpaper = false
                                        showWallpaperDialog = false
                                        if (success) {
                                            com.hrshd1eux.imava.core.util.HapticUtil.performSuccess(context)
                                            android.widget.Toast.makeText(context, "Home & Lock wallpaper applied! 📱", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            android.widget.Toast.makeText(context, "Failed to apply wallpaper", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.PhoneAndroid, contentDescription = null)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Both (Home & Lock Screen) 📱")
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    showWallpaperDialog = false
                                    if (activity != null) {
                                        com.hrshd1eux.imava.core.util.WallpaperUtil.launchPixelOrSystemWallpaperPicker(activity, item.uri)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Crop, contentDescription = null)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Pixel / System Wallpaper Picker ⚙️")
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    if (!isSettingWallpaper) {
                        TextButton(onClick = { showWallpaperDialog = false }) {
                            Text("Cancel")
                        }
                    }
                }
            )
        }


        if (showVideoTrimDialog) {
            val item = mediaItems.getOrNull(pagerState.currentPage) ?: activeItem
            if (item is com.hrshd1eux.imava.data.model.MediaItem.Video) {
                VideoTrimDialog(
                    mediaItem = item,
                    onDismiss = { showVideoTrimDialog = false },
                    onSuccess = { viewModel.refreshAll() }
                )
            }
        }

        if (showMotionExportDialog && motionVideoFile != null) {
            val baseName = java.io.File(currentItem.path).nameWithoutExtension.ifEmpty { "motion_photo" }
            MotionPhotoExportDialog(
                videoFile = motionVideoFile!!,
                baseName = baseName,
                onDismiss = { showMotionExportDialog = false }
            )
        }

        if (showOcrSheet && ocrRecognizedText.isNotBlank()) {
            OcrCopyBottomSheet(
                recognizedText = ocrRecognizedText,
                onDismiss = { showOcrSheet = false }
            )
        }
    }
}
