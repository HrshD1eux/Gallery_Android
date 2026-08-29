package com.hrshd1eux.imava.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.text.style.TextOverflow
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

@OptIn(ExperimentalLayoutApi::class)
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

    val plannedRenames = selectedItems.mapIndexed { index, item ->
        Pair(item, generateNewName(item, index))
    }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.DriveFileRenameOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Batch Rename (${selectedItems.size} items)",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .imePadding()
            ) {

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = mode == RenameMode.NUMBERED,
                        onClick = { mode = RenameMode.NUMBERED },
                        label = { Text("Numbered") }
                    )
                    FilterChip(
                        selected = mode == RenameMode.DATE_PREFIX,
                        onClick = { mode = RenameMode.DATE_PREFIX },
                        label = { Text("Date Prefix") }
                    )
                    FilterChip(
                        selected = mode == RenameMode.FIND_REPLACE,
                        onClick = { mode = RenameMode.FIND_REPLACE },
                        label = { Text("Find & Replace") }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                when (mode) {
                    RenameMode.NUMBERED -> {
                        OutlinedTextField(
                            value = prefix,
                            onValueChange = { prefix = it },
                            label = { Text("Prefix") },
                            placeholder = { Text("e.g. Vacation_") },
                            singleLine = true,
                            trailingIcon = {
                                if (prefix.isNotEmpty()) {
                                    IconButton(onClick = { prefix = "" }) {
                                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))


                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("Photo_", "Trip_", "Memory_", "Event_").forEach { sample ->
                                SuggestionChip(
                                    onClick = { prefix = sample },
                                    label = { Text(sample) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = startNumber,
                            onValueChange = { startNumber = it.filter { char -> char.isDigit() } },
                            label = { Text("Start Counter") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    RenameMode.DATE_PREFIX -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Prepends capture date 'YYYY-MM-DD_' to each file's original name.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    RenameMode.FIND_REPLACE -> {
                        OutlinedTextField(
                            value = findText,
                            onValueChange = { findText = it },
                            label = { Text("Find text") },
                            singleLine = true,
                            trailingIcon = {
                                if (findText.isNotEmpty()) {
                                    IconButton(onClick = { findText = "" }) {
                                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = replaceText,
                            onValueChange = { replaceText = it },
                            label = { Text("Replace with") },
                            singleLine = true,
                            trailingIcon = {
                                if (replaceText.isNotEmpty()) {
                                    IconButton(onClick = { replaceText = "" }) {
                                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))


                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚡ Live Preview",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${selectedItems.size} items",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        plannedRenames.take(4).forEach { (item, newName) ->
                            val oldName = File(item.path).name
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                    Text(
                                        text = oldName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "➔ ",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = newName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        if (plannedRenames.size > 4) {
                            Text(
                                text = "... and ${plannedRenames.size - 4} more files",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
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
                Text("Rename All (${selectedItems.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
