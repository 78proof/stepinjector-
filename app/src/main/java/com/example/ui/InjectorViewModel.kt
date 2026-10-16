package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.InjectionEntity
import com.example.data.repository.InjectorRepository
import com.example.data.repository.InjectorSettings
import com.example.health.HealthConnectManager
import com.example.health.HealthRecordItem
import com.example.health.PermissionStatusItem
import com.example.health.TodayStepsSummary
import com.example.worker.StepInjectionWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class HealthViewerRange(val label: String, val days: Long) {
    TODAY("Today", 1),
    WEEK("Past 7 Days", 7),
    MONTH("Past 30 Days", 30),
    SIX_MONTHS("6 Months", 180)
}

data class BackfillProgress(
    val isRunning: Boolean = false,
    val currentDay: Int = 0,
    val totalDays: Int = 0,
    val currentDateStr: String = "",
    val currentTimeWindow: String = "",
    val currentSteps: Long = 0L,
    val insertedCount: Int = 0,
    val skippedCount: Int = 0
)

data class NextPreviewData(
    val sampleSteps: Long,
    val distanceKm: Double,
    val caloriesKcal: Double,
    val timeDescription: String,
    val isGuardedToday: Boolean
)

data class ScheduleCycleInfo(
    val cycleStartDate: LocalDate = LocalDate.now(),
    val cycleEndDate: LocalDate = LocalDate.now(),
    val isOvernight: Boolean = false,
    val windowStartHour: Int = 14,
    val windowStartMinute: Int = 0,
    val windowEndHour: Int = 2,
    val windowEndMinute: Int = 0,
    val isInsideWindow: Boolean = false,
    val hasWindowPassed: Boolean = false,
    val totalWindowMinutes: Int = 720,
    val elapsedMinutes: Int = 0,
    val timeProgressPercent: Float = 0f,
    val cycleInjectedSteps: Long = 0L,
    val cycleHealthSteps: Long = 0L,
    val targetMin: Long = 10000L,
    val targetMax: Long = 11000L,
    val isInjected: Boolean = false,
    val latestLog: InjectionEntity? = null,
    val queryTimestamp: Instant = Instant.now()
)

class InjectorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = InjectorRepository(application)
    val healthManager = HealthConnectManager(application)

    val settings: StateFlow<InjectorSettings> = repository.settingsFlow

    val historyLogs: StateFlow<List<InjectionEntity>> = repository.getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestSuccess: StateFlow<InjectionEntity?> = repository.getLatestSuccessFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _hasPermissions = MutableStateFlow(false)
    val hasPermissions: StateFlow<Boolean> = _hasPermissions.asStateFlow()

    private val _permissionDetails = MutableStateFlow<List<PermissionStatusItem>>(emptyList())
    val permissionDetails: StateFlow<List<PermissionStatusItem>> = _permissionDetails.asStateFlow()

    private val _isHealthConnectAvailable = MutableStateFlow(healthManager.isSdkAvailable())
    val isHealthConnectAvailable: StateFlow<Boolean> = _isHealthConnectAvailable.asStateFlow()

    private val _isInjecting = MutableStateFlow(false)
    val isInjecting: StateFlow<Boolean> = _isInjecting.asStateFlow()

    private val _backfillProgress = MutableStateFlow(BackfillProgress())
    val backfillProgress: StateFlow<BackfillProgress> = _backfillProgress.asStateFlow()

    private val _healthRecords = MutableStateFlow<List<HealthRecordItem>>(emptyList())
    val healthRecords: StateFlow<List<HealthRecordItem>> = _healthRecords.asStateFlow()

    private val _isLoadingHealthRecords = MutableStateFlow(false)
    val isLoadingHealthRecords: StateFlow<Boolean> = _isLoadingHealthRecords.asStateFlow()

    private val _selectedViewerRange = MutableStateFlow(HealthViewerRange.WEEK)
    val selectedViewerRange: StateFlow<HealthViewerRange> = _selectedViewerRange.asStateFlow()

    val todayInjectedStepsFromDb: StateFlow<Long> = repository.getTodayInjectedStepsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _todayHealthSummary = MutableStateFlow(TodayStepsSummary())
    val todayHealthSummary: StateFlow<TodayStepsSummary> = _todayHealthSummary.asStateFlow()

    private val _scheduleCycleInfo = MutableStateFlow(ScheduleCycleInfo())
    val scheduleCycleInfo: StateFlow<ScheduleCycleInfo> = _scheduleCycleInfo.asStateFlow()

    private val _selectedDateScope = MutableStateFlow("CYCLE") // "CYCLE", "TODAY", "YESTERDAY"
    val selectedDateScope: StateFlow<String> = _selectedDateScope.asStateFlow()

    fun setDateScope(scope: String) {
        _selectedDateScope.value = scope
        refreshTodaySteps()
    }

    private val _isRefreshingTodaySteps = MutableStateFlow(false)
    val isRefreshingTodaySteps: StateFlow<Boolean> = _isRefreshingTodaySteps.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    init {
        checkPermissions()
        refreshHealthRecords(_selectedViewerRange.value)
        refreshTodaySteps()
    }

    fun refreshTodaySteps() {
        viewModelScope.launch {
            _isRefreshingTodaySteps.value = true
            val currentSettings = settings.value
            val nowTime = java.time.LocalTime.now()
            val today = LocalDate.now()
            val zoneId = java.time.ZoneId.systemDefault()

            val sH = currentSettings.windowStartHour
            val sM = currentSettings.windowStartMinute
            val eH = currentSettings.windowEndHour
            val eM = currentSettings.windowEndMinute
            val startTime = java.time.LocalTime.of(sH, sM)
            val endTime = java.time.LocalTime.of(eH, eM)

            val isOvernight = endTime.isBefore(startTime) || (endTime == startTime && sH == eH)

            // If overnight and it's early morning before windowEndTime (e.g. 1 AM when window ends at 2 AM):
            // The active schedule window started yesterday afternoon!
            val cycleStartDate = if (isOvernight && nowTime.isBefore(endTime)) {
                today.minusDays(1)
            } else {
                today
            }
            val cycleEndDate = if (isOvernight) cycleStartDate.plusDays(1) else cycleStartDate

            val windowStartDt = java.time.LocalDateTime.of(cycleStartDate, startTime)
            val windowEndDt = java.time.LocalDateTime.of(cycleEndDate, endTime)
            val windowStartInstant = windowStartDt.atZone(zoneId).toInstant()
            val windowEndInstant = windowEndDt.atZone(zoneId).toInstant()

            val isInsideWindow = if (isOvernight) {
                !nowTime.isBefore(startTime) || nowTime.isBefore(endTime)
            } else {
                !nowTime.isBefore(startTime) && nowTime.isBefore(endTime)
            }
            val hasWindowPassed = if (isOvernight) {
                nowTime.isAfter(endTime) && nowTime.isBefore(startTime)
            } else {
                nowTime.isAfter(endTime)
            }

            val totalWindowMinutes = if (isOvernight) {
                (24 * 60 - (sH * 60 + sM) + (eH * 60 + eM)).coerceAtLeast(10)
            } else {
                ((eH * 60 + eM) - (sH * 60 + sM)).coerceAtLeast(10)
            }

            val elapsedMinutes = if (hasWindowPassed) {
                totalWindowMinutes
            } else if (isInsideWindow) {
                if (isOvernight && nowTime.isBefore(endTime)) {
                    (24 * 60 - (sH * 60 + sM) + (nowTime.hour * 60 + nowTime.minute)).coerceIn(0, totalWindowMinutes)
                } else {
                    ((nowTime.hour * 60 + nowTime.minute) - (sH * 60 + sM)).coerceIn(0, totalWindowMinutes)
                }
            } else {
                0
            }

            val timeProgress = (elapsedMinutes.toFloat() / totalWindowMinutes.toFloat()).coerceIn(0f, 1f)

            // Query Health Connect for the active cycle window
            val cycleHealthSummary = withContext(Dispatchers.IO) {
                healthManager.queryStepsSummaryForRange(
                    windowStartInstant,
                    minOf(windowEndInstant, Instant.now().plusSeconds(60))
                )
            }

            // Query Room DB for steps injected in this cycle
            val dbStepsForCycle = withContext(Dispatchers.IO) {
                if (isOvernight) {
                    repository.getStepsForDates(cycleStartDate, cycleEndDate)
                } else {
                    repository.getTodayInjectedSteps(cycleStartDate)
                }
            }

            val latestCycleLog = withContext(Dispatchers.IO) {
                if (isOvernight) {
                    repository.getLatestSuccessForDates(cycleStartDate, cycleEndDate)
                } else {
                    repository.getSuccessForDate(cycleStartDate)
                }
            }

            val effectiveInjectedSteps = maxOf(dbStepsForCycle, cycleHealthSummary.stepsInjectedByUs)
            val isInjected = latestCycleLog != null || effectiveInjectedSteps >= currentSettings.minSteps.toLong()

            _scheduleCycleInfo.value = ScheduleCycleInfo(
                cycleStartDate = cycleStartDate,
                cycleEndDate = cycleEndDate,
                isOvernight = isOvernight,
                windowStartHour = sH,
                windowStartMinute = sM,
                windowEndHour = eH,
                windowEndMinute = eM,
                isInsideWindow = isInsideWindow,
                hasWindowPassed = hasWindowPassed,
                totalWindowMinutes = totalWindowMinutes,
                elapsedMinutes = elapsedMinutes,
                timeProgressPercent = timeProgress,
                cycleInjectedSteps = effectiveInjectedSteps,
                cycleHealthSteps = cycleHealthSummary.totalStepsInHealthConnect,
                targetMin = currentSettings.minSteps.toLong(),
                targetMax = currentSettings.maxSteps.toLong(),
                isInjected = isInjected,
                latestLog = latestCycleLog,
                queryTimestamp = Instant.now()
            )

            // Also refresh standard today summary
            val summary = withContext(Dispatchers.IO) {
                healthManager.queryTodayStepsSummary()
            }
            _todayHealthSummary.value = summary
            _isRefreshingTodaySteps.value = false
        }
    }

    fun checkPermissions() {
        viewModelScope.launch {
            _isHealthConnectAvailable.value = healthManager.isSdkAvailable()
            _hasPermissions.value = healthManager.hasAllPermissions()
            _permissionDetails.value = healthManager.getPermissionDetails()
            if (_hasPermissions.value) {
                refreshTodaySteps()
            }
        }
    }

    fun getRequiredPermissions(): Set<String> {
        return healthManager.requiredPermissions
    }

    fun onPermissionsResult() {
        viewModelScope.launch {
            checkPermissions()
            if (_hasPermissions.value) {
                _toastEvent.emit("Health Connect permissions granted!")
                refreshHealthRecords(_selectedViewerRange.value)
                refreshTodaySteps()
            } else {
                _toastEvent.emit("Some Health Connect permissions were not granted.")
            }
        }
    }

    fun injectRemainingStepsNow(remaining: Long) {
        if (remaining <= 0) return
        viewModelScope.launch {
            if (_isInjecting.value) return@launch
            val safeNow = Instant.now().minusSeconds(10)
            val durationMinutes = ((remaining / 120).coerceIn(5, 90)).toLong()
            val startTime = safeNow.minus(durationMinutes, ChronoUnit.MINUTES)
            injectInstantManual(
                steps = remaining,
                speedKmh = 4.8,
                customCalories = null,
                startTime = startTime,
                endTime = safeNow
            )
        }
    }

    fun injectInstantManual(
        steps: Long,
        speedKmh: Double,
        customCalories: Double?,
        startTime: Instant,
        endTime: Instant
    ) {
        viewModelScope.launch {
            if (_isInjecting.value) return@launch
            _isInjecting.value = true

            val today = LocalDate.now()
            val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

            val result = withContext(Dispatchers.IO) {
                healthManager.injectInstant(
                    steps = steps,
                    speedKmh = speedKmh,
                    customCaloriesKcal = customCalories,
                    startTime = startTime,
                    endTime = endTime
                )
            }

            if (result.isSuccess) {
                repository.insertLog(
                    InjectionEntity(
                        timestamp = System.currentTimeMillis(),
                        targetDate = todayStr,
                        stepCount = result.steps,
                        distanceMeters = result.distanceMeters,
                        caloriesKcal = result.caloriesKcal,
                        speedKmh = result.speedKmh,
                        startTimeMillis = result.startTime.toEpochMilli(),
                        endTimeMillis = result.endTime.toEpochMilli(),
                        isBackfill = false,
                        simulationMode = "MANUAL",
                        status = "SUCCESS",
                        notes = "Manual Inject: ${String.format("%.1f km/h", speedKmh)}",
                        healthRecordIds = result.recordIds.joinToString(",")
                    )
                )
                val distKm = String.format("%.2f km", result.distanceMeters / 1000.0)
                val calStr = String.format("%.0f kcal", result.caloriesKcal)
                _toastEvent.emit("Injected ${String.format("%,d", result.steps)} steps ($distKm • $calStr)!")
                refreshHealthRecords(_selectedViewerRange.value)
                refreshTodaySteps()
            } else {
                repository.insertLog(
                    InjectionEntity(
                        timestamp = System.currentTimeMillis(),
                        targetDate = todayStr,
                        stepCount = result.steps,
                        distanceMeters = result.distanceMeters,
                        caloriesKcal = result.caloriesKcal,
                        speedKmh = speedKmh,
                        startTimeMillis = 0,
                        endTimeMillis = 0,
                        isBackfill = false,
                        simulationMode = "MANUAL",
                        status = "ERROR",
                        notes = result.errorMessage ?: "Health Connect write error"
                    )
                )
                _toastEvent.emit("Injection failed: ${result.errorMessage}")
            }

            _isInjecting.value = false
        }
    }

    fun setRunning(running: Boolean) {
        val updated = settings.value.copy(isRunning = running)
        repository.updateSettings(updated)
        if (running && updated.isDailyScheduleEnabled) {
            StepInjectionWorker.scheduleDailyWork(getApplication())
            emitToast("Daily schedule active (24h WorkManager cycle)")
        } else {
            StepInjectionWorker.cancelDailyWork(getApplication())
            emitToast("Daily schedule paused")
        }
    }

    fun injectScheduledToday(targetDate: LocalDate? = null) {
        viewModelScope.launch {
            if (_isInjecting.value) return@launch
            _isInjecting.value = true
            val currentSettings = settings.value
            val effectiveDate = targetDate ?: _scheduleCycleInfo.value.cycleStartDate
            val dateStr = effectiveDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

            val result = withContext(Dispatchers.IO) {
                healthManager.injectDataForDate(
                    targetDate = effectiveDate,
                    minSteps = currentSettings.minSteps,
                    maxSteps = currentSettings.maxSteps,
                    windowStartHour = currentSettings.windowStartHour,
                    windowStartMinute = currentSettings.windowStartMinute,
                    windowEndHour = currentSettings.windowEndHour,
                    windowEndMinute = currentSettings.windowEndMinute,
                    isRealistic = currentSettings.isRealisticSimulationEnabled
                )
            }

            if (result.isSuccess) {
                repository.insertLog(
                    InjectionEntity(
                        timestamp = System.currentTimeMillis(),
                        targetDate = dateStr,
                        stepCount = result.steps,
                        distanceMeters = result.distanceMeters,
                        caloriesKcal = result.caloriesKcal,
                        speedKmh = result.speedKmh,
                        startTimeMillis = result.startTime.toEpochMilli(),
                        endTimeMillis = result.endTime.toEpochMilli(),
                        isBackfill = false,
                        simulationMode = if (currentSettings.isRealisticSimulationEnabled) "NATURAL" else "SPIKE",
                        status = "SUCCESS",
                        notes = "Schedule cycle ($dateStr): ${String.format("%,d", result.steps)} steps",
                        healthRecordIds = result.recordIds.joinToString(",")
                    )
                )
                val distKm = String.format("%.2f km", result.distanceMeters / 1000.0)
                val calStr = String.format("%.0f kcal", result.caloriesKcal)
                _toastEvent.emit("✓ Successfully injected ${String.format("%,d", result.steps)} steps for $dateStr ($distKm • $calStr) into Health Connect!")
                refreshHealthRecords(_selectedViewerRange.value)
                refreshTodaySteps()
            } else {
                repository.insertLog(
                    InjectionEntity(
                        timestamp = System.currentTimeMillis(),
                        targetDate = dateStr,
                        stepCount = result.steps,
                        distanceMeters = result.distanceMeters,
                        caloriesKcal = result.caloriesKcal,
                        speedKmh = result.speedKmh,
                        startTimeMillis = 0,
                        endTimeMillis = 0,
                        isBackfill = false,
                        simulationMode = if (currentSettings.isRealisticSimulationEnabled) "NATURAL" else "SPIKE",
                        status = "ERROR",
                        notes = result.errorMessage ?: "Health Connect write error"
                    )
                )
                _toastEvent.emit("Notice: ${result.errorMessage}")
            }

            _isInjecting.value = false
        }
    }

    fun saveScheduleSettings(
        minSteps: Int,
        maxSteps: Int,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        activeDays: Set<Int>,
        isRealistic: Boolean,
        duplicateGuard: Boolean,
        isDailyEnabled: Boolean,
        startIfStopped: Boolean = false
    ) {
        val willRun = if (startIfStopped) true else settings.value.isRunning
        val updated = settings.value.copy(
            isRunning = willRun,
            isDailyScheduleEnabled = isDailyEnabled,
            minSteps = minSteps.coerceAtLeast(100),
            maxSteps = maxSteps.coerceAtLeast(minSteps),
            windowStartHour = startHour.coerceIn(0, 23),
            windowStartMinute = startMinute.coerceIn(0, 59),
            windowEndHour = endHour.coerceIn(0, 23),
            windowEndMinute = endMinute.coerceIn(0, 59),
            activeDays = activeDays,
            isRealisticSimulationEnabled = isRealistic,
            duplicateGuardEnabled = duplicateGuard
        )
        repository.updateSettings(updated)
        refreshTodaySteps()
        if (willRun && isDailyEnabled) {
            StepInjectionWorker.scheduleDailyWork(getApplication())
            emitToast("✓ Settings saved & applied! Background schedule active.")
        } else {
            StepInjectionWorker.cancelDailyWork(getApplication())
            emitToast("✓ Schedule configuration saved.")
        }
    }

    fun applyAndStartSchedule(
        minSteps: Int,
        maxSteps: Int,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        activeDays: Set<Int>,
        isRealistic: Boolean,
        duplicateGuard: Boolean,
        isDailyEnabled: Boolean
    ) {
        saveScheduleSettings(
            minSteps = minSteps,
            maxSteps = maxSteps,
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute,
            activeDays = activeDays,
            isRealistic = isRealistic,
            duplicateGuard = duplicateGuard,
            isDailyEnabled = isDailyEnabled,
            startIfStopped = true
        )
    }

    fun stopSchedule() {
        val updated = settings.value.copy(isRunning = false)
        repository.updateSettings(updated)
        StepInjectionWorker.cancelDailyWork(getApplication())
        emitToast("Schedule stopped. Background injections paused.")
    }

    fun setStepRange(min: Int, max: Int) {
        val safeMin = min.coerceAtLeast(100)
        val safeMax = max.coerceAtLeast(safeMin)
        repository.updateSettings(settings.value.copy(minSteps = safeMin, maxSteps = safeMax))
    }

    fun setTimeWindow(startH: Int, startM: Int, endH: Int, endM: Int) {
        repository.updateSettings(
            settings.value.copy(
                windowStartHour = startH,
                windowStartMinute = startM,
                windowEndHour = endH,
                windowEndMinute = endM
            )
        )
    }

    fun toggleDailySchedule(enabled: Boolean) {
        val updated = settings.value.copy(isDailyScheduleEnabled = enabled)
        repository.updateSettings(updated)
        if (updated.isRunning && enabled) {
            StepInjectionWorker.scheduleDailyWork(getApplication())
            emitToast("Daily schedule enabled")
        } else {
            StepInjectionWorker.cancelDailyWork(getApplication())
            emitToast("Daily schedule disabled")
        }
    }

    fun toggleRealisticSimulation(enabled: Boolean) {
        repository.updateSettings(settings.value.copy(isRealisticSimulationEnabled = enabled))
    }

    fun toggleDuplicateGuard(enabled: Boolean) {
        repository.updateSettings(settings.value.copy(duplicateGuardEnabled = enabled))
    }

    fun toggleActiveDay(day: Int) {
        val current = settings.value.activeDays.toMutableSet()
        if (current.contains(day)) {
            if (current.size > 1) { // keep at least 1 day selected
                current.remove(day)
            }
        } else {
            current.add(day)
        }
        repository.updateSettings(settings.value.copy(activeDays = current))
    }

    fun setAllDays(all: Boolean) {
        val newDays = if (all) setOf(1, 2, 3, 4, 5, 6, 7) else setOf(1, 2, 3, 4, 5) // weekdays
        repository.updateSettings(settings.value.copy(activeDays = newDays))
    }

    fun startBackfillRange(
        startDate: LocalDate,
        endDate: LocalDate,
        minSteps: Int,
        maxSteps: Int,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        isRealistic: Boolean,
        overwriteDuplicates: Boolean
    ) {
        viewModelScope.launch {
            if (_backfillProgress.value.isRunning) return@launch

            val daysList = mutableListOf<LocalDate>()
            var curr = startDate
            while (!curr.isAfter(endDate)) {
                daysList.add(curr)
                curr = curr.plusDays(1)
            }
            val totalDays = daysList.size

            _backfillProgress.value = BackfillProgress(
                isRunning = true,
                currentDay = 0,
                totalDays = totalDays,
                insertedCount = 0,
                skippedCount = 0
            )

            var inserted = 0
            var skipped = 0

            withContext(Dispatchers.IO) {
                daysList.forEachIndexed { index, date ->
                    val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

                    _backfillProgress.value = _backfillProgress.value.copy(
                        currentDay = index + 1,
                        currentDateStr = dateStr
                    )

                    if (!overwriteDuplicates && settings.value.duplicateGuardEnabled) {
                        if (repository.isDateAlreadyInjected(date)) {
                            skipped++
                            _backfillProgress.value = _backfillProgress.value.copy(
                                skippedCount = skipped
                            )
                            return@forEachIndexed
                        }
                    }

                    val res = healthManager.injectDataForDate(
                        targetDate = date,
                        minSteps = minSteps,
                        maxSteps = maxSteps,
                        windowStartHour = startHour,
                        windowStartMinute = startMinute,
                        windowEndHour = endHour,
                        windowEndMinute = endMinute,
                        isRealistic = isRealistic
                    )

                    val zoneId = java.time.ZoneId.systemDefault()
                    val startFmt = DateTimeFormatter.ofPattern("h:mm a").withZone(zoneId).format(res.startTime)
                    val endFmt = DateTimeFormatter.ofPattern("h:mm a").withZone(zoneId).format(res.endTime)

                    if (res.isSuccess) {
                        inserted++
                        repository.insertLog(
                            InjectionEntity(
                                timestamp = System.currentTimeMillis(),
                                targetDate = dateStr,
                                stepCount = res.steps,
                                distanceMeters = res.distanceMeters,
                                caloriesKcal = res.caloriesKcal,
                                speedKmh = res.speedKmh,
                                startTimeMillis = res.startTime.toEpochMilli(),
                                endTimeMillis = res.endTime.toEpochMilli(),
                                isBackfill = true,
                                simulationMode = if (isRealistic) "NATURAL" else "SPIKE",
                                status = "SUCCESS",
                                notes = "Backfill $dateStr ($startFmt - $endFmt)",
                                healthRecordIds = res.recordIds.joinToString(",")
                            )
                        )
                    }

                    _backfillProgress.value = _backfillProgress.value.copy(
                        insertedCount = inserted,
                        currentTimeWindow = "$startFmt – $endFmt",
                        currentSteps = res.steps
                    )
                }
            }

            _backfillProgress.value = BackfillProgress(isRunning = false)
            _toastEvent.emit("Backfill complete: $inserted days injected, $skipped skipped.")
            refreshHealthRecords(_selectedViewerRange.value)
        }
    }

    fun setViewerRange(range: HealthViewerRange) {
        _selectedViewerRange.value = range
        refreshHealthRecords(range)
    }

    fun refreshHealthRecords(range: HealthViewerRange = _selectedViewerRange.value) {
        viewModelScope.launch {
            _isLoadingHealthRecords.value = true
            val now = Instant.now()
            val start = now.minus(range.days, ChronoUnit.DAYS)
            val records = withContext(Dispatchers.IO) {
                healthManager.queryHealthRecords(start, now)
            }
            _healthRecords.value = records
            _isLoadingHealthRecords.value = false
        }
    }

    fun deleteLogItem(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteLogAndHealthRecords(id, healthManager)
            }
            _toastEvent.emit("Deleted entry and removed records from Health Connect.")
            refreshHealthRecords(_selectedViewerRange.value)
        }
    }

    fun clearLogHistory() {
        viewModelScope.launch {
            repository.clearHistory(healthManager)
            emitToast("History logs cleared.")
        }
    }

    fun getNextPreview(settings: InjectorSettings, guarded: Boolean): NextPreviewData {
        val avgSteps = (settings.minSteps + settings.maxSteps) / 2L
        val distKm = (avgSteps * 0.762) / 1000.0
        val kcal = avgSteps * 0.043

        val startH = if (settings.windowStartHour == 0) 12 else if (settings.windowStartHour > 12) settings.windowStartHour - 12 else settings.windowStartHour
        val startAmPm = if (settings.windowStartHour >= 12) "PM" else "AM"
        val endH = if (settings.windowEndHour == 0) 12 else if (settings.windowEndHour > 12) settings.windowEndHour - 12 else settings.windowEndHour
        val endAmPm = if (settings.windowEndHour >= 12) "PM" else "AM"

        val timeStr = String.format("%d:%02d %s – %d:%02d %s", startH, settings.windowStartMinute, startAmPm, endH, settings.windowEndMinute, endAmPm)

        return NextPreviewData(
            sampleSteps = avgSteps,
            distanceKm = distKm,
            caloriesKcal = kcal,
            timeDescription = timeStr,
            isGuardedToday = guarded
        )
    }

    private fun emitToast(msg: String) {
        viewModelScope.launch {
            _toastEvent.emit(msg)
        }
    }
}
