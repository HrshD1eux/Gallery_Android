package com.HrshD1eux.Gallery.ui.editor

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.HrshD1eux.Gallery.core.util.PhotoEditorUtils
import com.HrshD1eux.Gallery.data.model.MediaItem
import com.HrshD1eux.Gallery.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class EditorTab {
    ROTATE,
    FLIP,
    CROP,
    BRIGHTNESS
}

@Composable
fun PhotoEditorScreen(
    viewModel: MainViewModel,
    mediaItem: MediaItem.Photo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var transformedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableStateOf(EditorTab.ROTATE) }
    var rotationDegrees by remember { mutableFloatStateOf(0f) }
    var flipHorizontal by remember { mutableStateOf(false) }
    var flipVertical by remember { mutableStateOf(false) }
    var brightnessOffset by remember { mutableFloatStateOf(0f) }
    
    // Crop ratio preset: null = Freeform, 1.0f = 1:1, 1.333f = 4:3, 1.777f = 16:9
    var cropAspectRatio by remember { mutableStateOf<Float?>(null) }
    
    // Freeform crop margins (0f to 0.4f normalized offset for left/top/right/bottom)
    var freeCropLeft by remember { mutableFloatStateOf(0f) }
    var freeCropTop by remember { mutableFloatStateOf(0f) }
    var freeCropRight by remember { mutableFloatStateOf(0f) }
    var freeCropBottom by remember { mutableFloatStateOf(0f) }

    val displayMetrics = context.resources.displayMetrics
    val reqWidth = displayMetrics.widthPixels.coerceAtLeast(1080)
    val reqHeight = displayMetrics.heightPixels.coerceAtLeast(1920)

    // Load sub-sampled source bitmap for preview to prevent OOM
    LaunchedEffect(mediaItem) {
        withContext(Dispatchers.IO) {
            sourceBitmap = PhotoEditorUtils.decodeSubSampledBitmapFromUri(
                context,
                mediaItem.uri,
                reqWidth,
                reqHeight
            )
        }
    }

    // Recompute transformed preview bitmap whenever adjustments change
    LaunchedEffect(sourceBitmap, rotationDegrees, flipHorizontal, flipVertical, cropAspectRatio, freeCropLeft, freeCropTop, freeCropRight, freeCropBottom, brightnessOffset) {
        val src = sourceBitmap ?: return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val cropRect = if (cropAspectRatio != null) {
                // Preset aspect ratio center crop
                val ratio = cropAspectRatio!!
                val srcWidth = src.width.toFloat()
                val srcHeight = src.height.toFloat()
                val srcRatio = srcWidth / srcHeight

                if (srcRatio > ratio) {
                    val cropW = srcHeight * ratio
                    val left = (srcWidth - cropW) / 2f / srcWidth
                    val right = 1f - left
                    RectF(left, 0f, right, 1f)
                } else {
                    val cropH = srcWidth / ratio
                    val top = (srcHeight - cropH) / 2f / srcHeight
                    val bottom = 1f - top
                    RectF(0f, top, 1f, bottom)
                }
            } else if (freeCropLeft > 0f || freeCropTop > 0f || freeCropRight > 0f || freeCropBottom > 0f) {
                // Freeform custom crop boundaries
                RectF(freeCropLeft, freeCropTop, 1f - freeCropRight, 1f - freeCropBottom)
            } else {
                null
            }

            transformedBitmap = PhotoEditorUtils.transformBitmap(
                source = src,
                rotationDegrees = rotationDegrees,
                flipHorizontal = flipHorizontal,
                flipVertical = flipVertical,
                cropRect = cropRect,
                brightnessOffset = brightnessOffset
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                enabled = !isSaving
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
            }

            Text(
                text = "Photo Editor",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )

            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                TextButton(
                    onClick = {
                        if (isSaving) return@TextButton
                        isSaving = true
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val fullResSource = PhotoEditorUtils.decodeFullBitmapFromUri(context, mediaItem.uri)
                                if (fullResSource != null) {
                                    val fullResCropRect = if (cropAspectRatio != null) {
                                        val ratio = cropAspectRatio!!
                                        val srcWidth = fullResSource.width.toFloat()
                                        val srcHeight = fullResSource.height.toFloat()
                                        val srcRatio = srcWidth / srcHeight

                                        if (srcRatio > ratio) {
                                            val cropW = srcHeight * ratio
                                            val left = (srcWidth - cropW) / 2f / srcWidth
                                            val right = 1f - left
                                            RectF(left, 0f, right, 1f)
                                        } else {
                                            val cropH = srcWidth / ratio
                                            val top = (srcHeight - cropH) / 2f / srcHeight
                                            val bottom = 1f - top
                                            RectF(0f, top, 1f, bottom)
                                        }
                                    } else if (freeCropLeft > 0f || freeCropTop > 0f || freeCropRight > 0f || freeCropBottom > 0f) {
                                        RectF(freeCropLeft, freeCropTop, 1f - freeCropRight, 1f - freeCropBottom)
                                    } else {
                                        null
                                    }

                                    val finalFullResBitmap = PhotoEditorUtils.transformBitmap(
                                        source = fullResSource,
                                        rotationDegrees = rotationDegrees,
                                        flipHorizontal = flipHorizontal,
                                        flipVertical = flipVertical,
                                        cropRect = fullResCropRect,
                                        brightnessOffset = brightnessOffset
                                    )

                                    viewModel.saveEditedPhoto(context, mediaItem, finalFullResBitmap)
                                } else {
                                    val fallbackBmp = transformedBitmap ?: sourceBitmap
                                    if (fallbackBmp != null) {
                                        viewModel.saveEditedPhoto(context, mediaItem, fallbackBmp)
                                    }
                                }
                            }
                            isSaving = false
                            onDismiss()
                        }
                    }
                ) {
                    Text("Save as copy", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        // Center Image Preview & Interactive 3x3 Crop Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp, bottom = 180.dp),
            contentAlignment = Alignment.Center
        ) {
            val previewBitmap = transformedBitmap ?: sourceBitmap
            if (previewBitmap != null) {
                val bitmapAspect = (previewBitmap.width.toFloat() / previewBitmap.height.toFloat()).coerceAtLeast(0.1f)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.aspectRatio(bitmapAspect)
                    ) {
                        Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = "Edited Photo Preview",
                            modifier = Modifier.fillMaxSize()
                        )

                        if (selectedTab == EditorTab.CROP) {
                            CropGridOverlay(
                                left = freeCropLeft,
                                top = freeCropTop,
                                right = freeCropRight,
                                bottom = freeCropBottom,
                                modifier = Modifier.fillMaxSize(),
                                onCropChange = { l, t, r, b ->
                                    freeCropLeft = l
                                    freeCropTop = t
                                    freeCropRight = r
                                    freeCropBottom = b
                                }
                            )
                        }
                    }
                }
            } else {
                CircularProgressIndicator(color = Color.White)
            }
        }

        // Bottom Controls Container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.9f))
        ) {
            // Contextual Control Panel for active tab
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                when (selectedTab) {
                    EditorTab.ROTATE -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            IconButton(onClick = { rotationDegrees = (rotationDegrees - 90f + 360f) % 360f }) {
                                Icon(imageVector = Icons.Default.Rotate90DegreesCcw, contentDescription = "Rotate Left", tint = Color.White)
                            }
                            Text(text = "${rotationDegrees.toInt()}°", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { rotationDegrees = (rotationDegrees + 90f) % 360f }) {
                                Icon(imageVector = Icons.Default.Rotate90DegreesCw, contentDescription = "Rotate Right", tint = Color.White)
                            }
                        }
                    }
                    EditorTab.FLIP -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = flipHorizontal,
                                onClick = { flipHorizontal = !flipHorizontal },
                                label = { Text("Flip Horizontal") }
                            )
                            FilterChip(
                                selected = flipVertical,
                                onClick = { flipVertical = !flipVertical },
                                label = { Text("Flip Vertical") }
                            )
                        }
                    }
                    EditorTab.CROP -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = cropAspectRatio == null,
                                    onClick = { cropAspectRatio = null },
                                    label = { Text("Free Crop") }
                                )
                                FilterChip(
                                    selected = cropAspectRatio == 1.0f,
                                    onClick = { cropAspectRatio = 1.0f },
                                    label = { Text("1:1") }
                                )
                                FilterChip(
                                    selected = cropAspectRatio == 1.3333f,
                                    onClick = { cropAspectRatio = 1.3333f },
                                    label = { Text("4:3") }
                                )
                                FilterChip(
                                    selected = cropAspectRatio == 1.7777f,
                                    onClick = { cropAspectRatio = 1.7777f },
                                    label = { Text("16:9") }
                                )
                            }

                        }
                    }
                    EditorTab.BRIGHTNESS -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(text = "Brightness", color = Color.White, style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = brightnessOffset,
                                onValueChange = { brightnessOffset = it },
                                valueRange = -100f..100f,
                                modifier = Modifier.weight(1f)
                            )
                            Text(text = "${brightnessOffset.toInt()}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Bottom Navigation Tabs
            NavigationBar(
                containerColor = Color.Black,
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = selectedTab == EditorTab.ROTATE,
                    onClick = { selectedTab = EditorTab.ROTATE },
                    icon = { Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate") },
                    label = { Text("Rotate") }
                )
                NavigationBarItem(
                    selected = selectedTab == EditorTab.FLIP,
                    onClick = { selectedTab = EditorTab.FLIP },
                    icon = { Icon(Icons.Default.Flip, contentDescription = "Flip") },
                    label = { Text("Flip") }
                )
                NavigationBarItem(
                    selected = selectedTab == EditorTab.CROP,
                    onClick = { selectedTab = EditorTab.CROP },
                    icon = { Icon(Icons.Default.Crop, contentDescription = "Crop") },
                    label = { Text("Crop") }
                )
                NavigationBarItem(
                    selected = selectedTab == EditorTab.BRIGHTNESS,
                    onClick = { selectedTab = EditorTab.BRIGHTNESS },
                    icon = { Icon(Icons.Default.Brightness6, contentDescription = "Brightness") },
                    label = { Text("Brightness") }
                )
            }
        }
    }
}

@Composable
fun CropGridOverlay(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    onCropChange: (left: Float, top: Float, right: Float, bottom: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeHandle by remember { mutableStateOf<Int?>(null) } // 0: TopLeft, 1: TopRight, 2: BottomLeft, 3: BottomRight

    val dragModifier = modifier.pointerInput(left, top, right, bottom) {
        detectDragGestures(
            onDragStart = { offset ->
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                val cropL = left * w
                val cropT = top * h
                val cropR = (1f - right) * w
                val cropB = (1f - bottom) * h

                val touchRadius = 80f
                val distTL = kotlin.math.hypot(offset.x - cropL, offset.y - cropT)
                val distTR = kotlin.math.hypot(offset.x - cropR, offset.y - cropT)
                val distBL = kotlin.math.hypot(offset.x - cropL, offset.y - cropB)
                val distBR = kotlin.math.hypot(offset.x - cropR, offset.y - cropB)

                activeHandle = when {
                    distTL <= touchRadius -> 0
                    distTR <= touchRadius -> 1
                    distBL <= touchRadius -> 2
                    distBR <= touchRadius -> 3
                    else -> null
                }
            },
            onDragEnd = { activeHandle = null },
            onDragCancel = { activeHandle = null },
            onDrag = { change, dragAmount ->
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                if (w <= 0 || h <= 0 || activeHandle == null) return@detectDragGestures

                var nLeft = left
                var nTop = top
                var nRight = right
                var nBottom = bottom

                val deltaX = dragAmount.x / w
                val deltaY = dragAmount.y / h

                when (activeHandle) {
                    0 -> { // TopLeft
                        nLeft = (left + deltaX).coerceIn(0f, 0.4f)
                        nTop = (top + deltaY).coerceIn(0f, 0.4f)
                    }
                    1 -> { // TopRight
                        nRight = (right - deltaX).coerceIn(0f, 0.4f)
                        nTop = (top + deltaY).coerceIn(0f, 0.4f)
                    }
                    2 -> { // BottomLeft
                        nLeft = (left + deltaX).coerceIn(0f, 0.4f)
                        nBottom = (bottom - deltaY).coerceIn(0f, 0.4f)
                    }
                    3 -> { // BottomRight
                        nRight = (right - deltaX).coerceIn(0f, 0.4f)
                        nBottom = (bottom - deltaY).coerceIn(0f, 0.4f)
                    }
                }
                onCropChange(nLeft, nTop, nRight, nBottom)
            }
        )
    }

    androidx.compose.foundation.Canvas(modifier = dragModifier) {
        val w = size.width
        val h = size.height

        val cropL = left * w
        val cropT = top * h
        val cropR = (1f - right) * w
        val cropB = (1f - bottom) * h

        val cropW = (cropR - cropL).coerceAtLeast(10f)
        val cropH = (cropB - cropT).coerceAtLeast(10f)

        // Semi-transparent dim background outside crop box
        drawRect(color = Color.Black.copy(alpha = 0.5f), size = androidx.compose.ui.geometry.Size(w, cropT))
        drawRect(color = Color.Black.copy(alpha = 0.5f), topLeft = androidx.compose.ui.geometry.Offset(0f, cropT), size = androidx.compose.ui.geometry.Size(cropL, cropH))
        drawRect(color = Color.Black.copy(alpha = 0.5f), topLeft = androidx.compose.ui.geometry.Offset(cropR, cropT), size = androidx.compose.ui.geometry.Size(w - cropR, cropH))
        drawRect(color = Color.Black.copy(alpha = 0.5f), topLeft = androidx.compose.ui.geometry.Offset(0f, cropB), size = androidx.compose.ui.geometry.Size(w, h - cropB))

        // White border around crop box
        drawRect(
            color = Color.White,
            topLeft = androidx.compose.ui.geometry.Offset(cropL, cropT),
            size = androidx.compose.ui.geometry.Size(cropW, cropH),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )

        // 3x3 Rule of thirds grid lines
        val x1 = cropL + cropW / 3f
        val x2 = cropL + 2f * cropW / 3f
        val y1 = cropT + cropH / 3f
        val y2 = cropT + 2f * cropH / 3f

        drawLine(color = Color.White.copy(alpha = 0.6f), start = androidx.compose.ui.geometry.Offset(x1, cropT), end = androidx.compose.ui.geometry.Offset(x1, cropB), strokeWidth = 1.dp.toPx())
        drawLine(color = Color.White.copy(alpha = 0.6f), start = androidx.compose.ui.geometry.Offset(x2, cropT), end = androidx.compose.ui.geometry.Offset(x2, cropB), strokeWidth = 1.dp.toPx())
        drawLine(color = Color.White.copy(alpha = 0.6f), start = androidx.compose.ui.geometry.Offset(cropL, y1), end = androidx.compose.ui.geometry.Offset(cropR, y1), strokeWidth = 1.dp.toPx())
        drawLine(color = Color.White.copy(alpha = 0.6f), start = androidx.compose.ui.geometry.Offset(cropL, y2), end = androidx.compose.ui.geometry.Offset(cropR, y2), strokeWidth = 1.dp.toPx())

        // 4 Corner Drag Handle circles
        val handleRadius = 10.dp.toPx()
        drawCircle(color = Color.White, radius = handleRadius, center = androidx.compose.ui.geometry.Offset(cropL, cropT))
        drawCircle(color = Color.White, radius = handleRadius, center = androidx.compose.ui.geometry.Offset(cropR, cropT))
        drawCircle(color = Color.White, radius = handleRadius, center = androidx.compose.ui.geometry.Offset(cropL, cropB))
        drawCircle(color = Color.White, radius = handleRadius, center = androidx.compose.ui.geometry.Offset(cropR, cropB))
    }
}
