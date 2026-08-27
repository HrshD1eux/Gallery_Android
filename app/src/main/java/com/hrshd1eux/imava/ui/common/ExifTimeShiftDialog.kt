package com.hrshd1eux.imava.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun ExifTimeShiftDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (offsetMillis: Long) -> Unit
) {
    var isForward by remember { mutableStateOf(true) }
    var daysText by remember { mutableStateOf("0") }
    var hoursText by remember { mutableStateOf("1") }
    var minutesText by remember { mutableStateOf("0") }

    val days = daysText.toLongOrNull() ?: 0L
    val hours = hoursText.toLongOrNull() ?: 0L
    val minutes = minutesText.toLongOrNull() ?: 0L

    val totalOffsetMs = (days * 86400000L + hours * 3600000L + minutes * 60000L) * (if (isForward) 1L else -1L)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Shift Date & Time") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Adjust capture timestamps for $selectedCount selected items.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { isForward = true }
                            .padding(end = 16.dp)
                    ) {
                        RadioButton(selected = isForward, onClick = { isForward = true })
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Forward (+)")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { isForward = false }
                    ) {
                        RadioButton(selected = !isForward, onClick = { isForward = false })
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Backward (-)")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = daysText,
                        onValueChange = { if (it.all { char -> char.isDigit() }) daysText = it },
                        label = { Text("Days") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = hoursText,
                        onValueChange = { if (it.all { char -> char.isDigit() }) hoursText = it },
                        label = { Text("Hours") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { if (it.all { char -> char.isDigit() }) minutesText = it },
                        label = { Text("Mins") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Shift: ${if (isForward) "+" else "-"}${days}d ${hours}h ${minutes}m",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(totalOffsetMs) },
                enabled = totalOffsetMs != 0L
            ) {
                Text("Apply Shift")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
