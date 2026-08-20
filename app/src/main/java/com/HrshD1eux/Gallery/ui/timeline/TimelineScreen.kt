package com.HrshD1eux.Gallery.ui.timeline

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.HrshD1eux.Gallery.data.model.MediaItem
import com.HrshD1eux.Gallery.data.model.formattedDuration
import com.HrshD1eux.Gallery.data.model.isVideo
import com.HrshD1eux.Gallery.data.model.TimelineItem
import com.HrshD1eux.Gallery.ui.MainViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimelineScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val lazyPagingItems = viewModel.pagingDataFlow.collectAsLazyPagingItems()
    val context = LocalContext.current
    val selectionState = viewModel.selectionState

    // 2. Pinch-to-zoom: continuous cell width
    var targetColumnWidth by remember { mutableFloatStateOf(120f) }

    val gridState = rememberLazyStaggeredGridState()

    val dragSelectionModifier = if (selectionState.inSelectionMode) {
        Modifier.pointerInput(selectionState.inSelectionMode) {
            detectDragGestures(
                onDragStart = { offset ->
                    val hitItem = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                        offset.x >= info.offset.x && offset.x <= (info.offset.x + info.size.width) &&
                        offset.y >= info.offset.y && offset.y <= (info.offset.y + info.size.height)
                    }
                    if (hitItem != null) {
                        val timelineItem = lazyPagingItems.peek(hitItem.index)
                        if (timelineItem is TimelineItem.Media) {
                            selectionState.select(timelineItem.item.id)
                            com.HrshD1eux.Gallery.core.util.HapticUtil.performSelection(context)
                        }
                    }
                },
                onDrag = { change, _ ->
                    change.consume()
                    val offset = change.position
                    val hitItem = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                        offset.x >= info.offset.x && offset.x <= (info.offset.x + info.size.width) &&
                        offset.y >= info.offset.y && offset.y <= (info.offset.y + info.size.height)
                    }
                    if (hitItem != null) {
                        val timelineItem = lazyPagingItems.peek(hitItem.index)
                        if (timelineItem is TimelineItem.Media && !selectionState.selectedIds.contains(timelineItem.item.id)) {
                            selectionState.select(timelineItem.item.id)
                            com.HrshD1eux.Gallery.core.util.HapticUtil.performSelection(context)
                        }
                    }
                }
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .then(
                if (!selectionState.inSelectionMode) {
                    Modifier.pointerInput(Unit) {
                        detectTransformGestures(panZoomLock = true) { _, _, zoom: Float, _ ->
                            if (kotlin.math.abs(zoom - 1f) > 0.02f) {
                                targetColumnWidth = (targetColumnWidth / zoom).coerceIn(80f, 300f)
                            }
                        }
                    }
                } else Modifier
            )
    ) {
        if (lazyPagingItems.itemCount == 0) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No media found on device",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(minSize = targetColumnWidth.dp),
                    state = gridState,
                    contentPadding = PaddingValues(
                        start = 2.dp,
                        end = 2.dp,
                        top = 8.dp,
                        bottom = 100.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalItemSpacing = 2.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(dragSelectionModifier)
                ) {
                    items(
                        count = lazyPagingItems.itemCount,
                        key = { index ->
                            val item = lazyPagingItems.peek(index)
                            when (item) {
                                is TimelineItem.Header -> "header_${item.title}"
                                is TimelineItem.Media -> item.item.id
                                null -> "placeholder_$index"
                            }
                        },
                        span = { index ->
                            val item = lazyPagingItems.peek(index)
                            if (item is TimelineItem.Header) {
                                StaggeredGridItemSpan.FullLine
                            } else {
                                StaggeredGridItemSpan.SingleLane
                            }
                        }
                    ) { index ->
                        val item = lazyPagingItems[index]
                        if (item != null) {
                            when (item) {
                                is TimelineItem.Header -> {
                                    TimelineHeader(title = item.title)
                                }
                                is TimelineItem.Media -> {
                                    val mediaItem = item.item
                                    val isSelected = selectionState.selectedIds.contains(mediaItem.id)
                                    val naturalRatio = if (mediaItem.width > 0 && mediaItem.height > 0) {
                                        mediaItem.width.toFloat() / mediaItem.height.toFloat()
                                    } else {
                                        1f
                                    }
                                    val cellRatio = if (viewModel.gridStyle == com.HrshD1eux.Gallery.ui.GridStyle.SQUARE) 1f else naturalRatio
                                    MediaGridCell(
                                        item = mediaItem,
                                        isSelected = isSelected,
                                        inSelectionMode = selectionState.inSelectionMode,
                                        aspectRatio = cellRatio,
                                        onClick = {
                                            if (selectionState.inSelectionMode) {
                                                selectionState.toggle(mediaItem.id)
                                                com.HrshD1eux.Gallery.core.util.HapticUtil.performSelection(context)
                                            } else {
                                                viewModel.activeMediaItem = mediaItem
                                            }
                                        },
                                        onLongClick = {
                                            selectionState.toggle(mediaItem.id)
                                            com.HrshD1eux.Gallery.core.util.HapticUtil.performLongPress(context)
                                        }
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            )
                        }
                    }
                }

                // Floating scroll scrubber on the right edge
                val dateHeaders by viewModel.datePositionHeaders.collectAsState()
                TimelineScrubber(
                    gridState = gridState,
                    headers = dateHeaders,
                    totalItemCount = lazyPagingItems.itemCount,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(vertical = 16.dp)
                )
            }
        }
    }
}

@Composable
fun TimelineHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGridCell(
    item: MediaItem,
    isSelected: Boolean,
    inSelectionMode: Boolean,
    aspectRatio: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest = remember(item.uri) {
        coil.request.ImageRequest.Builder(context)
            .data(item.uri)
            .crossfade(false)
            .allowHardware(true)
            .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
            .size(280, 280)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .precision(coil.size.Precision.INEXACT)
            .error(android.R.drawable.ic_menu_report_image)
            .fallback(android.R.drawable.ic_menu_report_image)
            .build()
    }

    val cellGradient = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.4f)
            ),
            startY = 100f
        )
    }

    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Gradient overlay for visual aesthetics and title visibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(cellGradient)
        )

        // Select mode overlays
        if (inSelectionMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        else Color.Transparent
                    )
            )
            
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
            )
        }

        // Trash days remaining indicator
        if (item.isTrashed) {
            val thirtyDaysMs = 30L * 24L * 60L * 60L * 1000L
            val daysRemaining = maxOf(1L, (thirtyDaysMs - (System.currentTimeMillis() - item.dateTaken)) / (24L * 60L * 60L * 1000L))
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color.Red.copy(alpha = 0.85f), MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${daysRemaining}d left",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
            }
        }

        // Video tag indicator
        if (item.isVideo) {
            Row(
                modifier = Modifier
                    .align(if (item.isTrashed) Alignment.BottomEnd else Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = item.formattedDuration,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
            }
        }
    }
}


