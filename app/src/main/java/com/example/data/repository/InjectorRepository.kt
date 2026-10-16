package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.db.AppDatabase
import com.example.data.db.InjectionEntity
import com.example.health.HealthConnectManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class InjectorSettings(
    val isRunning: Boolean = false,
    val minSteps: Int = 10000,
    val maxSteps: Int = 11000,
    val windowStartHour: Int = 14, // 2 PM
    val windowStartMinute: Int = 0,
    val windowEndHour: Int = 2,    // 2 AM (overnight)
    val windowEndMinute: Int = 0,
    val isDailyScheduleEnabled: Boolean = true,
    val isRealisticSimulationEnabled: Boolean = true,
    val duplicateGuardEnabled: Boolean = true,
    val activeDays: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7) // 1=Mon .. 7=Sun
)

class InjectorRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("step_injector_prefs", Context.MODE_PRIVATE)
    private val dao = AppDatabase.getInstance(context).injectionDao()

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<InjectorSettings> = _settingsFlow.asStateFlow()

    fun getAllLogs(): Flow<List<InjectionEntity>> = dao.getAllLogs()

    fun getLatestSuccessFlow(): Flow<InjectionEntity?> = dao.getLatestSuccessFlow()

    suspend fun getLatestSuccess(): InjectionEntity? = dao.getLatestSuccess()

    suspend fun getSuccessForDate(date: LocalDate): InjectionEntity? {
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        return dao.getSuccessForDate(dateStr)
    }

    suspend fun isDateAlreadyInjected(date: LocalDate): Boolean {
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        return dao.countSuccessForDate(dateStr) > 0
    }

    fun getTodayInjectedStepsFlow(date: LocalDate = LocalDate.now()): Flow<Long> {
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        return dao.getTodayInjectedStepsFlow(dateStr)
    }

    suspend fun getTodayInjectedSteps(date: LocalDate = LocalDate.now()): Long {
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        return dao.getTodayInjectedSteps(dateStr)
    }

    fun getStepsForDatesFlow(date1: LocalDate, date2: LocalDate): Flow<Long> {
        val d1 = date1.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val d2 = date2.format(DateTimeFormatter.ISO_LOCAL_DATE)
        return dao.getStepsForDatesFlow(d1, d2)
    }

    suspend fun getStepsForDates(date1: LocalDate, date2: LocalDate): Long {
        val d1 = date1.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val d2 = date2.format(DateTimeFormatter.ISO_LOCAL_DATE)
        return dao.getStepsForDates(d1, d2)
    }

    suspend fun getLatestSuccessForDates(date1: LocalDate, date2: LocalDate): InjectionEntity? {
        val d1 = date1.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val d2 = date2.format(DateTimeFormatter.ISO_LOCAL_DATE)
        return dao.getLatestSuccessForDates(d1, d2)
    }

    suspend fun insertLog(log: InjectionEntity): Long {
        return dao.insertLog(log)
    }

    suspend fun deleteLogAndHealthRecords(id: Long, healthManager: HealthConnectManager): Boolean {
        val log = dao.getById(id)
        if (log != null && log.healthRecordIds.isNotBlank()) {
            val ids = log.healthRecordIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            healthManager.deleteRecords(ids)
        }
        dao.deleteById(id)
        return true
    }

    suspend fun clearHistory(healthManager: HealthConnectManager) {
        dao.clearAllLogs()
    }

    private fun loadSettings(): InjectorSettings {
        val daysStrings = prefs.getStringSet("active_days", setOf("1", "2", "3", "4", "5", "6", "7"))
            ?: setOf("1", "2", "3", "4", "5", "6", "7")
        val activeDays = daysStrings.mapNotNull { it.toIntOrNull() }.toSet()

        return InjectorSettings(
            isRunning = prefs.getBoolean("is_running", false),
            minSteps = prefs.getInt("min_steps", 10000),
            maxSteps = prefs.getInt("max_steps", 11000),
            windowStartHour = prefs.getInt("window_start_hour", 14),
            windowStartMinute = prefs.getInt("window_start_minute", 0),
            windowEndHour = prefs.getInt("window_end_hour", 2),
            windowEndMinute = prefs.getInt("window_end_minute", 0),
            isDailyScheduleEnabled = prefs.getBoolean("is_daily_schedule_enabled", true),
            isRealisticSimulationEnabled = prefs.getBoolean("is_realistic_simulation_enabled", true),
            duplicateGuardEnabled = prefs.getBoolean("duplicate_guard_enabled", true),
            activeDays = if (activeDays.isEmpty()) setOf(1, 2, 3, 4, 5, 6, 7) else activeDays
        )
    }

    fun updateSettings(newSettings: InjectorSettings) {
        prefs.edit().apply {
            putBoolean("is_running", newSettings.isRunning)
            putInt("min_steps", newSettings.minSteps)
            putInt("max_steps", newSettings.maxSteps)
            putInt("window_start_hour", newSettings.windowStartHour)
            putInt("window_start_minute", newSettings.windowStartMinute)
            putInt("window_end_hour", newSettings.windowEndHour)
            putInt("window_end_minute", newSettings.windowEndMinute)
            putBoolean("is_daily_schedule_enabled", newSettings.isDailyScheduleEnabled)
            putBoolean("is_realistic_simulation_enabled", newSettings.isRealisticSimulationEnabled)
            putBoolean("duplicate_guard_enabled", newSettings.duplicateGuardEnabled)
            putStringSet("active_days", newSettings.activeDays.map { it.toString() }.toSet())
            apply()
        }
        _settingsFlow.value = newSettings
    }
}
