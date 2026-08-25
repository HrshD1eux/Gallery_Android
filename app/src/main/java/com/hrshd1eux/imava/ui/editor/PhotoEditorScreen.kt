package com.hrshd1eux.imava.ui.editor

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

    val hasModifications = rotationDegrees != 0f ||
            flipHorizontal || flipVertical ||
            brightnessOffset != 0f || contrast != 1.0f ||
            saturation != 1.0f || warmth != 0f ||
            selectedPreset != "none" ||
            freeCropLeft > 0.001f || freeCropTop > 0.001f ||
            freeCropRight > 0.001f || freeCropBottom > 0.001f ||
            cropAspectRatio != null

    var showDiscardConfirmDialog by remember { mutableStateOf(false) }

    fun performSaveEditedPhoto() {
        if (isSaving) return
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

    BackHandler(enabled = hasModifications && !isSaving) {
        showDiscardConfirmDialog = true
    }

    if (showDiscardConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text("Unsaved Edits") },
            text = { Text("You have unsaved changes to this photo. Do you want to save your edits or discard them?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardConfirmDialog = false
                        performSaveEditedPhoto()
                    }
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showDiscardConfirmDialog = false
                            onDismiss()
                        }
                    ) {
                        Text("Discard", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = { showDiscardConfirmDialog = false }) {
                        Text("Keep Editing")
                    }
                }
            }
        )
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
                onClick = {
                    if (hasModifications) {
                        showDiscardConfirmDialog = true
                    } else {
                        onDismiss()
                    }
                },
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
                    onClick = { performSaveEditedPhoto() }
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

private enum class CropHandle {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
    CENTER
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
    var activeHandle by remember { mutableStateOf(CropHandle.NONE) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(left, top, right, bottom) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        if (width <= 0f || height <= 0f) return@detectDragGestures

                        val touchX = startOffset.x
                        val touchY = startOffset.y

                        val curLeftPx = left * width
                        val curTopPx = top * height
                        val curRightPx = (1f - right) * width
                        val curBottomPx = (1f - bottom) * height

                        val cornerRadiusPx = 48.dp.toPx()
                        val edgeMarginPx = 32.dp.toPx()

                        fun dist(x1: Float, y1: Float, x2: Float, y2: Float) =
                            kotlin.math.hypot(x1 - x2, y1 - y2)

                        activeHandle = when {
                            dist(touchX, touchY, curLeftPx, curTopPx) < cornerRadiusPx -> CropHandle.TOP_LEFT
                            dist(touchX, touchY, curRightPx, curTopPx) < cornerRadiusPx -> CropHandle.TOP_RIGHT
                            dist(touchX, touchY, curLeftPx, curBottomPx) < cornerRadiusPx -> CropHandle.BOTTOM_LEFT
                            dist(touchX, touchY, curRightPx, curBottomPx) < cornerRadiusPx -> CropHandle.BOTTOM_RIGHT
                            abs(touchX - curLeftPx) < edgeMarginPx && touchY in curTopPx..curBottomPx -> CropHandle.LEFT
                            abs(touchX - curRightPx) < edgeMarginPx && touchY in curTopPx..curBottomPx -> CropHandle.RIGHT
                            abs(touchY - curTopPx) < edgeMarginPx && touchX in curLeftPx..curRightPx -> CropHandle.TOP
                            abs(touchY - curBottomPx) < edgeMarginPx && touchX in curLeftPx..curRightPx -> CropHandle.BOTTOM
                            touchX in curLeftPx..curRightPx && touchY in curTopPx..curBottomPx -> CropHandle.CENTER
                            else -> CropHandle.NONE
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        if (width <= 0f || height <= 0f || activeHandle == CropHandle.NONE) return@detectDragGestures

                        val dx = dragAmount.x / width
                        val dy = dragAmount.y / height

                        val curLeft = left
                        val curTop = top
                        val curRight = 1f - right
                        val curBottom = 1f - bottom
                        val minSize = 0.1f // 10% minimum size

                        var nLeft = left
                        var nTop = top
                        var nRight = right
                        var nBottom = bottom

                        when (activeHandle) {
                            CropHandle.TOP_LEFT -> {
                                nLeft = (curLeft + dx).coerceIn(0f, curRight - minSize)
                                nTop = (curTop + dy).coerceIn(0f, curBottom - minSize)
                            }
                            CropHandle.TOP_RIGHT -> {
                                val nR = (curRight + dx).coerceIn(curLeft + minSize, 1f)
                                nRight = 1f - nR
                                nTop = (curTop + dy).coerceIn(0f, curBottom - minSize)
                            }
                            CropHandle.BOTTOM_LEFT -> {
                                nLeft = (curLeft + dx).coerceIn(0f, curRight - minSize)
                                val nB = (curBottom + dy).coerceIn(curTop + minSize, 1f)
                                nBottom = 1f - nB
                            }
                            CropHandle.BOTTOM_RIGHT -> {
                                val nR = (curRight + dx).coerceIn(curLeft + minSize, 1f)
                                val nB = (curBottom + dy).coerceIn(curTop + minSize, 1f)
                                nRight = 1f - nR
                                nBottom = 1f - nB
                            }
                            CropHandle.LEFT -> {
                                nLeft = (curLeft + dx).coerceIn(0f, curRight - minSize)
                            }
                            CropHandle.RIGHT -> {
                                val nR = (curRight + dx).coerceIn(curLeft + minSize, 1f)
                                nRight = 1f - nR
                            }
                            CropHandle.TOP -> {
                                nTop = (curTop + dy).coerceIn(0f, curBottom - minSize)
                            }
                            CropHandle.BOTTOM -> {
                                val nB = (curBottom + dy).coerceIn(curTop + minSize, 1f)
                                nBottom = 1f - nB
                            }
                            CropHandle.CENTER -> {
                                val cropW = curRight - curLeft
                                val cropH = curBottom - curTop
                                val shiftedLeft = (curLeft + dx).coerceIn(0f, 1f - cropW)
                                val shiftedTop = (curTop + dy).coerceIn(0f, 1f - cropH)
                                nLeft = shiftedLeft
                                nTop = shiftedTop
                                nRight = 1f - (shiftedLeft + cropW)
                                nBottom = 1f - (shiftedTop + cropH)
                            }
                            CropHandle.NONE -> {}
                        }

                        onCropChange(nLeft, nTop, nRight, nBottom)
                    },
                    onDragEnd = { activeHandle = CropHandle.NONE },
                    onDragCancel = { activeHandle = CropHandle.NONE }
                )
            }
    ) {
        val width = size.width
        val height = size.height
        val curLeftPx = left * width
        val curTopPx = top * height
        val curRightPx = (1f - right) * width
        val curBottomPx = (1f - bottom) * height
        val cropWidth = curRightPx - curLeftPx
        val cropHeight = curBottomPx - curTopPx

        val dimColor = Color.Black.copy(alpha = 0.55f)

        // 1. Draw outer dim overlays
        // Top rect
        drawRect(dimColor, topLeft = Offset(0f, 0f), size = Size(width, curTopPx))
        // Bottom rect
        drawRect(dimColor, topLeft = Offset(0f, curBottomPx), size = Size(width, height - curBottomPx))
        // Left rect
        drawRect(dimColor, topLeft = Offset(0f, curTopPx), size = Size(curLeftPx, cropHeight))
        // Right rect
        drawRect(dimColor, topLeft = Offset(curRightPx, curTopPx), size = Size(width - curRightPx, cropHeight))

        // 2. Draw border
        drawRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(curLeftPx, curTopPx),
            size = Size(cropWidth, cropHeight),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
        )

        // 3. Draw 3x3 Rule-of-Thirds Grid
        val oneThirdW = cropWidth / 3f
        val twoThirdsW = cropWidth * 2f / 3f
        val oneThirdH = cropHeight / 3f
        val twoThirdsH = cropHeight * 2f / 3f
        val gridColor = Color.White.copy(alpha = 0.35f)
        val gridStroke = 1.dp.toPx()

        drawLine(gridColor, Offset(curLeftPx + oneThirdW, curTopPx), Offset(curLeftPx + oneThirdW, curBottomPx), strokeWidth = gridStroke)
        drawLine(gridColor, Offset(curLeftPx + twoThirdsW, curTopPx), Offset(curLeftPx + twoThirdsW, curBottomPx), strokeWidth = gridStroke)
        drawLine(gridColor, Offset(curLeftPx, curTopPx + oneThirdH), Offset(curRightPx, curTopPx + oneThirdH), strokeWidth = gridStroke)
        drawLine(gridColor, Offset(curLeftPx, curTopPx + twoThirdsH), Offset(curRightPx, curTopPx + twoThirdsH), strokeWidth = gridStroke)

        // 4. Draw Corner Brackets (Pointers)
        val bracketLen = 22.dp.toPx().coerceAtMost(cropWidth / 3f).coerceAtMost(cropHeight / 3f)
        val bracketThickness = 4.dp.toPx()
        val bracketColor = Color.White

        // Top-Left
        drawLine(bracketColor, Offset(curLeftPx - 1.dp.toPx(), curTopPx), Offset(curLeftPx + bracketLen, curTopPx), strokeWidth = bracketThickness)
        drawLine(bracketColor, Offset(curLeftPx, curTopPx - 1.dp.toPx()), Offset(curLeftPx, curTopPx + bracketLen), strokeWidth = bracketThickness)

        // Top-Right
        drawLine(bracketColor, Offset(curRightPx + 1.dp.toPx(), curTopPx), Offset(curRightPx - bracketLen, curTopPx), strokeWidth = bracketThickness)
        drawLine(bracketColor, Offset(curRightPx, curTopPx - 1.dp.toPx()), Offset(curRightPx, curTopPx + bracketLen), strokeWidth = bracketThickness)

        // Bottom-Left
        drawLine(bracketColor, Offset(curLeftPx - 1.dp.toPx(), curBottomPx), Offset(curLeftPx + bracketLen, curBottomPx), strokeWidth = bracketThickness)
        drawLine(bracketColor, Offset(curLeftPx, curBottomPx + 1.dp.toPx()), Offset(curLeftPx, curBottomPx - bracketLen), strokeWidth = bracketThickness)

        // Bottom-Right
        drawLine(bracketColor, Offset(curRightPx + 1.dp.toPx(), curBottomPx), Offset(curRightPx - bracketLen, curBottomPx), strokeWidth = bracketThickness)
        drawLine(bracketColor, Offset(curRightPx, curBottomPx + 1.dp.toPx()), Offset(curRightPx, curBottomPx - bracketLen), strokeWidth = bracketThickness)

        // 5. Draw Edge Midpoint Handles (Pills)
        val edgeLen = 16.dp.toPx().coerceAtMost(cropWidth / 4f)
        val edgeThickness = 3.dp.toPx()

        // Top edge handle
        drawLine(bracketColor, Offset(curLeftPx + cropWidth / 2f - edgeLen / 2f, curTopPx), Offset(curLeftPx + cropWidth / 2f + edgeLen / 2f, curTopPx), strokeWidth = edgeThickness)
        // Bottom edge handle
        drawLine(bracketColor, Offset(curLeftPx + cropWidth / 2f - edgeLen / 2f, curBottomPx), Offset(curLeftPx + cropWidth / 2f + edgeLen / 2f, curBottomPx), strokeWidth = edgeThickness)
        // Left edge handle
        drawLine(bracketColor, Offset(curLeftPx, curTopPx + cropHeight / 2f - edgeLen / 2f), Offset(curLeftPx, curTopPx + cropHeight / 2f + edgeLen / 2f), strokeWidth = edgeThickness)
        // Right edge handle
        drawLine(bracketColor, Offset(curRightPx, curTopPx + cropHeight / 2f - edgeLen / 2f), Offset(curRightPx, curTopPx + cropHeight / 2f + edgeLen / 2f), strokeWidth = edgeThickness)
    }
}
