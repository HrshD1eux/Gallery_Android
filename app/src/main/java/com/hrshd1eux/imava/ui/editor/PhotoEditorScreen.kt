package com.hrshd1eux.imava.ui.editor

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hrshd1eux.imava.core.util.HapticUtil
import com.hrshd1eux.imava.core.util.PhotoEditorUtils
import com.hrshd1eux.imava.core.util.PhotoMarkupUtils
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
    TUNING,
    MARKUP
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
    

    var brightnessOffset by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1.0f) }
    var saturation by remember { mutableFloatStateOf(1.0f) }
    var warmth by remember { mutableFloatStateOf(0f) }
    var selectedPreset by remember { mutableStateOf("none") }
    var activeTuneSubTab by remember { mutableStateOf("presets") }
    

    var cropAspectRatio by remember { mutableStateOf<Float?>(null) }
    var freeCropLeft by remember { mutableFloatStateOf(0f) }
    var freeCropTop by remember { mutableFloatStateOf(0f) }
    var freeCropRight by remember { mutableFloatStateOf(0f) }
    var freeCropBottom by remember { mutableFloatStateOf(0f) }
    var isCropApplied by remember { mutableStateOf(false) }


    var markupStrokes by remember { mutableStateOf(listOf<PhotoMarkupUtils.MarkupStroke>()) }
    var inProgressStrokePoints by remember { mutableStateOf(listOf<PointF>()) }
    var inProgressStartPoint by remember { mutableStateOf<PointF?>(null) }
    var inProgressEndPoint by remember { mutableStateOf<PointF?>(null) }
    var activeMarkupTool by remember { mutableStateOf(PhotoMarkupUtils.MarkupTool.PIXELATE_MOSAIC) }
    var activeMarkupColor by remember { mutableIntStateOf(android.graphics.Color.RED) }
    var activeMarkupWidth by remember { mutableFloatStateOf(24f) }

    val displayMetrics = context.resources.displayMetrics
    val reqWidth = displayMetrics.widthPixels.coerceAtLeast(1080)
    val reqHeight = displayMetrics.heightPixels.coerceAtLeast(1920)

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

    LaunchedEffect(
        sourceBitmap, rotationDegrees, flipHorizontal, flipVertical,
        cropAspectRatio, freeCropLeft, freeCropTop, freeCropRight, freeCropBottom,
        brightnessOffset, contrast, saturation, warmth, selectedPreset
    ) {
        val src = sourceBitmap ?: return@LaunchedEffect
        withContext(Dispatchers.Default) {
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

    val hasModifications = abs(rotationDegrees) > 0.05f || flipHorizontal || flipVertical ||
            brightnessOffset != 0f || contrast != 1.0f ||
            saturation != 1.0f || warmth != 0f ||
            selectedPreset != "none" ||
            freeCropLeft > 0.001f || freeCropTop > 0.001f ||
            freeCropRight > 0.001f || freeCropBottom > 0.001f ||
            cropAspectRatio != null ||
            markupStrokes.isNotEmpty()

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

                    val transformedFullRes = PhotoEditorUtils.transformBitmap(
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

                    val previewBmp = transformedBitmap ?: uncroppedPreviewBitmap ?: sourceBitmap
                    val finalFullResBitmap = if (markupStrokes.isNotEmpty() && previewBmp != null) {
                        val scaleX = transformedFullRes.width.toFloat() / previewBmp.width.toFloat()
                        val scaleY = transformedFullRes.height.toFloat() / previewBmp.height.toFloat()
                        val scaledStrokes = markupStrokes.map { stroke ->
                            stroke.copy(
                                points = stroke.points.map { pt -> PointF(pt.x * scaleX, pt.y * scaleY) },
                                strokeWidth = stroke.strokeWidth * scaleX,
                                startPoint = stroke.startPoint?.let { PointF(it.x * scaleX, it.y * scaleY) },
                                endPoint = stroke.endPoint?.let { PointF(it.x * scaleX, it.y * scaleY) }
                            )
                        }
                        PhotoMarkupUtils.renderStrokesToBitmap(transformedFullRes, scaledStrokes)
                    } else {
                        transformedFullRes
                    }

                    viewModel.saveEditedPhoto(context, mediaItem, finalFullResBitmap)
                } else {
                    val fallbackBmp = transformedBitmap ?: uncroppedPreviewBitmap ?: sourceBitmap
                    if (fallbackBmp != null) {
                        val finalBmp = if (markupStrokes.isNotEmpty()) {
                            PhotoMarkupUtils.renderStrokesToBitmap(fallbackBmp, markupStrokes)
                        } else {
                            fallbackBmp
                        }
                        viewModel.saveEditedPhoto(context, mediaItem, finalBmp)
                    }
                }
            }
            HapticUtil.performSuccess(context)
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
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                IconButton(
                    onClick = { performSaveEditedPhoto() },
                    enabled = hasModifications
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Save Changes",
                        tint = if (hasModifications) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }
        }


        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp, bottom = 140.dp),
            contentAlignment = Alignment.Center
        ) {
            val previewBitmap = if (selectedTab == EditorTab.CROP && !isCropApplied) {
                uncroppedPreviewBitmap ?: transformedBitmap ?: sourceBitmap
            } else {
                transformedBitmap ?: uncroppedPreviewBitmap ?: sourceBitmap
            }

            if (previewBitmap != null) {
                val bitmapAspect = previewBitmap.width.toFloat() / previewBitmap.height.toFloat()
                

                val displayBitmap = remember(previewBitmap, markupStrokes) {
                    if (markupStrokes.isNotEmpty()) {
                        PhotoMarkupUtils.renderStrokesToBitmap(previewBitmap, markupStrokes)
                    } else {
                        previewBitmap
                    }
                }

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
                            bitmap = displayBitmap.asImageBitmap(),
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


                        if (selectedTab == EditorTab.MARKUP) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(activeMarkupTool, activeMarkupColor, activeMarkupWidth) {
                                        detectDragGestures(
                                            onDragStart = { startOffset ->
                                                val bmpW = previewBitmap.width.toFloat()
                                                val bmpH = previewBitmap.height.toFloat()
                                                val normX = (startOffset.x / size.width) * bmpW
                                                val normY = (startOffset.y / size.height) * bmpH
                                                val pt = PointF(normX, normY)
                                                inProgressStartPoint = pt
                                                inProgressEndPoint = pt
                                                inProgressStrokePoints = listOf(pt)
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                val bmpW = previewBitmap.width.toFloat()
                                                val bmpH = previewBitmap.height.toFloat()
                                                val normX = (change.position.x / size.width) * bmpW
                                                val normY = (change.position.y / size.height) * bmpH
                                                val pt = PointF(normX, normY)
                                                inProgressEndPoint = pt
                                                inProgressStrokePoints = inProgressStrokePoints + pt
                                            },
                                            onDragEnd = {
                                                if (inProgressStrokePoints.isNotEmpty()) {
                                                    val newStroke = PhotoMarkupUtils.MarkupStroke(
                                                        tool = activeMarkupTool,
                                                        points = inProgressStrokePoints,
                                                        color = activeMarkupColor,
                                                        strokeWidth = activeMarkupWidth,
                                                        startPoint = inProgressStartPoint,
                                                        endPoint = inProgressEndPoint
                                                    )
                                                    markupStrokes = markupStrokes + newStroke
                                                    inProgressStrokePoints = emptyList()
                                                    inProgressStartPoint = null
                                                    inProgressEndPoint = null
                                                }
                                            },
                                            onDragCancel = {
                                                inProgressStrokePoints = emptyList()
                                                inProgressStartPoint = null
                                                inProgressEndPoint = null
                                            }
                                        )
                                    }
                            ) {

                                if (inProgressStrokePoints.size > 1) {
                                    val scaleX = size.width / previewBitmap.width.toFloat()
                                    val scaleY = size.height / previewBitmap.height.toFloat()
                                    val strokeColor = Color(activeMarkupColor)

                                    if (activeMarkupTool == PhotoMarkupUtils.MarkupTool.PEN || activeMarkupTool == PhotoMarkupUtils.MarkupTool.PIXELATE_MOSAIC) {
                                        for (i in 0 until inProgressStrokePoints.size - 1) {
                                            val p1 = inProgressStrokePoints[i]
                                            val p2 = inProgressStrokePoints[i + 1]
                                            drawLine(
                                                color = if (activeMarkupTool == PhotoMarkupUtils.MarkupTool.PIXELATE_MOSAIC) Color.Black.copy(alpha = 0.5f) else strokeColor,
                                                start = Offset(p1.x * scaleX, p1.y * scaleY),
                                                end = Offset(p2.x * scaleX, p2.y * scaleY),
                                                strokeWidth = activeMarkupWidth * scaleX,
                                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                                            )
                                        }
                                    } else if (inProgressStartPoint != null && inProgressEndPoint != null) {
                                        val p1 = inProgressStartPoint!!
                                        val p2 = inProgressEndPoint!!
                                        val startOffset = Offset(p1.x * scaleX, p1.y * scaleY)
                                        val endOffset = Offset(p2.x * scaleX, p2.y * scaleY)

                                        when (activeMarkupTool) {
                                            PhotoMarkupUtils.MarkupTool.RECTANGLE -> {
                                                val left = minOf(startOffset.x, endOffset.x)
                                                val top = minOf(startOffset.y, endOffset.y)
                                                val w = abs(endOffset.x - startOffset.x)
                                                val h = abs(endOffset.y - startOffset.y)
                                                drawRect(
                                                    color = strokeColor,
                                                    topLeft = Offset(left, top),
                                                    size = Size(w, h),
                                                    style = Stroke(width = activeMarkupWidth * scaleX)
                                                )
                                            }
                                            PhotoMarkupUtils.MarkupTool.CIRCLE -> {
                                                val left = minOf(startOffset.x, endOffset.x)
                                                val top = minOf(startOffset.y, endOffset.y)
                                                val w = abs(endOffset.x - startOffset.x)
                                                val h = abs(endOffset.y - startOffset.y)
                                                drawOval(
                                                    color = strokeColor,
                                                    topLeft = Offset(left, top),
                                                    size = Size(w, h),
                                                    style = Stroke(width = activeMarkupWidth * scaleX)
                                                )
                                            }
                                            PhotoMarkupUtils.MarkupTool.ARROW -> {
                                                drawLine(
                                                    color = strokeColor,
                                                    start = startOffset,
                                                    end = endOffset,
                                                    strokeWidth = activeMarkupWidth * scaleX,
                                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                                )
                                            }
                                            else -> {}
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                CircularProgressIndicator(color = Color.White)
            }
        }


        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.95f))
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                when (selectedTab) {
                    EditorTab.ROTATE -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            rotationDegrees = ((rotationDegrees - 90f + 360f) % 360f).let {
                                                if (it > 180f) it - 360f else it
                                            }
                                            com.hrshd1eux.imava.core.util.HapticUtil.performSelection(context)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Rotate90DegreesCcw,
                                            contentDescription = "Rotate -90°",
                                            tint = Color.White
                                        )
                                    }
                                    Text(
                                        text = "-90°",
                                        color = Color.White.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }


                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (abs(rotationDegrees) > 0.05f) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.15f),
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = String.format(java.util.Locale.US, "%+.1f°", rotationDegrees),
                                            color = if (abs(rotationDegrees) > 0.05f) MaterialTheme.colorScheme.onPrimaryContainer else Color.White,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (abs(rotationDegrees) > 0.05f) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Reset Angle",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable {
                                                        rotationDegrees = 0f
                                                        com.hrshd1eux.imava.core.util.HapticUtil.performClick(context)
                                                    }
                                            )
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "+90°",
                                        color = Color.White.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    IconButton(
                                        onClick = {
                                            rotationDegrees = ((rotationDegrees + 90f + 360f) % 360f).let {
                                                if (it > 180f) it - 360f else it
                                            }
                                            com.hrshd1eux.imava.core.util.HapticUtil.performSelection(context)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Rotate90DegreesCw,
                                            contentDescription = "Rotate +90°",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }


                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        rotationDegrees = (rotationDegrees - 1f).coerceIn(-180f, 180f)
                                        com.hrshd1eux.imava.core.util.HapticUtil.performSelection(context)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text("-1°", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }

                                Slider(
                                    value = rotationDegrees,
                                    onValueChange = { newValue ->
                                        // Snap to 0 if within +/- 0.5 degrees
                                        rotationDegrees = if (abs(newValue) < 0.5f) 0f else (Math.round(newValue * 2f) / 2f)
                                    },
                                    valueRange = -180f..180f,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp)
                                )

                                IconButton(
                                    onClick = {
                                        rotationDegrees = (rotationDegrees + 1f).coerceIn(-180f, 180f)
                                        com.hrshd1eux.imava.core.util.HapticUtil.performSelection(context)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text("+1°", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }


                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val presets = listOf(
                                    -45f to "-45°",
                                    0f to "0° (Reset)",
                                    45f to "+45°",
                                    90f to "90°",
                                    180f to "180°",
                                    -90f to "270° (-90°)"
                                )
                                items(presets) { (presetDeg, label) ->
                                    FilterChip(
                                        selected = abs(rotationDegrees - presetDeg) < 0.2f,
                                        onClick = {
                                            rotationDegrees = presetDeg
                                            com.hrshd1eux.imava.core.util.HapticUtil.performSelection(context)
                                        },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
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
                                label = { Text("Horizontal") }
                            )
                            FilterChip(
                                selected = flipVertical,
                                onClick = { flipVertical = !flipVertical },
                                label = { Text("Vertical") }
                            )
                        }
                    }
                    EditorTab.CROP -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                            ) {
                                Text("Drag corners/edges to crop", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                                if (freeCropLeft > 0.001f || freeCropTop > 0.001f || freeCropRight > 0.001f || freeCropBottom > 0.001f || cropAspectRatio != null) {
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
                                        Text("Reset", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                                    }
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
                    EditorTab.MARKUP -> {
                        Column(modifier = Modifier.fillMaxWidth()) {

                            LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                item {
                                    FilterChip(
                                        selected = activeMarkupTool == PhotoMarkupUtils.MarkupTool.PIXELATE_MOSAIC,
                                        onClick = {
                                            activeMarkupTool = PhotoMarkupUtils.MarkupTool.PIXELATE_MOSAIC
                                            activeMarkupWidth = 32f
                                        },
                                        label = { Text("⬛ Redact/Mosaic") }
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = activeMarkupTool == PhotoMarkupUtils.MarkupTool.PEN,
                                        onClick = {
                                            activeMarkupTool = PhotoMarkupUtils.MarkupTool.PEN
                                            activeMarkupWidth = 14f
                                        },
                                        label = { Text("✏️ Pen") }
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = activeMarkupTool == PhotoMarkupUtils.MarkupTool.ARROW,
                                        onClick = {
                                            activeMarkupTool = PhotoMarkupUtils.MarkupTool.ARROW
                                            activeMarkupWidth = 12f
                                        },
                                        label = { Text("↗️ Arrow") }
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = activeMarkupTool == PhotoMarkupUtils.MarkupTool.RECTANGLE,
                                        onClick = {
                                            activeMarkupTool = PhotoMarkupUtils.MarkupTool.RECTANGLE
                                            activeMarkupWidth = 8f
                                        },
                                        label = { Text("⬜ Box") }
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = activeMarkupTool == PhotoMarkupUtils.MarkupTool.CIRCLE,
                                        onClick = {
                                            activeMarkupTool = PhotoMarkupUtils.MarkupTool.CIRCLE
                                            activeMarkupWidth = 8f
                                        },
                                        label = { Text("⭕ Oval") }
                                    )
                                }
                            }


                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(
                                        android.graphics.Color.RED,
                                        android.graphics.Color.YELLOW,
                                        android.graphics.Color.GREEN,
                                        android.graphics.Color.BLUE,
                                        android.graphics.Color.WHITE,
                                        android.graphics.Color.BLACK
                                    ).forEach { colorInt ->
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(colorInt),
                                            border = if (activeMarkupColor == colorInt) androidx.compose.foundation.BorderStroke(2.dp, Color.White) else null,
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clickable {
                                                    activeMarkupColor = colorInt
                                                }
                                        ) {}
                                    }
                                }


                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        enabled = markupStrokes.isNotEmpty(),
                                        onClick = {
                                            if (markupStrokes.isNotEmpty()) {
                                                markupStrokes = markupStrokes.dropLast(1)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Undo,
                                            contentDescription = "Undo",
                                            tint = if (markupStrokes.isNotEmpty()) Color.White else Color.Gray
                                        )
                                    }
                                    IconButton(
                                        enabled = markupStrokes.isNotEmpty(),
                                        onClick = {
                                            markupStrokes = emptyList()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Clear All",
                                            tint = if (markupStrokes.isNotEmpty()) MaterialTheme.colorScheme.error else Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }


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
                NavigationBarItem(
                    selected = selectedTab == EditorTab.MARKUP,
                    onClick = { selectedTab = EditorTab.MARKUP },
                    icon = { Icon(Icons.Default.Brush, contentDescription = "Markup") },
                    label = { Text("Markup") }
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
    val currentLeft by rememberUpdatedState(left)
    val currentTop by rememberUpdatedState(top)
    val currentRight by rememberUpdatedState(right)
    val currentBottom by rememberUpdatedState(bottom)
    val currentOnCropChange by rememberUpdatedState(onCropChange)

    var activeHandle by remember { mutableStateOf(CropHandle.NONE) }
    var dragLeft by remember { mutableFloatStateOf(left) }
    var dragTop by remember { mutableFloatStateOf(top) }
    var dragRight by remember { mutableFloatStateOf(right) }
    var dragBottom by remember { mutableFloatStateOf(bottom) }

    LaunchedEffect(left, top, right, bottom) {
        if (activeHandle == CropHandle.NONE) {
            dragLeft = left
            dragTop = top
            dragRight = right
            dragBottom = bottom
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        val touchX = startOffset.x
                        val touchY = startOffset.y

                        val curLeftPx = dragLeft * width
                        val curTopPx = dragTop * height
                        val curRightPx = (1f - dragRight) * width
                        val curBottomPx = (1f - dragBottom) * height

                        val cornerHitboxPx = 48.dp.toPx()
                        val edgeHitboxPx = 36.dp.toPx()

                        fun dist(x1: Float, y1: Float, x2: Float, y2: Float) =
                            kotlin.math.hypot(x1 - x2, y1 - y2)

                        activeHandle = when {
                            dist(touchX, touchY, curLeftPx, curTopPx) < cornerHitboxPx -> CropHandle.TOP_LEFT
                            dist(touchX, touchY, curRightPx, curTopPx) < cornerHitboxPx -> CropHandle.TOP_RIGHT
                            dist(touchX, touchY, curLeftPx, curBottomPx) < cornerHitboxPx -> CropHandle.BOTTOM_LEFT
                            dist(touchX, touchY, curRightPx, curBottomPx) < cornerHitboxPx -> CropHandle.BOTTOM_RIGHT
                            abs(touchX - curLeftPx) < edgeHitboxPx && touchY in curTopPx..curBottomPx -> CropHandle.LEFT
                            abs(touchX - curRightPx) < edgeHitboxPx && touchY in curTopPx..curBottomPx -> CropHandle.RIGHT
                            abs(touchY - curTopPx) < edgeHitboxPx && touchX in curLeftPx..curRightPx -> CropHandle.TOP
                            abs(touchY - curBottomPx) < edgeHitboxPx && touchX in curLeftPx..curRightPx -> CropHandle.BOTTOM
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
                        val minSize = 0.08f

                        when (activeHandle) {
                            CropHandle.TOP_LEFT -> {
                                dragLeft = (dragLeft + dx).coerceIn(0f, (1f - dragRight) - minSize)
                                dragTop = (dragTop + dy).coerceIn(0f, (1f - dragBottom) - minSize)
                            }
                            CropHandle.TOP_RIGHT -> {
                                val nR = ((1f - dragRight) + dx).coerceIn(dragLeft + minSize, 1f)
                                dragRight = 1f - nR
                                dragTop = (dragTop + dy).coerceIn(0f, (1f - dragBottom) - minSize)
                            }
                            CropHandle.BOTTOM_LEFT -> {
                                dragLeft = (dragLeft + dx).coerceIn(0f, (1f - dragRight) - minSize)
                                val nB = ((1f - dragBottom) + dy).coerceIn(dragTop + minSize, 1f)
                                dragBottom = 1f - nB
                            }
                            CropHandle.BOTTOM_RIGHT -> {
                                val nR = ((1f - dragRight) + dx).coerceIn(dragLeft + minSize, 1f)
                                val nB = ((1f - dragBottom) + dy).coerceIn(dragTop + minSize, 1f)
                                dragRight = 1f - nR
                                dragBottom = 1f - nB
                            }
                            CropHandle.LEFT -> {
                                dragLeft = (dragLeft + dx).coerceIn(0f, (1f - dragRight) - minSize)
                            }
                            CropHandle.RIGHT -> {
                                val nR = ((1f - dragRight) + dx).coerceIn(dragLeft + minSize, 1f)
                                dragRight = 1f - nR
                            }
                            CropHandle.TOP -> {
                                dragTop = (dragTop + dy).coerceIn(0f, (1f - dragBottom) - minSize)
                            }
                            CropHandle.BOTTOM -> {
                                val nB = ((1f - dragBottom) + dy).coerceIn(dragTop + minSize, 1f)
                                dragBottom = 1f - nB
                            }
                            CropHandle.CENTER -> {
                                val cropW = (1f - dragRight) - dragLeft
                                val cropH = (1f - dragBottom) - dragTop
                                val shiftedLeft = (dragLeft + dx).coerceIn(0f, 1f - cropW)
                                val shiftedTop = (dragTop + dy).coerceIn(0f, 1f - cropH)
                                dragLeft = shiftedLeft
                                dragTop = shiftedTop
                                dragRight = 1f - (shiftedLeft + cropW)
                                dragBottom = 1f - (shiftedTop + cropH)
                            }
                            CropHandle.NONE -> {}
                        }

                        currentOnCropChange(dragLeft, dragTop, dragRight, dragBottom)
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
        val cropWidth = (curRightPx - curLeftPx).coerceAtLeast(1f)
        val cropHeight = (curBottomPx - curTopPx).coerceAtLeast(1f)

        val dimColor = Color.Black.copy(alpha = 0.55f)

        drawRect(dimColor, topLeft = Offset(0f, 0f), size = Size(width, curTopPx))
        drawRect(dimColor, topLeft = Offset(0f, curBottomPx), size = Size(width, height - curBottomPx))
        drawRect(dimColor, topLeft = Offset(0f, curTopPx), size = Size(curLeftPx, cropHeight))
        drawRect(dimColor, topLeft = Offset(curRightPx, curTopPx), size = Size(width - curRightPx, cropHeight))

        drawRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(curLeftPx, curTopPx),
            size = Size(cropWidth, cropHeight),
            style = Stroke(width = 1.5.dp.toPx())
        )

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

        val bracketLen = 24.dp.toPx().coerceAtMost(cropWidth / 3f).coerceAtMost(cropHeight / 3f)
        val bracketThickness = 4.dp.toPx()
        val bracketColor = Color.White
        val shadowColor = Color.Black.copy(alpha = 0.4f)
        val shadowOffset = 1.dp.toPx()

        fun drawBracketWithShadow(p1: Offset, p2: Offset, p3: Offset) {
            drawLine(shadowColor, p1 + Offset(shadowOffset, shadowOffset), p2 + Offset(shadowOffset, shadowOffset), strokeWidth = bracketThickness)
            drawLine(shadowColor, p2 + Offset(shadowOffset, shadowOffset), p3 + Offset(shadowOffset, shadowOffset), strokeWidth = bracketThickness)
            drawLine(bracketColor, p1, p2, strokeWidth = bracketThickness)
            drawLine(bracketColor, p2, p3, strokeWidth = bracketThickness)
        }

        drawBracketWithShadow(
            Offset(curLeftPx + bracketLen, curTopPx),
            Offset(curLeftPx, curTopPx),
            Offset(curLeftPx, curTopPx + bracketLen)
        )

        drawBracketWithShadow(
            Offset(curRightPx - bracketLen, curTopPx),
            Offset(curRightPx, curTopPx),
            Offset(curRightPx, curTopPx + bracketLen)
        )

        drawBracketWithShadow(
            Offset(curLeftPx, curBottomPx - bracketLen),
            Offset(curLeftPx, curBottomPx),
            Offset(curLeftPx + bracketLen, curBottomPx)
        )

        drawBracketWithShadow(
            Offset(curRightPx - bracketLen, curBottomPx),
            Offset(curRightPx, curBottomPx),
            Offset(curRightPx, curBottomPx - bracketLen)
        )

        val edgeLen = 18.dp.toPx().coerceAtMost(cropWidth / 4f)
        val edgeThickness = 3.dp.toPx()

        drawLine(bracketColor, Offset(curLeftPx + cropWidth / 2f - edgeLen / 2f, curTopPx), Offset(curLeftPx + cropWidth / 2f + edgeLen / 2f, curTopPx), strokeWidth = edgeThickness)
        drawLine(bracketColor, Offset(curLeftPx + cropWidth / 2f - edgeLen / 2f, curBottomPx), Offset(curLeftPx + cropWidth / 2f + edgeLen / 2f, curBottomPx), strokeWidth = edgeThickness)
        drawLine(bracketColor, Offset(curLeftPx, curTopPx + cropHeight / 2f - edgeLen / 2f), Offset(curLeftPx, curTopPx + cropHeight / 2f + edgeLen / 2f), strokeWidth = edgeThickness)
        drawLine(bracketColor, Offset(curRightPx, curTopPx + cropHeight / 2f - edgeLen / 2f), Offset(curRightPx, curTopPx + cropHeight / 2f + edgeLen / 2f), strokeWidth = edgeThickness)
    }
}
