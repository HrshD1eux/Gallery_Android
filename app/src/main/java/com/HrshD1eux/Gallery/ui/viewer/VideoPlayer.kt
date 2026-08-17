package com.HrshD1eux.Gallery.ui.viewer

import android.net.Uri
import android.view.TextureView
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
import androidx.compose.material.icons.filled.RotateRight
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
import androidx.compose.runtime.mutableFloatStateOf
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

    // Triple-state seeking: isUserSeeking (dragging), seekTargetMs (buffering), currentPosition (live)
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableLongStateOf(0L) }
    // Holds the target seek position until the player actually reaches it.
    // Prevents the position snapping back to 0 during re-buffering after seekTo().
    var seekTargetMs by remember { mutableLongStateOf(-1L) }

    var resizeModeState by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var isLocked by remember { mutableStateOf(false) }
    // internalRotation is Compose state — changing it triggers recompose which re-calls
    // AndroidView update{} where view.rotation is applied to the TextureView.
    var internalRotation by remember { mutableFloatStateOf(rotationDegrees) }
    var subtitlesEnabled by remember { mutableStateOf(true) }
    var doubleTapOverlayText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(rotationDegrees) { internalRotation = rotationDegrees }

    DisposableEffect(uri) {
        val player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(Media3Item.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
            playWhenReady = isSelectedPage
        }
        exoPlayer = player
        onDispose {
            player.clearVideoSurface()
            player.stop()
            player.clearMediaItems()
            player.release()
            exoPlayer = null
        }
    }

    LaunchedEffect(isSelectedPage) {
        exoPlayer?.let { player ->
            player.playWhenReady = isSelectedPage
            if (!isSelectedPage) player.pause()
        }
    }

    // Ticker: updates position only when NOT seeking and NOT waiting for a seek to complete
    LaunchedEffect(exoPlayer, isSelectedPage) {
        val player = exoPlayer ?: return@LaunchedEffect
        while (isActive) {
            isPlaying = player.isPlaying
            val d = player.duration
            if (d > 0L) duration = d

            if (!isUserSeeking) {
                val pos = player.currentPosition.coerceAtLeast(0L)
                if (seekTargetMs >= 0L) {
                    // We just seeked. Only clear the lock once the player has advanced past 0
                    // and is within 2s of the target (confirming it's not buffering at 0)
                    if (pos >= 1000L || pos >= (seekTargetMs - 2000L).coerceAtLeast(0L)) {
                        currentPosition = pos
                        seekTargetMs = -1L
                    }
                    // else: still re-buffering at 0ms, keep showing seekTargetMs in slider
                } else {
                    currentPosition = pos
                }
            }
            delay(150)
        }
    }

    LaunchedEffect(doubleTapOverlayText) {
        if (doubleTapOverlayText != null) { delay(800); doubleTapOverlayText = null }
    }

    Box(
        modifier = modifier.fillMaxSize().pointerInput(isLocked) {
            detectTapGestures(
                onTap = { if (!isLocked) onTap() },
                onDoubleTap = { offset ->
                    if (!isLocked) {
                        exoPlayer?.let { player ->
                            if (offset.x < size.width / 2) {
                                val newPos = (player.currentPosition - 10000L).coerceAtLeast(0L)
                                seekTargetMs = newPos; currentPosition = newPos; player.seekTo(newPos)
                                doubleTapOverlayText = "◀◀ 10s"
                            } else {
                                val newPos = (player.currentPosition + 10000L).coerceAtMost(
                                    if (player.duration > 0) player.duration else Long.MAX_VALUE)
                                seekTargetMs = newPos; currentPosition = newPos; player.seekTo(newPos)
                                doubleTapOverlayText = "10s ▶▶"
                            }
                        }
                    }
                }
            )
        }
    ) {
        exoPlayer?.let { player ->
            // ── TextureView directly as video output ─────────────────────────
            // Using TextureView (not PlayerView/SurfaceView) because TextureView is
            // drawn inside the normal View hierarchy and its rotation matrix
            // actually transforms the video pixels.
            // SurfaceView/PlayerView default is a hardware compositor overlay that
            // sits outside the view tree — view.rotation and graphicsLayer have zero
            // effect on it, which is why rotation never worked before.
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        player.setVideoTextureView(this)
                    }
                },
                update = { view ->
                    view.rotation = internalRotation
                },
                onRelease = { _ ->
                    player.clearVideoSurface()
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Title
        if (showChrome && !isLocked && title.isNotEmpty()) {
            Box(
                Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
            }
        }

        // Double-tap feedback
        doubleTapOverlayText?.let { text ->
            Box(
                Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(text, style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        }

        // Lock overlay
        if (isLocked) {
            Box(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp)) {
                IconButton(
                    onClick = { isLocked = false },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Default.Lock, "Unlock", tint = Color(0xFFFF5722))
                }
            }
        }

        // Controls
        AnimatedVisibility(
            visible = showChrome && !isLocked,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 76.dp)
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                val displayMs = when {
                    isUserSeeking -> dragPositionMs
                    seekTargetMs >= 0L -> seekTargetMs
                    else -> currentPosition
                }

                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(formatVideoTime(displayMs), style = MaterialTheme.typography.bodySmall, color = Color.White)
                    Text(formatVideoTime(duration), style = MaterialTheme.typography.bodySmall, color = Color.White)
                }

                val maxMs = if (duration > 0L) duration.toFloat() else 1000f
                val sliderVal = when {
                    isUserSeeking -> dragPositionMs.toFloat().coerceIn(0f, maxMs)
                    seekTargetMs >= 0L -> seekTargetMs.toFloat().coerceIn(0f, maxMs)
                    else -> currentPosition.toFloat().coerceIn(0f, maxMs)
                }

                Slider(
                    value = sliderVal,
                    onValueChange = { pos ->
                        if (duration > 0L) {
                            isUserSeeking = true
                            dragPositionMs = pos.toLong()
                        }
                    },
                    onValueChangeFinished = {
                        if (duration > 0L) {
                            val target = dragPositionMs.coerceIn(0L, duration)
                            seekTargetMs = target
                            currentPosition = target
                            exoPlayer?.seekTo(target)
                            isUserSeeking = false
                        }
                    },
                    valueRange = 0f..maxMs,
                    enabled = duration > 0L,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF5722),
                        activeTrackColor = Color(0xFFFF5722),
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth().height(28.dp)
                )

                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    Arrangement.SpaceEvenly, Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        exoPlayer?.let { player ->
                            val hasSubs = player.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT }
                            if (hasSubs) {
                                subtitlesEnabled = !subtitlesEnabled
                                player.trackSelectionParameters = player.trackSelectionParameters
                                    .buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled).build()
                                Toast.makeText(context, if (subtitlesEnabled) "Subtitles On" else "Subtitles Off", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "No subtitles in this video", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Icon(if (subtitlesEnabled) Icons.Default.Subtitles else Icons.Default.SubtitlesOff, "Subtitles", tint = Color.White)
                    }

                    IconButton(onClick = {
                        exoPlayer?.let { player ->
                            val newPos = (player.currentPosition - 10000L).coerceAtLeast(0L)
                            seekTargetMs = newPos; currentPosition = newPos; player.seekTo(newPos)
                            doubleTapOverlayText = "◀◀ 10s"
                        }
                    }) { Icon(Icons.Default.FastRewind, "Rewind 10s", tint = Color.White) }

                    IconButton(
                        onClick = {
                            exoPlayer?.let { player ->
                                if (player.isPlaying) player.pause() else player.play()
                                isPlaying = player.isPlaying
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    IconButton(onClick = {
                        exoPlayer?.let { player ->
                            val newPos = (player.currentPosition + 10000L).coerceAtMost(
                                if (player.duration > 0) player.duration else Long.MAX_VALUE)
                            seekTargetMs = newPos; currentPosition = newPos; player.seekTo(newPos)
                            doubleTapOverlayText = "10s ▶▶"
                        }
                    }) { Icon(Icons.Default.FastForward, "Forward 10s", tint = Color.White) }

                    IconButton(onClick = {
                        internalRotation = (internalRotation + 90f) % 360f
                        onRotationChange?.invoke(internalRotation)
                    }) { Icon(Icons.Default.RotateRight, "Rotate", tint = Color.White) }

                    IconButton(onClick = {
                        resizeModeState = when (resizeModeState) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    }) { Icon(Icons.Default.AspectRatio, "Aspect Ratio", tint = Color.White) }

                    IconButton(onClick = { isLocked = true }) {
                        Icon(Icons.Default.LockOpen, "Lock Controls", tint = Color.White)
                    }
                }
            }
        }
    }
}
