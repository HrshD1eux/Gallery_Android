package com.hrshd1eux.imava.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Calendar

@Composable
fun ExifTimeShiftDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (offsetMillis: Long, exactTimestamp: Long?) -> Unit
) {
    var mode by remember { mutableIntStateOf(0) } // 0 = Relative Shift, 1 = Exact Date

    // Relative Shift State
    var isForward by remember { mutableStateOf(true) }
    var daysText by remember { mutableStateOf("0") }
    var hoursText by remember { mutableStateOf("1") }
    var minutesText by remember { mutableStateOf("0") }

    val days = daysText.toLongOrNull() ?: 0L
    val hours = hoursText.toLongOrNull() ?: 0L
    val minutes = minutesText.toLongOrNull() ?: 0L
    val totalOffsetMs = (days * 86400000L + hours * 3600000L + minutes * 60000L) * (if (isForward) 1L else -1L)

    // Exact Date State
    val now = remember { Calendar.getInstance() }
    var yearText by remember { mutableStateOf(now.get(Calendar.YEAR).toString()) }
    var monthText by remember { mutableStateOf((now.get(Calendar.MONTH) + 1).toString()) }
    var dayText by remember { mutableStateOf(now.get(Calendar.DAY_OF_MONTH).toString()) }
    var exactHourText by remember { mutableStateOf(now.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')) }
    var exactMinuteText by remember { mutableStateOf(now.get(Calendar.MINUTE).toString().padStart(2, '0')) }

    val parsedExactTimestamp = remember(yearText, monthText, dayText, exactHourText, exactMinuteText) {
        val y = yearText.toIntOrNull()
        val m = monthText.toIntOrNull()
        val d = dayText.toIntOrNull()
        val h = exactHourText.toIntOrNull()
        val min = exactMinuteText.toIntOrNull()

        if (y != null && m != null && d != null && h != null && min != null &&
            m in 1..12 && d in 1..31 && h in 0..23 && min in 0..59) {
            Calendar.getInstance().apply {
                set(Calendar.YEAR, y)
                set(Calendar.MONTH, m - 1)
                set(Calendar.DAY_OF_MONTH, d)
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, min)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } else {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Date & Time") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
            ) {
                Text(
                    text = "Update timestamps for $selectedCount selected items.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = mode == 0,
                        onClick = { mode = 0 },
                        label = { Text("Shift Relative") }
                    )
                    FilterChip(
                        selected = mode == 1,
                        onClick = { mode = 1 },
                        label = { Text("Set Exact Date") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (mode == 0) {
                    // Relative shift mode
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

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = daysText,
                            onValueChange = { if (it.all { c -> c.isDigit() }) daysText = it },
                            label = { Text("Days") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = hoursText,
                            onValueChange = { if (it.all { c -> c.isDigit() }) hoursText = it },
                            label = { Text("Hours") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = minutesText,
                            onValueChange = { if (it.all { c -> c.isDigit() }) minutesText = it },
                            label = { Text("Mins") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Offset: ${if (isForward) "+" else "-"}${days}d ${hours}h ${minutes}m",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    // Exact date mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = yearText,
                            onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 4) yearText = it },
                            label = { Text("Year") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1.3f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = monthText,
                            onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 2) monthText = it },
                            label = { Text("Month") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = dayText,
                            onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 2) dayText = it },
                            label = { Text("Day") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = exactHourText,
                            onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 2) exactHourText = it },
                            label = { Text("Hour (0-23)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = exactMinuteText,
                            onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 2) exactMinuteText = it },
                            label = { Text("Minute (0-59)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (mode == 0) {
                        onConfirm(totalOffsetMs, null)
                    } else if (parsedExactTimestamp != null) {
                        onConfirm(0L, parsedExactTimestamp)
                    }
                },
                enabled = if (mode == 0) totalOffsetMs != 0L else parsedExactTimestamp != null
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
