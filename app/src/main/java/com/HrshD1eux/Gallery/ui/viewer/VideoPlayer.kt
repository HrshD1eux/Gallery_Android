package com.HrshD1eux.Gallery.ui.viewer

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.abs

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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var exoPlayer by remember(uri) { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    // Bulletproof Seeking State
    var isUserSeeking by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableLongStateOf(0L) }
    var pendingSeekTargetMs by remember { mutableStateOf<Long?>(null) }

    var resizeModeState by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var isLocked by remember { mutableStateOf(false) }
    var subtitlesEnabled by remember { mutableStateOf(true) }

    // Double-tap gesture feedback overlay state
    var doubleTapOverlayText by remember { mutableStateOf<String?>(null) }

    if (!isSelectedPage) {
        coil.compose.AsyncImage(
            model = coil.request.ImageRequest.Builder(context)
                .data(uri)
                .crossfade(true)
                .error(android.R.drawable.ic_menu_report_image)
                .fallback(android.R.drawable.ic_menu_report_image)
                .build(),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = modifier
        )
        return
    }

    DisposableEffect(uri, isSelectedPage) {
        val player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(Media3Item.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
            playWhenReady = true
        }

        val listener = object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    currentPosition = newPosition.positionMs
                    pendingSeekTargetMs = null
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    pendingSeekTargetMs = null
                    currentPosition = player.currentPosition.coerceAtLeast(0L)
                }
            }
        }
        player.addListener(listener)
        exoPlayer = player

        onDispose {
            player.removeListener(listener)
            player.stop()
            player.clearMediaItems()
            player.release()
            exoPlayer = null
        }
    }

    // Ticker coroutine to update playback position smoothly without overwriting active user seeks
    LaunchedEffect(exoPlayer, isSelectedPage) {
        val player = exoPlayer ?: return@LaunchedEffect
        while (isActive) {
            isPlaying = player.isPlaying
            val target = pendingSeekTargetMs
            if (!isUserSeeking) {
                if (target != null) {
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    if (pos >= target || abs(pos - target) < 500L) {
                        pendingSeekTargetMs = null
                        currentPosition = pos
                    } else {
                        currentPosition = target
                    }
                } else {
                    currentPosition = player.currentPosition.coerceAtLeast(0L)
                }
            }
            val dur = player.duration
            if (dur > 0L) {
                duration = dur
            }
            delay(50)
        }
    }

    // Dismiss gesture feedback overlay after 800ms
    LaunchedEffect(doubleTapOverlayText) {
        if (doubleTapOverlayText != null) {
            delay(800)
            doubleTapOverlayText = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isLocked) {
                detectTapGestures(
                    onTap = {
                        if (!isLocked) {
                            onTap()
                        }
                    },
                    onDoubleTap = { offset ->
                        if (!isLocked) {
                            exoPlayer?.let { player ->
                                val width = size.width
                                val basePos = pendingSeekTargetMs ?: currentPosition
                                if (offset.x < width / 2) {
                                    // Rewind 10 seconds
                                    val newPos = (basePos - 10000L).coerceAtLeast(0L)
                                    pendingSeekTargetMs = newPos
                                    currentPosition = newPos
                                    player.seekTo(newPos)
                                    doubleTapOverlayText = "◀◀ 10s Rewind"
                                } else {
                                    // Fast Forward 10 seconds
                                    val targetMax = if (player.duration > 0) player.duration else Long.MAX_VALUE
                                    val newPos = (basePos + 10000L).coerceAtMost(targetMax)
                                    pendingSeekTargetMs = newPos
                                    currentPosition = newPos
                                    player.seekTo(newPos)
                                    doubleTapOverlayText = "10s Fast Forward ▶▶"
                                }
                            }
                        }
                    }
                )
            }
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
                modifier = Modifier.fillMaxSize()
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

        // Double-tap gesture feedback overlay indicator
        doubleTapOverlayText?.let { text ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        }

        // If screen is locked, show a single Lock icon on top-left to unlock
        if (isLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                IconButton(
                    onClick = { isLocked = false },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Unlock controls",
                        tint = Color(0xFFFF5722)
                    )
                }
            }
        }

        // Custom Video Controls Overlay matching Screenshot 2 (Zero Overlap)
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
                // Display time position: Left = Current Position, Right = Total Duration
                val displayPosMs = when {
                    isUserSeeking -> dragPositionMs
                    pendingSeekTargetMs != null -> pendingSeekTargetMs!!
                    else -> currentPosition
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatVideoTime(displayPosMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    Text(
                        text = formatVideoTime(duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }

                // Custom Orange Seek Slider (Indestructible state tracking & zero-reset protection)
                val maxSeekMs = if (duration > 0L) duration.toFloat() else 1000f
                val sliderValueFloat = displayPosMs.toFloat().coerceIn(0f, maxSeekMs)

                Slider(
                    value = sliderValueFloat,
                    onValueChange = { pos ->
                        if (duration > 0L) {
                            isUserSeeking = true
                            dragPositionMs = pos.toLong().coerceIn(0L, duration)
                        }
                    },
                    onValueChangeFinished = {
                        if (duration > 0L) {
                            val targetMs = dragPositionMs.coerceIn(0L, duration)
                            pendingSeekTargetMs = targetMs
                            currentPosition = targetMs
                            exoPlayer?.seekTo(targetMs)
                            isUserSeeking = false
                        }
                    },
                    valueRange = 0f..maxSeekMs,
                    enabled = duration > 0L,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF5722),
                        activeTrackColor = Color(0xFFFF5722),
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                )

                // Custom Player Control Bar (No Rotate button, clean controls)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Subtitles / Audio Tracks Button
                    IconButton(onClick = {
                        exoPlayer?.let { player ->
                            val textTracksExist = player.currentTracks.groups.any { group ->
                                group.type == C.TRACK_TYPE_TEXT
                            }
                            if (textTracksExist) {
                                subtitlesEnabled = !subtitlesEnabled
                                val builder = player.trackSelectionParameters.buildUpon()
                                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
                                player.trackSelectionParameters = builder.build()
                                Toast.makeText(
                                    context,
                                    if (subtitlesEnabled) "Subtitles Enabled" else "Subtitles Disabled",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(context, "No subtitles available in video", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Icon(
                            imageVector = if (subtitlesEnabled) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                            contentDescription = "Subtitles",
                            tint = Color.White
                        )
                    }

                    // Fast Rewind 10s Button
                    IconButton(onClick = {
                        exoPlayer?.let { player ->
                            val basePos = pendingSeekTargetMs ?: currentPosition
                            val newPos = (basePos - 10000L).coerceAtLeast(0L)
                            pendingSeekTargetMs = newPos
                            currentPosition = newPos
                            player.seekTo(newPos)
                            doubleTapOverlayText = "◀◀ 10s Rewind"
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "Rewind 10s",
                            tint = Color.White
                        )
                    }

                    // Center Big Play / Pause Button
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

                    // Fast Forward 10s Button
                    IconButton(onClick = {
                        exoPlayer?.let { player ->
                            val basePos = pendingSeekTargetMs ?: currentPosition
                            val targetMax = if (player.duration > 0) player.duration else Long.MAX_VALUE
                            val newPos = (basePos + 10000L).coerceAtMost(targetMax)
                            pendingSeekTargetMs = newPos
                            currentPosition = newPos
                            player.seekTo(newPos)
                            doubleTapOverlayText = "10s Fast Forward ▶▶"
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Forward 10s",
                            tint = Color.White
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

                    // Lock Screen Controls Button
                    IconButton(onClick = { isLocked = true }) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "Lock Controls",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
