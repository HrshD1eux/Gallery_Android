package com.HrshD1eux.Gallery.ui.viewer

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun rememberZoomState(
    maxScale: Float = 5f,
    minScale: Float = 1f
): ZoomState {
    return remember { ZoomState(maxScale, minScale) }
}

class ZoomState(
    val maxScale: Float,
    val minScale: Float
) {
    var scale by mutableStateOf(1f)
    var offsetX by mutableStateOf(0f)
    var offsetY by mutableStateOf(0f)

    fun updateGesture(zoom: Float, pan: Offset) {
        scale = (scale * zoom).coerceIn(minScale, maxScale)
        if (scale > 1f) {
            // Constrain visual panning offsets based on zoom scale factor
            val maxOffset = 500f * (scale - 1f)
            offsetX = (offsetX + pan.x).coerceIn(-maxOffset, maxOffset)
            offsetY = (offsetY + pan.y).coerceIn(-maxOffset, maxOffset)
        } else {
            offsetX = 0f
            offsetY = 0f
        }
    }

    fun handleDoubleTap(@Suppress("UNUSED_PARAMETER") tapOffset: Offset) {
        if (scale > 1f) {
            reset()
        } else {
            scale = 2.5f
            offsetX = 0f
            offsetY = 0f
        }
    }

    fun reset() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }
}

fun Modifier.zoomable(
    state: ZoomState,
    onTap: () -> Unit = {},
    onDoubleTap: (Offset) -> Unit = { state.handleDoubleTap(it) }
): Modifier = this
    .pointerInput(state) {
        detectTapGestures(
            onDoubleTap = onDoubleTap,
            onTap = { onTap() }
        )
    }
    .pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val count = event.changes.size
                
                // If zoomed in (scale > 1) or user is actively pinching (2+ fingers)
                if (state.scale > 1f || count > 1) {
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    
                    // Mark as consumed so parents don't intercept it
                    event.changes.forEach { change ->
                        if (change.positionChanged()) {
                            change.consume()
                        }
                    }
                    
                    state.updateGesture(zoom, pan)
                }
            }
        }
    }
    .graphicsLayer(
        scaleX = state.scale,
        scaleY = state.scale,
        translationX = state.offsetX,
        translationY = state.offsetY
    )
