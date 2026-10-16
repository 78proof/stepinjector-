package com.example.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.InjectionEntity
import com.example.data.repository.InjectorSettings
import com.example.ui.InjectorViewModel
import com.example.ui.components.TimeSelectionDialog
import com.example.ui.components.formatTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DailyScheduleTab(
    viewModel: InjectorViewModel,
    settings: InjectorSettings,
    latestLog: InjectionEntity?,
    modifier: Modifier = Modifier
) {
    // Draft editable state so users have complete control and certainty
    var draftMinSteps by remember(settings.minSteps) { mutableIntStateOf(settings.minSteps) }
    var draftMaxSteps by remember(settings.maxSteps) { mutableIntStateOf(settings.maxSteps) }
    var draftStartHour by remember(settings.windowStartHour) { mutableIntStateOf(settings.windowStartHour) }
    var draftStartMinute by remember(settings.windowStartMinute) { mutableIntStateOf(settings.windowStartMinute) }
    var draftEndHour by remember(settings.windowEndHour) { mutableIntStateOf(settings.windowEndHour) }
    var draftEndMinute by remember(settings.windowEndMinute) { mutableIntStateOf(settings.windowEndMinute) }
    var draftActiveDays by remember(settings.activeDays) { mutableStateOf(settings.activeDays) }
    var draftIsRealistic by remember(settings.isRealisticSimulationEnabled) { mutableStateOf(settings.isRealisticSimulationEnabled) }
    var draftDuplicateGuard by remember(settings.duplicateGuardEnabled) { mutableStateOf(settings.duplicateGuardEnabled) }
    var draftIsDailyEnabled by remember(settings.isDailyScheduleEnabled) { mutableStateOf(settings.isDailyScheduleEnabled) }

    var showTimeDialog by remember { mutableStateOf(false) }
    var sampleSeed by remember { mutableIntStateOf(0) }

    val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    val isGuardedToday = latestLog != null && latestLog.targetDate == todayStr && latestLog.status == "SUCCESS"

    val todayEpochDay = LocalDate.now().toEpochDay()
    val dailySampleSeed = todayEpochDay * 7919L + sampleSeed

    // Dynamic simulated sample cross-check: changes automatically each day, or when user re-rolls
    val samplePreview = remember(
        draftMinSteps, draftMaxSteps, draftStartHour, draftStartMinute,
        draftEndHour, draftEndMinute, draftIsRealistic, sampleSeed, todayEpochDay
    ) {
        viewModel.healthManager.calculateSimulationSample(
            minSteps = draftMinSteps,
            maxSteps = draftMaxSteps,
            windowStartHour = draftStartHour,
            windowStartMinute = draftStartMinute,
            windowEndHour = draftEndHour,
            windowEndMinute = draftEndMinute,
            isRealistic = draftIsRealistic,
            randomSeed = dailySampleSeed
        )
    }

    if (showTimeDialog) {
        TimeSelectionDialog(
            initialStartHour = draftStartHour,
            initialStartMinute = draftStartMinute,
            initialEndHour = draftEndHour,
            initialEndMinute = draftEndMinute,
            onDismiss = { showTimeDialog = false },
            onConfirm = { sH, sM, eH, eM ->
                draftStartHour = sH
                draftStartMinute = sM
                draftEndHour = eH
                draftEndMinute = eM
                showTimeDialog = false
            }
        )
    }

    val todayDbSteps by viewModel.todayInjectedStepsFromDb.collectAsState()
    val todayHealthSummary by viewModel.todayHealthSummary.collectAsState()
    val isRefreshingTodaySteps by viewModel.isRefreshingTodaySteps.collectAsState()
    val isInjecting by viewModel.isInjecting.collectAsState()
    val cycleInfo by viewModel.scheduleCycleInfo.collectAsState()
    val selectedScope by viewModel.selectedDateScope.collectAsState()

    val dayFmt = remember { DateTimeFormatter.ofPattern("EEE, MMM d") }
    val cycleStartStr = cycleInfo.cycleStartDate.format(dayFmt)
    val cycleEndStr = cycleInfo.cycleEndDate.format(dayFmt)
    val todayDateStr = LocalDate.now().format(dayFmt)
    val nowTimeFmt = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a"))

    val isScopeCycle = selectedScope == "CYCLE"
    val injectedSoFar = if (isScopeCycle) cycleInfo.cycleInjectedSteps else maxOf(todayDbSteps, todayHealthSummary.stepsInjectedByUs)
    val targetMin = if (isScopeCycle) cycleInfo.targetMin else settings.minSteps.toLong()
    val targetMax = if (isScopeCycle) cycleInfo.targetMax else settings.maxSteps.toLong()
    val remainingToMin = (targetMin - injectedSoFar).coerceAtLeast(0L)
    val remainingToMax = (targetMax - injectedSoFar).coerceAtLeast(0L)
    val isGoalMet = injectedSoFar >= targetMin
    val stepProgressPercent = if (targetMin > 0L) (injectedSoFar.toFloat() / targetMin.toFloat()).coerceIn(0f, 1f) else 0f

    val distanceSoFarKm = (injectedSoFar * 0.762) / 1000.0
    val caloriesSoFarKcal = injectedSoFar * 0.043
    val totalHealthConnectSteps = if (isScopeCycle) cycleInfo.cycleHealthSteps else todayHealthSummary.totalStepsInHealthConnect

    val timeProgressPercent = cycleInfo.timeProgressPercent
    val elapsedMinutes = cycleInfo.elapsedMinutes
    val totalWindowMinutes = cycleInfo.totalWindowMinutes
    val isInsideWindow = cycleInfo.isInsideWindow
    val activeStartHour = cycleInfo.windowStartHour
    val activeStartMinute = cycleInfo.windowStartMinute
    val activeEndHour = cycleInfo.windowEndHour
    val activeEndMinute = cycleInfo.windowEndMinute

    val hasUnsavedChanges = draftMinSteps != settings.minSteps ||
        draftMaxSteps != settings.maxSteps ||
        draftStartHour != settings.windowStartHour ||
        draftStartMinute != settings.windowStartMinute ||
        draftEndHour != settings.windowEndHour ||
        draftEndMinute != settings.windowEndMinute ||
        draftIsRealistic != settings.isRealisticSimulationEnabled ||
        draftDuplicateGuard != settings.duplicateGuardEnabled ||
        draftIsDailyEnabled != settings.isDailyScheduleEnabled ||
        draftActiveDays != settings.activeDays

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. TODAY'S SCHEDULE PROGRESS & REMAINING TRACKER (Shows injected so far and steps left at any time)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isGoalMet) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("schedule_progress_tracker_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header with status badge and live refresh
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isGoalMet) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primaryContainer
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isGoalMet) Icons.Default.CheckCircle else Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = if (isGoalMet) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Today's Schedule Tracker",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isGoalMet) "Daily schedule target achieved! 🎉"
                                else if (settings.isRunning) "Schedule active • Monitoring progress"
                                else "Schedule paused",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Refresh Button
                    OutlinedButton(
                        onClick = { viewModel.refreshTodaySteps() },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("refresh_schedule_progress_btn")
                    ) {
                        if (isRefreshingTodaySteps) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(14.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Refresh", style = MaterialTheme.typography.labelSmall)
                    }
                }

                // Injected So Far vs Steps Left (Hero Dual-Metric Cards)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Left Box: Injected So Far
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.DirectionsRun,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "INJECTED SO FAR",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = String.format("%,d", injectedSoFar),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${String.format("%.2f", distanceSoFarKm)} km • ${String.format("%.0f", caloriesSoFarKcal)} kcal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Right Box: Steps Left
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isGoalMet) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isGoalMet) Icons.Default.Check else Icons.Default.HourglassBottom,
                                    contentDescription = null,
                                    tint = if (isGoalMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "STEPS LEFT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isGoalMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (remainingToMin > 0L) String.format("%,d", remainingToMin) else "0 (Done!)",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isGoalMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (remainingToMin > 0L) {
                                    "To reach min ${String.format("%,d", targetMin)}"
                                } else {
                                    "+${String.format("%,d", (injectedSoFar - targetMin).coerceAtLeast(0L))} above goal"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 1. Time Window Elapsed Progress Bar (Updates as time passes through the day)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Window Time Elapsed: ${(timeProgressPercent * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "${elapsedMinutes / 60}h ${elapsedMinutes % 60}m / ${totalWindowMinutes / 60}h ${totalWindowMinutes % 60}m",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LinearProgressIndicator(
                        progress = timeProgressPercent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    )
                }

                // 2. Step Target Progress Bar with Percentage
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Daily Steps Target: ${(stepProgressPercent * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "${String.format("%,d", injectedSoFar)} / ${String.format("%,d", targetMin)} steps",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LinearProgressIndicator(
                        progress = stepProgressPercent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    )
                }

                // Breakdown and Status Badges
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Health Connect live reading badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Health Connect: ${String.format("%,d", totalHealthConnectSteps)} steps today",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Window Status Badge (Shows saved schedule window)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isInsideWindow) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isInsideWindow) "🟢 In Window (${formatTime(activeStartHour, activeStartMinute)} – ${formatTime(activeEndHour, activeEndMinute)})"
                                else "⏰ Window: ${formatTime(activeStartHour, activeStartMinute)} – ${formatTime(activeEndHour, activeEndMinute)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isInsideWindow) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (remainingToMax > 0L && remainingToMin == 0L) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${String.format("%,d", remainingToMax)} steps to max (${String.format("%,d", targetMax)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Interactive Quick Action: Inject Today's Schedule Steps Now!
                if (remainingToMin > 0L) {
                    Button(
                        onClick = { viewModel.injectScheduledToday() },
                        enabled = !isInjecting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("inject_remaining_steps_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (isInjecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Injecting into Health Connect...", style = MaterialTheme.typography.labelLarge)
                        } else {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "⚡ Inject Today's Schedule (${String.format("%,d", remainingToMin)} Steps Left)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Master Start/Stop Card with Toggle & Status
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (settings.isRunning) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().testTag("schedule_master_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (settings.isRunning) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (settings.isRunning) Icons.Default.PlayArrow else Icons.Default.Stop,
                            contentDescription = null,
                            tint = if (settings.isRunning) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (settings.isRunning) "SCHEDULE ACTIVE" else "SCHEDULE PAUSED",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (settings.isRunning) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (settings.isRunning) "WorkManager 24h cycle is running" else "Configure settings below & tap Start",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (settings.isRunning) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Switch(
                    checked = settings.isRunning,
                    onCheckedChange = { isChecked ->
                        if (isChecked) {
                            viewModel.applyAndStartSchedule(
                                minSteps = draftMinSteps,
                                maxSteps = draftMaxSteps,
                                startHour = draftStartHour,
                                startMinute = draftStartMinute,
                                endHour = draftEndHour,
                                endMinute = draftEndMinute,
                                activeDays = draftActiveDays,
                                isRealistic = draftIsRealistic,
                                duplicateGuard = draftDuplicateGuard,
                                isDailyEnabled = draftIsDailyEnabled
                            )
                        } else {
                            viewModel.stopSchedule()
                        }
                    },
                    modifier = Modifier.testTag("schedule_master_switch")
                )
            }
        }

        // UNSAVED CHANGES WARNING BANNER
        if (hasUnsavedChanges) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("unsaved_changes_banner")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Unsaved Slider Modifications",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "The Active Tracker keeps your saved schedule (${String.format("%,d", settings.minSteps)} - ${String.format("%,d", settings.maxSteps)} steps) and will NOT change when moving sliders until you tap 'Save & Apply'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }

        // DEDICATED START / SAVE / STOP BUTTONS
        if (!settings.isRunning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Save & Apply Settings Button (Saves without starting)
                OutlinedButton(
                    onClick = {
                        viewModel.saveScheduleSettings(
                            minSteps = draftMinSteps,
                            maxSteps = draftMaxSteps,
                            startHour = draftStartHour,
                            startMinute = draftStartMinute,
                            endHour = draftEndHour,
                            endMinute = draftEndMinute,
                            activeDays = draftActiveDays,
                            isRealistic = draftIsRealistic,
                            duplicateGuard = draftDuplicateGuard,
                            isDailyEnabled = draftIsDailyEnabled,
                            startIfStopped = false
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("save_schedule_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save & Apply", fontWeight = FontWeight.Bold)
                }

                // Start Schedule Button (Saves and starts running)
                Button(
                    onClick = {
                        viewModel.applyAndStartSchedule(
                            minSteps = draftMinSteps,
                            maxSteps = draftMaxSteps,
                            startHour = draftStartHour,
                            startMinute = draftStartMinute,
                            endHour = draftEndHour,
                            endMinute = draftEndMinute,
                            activeDays = draftActiveDays,
                            isRealistic = draftIsRealistic,
                            duplicateGuard = draftDuplicateGuard,
                            isDailyEnabled = draftIsDailyEnabled
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("start_schedule_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "▶ Start Schedule",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.saveScheduleSettings(
                            minSteps = draftMinSteps,
                            maxSteps = draftMaxSteps,
                            startHour = draftStartHour,
                            startMinute = draftStartMinute,
                            endHour = draftEndHour,
                            endMinute = draftEndMinute,
                            activeDays = draftActiveDays,
                            isRealistic = draftIsRealistic,
                            duplicateGuard = draftDuplicateGuard,
                            isDailyEnabled = draftIsDailyEnabled,
                            startIfStopped = false
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("apply_schedule_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save & Apply Updates", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { viewModel.stopSchedule() },
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("stop_schedule_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stop Schedule")
                }
            }
        }

        // SIMULATION CROSS-CHECK BOX (User requested box telling what random distance & start/stop time the app will choose)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ),
            modifier = Modifier.fillMaxWidth().testTag("schedule_simulation_cross_check_card")
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
                            text = "Schedule Simulation Cross-Check",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    OutlinedButton(
                        onClick = { sampleSeed++ },
                        modifier = Modifier.testTag("reroll_schedule_preview_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Re-roll", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Text(
                    text = "See exactly what random times & metrics the scheduler will produce:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Metric display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Random Steps", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = "${String.format("%,d", samplePreview.steps)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column {
                        Text("Random Distance", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = String.format("%.2f km", samplePreview.distanceKm),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Column {
                        Text("Calculated Calories", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = String.format("%.0f kcal", samplePreview.caloriesKcal),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                // Start and Stop Time Range Box
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
                                text = "Sample Injected Window: ${samplePreview.timeSpanFormatted}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Duration: ${samplePreview.durationFormatted} (within selected ${formatTime(draftStartHour, draftStartMinute)} to ${formatTime(draftEndHour, draftEndMinute)} span)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isGuardedToday && draftDuplicateGuard) {
                    Text(
                        text = "🔒 Duplicate Guard: Today ($todayStr) is already logged and protected.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Active Days of Week Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth().testTag("active_days_card")
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
                        text = "Active Days of Week",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select which days the automatic schedule should inject steps:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                val daysList = listOf(
                    1 to "Mon",
                    2 to "Tue",
                    3 to "Wed",
                    4 to "Thu",
                    5 to "Fri",
                    6 to "Sat",
                    7 to "Sun"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    daysList.forEach { (dayInt, label) ->
                        val isSelected = draftActiveDays.contains(dayInt)
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable {
                                    val current = draftActiveDays.toMutableSet()
                                    if (current.contains(dayInt)) {
                                        if (current.size > 1) current.remove(dayInt)
                                    } else {
                                        current.add(dayInt)
                                    }
                                    draftActiveDays = current
                                }
                                .testTag("day_btn_$dayInt")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label.take(2),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = draftActiveDays.size == 7,
                        onClick = { draftActiveDays = setOf(1, 2, 3, 4, 5, 6, 7) },
                        label = { Text("All 7 Days") },
                        modifier = Modifier.testTag("all_days_chip")
                    )
                    FilterChip(
                        selected = draftActiveDays == setOf(1, 2, 3, 4, 5),
                        onClick = { draftActiveDays = setOf(1, 2, 3, 4, 5) },
                        label = { Text("Weekdays Only (M-F)") },
                        modifier = Modifier.testTag("weekdays_chip")
                    )
                }
            }
        }

        // Daily Step Range Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth().testTag("schedule_step_range_card")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Daily Step Range",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${String.format("%,d", draftMinSteps)} – ${String.format("%,d", draftMaxSteps)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                var sliderMin by remember(draftMinSteps) { mutableFloatStateOf(draftMinSteps.toFloat()) }
                var sliderMax by remember(draftMaxSteps) { mutableFloatStateOf(draftMaxSteps.toFloat()) }

                RangeSlider(
                    value = sliderMin..sliderMax,
                    onValueChange = { range ->
                        sliderMin = range.start
                        sliderMax = range.endInclusive
                        draftMinSteps = range.start.toInt()
                        draftMaxSteps = range.endInclusive.toInt()
                    },
                    valueRange = 1000f..30000f,
                    steps = 28,
                    modifier = Modifier.testTag("schedule_range_slider")
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = draftMinSteps == 10000 && draftMaxSteps == 11000,
                        onClick = { draftMinSteps = 10000; draftMaxSteps = 11000 },
                        label = { Text("10k – 11k (Default)") }
                    )
                    FilterChip(
                        selected = draftMinSteps == 5000 && draftMaxSteps == 8000,
                        onClick = { draftMinSteps = 5000; draftMaxSteps = 8000 },
                        label = { Text("5k – 8k") }
                    )
                    FilterChip(
                        selected = draftMinSteps == 15000 && draftMaxSteps == 20000,
                        onClick = { draftMinSteps = 15000; draftMaxSteps = 20000 },
                        label = { Text("15k – 20k") }
                    )
                }
            }
        }

        // Daily Time Window Card (Supports 5 AM to 8 AM and any range)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth().testTag("schedule_time_window_card")
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
                            text = "Daily Time Span",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = { showTimeDialog = true },
                        modifier = Modifier.testTag("schedule_edit_window_btn")
                    ) {
                        Text("Edit Span")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                val timeLabel = "${formatTime(draftStartHour, draftStartMinute)} to ${formatTime(draftEndHour, draftEndMinute)}"
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )

                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Supports any custom timing (e.g. 5:00 AM to 8:00 AM). Injections vary naturally inside this span.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick presets right on card
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = draftStartHour == 5 && draftEndHour == 8,
                        onClick = {
                            draftStartHour = 5; draftStartMinute = 0; draftEndHour = 8; draftEndMinute = 0
                        },
                        label = { Text("5 AM – 8 AM") },
                        modifier = Modifier.testTag("quick_preset_5_8")
                    )
                    FilterChip(
                        selected = draftStartHour == 8 && draftEndHour == 12,
                        onClick = {
                            draftStartHour = 8; draftStartMinute = 0; draftEndHour = 12; draftEndMinute = 0
                        },
                        label = { Text("8 AM – 12 PM") }
                    )
                    FilterChip(
                        selected = draftStartHour == 14 && draftEndHour == 2,
                        onClick = {
                            draftStartHour = 14; draftStartMinute = 0; draftEndHour = 2; draftEndMinute = 0
                        },
                        label = { Text("2 PM – 2 AM") }
                    )
                }
            }
        }

        // Schedule & Feature Toggles Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Automation Options",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                // Daily Schedule Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("WorkManager 24h Trigger", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Runs in background even in battery saver & low battery", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = draftIsDailyEnabled,
                        onCheckedChange = { draftIsDailyEnabled = it },
                        modifier = Modifier.testTag("schedule_daily_switch")
                    )
                }

                // Realistic Activity Simulation Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Realistic Multi-Intervals", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Distributes steps naturally across window", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = draftIsRealistic,
                        onCheckedChange = { draftIsRealistic = it },
                        modifier = Modifier.testTag("schedule_realistic_switch")
                    )
                }

                // Duplicate Guard Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Duplicate Guard", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Prevents double injections on same day", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = draftDuplicateGuard,
                        onCheckedChange = { draftDuplicateGuard = it },
                        modifier = Modifier.testTag("schedule_duplicate_guard_switch")
                    )
                }
            }
        }
    }
}
