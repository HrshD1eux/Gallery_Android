package com.hrshd1eux.imava.ui.viewer

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hrshd1eux.imava.core.util.HapticUtil
import com.hrshd1eux.imava.core.util.MotionPhotoUtil
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun MotionPhotoExportDialog(
    videoFile: File,
    baseName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var durationUs by remember { mutableLongStateOf(2_000_000L) }
    var currentSliderPos by remember { mutableFloatStateOf(0f) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var processingMsg by remember { mutableStateOf("") }

    LaunchedEffect(videoFile) {
        val dur = MotionPhotoUtil.getVideoDurationUs(videoFile)
        if (dur > 0L) {
            durationUs = dur
        }
        val initialFrame = MotionPhotoUtil.extractFrameAt(videoFile, 0L)
        previewBitmap = initialFrame
    }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = {
            Text("Motion Photo Tools 🎞️")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Frame Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = "Frame preview",
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }

                    if (isProcessing) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = processingMsg,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrubber slider
                Text(
                    text = "Scrub Frame: ${(currentSliderPos * (durationUs / 1_000_000f)).let { String.format("%.2fs", it) }}",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = currentSliderPos,
                    onValueChange = { pos ->
                        currentSliderPos = pos
                        val targetUs = (pos * durationUs).toLong()
                        scope.launch {
                            val frame = MotionPhotoUtil.extractFrameAt(videoFile, targetUs)
                            if (frame != null) {
                                previewBitmap = frame
                            }
                        }
                    },
                    enabled = !isProcessing,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Actions
                Button(
                    onClick = {
                        val bmp = previewBitmap ?: return@Button
                        isProcessing = true
                        processingMsg = "Saving still frame..."
                        scope.launch {
                            val uri = MotionPhotoUtil.saveExtractedFrame(context, bmp, baseName)
                            isProcessing = false
                            if (uri != null) {
                                HapticUtil.performSuccess(context)
                                Toast.makeText(context, "Saved frame to Pictures! 📸", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                Toast.makeText(context, "Failed to save frame", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isProcessing && previewBitmap != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Frame as Photo")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            isProcessing = true
                            processingMsg = "Encoding animated GIF..."
                            scope.launch {
                                val uri = MotionPhotoUtil.exportMotionToGif(context, videoFile, baseName)
                                isProcessing = false
                                if (uri != null) {
                                    HapticUtil.performSuccess(context)
                                    Toast.makeText(context, "Exported as GIF! 🎞️", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, "Failed to export GIF", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isProcessing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Gif, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export GIF")
                    }

                    FilledTonalButton(
                        onClick = {
                            isProcessing = true
                            processingMsg = "Exporting MP4 video..."
                            scope.launch {
                                val uri = MotionPhotoUtil.exportMotionVideo(context, videoFile, baseName)
                                isProcessing = false
                                if (uri != null) {
                                    HapticUtil.performSuccess(context)
                                    Toast.makeText(context, "Saved video clip to Movies! 🎬", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, "Failed to export video", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isProcessing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Movie, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export MP4")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isProcessing
            ) {
                Text("Close")
            }
        }
    )
}
