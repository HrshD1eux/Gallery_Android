package com.hrshd1eux.imava.ui.viewer

import android.content.Context
import android.media.AudioManager
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
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
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

    // Seeking State
    var isUserSeeking by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableLongStateOf(0L) }
    var pendingSeekTargetMs by remember { mutableStateOf<Long?>(null) }

    // Gesture feedback overlay state
    var gestureOverlayText by remember { mutableStateOf<String?>(null) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var isHolding2x by remember { mutableStateOf(false) }

    // Track Selector State
    var showTrackDialog by remember { mutableStateOf(false) }
    var availableTracks by remember { mutableStateOf<Tracks?>(null) }

    // Audio & Brightness Managers
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember(audioManager) { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var currentBrightness by remember {
        val window = (context as? android.app.Activity)?.window
        val cur = window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (cur >= 0f) cur else 0.5f)
    }

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
                        val mediaSource = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(
                            EncryptedVaultDataSource.Factory(file)
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
            override fun onTracksChanged(tracks: Tracks) {
                availableTracks = tracks
            }
        }
        player.addListener(listener)
        exoPlayer = player

        onDispose {
            player.removeListener(listener)
            player.release()
            exoPlayer = null
        }
    }

    // High frequency position sync
    LaunchedEffect(exoPlayer) {
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

    // Dismiss gesture feedback overlay after 1000ms
    LaunchedEffect(gestureOverlayText, isHolding2x) {
        if (gestureOverlayText != null && !isHolding2x) {
            delay(1000)
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
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    if (abs(dragAmount.y) > abs(dragAmount.x) * 1.2f) {
                        change.consume()
                        val width = size.width
                        val isLeftSide = change.position.x < width / 2f
                        if (isLeftSide) {
                            // Left side vertical drag: Adjust Brightness
                            val delta = -dragAmount.y / 600f
                            val newBrightness = (currentBrightness + delta).coerceIn(0.01f, 1f)
                            currentBrightness = newBrightness
                            (context as? android.app.Activity)?.window?.let { window ->
                                val lp = window.attributes
                                lp.screenBrightness = newBrightness
                                window.attributes = lp
                            }
                            gestureOverlayText = "☀️ Brightness: ${(newBrightness * 100).toInt()}%"
                        } else {
                            // Right side vertical drag: Adjust Media Volume
                            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            if (dragAmount.y < -10) {
                                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                            } else if (dragAmount.y > 10) {
                                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                            }
                            val updatedVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val volPct = (updatedVol.toFloat() / maxVolume * 100).toInt()
                            gestureOverlayText = if (updatedVol == 0) "🔇 Volume: 0%" else "🔊 Volume: $volPct%"
                        }
                    }
                }
            }
    ) {
        exoPlayer?.let { player ->
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
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

        // Gesture feedback overlay indicator (Volume, Brightness, 5s seek, 2x speed, play/pause)
        gestureOverlayText?.let { text ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.8f), CircleShape)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        }

        // Video Progress / Seekbar & Track Control Overlay
        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 68.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
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

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            onClick = { showSpeedDialog = true },
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = "${playbackSpeed}x",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        IconButton(
                            onClick = { showTrackDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = "Audio & Subtitle Tracks",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatVideoTime(duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }
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

    // Playback Speed Selection Dialog
    if (showSpeedDialog && exoPlayer != null) {
        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Playback Speed ⚡") },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(speeds) { sp ->
                        val isSel = sp == playbackSpeed
                        Surface(
                            onClick = {
                                playbackSpeed = sp
                                exoPlayer?.setPlaybackSpeed(sp)
                                showSpeedDialog = false
                            },
                            color = if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (sp == 1.0f) "1.0x (Normal)" else "${sp}x",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (isSel) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Audio & Subtitles Selection Dialog
    if (showTrackDialog && exoPlayer != null) {
        val player = exoPlayer!!
        val tracks = availableTracks ?: player.currentTracks

        AlertDialog(
            onDismissRequest = { showTrackDialog = false },
            title = {
                Text(
                    text = "Audio & Subtitles 🎧",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                    val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }

                    if (audioGroups.isNotEmpty()) {
                        item {
                            Text(
                                text = "Audio Tracks",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(audioGroups) { group ->
                            val trackGroup = group.mediaTrackGroup
                            for (i in 0 until group.length) {
                                val format = group.getTrackFormat(i)
                                val isSelected = group.isTrackSelected(i)
                                val trackName = format.label ?: format.language ?: "Audio Track ${i + 1}"

                                Surface(
                                    onClick = {
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setOverrideForType(
                                                TrackSelectionOverride(trackGroup, listOf(i))
                                            )
                                            .build()
                                        showTrackDialog = false
                                    },
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = trackName, style = MaterialTheme.typography.bodyMedium)
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Subtitles / Captions",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }

                    item {
                        val isNoneSelected = textGroups.none { it.isSelected }
                        Surface(
                            onClick = {
                                player.trackSelectionParameters = player.trackSelectionParameters
                                    .buildUpon()
                                    .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                    .build()
                                showTrackDialog = false
                            },
                            color = if (isNoneSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Off (Disabled)", style = MaterialTheme.typography.bodyMedium)
                                if (isNoneSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    items(textGroups) { group ->
                        val trackGroup = group.mediaTrackGroup
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val isSelected = group.isTrackSelected(i)
                            val trackName = format.label ?: format.language ?: "Subtitle Track ${i + 1}"

                            Surface(
                                onClick = {
                                    player.trackSelectionParameters = player.trackSelectionParameters
                                        .buildUpon()
                                        .setOverrideForType(
                                            TrackSelectionOverride(trackGroup, listOf(i))
                                        )
                                        .build()
                                    showTrackDialog = false
                                },
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = trackName, style = MaterialTheme.typography.bodyMedium)
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrackDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
