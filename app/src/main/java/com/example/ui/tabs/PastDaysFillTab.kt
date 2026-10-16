package com.example.ui.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.BackfillProgress
import com.example.ui.InjectorViewModel
import com.example.ui.components.TimeSelectionDialog
import com.example.ui.components.formatTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PastDaysFillTab(
    viewModel: InjectorViewModel,
    progress: BackfillProgress,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()

    var selectedSpanDays by remember { mutableIntStateOf(30) }
    var startDate by remember { mutableStateOf(today.minusDays(30)) }
    var endDate by remember { mutableStateOf(today.minusDays(1)) }

    // Time window for past days (Supports 5am - 8am and any custom window)
    var startHour by remember { mutableIntStateOf(5) }
    var startMinute by remember { mutableIntStateOf(0) }
    var endHour by remember { mutableIntStateOf(8) }
    var endMinute by remember { mutableIntStateOf(0) }
    var showTimeDialog by remember { mutableStateOf(false) }

    // Step range for past days
    var minSteps by remember { mutableIntStateOf(9000) }
    var maxSteps by remember { mutableIntStateOf(12000) }
    var isRealistic by remember { mutableStateOf(true) }
    var overwriteDuplicates by remember { mutableStateOf(false) }

    var sampleSeed by remember { mutableIntStateOf(0) }

    val totalSpanDays = (ChronoUnit.DAYS.between(startDate, endDate) + 1).coerceAtLeast(1)
    val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")

    // Dynamic simulated sample cross-check
    val sampleDayPreview = remember(
        minSteps, maxSteps, startHour, startMinute, endHour, endMinute, isRealistic, sampleSeed
    ) {
        viewModel.healthManager.calculateSimulationSample(
            minSteps = minSteps,
            maxSteps = maxSteps,
            windowStartHour = startHour,
            windowStartMinute = startMinute,
            windowEndHour = endHour,
            windowEndMinute = endMinute,
            isRealistic = isRealistic,
            targetDate = endDate
        )
    }

    if (showTimeDialog) {
        TimeSelectionDialog(
            initialStartHour = startHour,
            initialStartMinute = startMinute,
            initialEndHour = endHour,
            initialEndMinute = endMinute,
            onDismiss = { showTimeDialog = false },
            onConfirm = { sH, sM, eH, eM ->
                startHour = sH
                startMinute = sM
                endHour = eH
                endMinute = eM
                showTimeDialog = false
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!hasPermissions) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("past_days_permission_alert")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Health Connect Permissions Required",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Grant permissions to write past historical data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = onRequestPermissions,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Grant Access")
                        }
                    }
                }
            }
        }

        // Title banner
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            modifier = Modifier.fillMaxWidth().testTag("past_days_header_card")
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.HistoryEdu,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Past Days Fill (Backfill)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Configure date range, daily time window, and steps to populate past records.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // 1. DATE RANGE CARD (Clear Start Date and End Date)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth().testTag("past_days_date_card")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Target Date Range",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "${dateFormatter.format(startDate)}  ➔  ${dateFormatter.format(endDate)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Total span: $totalSpanDays days",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Date Presets
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(3, 7, 14, 30, 60, 90, 180).forEach { days ->
                        val label = if (days == 180) "6 Months (180d)" else "$days Days"
                        FilterChip(
                            selected = selectedSpanDays == days,
                            onClick = {
                                selectedSpanDays = days
                                startDate = today.minusDays(days.toLong())
                                endDate = today.minusDays(1)
                            },
                            label = { Text(label) },
                            modifier = Modifier.testTag("past_chip_$days")
                        )
                    }
                }
            }
        }

        // 2. DAILY TIME WINDOW CARD (Explicit Date & Time, Flexible 5 AM - 8 AM)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth().testTag("past_days_time_card")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Daily Time Window",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = { showTimeDialog = true },
                        modifier = Modifier.testTag("edit_past_time_btn")
                    ) {
                        Text("Edit Time")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                val timeSpanLabel = "${formatTime(startHour, startMinute)} to ${formatTime(endHour, endMinute)}"
                Text(
                    text = timeSpanLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )

                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Each past day's steps will be injected within this daily window.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Presets for quick times (including 5 AM - 8 AM)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = startHour == 5 && endHour == 8,
                        onClick = {
                            startHour = 5; startMinute = 0; endHour = 8; endMinute = 0
                        },
                        label = { Text("5 AM – 8 AM (Early)") },
                        modifier = Modifier.testTag("past_preset_5_8")
                    )
                    FilterChip(
                        selected = startHour == 8 && endHour == 12,
                        onClick = {
                            startHour = 8; startMinute = 0; endHour = 12; endMinute = 0
                        },
                        label = { Text("8 AM – 12 PM") }
                    )
                    FilterChip(
                        selected = startHour == 14 && endHour == 17,
                        onClick = {
                            startHour = 14; startMinute = 0; endHour = 17; endMinute = 0
                        },
                        label = { Text("2 PM – 5 PM") }
                    )
                    FilterChip(
                        selected = startHour == 8 && endHour == 22,
                        onClick = {
                            startHour = 8; startMinute = 0; endHour = 22; endMinute = 0
                        },
                        label = { Text("8 AM – 10 PM") }
                    )
                }
            }
        }

        // 3. STEP RANGE CARD FOR PAST DAYS
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth().testTag("past_days_step_range_card")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Steps Per Past Day", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "${String.format("%,d", minSteps)} – ${String.format("%,d", maxSteps)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                var sliderMin by remember(minSteps) { mutableFloatStateOf(minSteps.toFloat()) }
                var sliderMax by remember(maxSteps) { mutableFloatStateOf(maxSteps.toFloat()) }

                RangeSlider(
                    value = sliderMin..sliderMax,
                    onValueChange = { range ->
                        sliderMin = range.start
                        sliderMax = range.endInclusive
                        minSteps = range.start.toInt()
                        maxSteps = range.endInclusive.toInt()
                    },
                    valueRange = 1000f..30000f,
                    steps = 28,
                    modifier = Modifier.testTag("past_steps_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = overwriteDuplicates,
                        onCheckedChange = { overwriteDuplicates = it },
                        modifier = Modifier.testTag("past_days_overwrite_cb")
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text("Overwrite days that already have records", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text("If unchecked, days with existing data will be skipped safely.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // 4. SIMULATION CROSS-CHECK BOX (User requested box telling what random distance & start/stop time the app will choose)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
            ),
            modifier = Modifier.fillMaxWidth().testTag("backfill_simulation_cross_check_card")
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Backfill Simulation Cross-Check",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    OutlinedButton(
                        onClick = { sampleSeed++ },
                        modifier = Modifier.testTag("reroll_backfill_preview_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Re-roll", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Text(
                    text = "Sample injection for one day in the selected range:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Single day sample metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Sample Steps", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = "${String.format("%,d", sampleDayPreview.steps)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column {
                        Text("Sample Distance", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = String.format("%.2f km", sampleDayPreview.distanceKm),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Column {
                        Text("Calories", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = String.format("%.0f kcal", sampleDayPreview.caloriesKcal),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                // Window display
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sample Daily Window: ${sampleDayPreview.timeSpanFormatted}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Duration: ${sampleDayPreview.durationFormatted} (within selected ${formatTime(startHour, startMinute)} to ${formatTime(endHour, endMinute)} span)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Total estimate across all days
                val avgSteps = (minSteps + maxSteps) / 2L
                val totalEstimatedSteps = avgSteps * totalSpanDays
                val totalEstimatedKm = (totalEstimatedSteps * 0.762) / 1000.0
                Text(
                    text = "📊 Total Backfill Estimate: ~$totalSpanDays days  ➔  ~${String.format("%,d", totalEstimatedSteps)} steps  •  ~${String.format("%.1f", totalEstimatedKm)} km",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        // Realistic Simulation Toggle
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Realistic Activity Simulation", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Distributes steps naturally across window (Debug Fitter style)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Switch(
                    checked = isRealistic,
                    onCheckedChange = { isRealistic = it },
                    modifier = Modifier.testTag("past_realistic_switch")
                )
            }
        }

        // Progress section (if running)
        if (progress.isRunning) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth().testTag("backfill_progress_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Backfill In Progress...",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Day ${progress.currentDay} of ${progress.totalDays}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val fraction = if (progress.totalDays > 0) progress.currentDay.toFloat() / progress.totalDays else 0f
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Writing: ${progress.currentDateStr} (${progress.currentTimeWindow})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "✓ Inserted: ${progress.insertedCount}  •  ⊘ Skipped (duplicates): ${progress.skippedCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Start Backfill Button
        Button(
            onClick = {
                viewModel.startBackfillRange(
                    startDate = startDate,
                    endDate = endDate,
                    minSteps = minSteps,
                    maxSteps = maxSteps,
                    startHour = startHour,
                    startMinute = startMinute,
                    endHour = endHour,
                    endMinute = endMinute,
                    isRealistic = isRealistic,
                    overwriteDuplicates = overwriteDuplicates
                )
            },
            enabled = !progress.isRunning && hasPermissions,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("start_backfill_btn"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            if (progress.isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onSecondary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Injecting Historical Data...")
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "▶ Start Backfill ($totalSpanDays Days)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
