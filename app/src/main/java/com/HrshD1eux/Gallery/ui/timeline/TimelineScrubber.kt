package com.HrshD1eux.Gallery.ui.timeline

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.HrshD1eux.Gallery.data.repository.DatePositionHeader
import kotlinx.coroutines.launch

@Composable
fun TimelineScrubber(
    gridState: LazyStaggeredGridState,
    headers: List<DatePositionHeader>,
    totalItemCount: Int,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    if (headers.isEmpty()) return

    val displayHeaders = remember(headers) {
        if (headers.size <= 6) {
            headers
        } else {
            val step = (headers.size - 1) / 5f
            (0..5).map { i ->
                val index = (i * step).toInt().coerceIn(0, headers.lastIndex)
                headers[index]
            }.distinctBy { it.title }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(40.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(headers, totalItemCount) {
                    fun scrollToY(y: Float) {
                        if (totalItemCount <= 0) return
                        val totalHeight = size.height.toFloat()
                        if (totalHeight <= 0f) return
                        val fraction = (y / totalHeight).coerceIn(0f, 1f)
                        val index = (fraction * (headers.size - 1)).toInt().coerceIn(0, headers.lastIndex)
                        val rawTargetIndex = headers[index].positionIndex
                        val targetGridIndex = rawTargetIndex.coerceIn(0, (totalItemCount - 1).coerceAtLeast(0))
                        
                        coroutineScope.launch {
                            try {
                                gridState.scrollToItem(targetGridIndex)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    detectTapGestures { offset ->
                        scrollToY(offset.y)
                    }
                }
                .pointerInput(headers, totalItemCount) {
                    fun scrollToY(y: Float) {
                        if (totalItemCount <= 0) return
                        val totalHeight = size.height.toFloat()
                        if (totalHeight <= 0f) return
                        val fraction = (y / totalHeight).coerceIn(0f, 1f)
                        val index = (fraction * (headers.size - 1)).toInt().coerceIn(0, headers.lastIndex)
                        val rawTargetIndex = headers[index].positionIndex
                        val targetGridIndex = rawTargetIndex.coerceIn(0, (totalItemCount - 1).coerceAtLeast(0))
                        
                        coroutineScope.launch {
                            try {
                                gridState.scrollToItem(targetGridIndex)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        scrollToY(change.position.y)
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            displayHeaders.forEach { header ->
                val title = header.title
                val displayLabel = if (title == "Today") "TD" else if (title == "Yesterday") "YS" else title.take(3).uppercase()
                Text(
                    text = displayLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
