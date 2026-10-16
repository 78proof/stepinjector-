package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.db.InjectionEntity
import com.example.data.repository.InjectorRepository
import com.example.health.HealthConnectManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class StepInjectionWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val repository = InjectorRepository(appContext)
        val settings = repository.settingsFlow.value

        if (!settings.isRunning || !settings.isDailyScheduleEnabled) {
            return Result.success()
        }

        val today = LocalDate.now()
        val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

        // Check active days of week (1=Mon .. 7=Sun)
        val dayOfWeek = today.dayOfWeek.value
        if (!settings.activeDays.contains(dayOfWeek)) {
            // Day is not scheduled to run
            return Result.success()
        }

        // Check if window has started today
        val nowTime = java.time.LocalTime.now()
        val startTime = java.time.LocalTime.of(settings.windowStartHour, settings.windowStartMinute)
        val endTime = java.time.LocalTime.of(settings.windowEndHour, settings.windowEndMinute)
        val isOvernight = endTime.isBefore(startTime)
        val isInsideWindow = if (isOvernight) {
            !nowTime.isBefore(startTime) || nowTime.isBefore(endTime)
        } else {
            !nowTime.isBefore(startTime) && nowTime.isBefore(endTime)
        }
        val hasWindowPassed = if (isOvernight) false else nowTime.isAfter(endTime)

        if (!isInsideWindow && !hasWindowPassed) {
            // Window has not arrived yet today. Will check again on next periodic cycle.
            return Result.success()
        }

        if (settings.duplicateGuardEnabled && repository.isDateAlreadyInjected(today)) {
            // Day already completed
            return Result.success()
        }

        val healthManager = HealthConnectManager(appContext)
        if (!healthManager.isSdkAvailable()) {
            return Result.failure()
        }

        val result = healthManager.injectDataForDate(
            targetDate = today,
            minSteps = settings.minSteps,
            maxSteps = settings.maxSteps,
            windowStartHour = settings.windowStartHour,
            windowStartMinute = settings.windowStartMinute,
            windowEndHour = settings.windowEndHour,
            windowEndMinute = settings.windowEndMinute,
            isRealistic = settings.isRealisticSimulationEnabled
        )

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
                    simulationMode = if (settings.isRealisticSimulationEnabled) "NATURAL" else "SPIKE",
                    status = "SUCCESS",
                    notes = "Scheduled automated injection",
                    healthRecordIds = result.recordIds.joinToString(",")
                )
            )
            showNotification(result.steps, result.distanceMeters, result.caloriesKcal)
            return Result.success()
        } else {
            repository.insertLog(
                InjectionEntity(
                    timestamp = System.currentTimeMillis(),
                    targetDate = todayStr,
                    stepCount = result.steps,
                    distanceMeters = result.distanceMeters,
                    caloriesKcal = result.caloriesKcal,
                    speedKmh = result.speedKmh,
                    startTimeMillis = 0,
                    endTimeMillis = 0,
                    isBackfill = false,
                    simulationMode = if (settings.isRealisticSimulationEnabled) "NATURAL" else "SPIKE",
                    status = "ERROR",
                    notes = result.errorMessage ?: "Unknown error"
                )
            )
            return Result.retry()
        }
    }

    private fun showNotification(steps: Long, distanceMeters: Double, calories: Double) {
        val channelId = "step_injector_channel"
        val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Step Injector Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows notifications when automated step data is injected"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val kmStr = String.format("%.2f km", distanceMeters / 1000.0)
        val kcalStr = String.format("%.0f kcal", calories)
        val content = "Injected ${String.format("%,d", steps)} steps ($kmStr • $kcalStr)"

        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Step Injector: Steps Logged")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(1001, notification)
        } catch (e: SecurityException) {
            // Missing POST_NOTIFICATIONS
        }
    }

    companion object {
        const val WORK_NAME = "step_injector_daily_worker"

        fun scheduleDailyWork(context: Context) {
            // Set constraints to allow execution in battery saver, low battery, and offline
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            // Check every 15 minutes during the scheduled window
            val periodicWork = PeriodicWorkRequestBuilder<StepInjectionWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicWork
            )

            // Immediate one-shot run to verify schedule immediately without waiting
            val oneTimeWork = androidx.work.OneTimeWorkRequestBuilder<StepInjectionWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_immediate_check",
                androidx.work.ExistingWorkPolicy.REPLACE,
                oneTimeWork
            )
        }

        fun cancelDailyWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork("${WORK_NAME}_immediate_check")
        }
    }
}
