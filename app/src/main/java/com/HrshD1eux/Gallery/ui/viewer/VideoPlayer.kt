package com.HrshD1eux.Gallery.ui.viewer

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerContainer(
    uri: Uri,
    isSelectedPage: Boolean,
    showChrome: Boolean,
    onTap: () -> Unit,
    rotationDegrees: Float = 0f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var exoPlayer by remember(uri) {
        mutableStateOf<ExoPlayer?>(null)
    }
    var playerViewRef by remember {
        mutableStateOf<PlayerView?>(null)
    }

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
            playerViewRef = null
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

    LaunchedEffect(showChrome) {
        playerViewRef?.let { view ->
            if (showChrome) {
                view.showController()
            } else {
                view.hideController()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap
            )
    ) {
        exoPlayer?.let { player ->
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // Offset Media3 bottom controls container (seek bar & time text) by 96dp bottom margin so it floats above the action bar
                        val bottomBar = findViewById<android.view.View>(androidx.media3.ui.R.id.exo_bottom_bar)
                        bottomBar?.let { bar ->
                            val marginPx = (96 * ctx.resources.displayMetrics.density).toInt()
                            val params = bar.layoutParams as? ViewGroup.MarginLayoutParams
                            params?.let { lp ->
                                lp.bottomMargin = marginPx
                                bar.layoutParams = lp
                            }
                        }

                        if (showChrome) {
                            showController()
                        } else {
                            hideController()
                        }

                        playerViewRef = this
                    }
                },
                update = { view ->
                    view.player = player
                    playerViewRef = view
                },
                onRelease = { view ->
                    view.player = null
                    playerViewRef = null
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = rotationDegrees
                    }
            )
        }
    }
}
