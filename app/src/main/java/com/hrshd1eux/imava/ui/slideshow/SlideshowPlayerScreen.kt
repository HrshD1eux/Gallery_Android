package com.hrshd1eux.imava.ui.slideshow

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hrshd1eux.imava.core.util.HapticUtil
import com.hrshd1eux.imava.data.model.MediaItem
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SlideshowPlayerScreen(
    mediaItems: List<MediaItem>,
    startIndex: Int = 0,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    if (mediaItems.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    var isShuffled by remember { mutableStateOf(false) }
    var playlist by remember(mediaItems, isShuffled) {
        mutableStateOf(if (isShuffled) mediaItems.shuffled() else mediaItems)
    }

    var currentIndex by remember { mutableIntStateOf(startIndex.coerceIn(0, playlist.size - 1)) }
    var isPlaying by remember { mutableStateOf(true) }
    var slideDurationMs by remember { mutableLongStateOf(4500L) }
    var showControls by remember { mutableStateOf(true) }


    val scaleAnim = remember { Animatable(1.0f) }
    val transXAnim = remember { Animatable(0f) }
    val transYAnim = remember { Animatable(0f) }

    BackHandler {
        onDismiss()
    }


    LaunchedEffect(currentIndex, isPlaying, slideDurationMs) {
        if (!isPlaying) return@LaunchedEffect

        // Alternate pan directions on every slide
        val targetScale = if (currentIndex % 2 == 0) 1.18f else 1.0f
        val startScale = if (currentIndex % 2 == 0) 1.0f else 1.18f
        val targetX = if (currentIndex % 3 == 0) 25f else -25f
        val targetY = if (currentIndex % 2 == 0) -15f else 15f

        scaleAnim.snapTo(startScale)
        transXAnim.snapTo(0f)
        transYAnim.snapTo(0f)

        coroutineScope {
            launch {
                scaleAnim.animateTo(
                    targetValue = targetScale,
                    animationSpec = tween(durationMillis = slideDurationMs.toInt(), easing = LinearEasing)
                )
            }
            launch {
                transXAnim.animateTo(
                    targetValue = targetX,
                    animationSpec = tween(durationMillis = slideDurationMs.toInt(), easing = LinearEasing)
                )
            }
            launch {
                transYAnim.animateTo(
                    targetValue = targetY,
                    animationSpec = tween(durationMillis = slideDurationMs.toInt(), easing = LinearEasing)
                )
            }
            launch {
                delay(slideDurationMs)
                currentIndex = (currentIndex + 1) % playlist.size
            }
        }
    }

    // Auto-hide controls after 3 seconds of inactivity
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3500)
            showControls = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val width = size.width
                        if (offset.x < width * 0.25f) {
                            currentIndex = if (currentIndex > 0) currentIndex - 1 else playlist.size - 1
                            HapticUtil.performSelection(context)
                        } else if (offset.x > width * 0.75f) {
                            currentIndex = (currentIndex + 1) % playlist.size
                            HapticUtil.performSelection(context)
                        } else {
                            showControls = !showControls
                        }
                    }
                )
            }
    ) {
        val currentItem = playlist[currentIndex]

        Crossfade(
            targetState = currentItem,
            animationSpec = tween(800),
            modifier = Modifier.fillMaxSize()
        ) { item ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scaleAnim.value
                        scaleY = scaleAnim.value
                        translationX = transXAnim.value
                        translationY = transYAnim.value
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = item.uri,
                    contentDescription = item.path.substringAfterLast("/"),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Top Bar Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit", tint = Color.White)
                }

                Text(
                    text = "${currentIndex + 1} / ${playlist.size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                IconButton(
                    onClick = {
                        isShuffled = !isShuffled
                        HapticUtil.performSelection(context)
                    }
                ) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffled) MaterialTheme.colorScheme.primary else Color.White
                    )
                }
            }
        }

        // Bottom Controls Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Speed duration presets (3s, 5s, 8s)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    listOf(
                        "Fast (3s)" to 3000L,
                        "Normal (5s)" to 5000L,
                        "Cinematic (8s)" to 8000L
                    ).forEach { (label, dur) ->
                        FilterChip(
                            selected = slideDurationMs == dur,
                            onClick = {
                                slideDurationMs = dur
                                HapticUtil.performSelection(context)
                            },
                            label = { Text(label) }
                        )
                    }
                }

                // Playback Control Buttons (Prev, Play/Pause, Next)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    IconButton(
                        onClick = {
                            currentIndex = if (currentIndex > 0) currentIndex - 1 else playlist.size - 1
                            HapticUtil.performSelection(context)
                        }
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(32.dp))
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(56.dp)
                            .clickable {
                                isPlaying = !isPlaying
                                HapticUtil.performClick(context)
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            currentIndex = (currentIndex + 1) % playlist.size
                            HapticUtil.performSelection(context)
                        }
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}
