package com.hrshd1eux.imava.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hrshd1eux.imava.data.model.MediaItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RenameMode {
    NUMBERED,
    DATE_PREFIX,
    FIND_REPLACE
}

@Composable
fun BatchRenameDialog(
    selectedItems: List<MediaItem>,
    onDismiss: () -> Unit,
    onConfirmRename: (List<Pair<MediaItem, String>>) -> Unit
) {
    var mode by remember { mutableStateOf(RenameMode.NUMBERED) }
    var prefix by remember { mutableStateOf("Photo_") }
    var startNumber by remember { mutableStateOf("1") }
    var findText by remember { mutableStateOf("IMG_") }
    var replaceText by remember { mutableStateOf("Trip_") }

    fun generateNewName(item: MediaItem, index: Int): String {
        val file = File(item.path)
        val nameWithoutExt = file.nameWithoutExtension
        val ext = if (file.extension.isNotEmpty()) ".${file.extension}" else ""

        return when (mode) {
            RenameMode.NUMBERED -> {
                val start = startNumber.toIntOrNull() ?: 1
                val num = start + index
                val formattedNum = String.format(Locale.US, "%03d", num)
                "$prefix$formattedNum$ext"
            }
            RenameMode.DATE_PREFIX -> {
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(if (item.dateTaken > 0) item.dateTaken else System.currentTimeMillis()))
                "${dateStr}_$nameWithoutExt$ext"
            }
            RenameMode.FIND_REPLACE -> {
                val newBase = if (findText.isNotEmpty()) nameWithoutExt.replace(findText, replaceText) else nameWithoutExt
                "$newBase$ext"
            }
        }
    }

    val plannedRenames = remember(mode, prefix, startNumber, findText, replaceText, selectedItems) {
        selectedItems.mapIndexed { index, item ->
            Pair(item, generateNewName(item, index))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.DriveFileRenameOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Batch Rename (${selectedItems.size} items)")
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Mode Selector Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = mode == RenameMode.NUMBERED,
                        onClick = { mode = RenameMode.NUMBERED },
                        label = { Text("Numbered") }
                    )
                    FilterChip(
                        selected = mode == RenameMode.DATE_PREFIX,
                        onClick = { mode = RenameMode.DATE_PREFIX },
                        label = { Text("Date") }
                    )
                    FilterChip(
                        selected = mode == RenameMode.FIND_REPLACE,
                        onClick = { mode = RenameMode.FIND_REPLACE },
                        label = { Text("Replace") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (mode) {
                    RenameMode.NUMBERED -> {
                        OutlinedTextField(
                            value = prefix,
                            onValueChange = { prefix = it },
                            label = { Text("Prefix (e.g. Vacation_)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = startNumber,
                            onValueChange = { startNumber = it.filter { char -> char.isDigit() } },
                            label = { Text("Starting Number") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    RenameMode.DATE_PREFIX -> {
                        Text(
                            text = "Prepends 'YYYY-MM-DD_' to each file's original name based on capture date.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    RenameMode.FIND_REPLACE -> {
                        OutlinedTextField(
                            value = findText,
                            onValueChange = { findText = it },
                            label = { Text("Find text") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = replaceText,
                            onValueChange = { replaceText = it },
                            label = { Text("Replace with") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Live Preview Box
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Preview:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        plannedRenames.take(3).forEach { (item, newName) ->
                            val oldName = File(item.path).name
                            Text(
                                text = "$oldName  ➔  $newName",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }
                        if (plannedRenames.size > 3) {
                            Text(
                                text = "... and ${plannedRenames.size - 3} more",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmRename(plannedRenames) }
            ) {
                Text("Rename All")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
