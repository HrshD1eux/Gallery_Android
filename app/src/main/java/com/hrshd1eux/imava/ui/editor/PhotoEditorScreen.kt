package com.hrshd1eux.imava.ui.editor

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hrshd1eux.imava.core.util.PhotoEditorUtils
import com.hrshd1eux.imava.data.model.MediaItem
import com.hrshd1eux.imava.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

enum class EditorTab {
    ROTATE,
    FLIP,
    CROP,
    TUNING
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
    var uncroppedPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var transformedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableStateOf(EditorTab.ROTATE) }
    var rotationDegrees by remember { mutableFloatStateOf(0f) }
    var flipHorizontal by remember { mutableStateOf(false) }
    var flipVertical by remember { mutableStateOf(false) }
    
    // Tuning & Filters
    var brightnessOffset by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1.0f) }
    var saturation by remember { mutableFloatStateOf(1.0f) }
    var warmth by remember { mutableFloatStateOf(0f) }
    var selectedPreset by remember { mutableStateOf("none") }
    var activeTuneSubTab by remember { mutableStateOf("presets") } // "presets", "brightness", "contrast", "saturation", "warmth"
    
    // Crop ratio preset: null = Freeform, 1.0f = 1:1, 1.333f = 4:3, 1.777f = 16:9
    var cropAspectRatio by remember { mutableStateOf<Float?>(null) }
    
    // Freeform crop margins (0f to 0.9f normalized offset for left/top/right/bottom)
    var freeCropLeft by remember { mutableFloatStateOf(0f) }
    var freeCropTop by remember { mutableFloatStateOf(0f) }
    var freeCropRight by remember { mutableFloatStateOf(0f) }
    var freeCropBottom by remember { mutableFloatStateOf(0f) }
    var isCropApplied by remember { mutableStateOf(false) }

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
    LaunchedEffect(
        sourceBitmap, rotationDegrees, flipHorizontal, flipVertical,
        cropAspectRatio, freeCropLeft, freeCropTop, freeCropRight, freeCropBottom,
        brightnessOffset, contrast, saturation, warmth, selectedPreset
    ) {
        val src = sourceBitmap ?: return@LaunchedEffect
        withContext(Dispatchers.Default) {
            // Uncropped base transformed bitmap (for live crop overlay editing)
            val baseBmp = PhotoEditorUtils.transformBitmap(
                source = src,
                rotationDegrees = rotationDegrees,
                flipHorizontal = flipHorizontal,
                flipVertical = flipVertical,
                cropRect = null,
                brightnessOffset = brightnessOffset,
                contrast = contrast,
                saturation = saturation,
                warmth = warmth,
                preset = selectedPreset
            )
            uncroppedPreviewBitmap = baseBmp

            val cropRect = if (cropAspectRatio != null) {
                // Preset aspect ratio center crop
                val ratio = cropAspectRatio!!
                val srcWidth = baseBmp.width.toFloat()
                val srcHeight = baseBmp.height.toFloat()
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

            transformedBitmap = if (cropRect != null) {
                PhotoEditorUtils.transformBitmap(
                    source = src,
                    rotationDegrees = rotationDegrees,
                    flipHorizontal = flipHorizontal,
                    flipVertical = flipVertical,
                    cropRect = cropRect,
                    brightnessOffset = brightnessOffset,
                    contrast = contrast,
                    saturation = saturation,
                    warmth = warmth,
                    preset = selectedPreset
                )
            } else {
                baseBmp
            }
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
                                        brightnessOffset = brightnessOffset,
                                        contrast = contrast,
                                        saturation = saturation,
                                        warmth = warmth,
                                        preset = selectedPreset
                                    )

                                    viewModel.saveEditedPhoto(context, mediaItem, finalFullResBitmap)
                                } else {
                                    val fallbackBmp = transformedBitmap ?: uncroppedPreviewBitmap ?: sourceBitmap
                                    if (fallbackBmp != null) {
                                        viewModel.saveEditedPhoto(context, mediaItem, fallbackBmp)
                                    }
                                }
                            }
                            com.hrshd1eux.imava.core.util.HapticUtil.performSuccess(context)
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
                .padding(top = 64.dp, bottom = 200.dp),
            contentAlignment = Alignment.Center
        ) {
            // When in Crop tab and not applied, display uncropped preview for pointer manipulation
            val previewBitmap = if (selectedTab == EditorTab.CROP && !isCropApplied) {
                uncroppedPreviewBitmap ?: sourceBitmap
            } else {
                transformedBitmap ?: uncroppedPreviewBitmap ?: sourceBitmap
            }

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

                        if (selectedTab == EditorTab.CROP && !isCropApplied) {
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
                .background(Color.Black.copy(alpha = 0.95f))
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
                            // Apply / Reset Action Row
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        freeCropLeft = 0f
                                        freeCropTop = 0f
                                        freeCropRight = 0f
                                        freeCropBottom = 0f
                                        cropAspectRatio = null
                                        isCropApplied = false
                                    }
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reset", color = Color.White)
                                }

                                Button(
                                    onClick = {
                                        isCropApplied = true
                                        com.hrshd1eux.imava.core.util.HapticUtil.performSuccess(context)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Apply Crop")
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = cropAspectRatio == null,
                                    onClick = {
                                        cropAspectRatio = null
                                        isCropApplied = false
                                    },
                                    label = { Text("Freeform") }
                                )
                                FilterChip(
                                    selected = cropAspectRatio == 1.0f,
                                    onClick = {
                                        cropAspectRatio = 1.0f
                                        isCropApplied = true
                                    },
                                    label = { Text("1:1") }
                                )
                                FilterChip(
                                    selected = cropAspectRatio == 1.3333f,
                                    onClick = {
                                        cropAspectRatio = 1.3333f
                                        isCropApplied = true
                                    },
                                    label = { Text("4:3") }
                                )
                                FilterChip(
                                    selected = cropAspectRatio == 1.7777f,
                                    onClick = {
                                        cropAspectRatio = 1.7777f
                                        isCropApplied = true
                                    },
                                    label = { Text("16:9") }
                                )
                            }
                        }
                    }
                    EditorTab.TUNING -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Sub-tabs / Filters switcher
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    FilterChip(
                                        selected = activeTuneSubTab == "presets",
                                        onClick = { activeTuneSubTab = "presets" },
                                        label = { Text("🎨 Presets") }
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = activeTuneSubTab == "brightness",
                                        onClick = { activeTuneSubTab = "brightness" },
                                        label = { Text("☀️ Brightness") }
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = activeTuneSubTab == "contrast",
                                        onClick = { activeTuneSubTab = "contrast" },
                                        label = { Text("🌓 Contrast") }
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = activeTuneSubTab == "saturation",
                                        onClick = { activeTuneSubTab = "saturation" },
                                        label = { Text("🌈 Saturation") }
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = activeTuneSubTab == "warmth",
                                        onClick = { activeTuneSubTab = "warmth" },
                                        label = { Text("🔥 Warmth") }
                                    )
                                }
                            }

                            when (activeTuneSubTab) {
                                "presets" -> {
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val presets = listOf(
                                            "none" to "Original",
                                            "bw" to "B&W",
                                            "sunset" to "Sunset",
                                            "cool" to "Cool",
                                            "sepia" to "Sepia",
                                            "vivid" to "Vivid"
                                        )
                                        items(presets) { (key, label) ->
                                            FilterChip(
                                                selected = selectedPreset == key,
                                                onClick = { selectedPreset = key },
                                                label = { Text(label) }
                                            )
                                        }
                                    }
                                }
                                "brightness" -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Slider(
                                            value = brightnessOffset,
                                            onValueChange = { brightnessOffset = it },
                                            valueRange = -100f..100f,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = "${brightnessOffset.toInt()}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                "contrast" -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Slider(
                                            value = contrast,
                                            onValueChange = { contrast = it },
                                            valueRange = 0.5f..2.0f,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = String.format("%.1fx", contrast), color = Color.White, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                "saturation" -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Slider(
                                            value = saturation,
                                            onValueChange = { saturation = it },
                                            valueRange = 0.0f..2.0f,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = String.format("%.1fx", saturation), color = Color.White, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                "warmth" -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Slider(
                                            value = warmth,
                                            onValueChange = { warmth = it },
                                            valueRange = -50f..50f,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = "${warmth.toInt()}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
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
                    onClick = {
                        selectedTab = EditorTab.CROP
                        isCropApplied = false
                    },
                    icon = { Icon(Icons.Default.Crop, contentDescription = "Crop") },
                    label = { Text("Crop") }
                )
                NavigationBarItem(
                    selected = selectedTab == EditorTab.TUNING,
                    onClick = { selectedTab = EditorTab.TUNING },
                    icon = { Icon(Icons.Default.Tune, contentDescription = "Tune") },
                    label = { Text("Tuning") }
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
    modifier: Modifier = Modifier,
    onCropChange: (left: Float, top: Float, right: Float, bottom: Float) -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(left, top, right, bottom) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()
                    if (width <= 0f || height <= 0f) return@detectDragGestures

                    val touchX = change.position.x / width
                    val touchY = change.position.y / height

                    val currentLeft = left
                    val currentTop = top
                    val currentRight = 1f - right
                    val currentBottom = 1f - bottom

                    val distLeft = abs(touchX - currentLeft)
                    val distRight = abs(touchX - currentRight)
                    val distTop = abs(touchY - currentTop)
                    val distBottom = abs(touchY - currentBottom)

                    val isHorizontalHandle = distLeft < distTop && distLeft < distBottom || distRight < distTop && distRight < distBottom

                    if (isHorizontalHandle) {
                        if (distLeft < distRight) {
                            val newLeft = (currentLeft + dragAmount.x / width).coerceIn(0f, currentRight - 0.1f)
                            onCropChange(newLeft, top, right, bottom)
                        } else {
                            val newRight = (1f - (currentRight + dragAmount.x / width)).coerceIn(0f, 1f - currentLeft - 0.1f)
                            onCropChange(left, top, newRight, bottom)
                        }
                    } else {
                        if (distTop < distBottom) {
                            val newTop = (currentTop + dragAmount.y / height).coerceIn(0f, currentBottom - 0.1f)
                            onCropChange(left, newTop, right, bottom)
                        } else {
                            val newBottom = (1f - (currentBottom + dragAmount.y / height)).coerceIn(0f, 1f - currentTop - 0.1f)
                            onCropChange(left, top, right, newBottom)
                        }
                    }
                }
            }
    ) {
        // Semi-transparent dark overlay around the cropped window
        Column(modifier = Modifier.fillMaxSize()) {
            if (top > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(top.coerceAtLeast(0.001f))
                        .background(Color.Black.copy(alpha = 0.6f))
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight((1f - top - bottom).coerceAtLeast(0.001f))
            ) {
                if (left > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(left.coerceAtLeast(0.001f))
                            .background(Color.Black.copy(alpha = 0.6f))
                    )
                }
                // Center clear cropped area with 3x3 grid lines
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight((1f - left - right).coerceAtLeast(0.001f))
                        .background(Color.Transparent)
                ) {
                    // Outer border
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Transparent)
                            .padding(1.dp)
                    )
                }
                if (right > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(right.coerceAtLeast(0.001f))
                            .background(Color.Black.copy(alpha = 0.6f))
                    )
                }
            }
            if (bottom > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(bottom.coerceAtLeast(0.001f))
                        .background(Color.Black.copy(alpha = 0.6f))
                )
            }
        }
    }
}
