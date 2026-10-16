package com.example.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.InjectionEntity
import com.example.ui.InjectorViewModel
import com.example.ui.components.TimeSelectionDialog
import com.example.ui.components.formatTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.random.Random

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InjectNowTab(
    viewModel: InjectorViewModel,
    latestLog: InjectionEntity?,
    hasPermissions: Boolean,
    isInjecting: Boolean,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    var stepCount by remember { mutableLongStateOf(10000L) }
    var stepInputText by remember { mutableStateOf("10,000") }
    var speedKmh by remember { mutableDoubleStateOf(5.0) }
    var useCustomCalories by remember { mutableStateOf(false) }
    var customCaloriesInput by remember { mutableStateOf("430") }
    var naturalStepVariation by remember { mutableStateOf(true) }
    var variancePercent by remember { mutableFloatStateOf(2.5f) } // +/- 2.5% authentic human variation

    // Time window selection for instant inject
    var startHour by remember { mutableIntStateOf(14) }
    var startMinute by remember { mutableIntStateOf(0) }
    var endHour by remember { mutableIntStateOf(16) }
    var endMinute by remember { mutableIntStateOf(30) }
    var showTimeDialog by remember { mutableStateOf(false) }
    var varyTimesNaturally by remember { mutableStateOf(true) }
    var previewSeed by remember { mutableIntStateOf(0) }

    val zoneId = ZoneId.systemDefault()
    val today = LocalDate.now()

    // Dialog for picking custom start & end time
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

    // Natural step variation: NEVER a static round number (e.g. 10000 becomes 10183 or 9842)
    val effectiveSteps = remember(stepCount, naturalStepVariation, variancePercent, previewSeed) {
        if (naturalStepVariation) {
            val rng = Random(stepCount * 41L + previewSeed * 104729L + 137L)
            val sign = if (rng.nextBoolean()) 1.0 else -1.0
            val pct = rng.nextDouble(0.008, (variancePercent / 100f).toDouble().coerceAtLeast(0.012))
            val jitter = (stepCount * pct * sign).toLong()
            val finalJitter = if (jitter == 0L) 142L else jitter
            (stepCount + finalJitter).coerceAtLeast(100L)
        } else {
            stepCount
        }
    }

    // Calculations based on actual injected steps
    val strideLengthMeters = 0.762
    val distanceMeters = effectiveSteps * strideLengthMeters
    val distanceKm = distanceMeters / 1000.0

    val calculatedCalories = effectiveSteps * 0.043 * (speedKmh / 5.0).coerceIn(0.8, 2.0)
    val effectiveCalories = if (useCustomCalories) {
        customCaloriesInput.toDoubleOrNull() ?: calculatedCalories
    } else {
        calculatedCalories
    }

    // Dynamic simulated sample cross-check
    val simulatedSample = remember(
        effectiveSteps, speedKmh, effectiveCalories, startHour, startMinute, endHour, endMinute,
        varyTimesNaturally, previewSeed
    ) {
        val sBase = LocalDateTime.of(today, LocalTime.of(startHour.coerceIn(0, 23), startMinute.coerceIn(0, 59)))
        val eBase = if (endHour < startHour || (endHour == startHour && endMinute <= startMinute)) {
            LocalDateTime.of(today.plusDays(1), LocalTime.of(endHour.coerceIn(0, 23), endMinute.coerceIn(0, 59)))
        } else {
            LocalDateTime.of(today, LocalTime.of(endHour.coerceIn(0, 23), endMinute.coerceIn(0, 59)))
        }

        val totalWindowMinutes = ChronoUnit.MINUTES.between(sBase, eBase).coerceAtLeast(10L)
        val (sDt, eDt) = if (varyTimesNaturally) {
            val sessionMinutes = (totalWindowMinutes * 0.75).toLong().coerceIn(10L, 120L)
            val maxOffset = (totalWindowMinutes - sessionMinutes).coerceAtLeast(0L)
            val randomOffset = if (maxOffset > 0) Random.nextLong(0, maxOffset + 1L) else 0L
            val start = sBase.plusMinutes(randomOffset)
            Pair(start, start.plusMinutes(sessionMinutes))
        } else {
            Pair(sBase, eBase)
        }

        val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
        val spanFmt = "${timeFormatter.format(sDt)}  ➔  ${timeFormatter.format(eDt)}"
        val durMins = ChronoUnit.MINUTES.between(sDt, eDt)
        val dHours = durMins / 60
        val dM = durMins % 60
        val durFmt = if (dHours > 0) "${dHours}h ${dM}m" else "${dM} mins"

        Triple(spanFmt, durFmt, Pair(sDt.atZone(zoneId).toInstant(), eDt.atZone(zoneId).toInstant()))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Permissions banner if not granted
        if (!hasPermissions) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("inject_now_permission_alert")
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
                            text = "Health Connect Access Needed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Grant permissions to write steps, speed, and calories.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = onRequestPermissions,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("inject_now_grant_btn")
                        ) {
                            Text("Grant Access")
                        }
                    }
                }
            }
        }

        // Header Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth().testTag("inject_now_header_card")
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Instant Step Injection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Customize steps, speed, and time range for quick testing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // Time Selection Card (Start Time to End Time with Natural Variation)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth().testTag("instant_time_window_card")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Injection Time Window", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { showTimeDialog = true },
                        modifier = Modifier.testTag("custom_time_window_btn")
                    ) {
                        Text("Edit Time")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${formatTime(startHour, startMinute)}  ➔  ${formatTime(endHour, endMinute)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(10.dp))

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
                        modifier = Modifier.testTag("preset_5am_8am_instant")
                    )

                    FilterChip(
                        selected = startHour == 8 && endHour == 10,
                        onClick = {
                            startHour = 8; startMinute = 0; endHour = 10; endMinute = 0
                        },
                        label = { Text("8 AM – 10 AM") }
                    )

                    FilterChip(
                        selected = startHour == 14 && endHour == 17,
                        onClick = {
                            startHour = 14; startMinute = 0; endHour = 17; endMinute = 0
                        },
                        label = { Text("2 PM – 5 PM") }
                    )

                    val nowLocal = LocalTime.now()
                    FilterChip(
                        selected = false,
                        onClick = {
                            val nowH = nowLocal.hour
                            val nowM = nowLocal.minute
                            val sH = if (nowH >= 1) nowH - 1 else 0
                            startHour = sH
                            startMinute = nowM
                            endHour = nowH
                            endMinute = nowM
                        },
                        label = { Text("Past 1h") },
                        modifier = Modifier.testTag("preset_past_1h")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Vary Times Naturally Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Vary start & end time naturally", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Randomizes timestamps inside selected window", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = varyTimesNaturally,
                        onCheckedChange = { varyTimesNaturally = it },
                        modifier = Modifier.testTag("vary_times_switch")
                    )
                }
            }
        }

        // Step Count Input Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth().testTag("step_count_input_card")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Step Count", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = if (naturalStepVariation) "➔ ${String.format("%,d", effectiveSteps)} varied" else "${String.format("%,d", stepCount)} static",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Text Input for typing exact numbers (e.g. 10000, 12000)
                OutlinedTextField(
                    value = stepInputText,
                    onValueChange = { newVal ->
                        stepInputText = newVal
                        val digits = newVal.filter { it.isDigit() }
                        val parsed = digits.toLongOrNull()
                        if (parsed != null && parsed in 100L..100000L) {
                            stepCount = parsed
                        }
                    },
                    label = { Text("Target Step Count (e.g. 10000)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("step_count_text_field"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Slider(
                    value = stepCount.toFloat(),
                    onValueChange = {
                        stepCount = it.toLong()
                        stepInputText = String.format("%,d", stepCount)
                    },
                    valueRange = 500f..35000f,
                    steps = 68,
                    modifier = Modifier.testTag("step_count_slider")
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(5000L, 8000L, 10000L, 12000L, 15000L, 20000L).forEach { count ->
                        FilterChip(
                            selected = stepCount == count,
                            onClick = {
                                stepCount = count
                                stepInputText = String.format("%,d", count)
                            },
                            label = { Text("${count / 1000}k") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Natural step variation toggle (prevents rigid static numbers)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Shuffle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text("Natural Step Variance", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = if (naturalStepVariation) "Non-static authentic steps: ${String.format("%,d", effectiveSteps)} (${if (effectiveSteps >= stepCount) "+${effectiveSteps - stepCount}" else "${effectiveSteps - stepCount}"} steps)"
                                        else "Exact static number (${String.format("%,d", stepCount)})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (naturalStepVariation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = naturalStepVariation,
                                onCheckedChange = { naturalStepVariation = it },
                                modifier = Modifier.testTag("natural_step_variation_switch")
                            )
                        }

                        if (naturalStepVariation) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Variance:", style = MaterialTheme.typography.labelSmall)
                                listOf(1.0f to "±1%", 2.5f to "±2.5% (Natural)", 5.0f to "±5%").forEach { (pct, label) ->
                                    FilterChip(
                                        selected = variancePercent == pct,
                                        onClick = { variancePercent = pct },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                OutlinedButton(
                                    onClick = { previewSeed++ },
                                    modifier = Modifier.testTag("reroll_variance_btn")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Re-roll", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Speed & Intensity Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth().testTag("speed_intensity_card")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Speed & Pace", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = String.format("%.1f km/h", speedKmh),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Slider(
                    value = speedKmh.toFloat(),
                    onValueChange = { speedKmh = Math.round(it * 10.0) / 10.0 },
                    valueRange = 2.0f..15.0f,
                    steps = 25,
                    modifier = Modifier.testTag("speed_slider")
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = speedKmh == 4.0,
                        onClick = { speedKmh = 4.0 },
                        label = { Text("Walk (4.0)") }
                    )
                    FilterChip(
                        selected = speedKmh == 5.0,
                        onClick = { speedKmh = 5.0 },
                        label = { Text("Brisk (5.0)") }
                    )
                    FilterChip(
                        selected = speedKmh == 6.5,
                        onClick = { speedKmh = 6.5 },
                        label = { Text("Fast (6.5)") }
                    )
                    FilterChip(
                        selected = speedKmh == 9.0,
                        onClick = { speedKmh = 9.0 },
                        label = { Text("Jog (9.0)") }
                    )
                }
            }
        }

        // Custom Calories Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth().testTag("calories_card")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Custom Calories Burned", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Switch(
                        checked = useCustomCalories,
                        onCheckedChange = { useCustomCalories = it },
                        modifier = Modifier.testTag("custom_calories_switch")
                    )
                }

                if (useCustomCalories) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customCaloriesInput,
                        onValueChange = { customCaloriesInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Calories (kcal)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("custom_calories_input")
                    )
                }
            }
        }

        // SIMULATION CROSS-CHECK & CALCULATIONS BOX (User requested box telling what random distance & start/stop time the app will choose)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ),
            modifier = Modifier.fillMaxWidth().testTag("instant_simulation_cross_check_card")
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
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Injection Simulation Cross-Check",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (varyTimesNaturally) {
                        OutlinedButton(
                            onClick = { previewSeed++ },
                            modifier = Modifier.testTag("reroll_instant_preview_btn")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Re-roll", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Text(
                    text = "Verify the exact random timestamps and metrics before injecting:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Metrics row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (naturalStepVariation) "Varied Steps" else "Steps",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "${String.format("%,d", effectiveSteps)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column {
                        Text("Calculated Distance", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = String.format("%.2f km", distanceKm),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Column {
                        Text("Calories", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = String.format("%.0f kcal", effectiveCalories),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                // Injected Timestamps Box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Start / Stop: ${simulatedSample.first}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Duration: ${simulatedSample.second} • Speed: ${String.format("%.1f km/h", speedKmh)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Prominent Inject Button
        Button(
            onClick = {
                val chosenStart = simulatedSample.third.first
                val chosenEnd = simulatedSample.third.second

                viewModel.injectInstantManual(
                    steps = effectiveSteps,
                    speedKmh = speedKmh,
                    customCalories = if (useCustomCalories) customCaloriesInput.toDoubleOrNull() else null,
                    startTime = chosenStart,
                    endTime = chosenEnd
                )
            },
            enabled = !isInjecting && hasPermissions,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("inject_instant_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (isInjecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Injecting into Health Connect...")
            } else {
                Icon(Icons.Default.Bolt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "⚡ Inject Steps Now",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Last Injection Status Banner
        if (latestLog != null) {
            val instant = Instant.ofEpochMilli(latestLog.timestamp)
            val timeFormatted = DateTimeFormatter.ofPattern("MMM dd • h:mm a")
                .withZone(ZoneId.systemDefault())
                .format(instant)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth().testTag("last_injection_banner")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Last Injected Successfully",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$timeFormatted: ${String.format("%,d", latestLog.stepCount)} steps (${String.format("%.2f km", latestLog.distanceMeters / 1000.0)} • ${String.format("%.0f kcal", latestLog.caloriesKcal)})",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
