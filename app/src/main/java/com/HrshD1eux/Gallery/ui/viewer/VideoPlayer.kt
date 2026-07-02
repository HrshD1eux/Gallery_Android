package com.HrshD1eux.Gallery.ui.viewer

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

fun formatVideoTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600

    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerContainer(
    uri: Uri,
    title: String = "",
    isSelectedPage: Boolean,
    showChrome: Boolean,
    onTap: () -> Unit,
    rotationDegrees: Float = 0f,
    onRotationChange: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var exoPlayer by remember(uri) { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var resizeModeState by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var isLocked by remember { mutableStateOf(false) }

    DisposableEffect(uri) {
        val player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(Media3Item.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
            playWhenReady = isSelectedPage
        }
        exoPlayer = player

        onDispose {
            player.stop()
            player.clearMediaItems()
            player.release()
            exoPlayer = null
        }
    }

    LaunchedEffect(isSelectedPage) {
        exoPlayer?.let { player ->
            if (isSelectedPage) {
                player.playWhenReady = true
            } else {
                player.playWhenReady = false
                player.pause()
            }
        }
    }

    // Ticker coroutine to update playback position smoothly
    LaunchedEffect(exoPlayer, isSelectedPage) {
        val player = exoPlayer ?: return@LaunchedEffect
        while (isActive) {
            isPlaying = player.isPlaying
            if (!isSeeking) {
                currentPosition = player.currentPosition.coerceAtLeast(0L)
            }
            duration = player.duration.coerceAtLeast(0L)
            delay(200)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (!isLocked) {
                        onTap()
                    }
                }
            )
    ) {
        exoPlayer?.let { player ->
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false // Completely disable built-in controller to eliminate all overlap
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        resizeMode = resizeModeState
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    view.player = player
                    view.resizeMode = resizeModeState
                },
                onRelease = { view ->
                    view.player = null
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = rotationDegrees
                    }
            )
        }

        // Title Bar at top center (when chrome is visible)
        if (showChrome && !isLocked && title.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 8.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }

        // Lock button floating on left side
        if (showChrome || isLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
            ) {
                IconButton(
                    onClick = { isLocked = !isLocked },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (isLocked) "Unlock controls" else "Lock controls",
                        tint = if (isLocked) Color(0xFFFF5722) else Color.White
                    )
                }
            }
        }

        // Custom Video Controls Overlay matching Screenshot 2
        AnimatedVisibility(
            visible = showChrome && !isLocked,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 76.dp) // Sits cleanly above gallery bottom action bar
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Time Indicators: Left = Current Position, Right = Total Duration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatVideoTime(if (isSeeking) sliderPosition.toLong() else currentPosition),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    Text(
                        text = formatVideoTime(duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }

                // Custom Orange Seek Slider
                val maxSeek = if (duration > 0L) duration.toFloat() else 1f
                val currentSeek = if (isSeeking) sliderPosition else currentPosition.toFloat().coerceIn(0f, maxSeek)

                Slider(
                    value = currentSeek,
                    onValueChange = { pos ->
                        isSeeking = true
                        sliderPosition = pos
                    },
                    onValueChangeFinished = {
                        exoPlayer?.seekTo(sliderPosition.toLong())
                        currentPosition = sliderPosition.toLong()
                        isSeeking = false
                    },
                    valueRange = 0f..maxSeek,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF5722),
                        activeTrackColor = Color(0xFFFF5722),
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                )

                // Custom Player Control Bar (Screenshot 2 style)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Subtitles / Audio Tracks
                    IconButton(onClick = { /* Toggle subtitles/audio */ }) {
                        Icon(
                            imageVector = Icons.Default.Subtitles,
                            contentDescription = "Subtitles",
                            tint = Color.White
                        )
                    }

                    // Rotate
                    IconButton(onClick = { onRotationChange?.invoke((rotationDegrees + 90f) % 360f) }) {
                        Icon(
                            imageVector = Icons.Default.RotateRight,
                            contentDescription = "Rotate",
                            tint = Color.White
                        )
                    }

                    // Center Play / Pause Big Button
                    IconButton(
                        onClick = {
                            exoPlayer?.let { player ->
                                if (player.isPlaying) {
                                    player.pause()
                                } else {
                                    player.play()
                                }
                                isPlaying = player.isPlaying
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Aspect Ratio / Fit Screen mode
                    IconButton(onClick = {
                        resizeModeState = when (resizeModeState) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = "Aspect Ratio",
                            tint = Color.White
                        )
                    }

                    // Lock Button
                    IconButton(onClick = { isLocked = true }) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
