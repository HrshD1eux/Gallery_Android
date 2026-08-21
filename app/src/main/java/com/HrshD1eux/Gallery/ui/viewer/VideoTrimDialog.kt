package com.HrshD1eux.Gallery.ui.viewer

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.HrshD1eux.Gallery.core.util.HapticUtil
import com.HrshD1eux.Gallery.core.util.VideoTrimmer
import com.HrshD1eux.Gallery.data.model.MediaItem
import kotlinx.coroutines.launch
import java.util.Locale

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults

@Composable
fun VideoTrimDialog(
    mediaItem: MediaItem.Video,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val totalDurationMs = (mediaItem.durationMs).coerceAtLeast(1000L)
    var sliderPosition by remember { mutableStateOf(0f..totalDurationMs.toFloat()) }
    var exportAsGif by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    val startMs = sliderPosition.start.toLong()
    val endMs = sliderPosition.endInclusive.toLong()
    val selectedDurationMs = (endMs - startMs).coerceAtLeast(0L)

    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        val millis = (ms % 1000) / 100
        return String.format(Locale.getDefault(), "%02d:%02d.%d", min, sec, millis)
    }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.ContentCut,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = { Text(text = "Trim Video / Create GIF") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = mediaItem.path.substringAfterLast('/'),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mode Chips in a horizontal scrollable row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !exportAsGif,
                        onClick = { exportAsGif = false },
                        label = { Text("Trim Video (.mp4)") },
                        leadingIcon = { Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = exportAsGif,
                        onClick = { exportAsGif = true },
                        label = { Text("Make GIF (.gif)") },
                        leadingIcon = { Icon(Icons.Default.Gif, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Time Range Display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Start: ${formatDuration(startMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Length: ${formatDuration(selectedDurationMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "End: ${formatDuration(endMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                RangeSlider(
                    value = sliderPosition,
                    onValueChange = { range ->
                        sliderPosition = range
                    },
                    valueRange = 0f..totalDurationMs.toFloat(),
                    enabled = !isProcessing
                )

                if (isProcessing) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (exportAsGif) "Generating GIF..." else "Trimming video...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isProcessing && selectedDurationMs >= 500L,
                onClick = {
                    isProcessing = true
                    scope.launch {
                        val resultUri = if (exportAsGif) {
                            VideoTrimmer.convertVideoToGif(context, mediaItem.uri, startMs, endMs)
                        } else {
                            VideoTrimmer.trimVideo(context, mediaItem.uri, startMs, endMs)
                        }

                        isProcessing = false
                        if (resultUri != null) {
                            HapticUtil.performSuccess(context)
                            Toast.makeText(
                                context,
                                if (exportAsGif) "Saved to Pictures/GIFs" else "Saved to Movies/Trimmed",
                                Toast.LENGTH_LONG
                            ).show()
                            onSuccess()
                            onDismiss()
                        } else {
                            HapticUtil.performError(context)
                            Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            ) {
                Text(if (exportAsGif) "Export GIF" else "Trim & Save")
            }
        },
        dismissButton = {
            if (!isProcessing) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
