package com.hrshd1eux.imava.ui.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hrshd1eux.imava.core.util.HapticUtil

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OcrCopyBottomSheet(
    recognizedText: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var textFieldValue by remember(recognizedText) {
        mutableStateOf(TextFieldValue(recognizedText))
    }

    val safeMin = textFieldValue.selection.min.coerceIn(0, textFieldValue.text.length)
    val safeMax = textFieldValue.selection.max.coerceIn(0, textFieldValue.text.length)
    val hasSelection = !textFieldValue.selection.collapsed && safeMin < safeMax
    val selectedText = if (hasSelection) textFieldValue.text.substring(safeMin, safeMax) else ""

    val detectedLines = remember(recognizedText) {
        recognizedText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Recognized Text 📝",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = if (hasSelection) "${selectedText.length} characters selected" else "Select specific text or copy all",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasSelection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Primary Copy button: adapts to selection
                Button(
                    onClick = {
                        val textToCopy = if (hasSelection) selectedText else textFieldValue.text
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Recognized Text", textToCopy)
                        clipboard.setPrimaryClip(clip)
                        HapticUtil.performSuccess(context)
                        val message = if (hasSelection) "Copied selected text! 📋" else "Copied all text! 📋"
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1.3f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (hasSelection) "Copy Selected" else "Copy All")
                }

                // Secondary toggle: Select All / Copy All
                FilledTonalButton(
                    onClick = {
                        if (hasSelection) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Recognized Text", textFieldValue.text)
                            clipboard.setPrimaryClip(clip)
                            HapticUtil.performSuccess(context)
                            Toast.makeText(context, "Copied all text! 📋", Toast.LENGTH_SHORT).show()
                        } else {
                            textFieldValue = textFieldValue.copy(
                                selection = TextRange(0, textFieldValue.text.length)
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (hasSelection) Icons.Default.ContentCopy else Icons.Default.SelectAll,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (hasSelection) "Copy All" else "Select All")
                }

                // Share button
                FilledTonalButton(
                    onClick = {
                        val textToShare = if (hasSelection) selectedText else textFieldValue.text
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_TEXT, textToShare)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, if (hasSelection) "Share Selected Text" else "Share Text"))
                    },
                    modifier = Modifier.weight(0.9f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Text Field with native cursor selection handles
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 240.dp),
                label = {
                    Text(if (hasSelection) "Selected: \"${selectedText.take(25)}${if (selectedText.length > 25) "..." else ""}\"" else "Tap and drag handles to select text")
                },
                supportingText = {
                    Text("💡 Double tap words or drag blue handles to select specific text to copy")
                },
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(12.dp)
            )

            // Detected Lines Quick-Select Chips (if multiple lines exist)
            if (detectedLines.size > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Detected Lines (tap to select & copy):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    detectedLines.forEach { line ->
                        val isLineSelected = hasSelection && selectedText == line
                        FilterChip(
                            selected = isLineSelected,
                            onClick = {
                                val idx = textFieldValue.text.indexOf(line)
                                if (idx >= 0) {
                                    textFieldValue = textFieldValue.copy(
                                        selection = TextRange(idx, idx + line.length)
                                    )
                                }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Recognized Text", line)
                                clipboard.setPrimaryClip(clip)
                                HapticUtil.performSuccess(context)
                                Toast.makeText(context, "Selected & copied line! 📋", Toast.LENGTH_SHORT).show()
                            },
                            label = {
                                Text(line, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
