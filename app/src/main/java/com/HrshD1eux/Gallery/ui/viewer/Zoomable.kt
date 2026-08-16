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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

@Composable
fun rememberZoomState(
    maxScale: Float = 15f,
    minScale: Float = 1f,
    doubleTapScale: Float = 3f
): ZoomState {
    return remember(maxScale, minScale, doubleTapScale) { ZoomState(maxScale, minScale, doubleTapScale) }
}

class ZoomState(
    val maxScale: Float = 15f,
    val minScale: Float = 1f,
    val doubleTapScale: Float = 3f
) {
    var scale by mutableStateOf(1f)
    var offsetX by mutableStateOf(0f)
    var offsetY by mutableStateOf(0f)
    var layoutSize by mutableStateOf(IntSize.Zero)

    fun updateGesture(zoom: Float, pan: Offset) {
        val newScale = (scale * zoom).coerceIn(minScale, maxScale)
        scale = newScale
        if (newScale > 1f) {
            val boundX = if (layoutSize.width > 0) {
                (layoutSize.width * (newScale - 1f)) / 2f
            } else {
                1500f * (newScale - 1f)
            }
            val boundY = if (layoutSize.height > 0) {
                (layoutSize.height * (newScale - 1f)) / 2f
            } else {
                1500f * (newScale - 1f)
            }
            offsetX = (offsetX + pan.x).coerceIn(-boundX, boundX)
            offsetY = (offsetY + pan.y).coerceIn(-boundY, boundY)
        } else {
            offsetX = 0f
            offsetY = 0f
        }
    }

    fun handleDoubleTap(tapOffset: Offset) {
        if (scale > 1.05f) {
            reset()
        } else {
            val targetScale = doubleTapScale.coerceIn(minScale, maxScale)
            scale = targetScale
            if (layoutSize.width > 0 && layoutSize.height > 0) {
                val centerX = layoutSize.width / 2f
                val centerY = layoutSize.height / 2f
                val boundX = (layoutSize.width * (targetScale - 1f)) / 2f
                val boundY = (layoutSize.height * (targetScale - 1f)) / 2f
                val targetOffsetX = (centerX - tapOffset.x) * (targetScale - 1f)
                val targetOffsetY = (centerY - tapOffset.y) * (targetScale - 1f)
                offsetX = targetOffsetX.coerceIn(-boundX, boundX)
                offsetY = targetOffsetY.coerceIn(-boundY, boundY)
            } else {
                offsetX = 0f
                offsetY = 0f
            }
        }
    }

    fun reset() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    fun canConsumePan(panX: Float): Boolean {
        if (scale <= 1.01f) return false
        val boundX = if (layoutSize.width > 0) {
            (layoutSize.width * (scale - 1f)) / 2f
        } else {
            1500f * (scale - 1f)
        }
        val tolerance = 1.5f
        if (panX > 0 && offsetX >= boundX - tolerance) return false
        if (panX < 0 && offsetX <= -boundX + tolerance) return false
        return true
    }
}

fun Modifier.zoomable(
    state: ZoomState,
    onTap: () -> Unit = {},
    onDoubleTap: (Offset) -> Unit = { state.handleDoubleTap(it) }
): Modifier = this
    .onSizeChanged { state.layoutSize = it }
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
                
                // If zoomed in (scale > 1.01f) or user is actively pinching (2+ fingers)
                if (state.scale > 1.01f || count > 1) {
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    
                    val isPinching = count > 1 && kotlin.math.abs(zoom - 1f) > 0.001f
                    val isVerticalDominant = kotlin.math.abs(pan.y) > kotlin.math.abs(pan.x) * 1.2f
                    val canConsumeHorizontal = state.canConsumePan(pan.x)
                    
                    val shouldConsume = isPinching || (state.scale > 1.01f && (canConsumeHorizontal || isVerticalDominant))

                    if (shouldConsume) {
                        event.changes.forEach { change ->
                            if (change.positionChanged()) {
                                change.consume()
                            }
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
