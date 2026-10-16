package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimeSelectionDialog(
    initialStartHour: Int,
    initialStartMinute: Int,
    initialEndHour: Int,
    initialEndMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) -> Unit
) {
    var startHour by remember { mutableIntStateOf(initialStartHour.coerceIn(0, 23)) }
    var startMinute by remember { mutableIntStateOf(initialStartMinute.coerceIn(0, 59)) }
    var endHour by remember { mutableIntStateOf(initialEndHour.coerceIn(0, 23)) }
    var endMinute by remember { mutableIntStateOf(initialEndMinute.coerceIn(0, 59)) }

    // Calculate duration in hours and minutes
    val startTotalMinutes = startHour * 60 + startMinute
    val endTotalMinutes = endHour * 60 + endMinute
    val durationMinutes = if (endTotalMinutes > startTotalMinutes) {
        endTotalMinutes - startTotalMinutes
    } else if (endTotalMinutes < startTotalMinutes) {
        (24 * 60 - startTotalMinutes) + endTotalMinutes
    } else {
        24 * 60 // full 24h
    }
    val durHours = durationMinutes / 60
    val durMins = durationMinutes % 60

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select Time Window", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Window display banner
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "${formatTime(startHour, startMinute)}  ➔  ${formatTime(endHour, endMinute)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Window span: ${durHours}h ${if (durMins > 0) "${durMins}m" else ""}" +
                                    if (endTotalMinutes <= startTotalMinutes && durationMinutes != 24 * 60) " (crosses midnight)" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // 1. START TIME PICKER
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Start Time", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text(
                                text = formatTime(startHour, startMinute),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Stepper for hour
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { startHour = if (startHour > 0) startHour - 1 else 23 },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Minus 1 hour")
                            }
                            Slider(
                                value = startHour.toFloat(),
                                onValueChange = { startHour = it.toInt() },
                                valueRange = 0f..23f,
                                steps = 22,
                                modifier = Modifier.weight(1f).testTag("start_hour_slider")
                            )
                            IconButton(
                                onClick = { startHour = if (startHour < 23) startHour + 1 else 0 },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Plus 1 hour")
                            }
                        }

                        // Minute options
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(0, 15, 30, 45).forEach { min ->
                                FilterChip(
                                    selected = startMinute == min,
                                    onClick = { startMinute = min },
                                    label = { Text(String.format(":%02d", min)) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 2. END TIME PICKER
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("End Time", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text(
                                text = formatTime(endHour, endMinute),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Stepper for hour
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { endHour = if (endHour > 0) endHour - 1 else 23 },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Minus 1 hour")
                            }
                            Slider(
                                value = endHour.toFloat(),
                                onValueChange = { endHour = it.toInt() },
                                valueRange = 0f..23f,
                                steps = 22,
                                modifier = Modifier.weight(1f).testTag("end_hour_slider")
                            )
                            IconButton(
                                onClick = { endHour = if (endHour < 23) endHour + 1 else 0 },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Plus 1 hour")
                            }
                        }

                        // Minute options
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(0, 15, 30, 45).forEach { min ->
                                FilterChip(
                                    selected = endMinute == min,
                                    onClick = { endMinute = min },
                                    label = { Text(String.format(":%02d", min)) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 3. QUICK PRESETS (Includes 5 AM - 8 AM as requested)
                Text("Quick Presets:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = startHour == 5 && endHour == 8 && startMinute == 0 && endMinute == 0,
                        onClick = {
                            startHour = 5; startMinute = 0; endHour = 8; endMinute = 0
                        },
                        label = { Text("5 AM – 8 AM (Early)") },
                        modifier = Modifier.testTag("preset_5am_8am")
                    )
                    FilterChip(
                        selected = startHour == 8 && endHour == 12 && startMinute == 0 && endMinute == 0,
                        onClick = {
                            startHour = 8; startMinute = 0; endHour = 12; endMinute = 0
                        },
                        label = { Text("8 AM – 12 PM") }
                    )
                    FilterChip(
                        selected = startHour == 14 && endHour == 17 && startMinute == 0 && endMinute == 0,
                        onClick = {
                            startHour = 14; startMinute = 0; endHour = 17; endMinute = 0
                        },
                        label = { Text("2 PM – 5 PM") }
                    )
                    FilterChip(
                        selected = startHour == 17 && endHour == 21 && startMinute == 0 && endMinute == 0,
                        onClick = {
                            startHour = 17; startMinute = 0; endHour = 21; endMinute = 0
                        },
                        label = { Text("5 PM – 9 PM") }
                    )
                    FilterChip(
                        selected = startHour == 14 && endHour == 2 && startMinute == 0 && endMinute == 0,
                        onClick = {
                            startHour = 14; startMinute = 0; endHour = 2; endMinute = 0
                        },
                        label = { Text("2 PM – 2 AM (Night)") }
                    )
                    FilterChip(
                        selected = startHour == 6 && endHour == 23 && startMinute == 0 && endMinute == 0,
                        onClick = {
                            startHour = 6; startMinute = 0; endHour = 23; endMinute = 0
                        },
                        label = { Text("6 AM – 11 PM (All Day)") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(startHour, startMinute, endHour, endMinute) },
                modifier = Modifier.testTag("confirm_time_button")
            ) {
                Text("Apply Window")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun formatTime(hour: Int, minute: Int): String {
    val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    val amPm = if (hour >= 12) "PM" else "AM"
    return String.format("%d:%02d %s", h, minute, amPm)
}
