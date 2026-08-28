package com.hrshd1eux.imava.ui.collage

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hrshd1eux.imava.core.util.CollageMakerUtil
import com.hrshd1eux.imava.core.util.HapticUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            // Clean Top Bar with Back, Title, and Save
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
                            val uri = imageUris[index]
                            val transform = cellTransforms.getOrElse(index) { CollageMakerUtil.CellTransform() }

                            val cellLeft = (containerWidth * cell.leftFraction) + (spacingDp.dp / 2f)
                            val cellTop = (containerHeight * cell.topFraction) + (spacingDp.dp / 2f)
                            val cellWidth = (containerWidth * (cell.rightFraction - cell.leftFraction)) - spacingDp.dp
                            val cellHeight = (containerHeight * (cell.bottomFraction - cell.topFraction)) - spacingDp.dp

                            Box(
                                modifier = Modifier
                                    .offset(x = cellLeft, y = cellTop)
                                    .size(width = cellWidth.coerceAtLeast(1.dp), height = cellHeight.coerceAtLeast(1.dp))
                                    .clip(RoundedCornerShape(cornerRadiusDp.dp))
                                    .background(Color.DarkGray)
                                    .pointerInput(index) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            val current = cellTransforms.getOrElse(index) { CollageMakerUtil.CellTransform() }
                                            val newScale = (current.scale * zoom).coerceIn(1f, 4f)
                                            val newPanX = (current.panX + pan.x / 150f).coerceIn(-1f, 1f)
                                            val newPanY = (current.panY + pan.y / 150f).coerceIn(-1f, 1f)
                                            val updated = cellTransforms.toMutableList()
                                            while (updated.size <= index) {
                                                updated.add(CollageMakerUtil.CellTransform())
                                            }
                                            updated[index] = CollageMakerUtil.CellTransform(newScale, newPanX, newPanY)
                                            cellTransforms = updated
                                        }
                                    }
                            ) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Collage Photo $index",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer(
                                            scaleX = transform.scale,
                                            scaleY = transform.scale,
                                            translationX = transform.panX * 120f,
                                            translationY = transform.panY * 120f
                                        )
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Styling Controls
            Surface(
                tonalElevation = 6.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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

                        Spacer(modifier = Modifier.weight(1f))

                        if (imageUris.size in 2..4) {
                            IconButton(
                                onClick = {
                                    layoutVariant = (layoutVariant + 1) % 2
                                    HapticUtil.performSelection(context)
                                }
                            ) {
                                Icon(Icons.Default.Dashboard, contentDescription = "Change Layout Variant")
                            }
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
