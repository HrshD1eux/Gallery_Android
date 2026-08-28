package com.hrshd1eux.imava.ui.collage

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Hexagon
import androidx.compose.material.icons.filled.PanoramaFishEye
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Square
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hrshd1eux.imava.core.util.CollageMakerUtil
import com.hrshd1eux.imava.core.util.HapticUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

val DiamondShape: Shape = GenericShape { size, _ ->
    moveTo(size.width / 2f, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width / 2f, size.height)
    lineTo(0f, size.height / 2f)
    close()
}

val HexagonShape: Shape = GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    val w = size.width / 2f
    val h = size.height / 2f
    moveTo(cx, cy - h)
    lineTo(cx + w, cy - h / 2f)
    lineTo(cx + w, cy + h / 2f)
    lineTo(cx, cy + h)
    lineTo(cx - w, cy + h / 2f)
    lineTo(cx - w, cy - h / 2f)
    close()
}

val HeartShape: Shape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val cx = w / 2f

    moveTo(cx, h * 0.3f)
    cubicTo(cx, h * 0.08f, w * 0.05f, 0f, w * 0.05f, h * 0.35f)
    cubicTo(w * 0.05f, h * 0.6f, cx - w * 0.2f, h * 0.8f, cx, h * 0.98f)
    cubicTo(cx + w * 0.2f, h * 0.8f, w * 0.95f, h * 0.6f, w * 0.95f, h * 0.35f)
    cubicTo(w * 0.95f, 0f, cx, h * 0.08f, cx, h * 0.3f)
    close()
}

val StarShape: Shape = GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = min(size.width, size.height) / 2f
    val innerRadius = radius * 0.45f
    val step = (Math.PI / 5.0).toFloat()
    var angle = -Math.PI.toFloat() / 2f

    moveTo(cx + radius * cos(angle), cy + radius * sin(angle))
    for (k in 1..5) {
        angle += step
        lineTo(cx + innerRadius * cos(angle), cy + innerRadius * sin(angle))
        angle += step
        lineTo(cx + radius * cos(angle), cy + radius * sin(angle))
    }
    close()
}

fun getShapeForType(shape: CollageMakerUtil.CollageShape, cornerRadiusDp: Dp): Shape {
    return when (shape) {
        CollageMakerUtil.CollageShape.ROUNDED -> RoundedCornerShape(cornerRadiusDp)
        CollageMakerUtil.CollageShape.CIRCLE -> CircleShape
        CollageMakerUtil.CollageShape.DIAMOND -> DiamondShape
        CollageMakerUtil.CollageShape.HEXAGON -> HexagonShape
        CollageMakerUtil.CollageShape.HEART -> HeartShape
        CollageMakerUtil.CollageShape.STAR -> StarShape
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageMakerScreen(
    imageUris: List<Uri>,
    onDismiss: () -> Unit,
    onCollageSaved: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedAspect by remember { mutableStateOf(CollageMakerUtil.CollageAspectRatio.SQUARE_1_1) }
    var selectedShape by remember { mutableStateOf(CollageMakerUtil.CollageShape.ROUNDED) }
    var layoutVariant by remember { mutableIntStateOf(0) }
    var spacingDp by remember { mutableFloatStateOf(8f) }
    var cornerRadiusDp by remember { mutableFloatStateOf(16f) }
    var isDarkBackground by remember { mutableStateOf(true) }

    var cellTransforms by remember(imageUris.size, layoutVariant) {
        mutableStateOf(List(imageUris.size) { CollageMakerUtil.CellTransform() })
    }

    var isSaving by remember { mutableStateOf(false) }

    BackHandler {
        onDismiss()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Clean Top App Bar
            TopAppBar(
                title = { Text("Collage Maker (${imageUris.size} Photos)") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        enabled = !isSaving,
                        onClick = {
                            isSaving = true
                            scope.launch {
                                HapticUtil.performClick(context)
                                val highResBmp = withContext(Dispatchers.IO) {
                                    CollageMakerUtil.generateCollageBitmap(
                                        context = context,
                                        imageUris = imageUris,
                                        aspectRatio = selectedAspect,
                                        shape = selectedShape,
                                        layoutVariant = layoutVariant,
                                        spacingPx = spacingDp * 4f,
                                        cornerRadiusPx = cornerRadiusDp * 4f,
                                        backgroundColor = if (isDarkBackground) AndroidColor.BLACK else AndroidColor.WHITE,
                                        outputDimension = 2048,
                                        transforms = cellTransforms
                                    )
                                }
                                val savedUri = CollageMakerUtil.saveCollage(context, highResBmp)
                                isSaving = false
                                if (savedUri != null) {
                                    Toast.makeText(context, "Collage saved to Pictures/Collages 🎨", Toast.LENGTH_SHORT).show()
                                    onCollageSaved(savedUri)
                                } else {
                                    Toast.makeText(context, "Failed to save collage", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )

            // Interactive Collage Preview Area with per-cell pinch-to-zoom & pan
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val previewRatio = selectedAspect.widthRatio / selectedAspect.heightRatio

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .aspectRatio(previewRatio, matchHeightConstraintsFirst = true)
                        .background(
                            if (isDarkBackground) Color.Black else Color.White,
                            RoundedCornerShape(8.dp)
                        )
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    val containerWidth = maxWidth
                    val containerHeight = maxHeight
                    val cells = remember(imageUris.size, layoutVariant) {
                        CollageMakerUtil.computeCellLayouts(imageUris.size, layoutVariant)
                    }

                    cells.forEachIndexed { index, cell ->
                        if (index < imageUris.size) {
                            CollageCellItem(
                                key = "${imageUris[index]}_${layoutVariant}_$index",
                                uri = imageUris[index],
                                cell = cell,
                                containerWidth = containerWidth,
                                containerHeight = containerHeight,
                                spacingDp = spacingDp.dp,
                                cornerRadiusDp = cornerRadiusDp.dp,
                                shapeType = selectedShape,
                                onTransformChanged = { newTransform ->
                                    val updated = cellTransforms.toMutableList()
                                    while (updated.size <= index) {
                                        updated.add(CollageMakerUtil.CellTransform())
                                    }
                                    updated[index] = newTransform
                                    cellTransforms = updated
                                }
                            )
                        }
                    }
                }
            }

            // Bottom Styling & Shape Controls
            Surface(
                tonalElevation = 6.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Row 1: Aspect Ratio & Layout & Background
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            "1:1" to CollageMakerUtil.CollageAspectRatio.SQUARE_1_1,
                            "4:5" to CollageMakerUtil.CollageAspectRatio.PORTRAIT_4_5,
                            "9:16" to CollageMakerUtil.CollageAspectRatio.STORY_9_16,
                            "16:9" to CollageMakerUtil.CollageAspectRatio.LANDSCAPE_16_9
                        ).forEach { (label, aspect) ->
                            FilterChip(
                                selected = selectedAspect == aspect,
                                onClick = {
                                    selectedAspect = aspect
                                    HapticUtil.performSelection(context)
                                },
                                label = { Text(label) }
                            )
                        }

                        IconButton(
                            onClick = {
                                layoutVariant = layoutVariant + 1
                                HapticUtil.performSelection(context)
                            }
                        ) {
                            Icon(Icons.Default.Dashboard, contentDescription = "Change Layout Variant")
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (isDarkBackground) Color.Black else Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .size(32.dp)
                                .clickable {
                                    isDarkBackground = !isDarkBackground
                                    HapticUtil.performSelection(context)
                                }
                        ) {}
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Row 2: Shapes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            "Rounded" to CollageMakerUtil.CollageShape.ROUNDED,
                            "Circle" to CollageMakerUtil.CollageShape.CIRCLE,
                            "Diamond" to CollageMakerUtil.CollageShape.DIAMOND,
                            "Hexagon" to CollageMakerUtil.CollageShape.HEXAGON,
                            "Heart" to CollageMakerUtil.CollageShape.HEART,
                            "Star" to CollageMakerUtil.CollageShape.STAR
                        ).forEach { (name, shape) ->
                            FilterChip(
                                selected = selectedShape == shape,
                                onClick = {
                                    selectedShape = shape
                                    HapticUtil.performSelection(context)
                                },
                                label = { Text(name) },
                                leadingIcon = {
                                    when (shape) {
                                        CollageMakerUtil.CollageShape.ROUNDED -> Icon(Icons.Default.Square, contentDescription = null, modifier = Modifier.size(16.dp))
                                        CollageMakerUtil.CollageShape.CIRCLE -> Icon(Icons.Default.PanoramaFishEye, contentDescription = null, modifier = Modifier.size(16.dp))
                                        CollageMakerUtil.CollageShape.DIAMOND -> Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        CollageMakerUtil.CollageShape.HEXAGON -> Icon(Icons.Default.Hexagon, contentDescription = null, modifier = Modifier.size(16.dp))
                                        CollageMakerUtil.CollageShape.HEART -> Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                                        CollageMakerUtil.CollageShape.STAR -> Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Spacing", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = spacingDp,
                            onValueChange = { spacingDp = it },
                            valueRange = 0f..24f,
                            modifier = Modifier.fillMaxWidth(0.8f)
                        )
                    }

                    if (selectedShape == CollageMakerUtil.CollageShape.ROUNDED) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Radius", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Slider(
                                value = cornerRadiusDp,
                                onValueChange = { cornerRadiusDp = it },
                                valueRange = 0f..32f,
                                modifier = Modifier.fillMaxWidth(0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollageCellItem(
    key: String,
    uri: Uri,
    cell: CollageMakerUtil.CollageCell,
    containerWidth: Dp,
    containerHeight: Dp,
    spacingDp: Dp,
    cornerRadiusDp: Dp,
    shapeType: CollageMakerUtil.CollageShape,
    onTransformChanged: (CollageMakerUtil.CellTransform) -> Unit
) {
    var scale by remember(key) { mutableFloatStateOf(1f) }
    var panOffset by remember(key) { mutableStateOf(Offset.Zero) }

    val cellLeft = (containerWidth * cell.leftFraction) + (spacingDp / 2f)
    val cellTop = (containerHeight * cell.topFraction) + (spacingDp / 2f)
    val cellWidth = (containerWidth * (cell.rightFraction - cell.leftFraction)) - spacingDp
    val cellHeight = (containerHeight * (cell.bottomFraction - cell.topFraction)) - spacingDp

    val shape = getShapeForType(shapeType, cornerRadiusDp)

    Box(
        modifier = Modifier
            .offset(x = cellLeft, y = cellTop)
            .size(width = cellWidth.coerceAtLeast(1.dp), height = cellHeight.coerceAtLeast(1.dp))
            .clip(shape)
            .background(Color.DarkGray)
            .pointerInput(key) {
                detectTransformGestures(panZoomLock = false) { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 4f)
                    val maxPanX = (newScale - 1f) * 150f
                    val maxPanY = (newScale - 1f) * 150f
                    val newPanX = (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX)
                    val newPanY = (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)

                    scale = newScale
                    panOffset = Offset(newPanX, newPanY)

                    val normalizedPanX = if (maxPanX > 0f) newPanX / maxPanX else 0f
                    val normalizedPanY = if (maxPanY > 0f) newPanY / maxPanY else 0f
                    onTransformChanged(CollageMakerUtil.CellTransform(scale, normalizedPanX, normalizedPanY))
                }
            }
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = panOffset.x
                    translationY = panOffset.y
                }
        )
    }
}
