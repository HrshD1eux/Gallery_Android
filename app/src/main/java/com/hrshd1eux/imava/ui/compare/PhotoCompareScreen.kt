package com.hrshd1eux.imava.ui.compare

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hrshd1eux.imava.data.model.MediaItem

enum class CompareLayoutMode {
    VERTICAL, HORIZONTAL
}

@Composable
fun PhotoCompareScreen(
    item1: MediaItem,
    item2: MediaItem,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isSynchronized by remember { mutableStateOf(true) }
    var layoutMode by remember { mutableStateOf(CompareLayoutMode.VERTICAL) }

    var scale1 by remember { mutableFloatStateOf(1f) }
    var offsetX1 by remember { mutableFloatStateOf(0f) }
    var offsetY1 by remember { mutableFloatStateOf(0f) }

    var scale2 by remember { mutableFloatStateOf(1f) }
    var offsetX2 by remember { mutableFloatStateOf(0f) }
    var offsetY2 by remember { mutableFloatStateOf(0f) }

    fun resetZoom() {
        scale1 = 1f; offsetX1 = 0f; offsetY1 = 0f
        scale2 = 1f; offsetX2 = 0f; offsetY2 = 0f
    }

    BackHandler(onBack = onBack)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (layoutMode == CompareLayoutMode.VERTICAL) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    ComparePane(
                        item = item1,
                        scale = scale1,
                        offsetX = offsetX1,
                        offsetY = offsetY1,
                        onTransform = { zoomChange, panChange ->
                            scale1 = (scale1 * zoomChange).coerceIn(0.8f, 10f)
                            offsetX1 += panChange.x
                            offsetY1 += panChange.y
                            if (isSynchronized) {
                                scale2 = scale1
                                offsetX2 = offsetX1
                                offsetY2 = offsetY1
                            }
                        },
                        label = "Photo A"
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color.DarkGray)
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    ComparePane(
                        item = item2,
                        scale = scale2,
                        offsetX = offsetX2,
                        offsetY = offsetY2,
                        onTransform = { zoomChange, panChange ->
                            scale2 = (scale2 * zoomChange).coerceIn(0.8f, 10f)
                            offsetX2 += panChange.x
                            offsetY2 += panChange.y
                            if (isSynchronized) {
                                scale1 = scale2
                                offsetX1 = offsetX2
                                offsetY1 = offsetY2
                            }
                        },
                        label = "Photo B"
                    )
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    ComparePane(
                        item = item1,
                        scale = scale1,
                        offsetX = offsetX1,
                        offsetY = offsetY1,
                        onTransform = { zoomChange, panChange ->
                            scale1 = (scale1 * zoomChange).coerceIn(0.8f, 10f)
                            offsetX1 += panChange.x
                            offsetY1 += panChange.y
                            if (isSynchronized) {
                                scale2 = scale1
                                offsetX2 = offsetX1
                                offsetY2 = offsetY1
                            }
                        },
                        label = "Photo A"
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .background(Color.DarkGray)
                )

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    ComparePane(
                        item = item2,
                        scale = scale2,
                        offsetX = offsetX2,
                        offsetY = offsetY2,
                        onTransform = { zoomChange, panChange ->
                            scale2 = (scale2 * zoomChange).coerceIn(0.8f, 10f)
                            offsetX2 += panChange.x
                            offsetY2 += panChange.y
                            if (isSynchronized) {
                                scale1 = scale2
                                offsetX1 = offsetX2
                                offsetY1 = offsetY2
                            }
                        },
                        label = "Photo B"
                    )
                }
            }
        }


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Text(
                text = "Side-by-Side Compare",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        layoutMode = if (layoutMode == CompareLayoutMode.VERTICAL) {
                            CompareLayoutMode.HORIZONTAL
                        } else {
                            CompareLayoutMode.VERTICAL
                        }
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                ) {
                    Icon(
                        imageVector = if (layoutMode == CompareLayoutMode.VERTICAL) Icons.Default.SwapHoriz else Icons.Default.SwapVert,
                        contentDescription = "Toggle Split Orientation",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { isSynchronized = !isSynchronized },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                ) {
                    Icon(
                        imageVector = if (isSynchronized) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Toggle Zoom Sync",
                        tint = if (isSynchronized) MaterialTheme.colorScheme.primary else Color.White
                    )
                }
            }
        }


        Surface(
            color = Color.Black.copy(alpha = 0.7f),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isSynchronized) "⚡ Synchronized Zoom & Pan" else "🖐️ Independent Pan",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )

                Button(
                    onClick = { resetZoom() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("1:1 Reset Zoom")
                }
            }
        }
    }
}

@Composable
private fun ComparePane(
    item: MediaItem,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onTransform: (zoomChange: Float, panChange: androidx.compose.ui.geometry.Offset) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    onTransform(zoom, pan)
                }
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.uri)
                .crossfade(true)
                .build(),
            contentDescription = item.path.substringAfterLast('/'),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = item.path.substringAfterLast('/'),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }
        }
    }
}
