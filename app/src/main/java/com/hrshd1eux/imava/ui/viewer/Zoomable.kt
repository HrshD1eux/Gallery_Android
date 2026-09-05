package com.hrshd1eux.imava.ui.viewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun rememberZoomState(
    maxScale: Float = 15f,
    minScale: Float = 1f,
    doubleTapScale: Float = 3f
): ZoomState {
    val scope = rememberCoroutineScope()
    return remember(maxScale, minScale, doubleTapScale) {
        ZoomState(scope, maxScale, minScale, doubleTapScale)
    }
}

class ZoomState(
    private val scope: CoroutineScope? = null,
    val maxScale: Float = 15f,
    val minScale: Float = 1f,
    val doubleTapScale: Float = 3f
) {
    var scale by mutableStateOf(1f)
    var offsetX by mutableStateOf(0f)
    var offsetY by mutableStateOf(0f)
    var layoutSize by mutableStateOf(IntSize.Zero)

    private var animationJob: Job? = null

    fun updateGesture(zoom: Float, pan: Offset) {
        animationJob?.cancel()
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
            animateTo(1f, 0f, 0f)
        } else {
            val targetScale = doubleTapScale.coerceIn(minScale, maxScale)
            val (targetOffsetX, targetOffsetY) = if (layoutSize.width > 0 && layoutSize.height > 0) {
                val centerX = layoutSize.width / 2f
                val centerY = layoutSize.height / 2f
                val boundX = (layoutSize.width * (targetScale - 1f)) / 2f
                val boundY = (layoutSize.height * (targetScale - 1f)) / 2f
                val tX = ((centerX - tapOffset.x) * (targetScale - 1f)).coerceIn(-boundX, boundX)
                val tY = ((centerY - tapOffset.y) * (targetScale - 1f)).coerceIn(-boundY, boundY)
                Pair(tX, tY)
            } else {
                Pair(0f, 0f)
            }
            animateTo(targetScale, targetOffsetX, targetOffsetY)
        }
    }

    fun animateTo(targetScale: Float, targetOffsetX: Float, targetOffsetY: Float) {
        animationJob?.cancel()
        val currentScope = scope
        if (currentScope != null) {
            animationJob = currentScope.launch {
                val initialScale = scale
                val initialX = offsetX
                val initialY = offsetY
                val animatable = Animatable(0f)
                animatable.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                ) {
                    scale = initialScale + (targetScale - initialScale) * value
                    offsetX = initialX + (targetOffsetX - initialX) * value
                    offsetY = initialY + (targetOffsetY - initialY) * value
                }
            }
        } else {
            scale = targetScale
            offsetX = targetOffsetX
            offsetY = targetOffsetY
        }
    }

    fun reset() {
        animationJob?.cancel()
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
        val tolerance = 2f
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
                val pressedPointers = event.changes.filter { it.pressed }

                if (pressedPointers.isEmpty()) {
                    if (state.scale < state.minScale) {
                        state.animateTo(state.minScale, 0f, 0f)
                    } else if (state.scale > state.maxScale) {
                        state.animateTo(state.maxScale, state.offsetX, state.offsetY)
                    }
                } else if (state.scale > 1.01f || count > 1) {
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
                // When count == 1 and scale <= 1.01f: do not consume, allow HorizontalPager and vertical drag dismiss full priority!
            }
        }
    }
    .graphicsLayer {
        scaleX = state.scale
        scaleY = state.scale
        translationX = state.offsetX
        translationY = state.offsetY
    }
