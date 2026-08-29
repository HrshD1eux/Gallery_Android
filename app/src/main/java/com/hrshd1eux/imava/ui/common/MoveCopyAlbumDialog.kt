package com.hrshd1eux.imava.ui.common

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hrshd1eux.imava.data.media.BucketInfo
import java.io.File

@Composable
fun MoveCopyAlbumDialog(
    buckets: List<BucketInfo>,
    isCopy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (targetDirectory: File, isCopy: Boolean) -> Unit
) {
    var showCreateAlbumDialog by remember { mutableStateOf(false) }
    var selectedBucket by remember { mutableStateOf<BucketInfo?>(null) }
    var currentIsCopy by remember { mutableStateOf(isCopy) }

    if (showCreateAlbumDialog) {
        var newAlbumName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateAlbumDialog = false },
            title = { Text("New Album") },
            text = {
                OutlinedTextField(
                    value = newAlbumName,
                    onValueChange = { newAlbumName = it },
                    label = { Text("Album Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newAlbumName.trim()
                        if (name.isNotBlank()) {
                            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                            val newDir = File(picturesDir, name)
                            newDir.mkdirs()
                            showCreateAlbumDialog = false
                            onConfirm(newDir, currentIsCopy)
                        }
                    },
                    enabled = newAlbumName.isNotBlank()
                ) {
                    Text("Create & Select")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateAlbumDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (currentIsCopy) "Copy to Album" else "Move to Album")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { currentIsCopy = false }
                            .padding(end = 16.dp)
                    ) {
                        RadioButton(
                            selected = !currentIsCopy,
                            onClick = { currentIsCopy = false }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Move")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { currentIsCopy = true }
                    ) {
                        RadioButton(
                            selected = currentIsCopy,
                            onClick = { currentIsCopy = true }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCreateAlbumDialog = true }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "+ Create New Album",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) {
                    items(buckets.filter { it.name != "Hidden Vault" && it.name != "Trash" }) { bucket ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedBucket = bucket }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedBucket?.id == bucket.id,
                                onClick = { selectedBucket = bucket }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = bucket.name,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${bucket.count} items",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val bucket = selectedBucket ?: return@Button
                    val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val targetDir = File(picturesDir, bucket.name)
                    onConfirm(targetDir, currentIsCopy)
                },
                enabled = selectedBucket != null
            ) {
                Text(if (currentIsCopy) "Copy" else "Move")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
