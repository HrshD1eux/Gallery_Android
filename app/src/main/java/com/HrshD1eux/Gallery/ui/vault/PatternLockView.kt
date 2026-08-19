package com.HrshD1eux.Gallery.ui.vault

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

@Composable
fun PatternLockView(
    onPatternComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    errorColor: Color = MaterialTheme.colorScheme.error,
    dotColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val selectedDots = remember { mutableStateListOf<Int>() }
    var currentTouchPosition by remember { mutableStateOf<Offset?>(null) }

    val activeColor = if (isError) errorColor else lineColor

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(16.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isError) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            selectedDots.clear()
                            currentTouchPosition = offset
                            val dotIndex = getTouchedDotIndex(offset, size.width.toFloat(), size.height.toFloat())
                            if (dotIndex != -1 && !selectedDots.contains(dotIndex)) {
                                selectedDots.add(dotIndex)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val offset = change.position
                            currentTouchPosition = offset
                            val dotIndex = getTouchedDotIndex(offset, size.width.toFloat(), size.height.toFloat())
                            if (dotIndex != -1 && !selectedDots.contains(dotIndex)) {
                                selectedDots.add(dotIndex)
                            }
                        },
                        onDragEnd = {
                            currentTouchPosition = null
                            if (selectedDots.isNotEmpty()) {
                                val patternString = selectedDots.joinToString("-")
                                onPatternComplete(patternString)
                            }
                        },
                        onDragCancel = {
                            currentTouchPosition = null
                            selectedDots.clear()
                        }
                    )
                }
        ) {
            val width = size.width
            val height = size.height
            val radius = 16.dp.toPx()
            val strokeWidth = 8.dp.toPx()

            val dotCenters = List(9) { index ->
                val col = index % 3
                val row = index / 3
                Offset(
                    x = (col * 2 + 1) * width / 6f,
                    y = (row * 2 + 1) * height / 6f
                )
            }

            // Draw connecting lines
            if (selectedDots.size > 1) {
                for (i in 0 until selectedDots.size - 1) {
                    val p1 = dotCenters[selectedDots[i]]
                    val p2 = dotCenters[selectedDots[i + 1]]
                    drawLine(
                        color = activeColor,
                        start = p1,
                        end = p2,
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Draw line to active touch position
            currentTouchPosition?.let { touchPos ->
                if (selectedDots.isNotEmpty()) {
                    val lastDot = dotCenters[selectedDots.last()]
                    drawLine(
                        color = activeColor.copy(alpha = 0.5f),
                        start = lastDot,
                        end = touchPos,
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Draw 3x3 Dots
            dotCenters.forEachIndexed { index, center ->
                val isSelected = selectedDots.contains(index)
                if (isSelected) {
                    drawCircle(
                        color = activeColor.copy(alpha = 0.2f),
                        radius = radius * 1.8f,
                        center = center
                    )
                    drawCircle(
                        color = activeColor,
                        radius = radius,
                        center = center
                    )
                } else {
                    drawCircle(
                        color = dotColor,
                        radius = radius * 0.5f,
                        center = center
                    )
                }
            }
        }
    }
}

private fun getTouchedDotIndex(offset: Offset, width: Float, height: Float): Int {
    val hitRadius = width / 6f
    for (i in 0 until 9) {
        val col = i % 3
        val row = i / 3
        val centerX = (col * 2 + 1) * width / 6f
        val centerY = (row * 2 + 1) * height / 6f
        val dist = hypot(offset.x - centerX, offset.y - centerY)
        if (dist <= hitRadius) {
            return i
        }
    }
    return -1
}
