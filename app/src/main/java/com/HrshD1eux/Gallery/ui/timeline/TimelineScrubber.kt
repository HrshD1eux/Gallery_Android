package com.HrshD1eux.Gallery.ui.timeline

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.HrshD1eux.Gallery.data.model.TimelineItem

@Composable
fun TimelineScrubber(
    gridState: LazyStaggeredGridState,
    flatList: List<TimelineItem>,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    // Extract headers and their corresponding positions in the flat list
    val headers = remember(flatList) {
        flatList.mapIndexedNotNull { index, item ->
            if (item is TimelineItem.Header) index to item.title else null
        }
    }

    if (headers.isEmpty()) return

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(40.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(headers) {
                    fun scrollToY(y: Float) {
                        if (size.height <= 0) return
                        val fraction = (y / size.height).coerceIn(0f, 1f)
                        val index = (fraction * (headers.size - 1)).toInt().coerceIn(0, headers.lastIndex)
                        val targetGridIndex = headers[index].first
                        coroutineScope.launch {
                            // Instant fast scroll to corresponding layout index
                            gridState.scrollToItem(targetGridIndex)
                        }
                    }

                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        scrollToY(change.position.y)
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Render scrubber markers
            headers.forEach { (_, title) ->
                // Use first 3 letters for month headers, or 'T'/'Y' for Today/Yesterday
                val displayLabel = if (title == "Today") "TD" else if (title == "Yesterday") "YS" else title.take(3).uppercase()
                Text(
                    text = displayLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}
