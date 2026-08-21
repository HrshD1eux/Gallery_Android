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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
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
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var exoPlayer by remember(uri) { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    // Bulletproof Seeking State
    var isUserSeeking by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableLongStateOf(0L) }
    var pendingSeekTargetMs by remember { mutableStateOf<Long?>(null) }

    // Gesture feedback overlay state
    var gestureOverlayText by remember { mutableStateOf<String?>(null) }
    var isHolding2x by remember { mutableStateOf(false) }

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
            val path = uri.path
            if (path != null && (path.contains("/vault/") || path.contains("vault_") || uri.scheme == "vault")) {
                val file = java.io.File(path)
                if (file.exists()) {
                    try {
                        val byteOut = java.io.ByteArrayOutputStream()
                        file.inputStream().use { input ->
                            com.HrshD1eux.Gallery.core.util.VaultCrypto.decrypt(input, byteOut)
                        }
                        val bytes = byteOut.toByteArray()
                        val mediaSource = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(
                            androidx.media3.datasource.DataSource.Factory {
                                androidx.media3.datasource.ByteArrayDataSource(bytes)
                            }
                        ).createMediaSource(Media3Item.fromUri(uri))
                        setMediaSource(mediaSource)
                    } catch (_: Exception) {
                        setMediaItem(Media3Item.fromUri(uri))
                    }
                } else {
                    setMediaItem(Media3Item.fromUri(uri))
                }
            } else {
                setMediaItem(Media3Item.fromUri(uri))
            }
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

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
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

    // Dismiss gesture feedback overlay after 800ms (unless holding 2x)
    LaunchedEffect(gestureOverlayText, isHolding2x) {
        if (gestureOverlayText != null && !isHolding2x) {
            delay(800)
            if (!isHolding2x) {
                gestureOverlayText = null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        exoPlayer?.let { player ->
                            if (player.isPlaying) {
                                player.pause()
                                gestureOverlayText = "⏸️ Pause"
                            } else {
                                player.play()
                                gestureOverlayText = "▶ Play"
                            }
                        }
                        onTap()
                    },
                    onDoubleTap = { offset ->
                        exoPlayer?.let { player ->
                            val width = size.width
                            val basePos = pendingSeekTargetMs ?: currentPosition
                            if (offset.x < width / 2) {
                                // Rewind 5 seconds
                                val newPos = (basePos - 5000L).coerceAtLeast(0L)
                                pendingSeekTargetMs = newPos
                                currentPosition = newPos
                                player.seekTo(newPos)
                                gestureOverlayText = "◀◀ -5s"
                            } else {
                                // Fast Forward 5 seconds
                                val targetMax = if (player.duration > 0) player.duration else Long.MAX_VALUE
                                val newPos = (basePos + 5000L).coerceAtMost(targetMax)
                                pendingSeekTargetMs = newPos
                                currentPosition = newPos
                                player.seekTo(newPos)
                                gestureOverlayText = "+5s ▶▶"
                            }
                        }
                    },
                    onPress = {
                        val longPressJob = scope.launch {
                            delay(400)
                            isHolding2x = true
                            exoPlayer?.setPlaybackSpeed(2.0f)
                            gestureOverlayText = "2x Speed ⏩"
                        }
                        try {
                            awaitRelease()
                        } finally {
                            longPressJob.cancel()
                            if (isHolding2x) {
                                isHolding2x = false
                                exoPlayer?.setPlaybackSpeed(1.0f)
                                gestureOverlayText = null
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
                        useController = false // Completely disable built-in controller
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        this.resizeMode = resizeMode
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    view.player = player
                    view.resizeMode = resizeMode
                },
                onRelease = { view ->
                    view.player = null
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Gesture feedback overlay indicator (5s seek, 2x speed, play/pause)
        gestureOverlayText?.let { text ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.75f), CircleShape)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        }

        // Video Progress / Seekbar Overlay (Row 1 above gallery bottom action bar)
        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 68.dp) // Sits cleanly above gallery bottom action bar
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
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
            }
        }
    }
}
