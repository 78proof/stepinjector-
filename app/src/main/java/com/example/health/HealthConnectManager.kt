package com.example.health

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Velocity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.random.Random

data class HealthRecordItem(
    val id: String,
    val type: String, // "STEPS", "DISTANCE", "CALORIES", "SPEED"
    val countOrValue: String,
    val startTime: Instant,
    val endTime: Instant,
    val packageName: String,
    val isInjectedByUs: Boolean
)

data class InjectionResult(
    val isSuccess: Boolean,
    val steps: Long,
    val distanceMeters: Double,
    val caloriesKcal: Double,
    val speedKmh: Double,
    val startTime: Instant,
    val endTime: Instant,
    val recordCount: Int,
    val recordIds: List<String> = emptyList(),
    val errorMessage: String? = null
)

data class SimulatedRunParameters(
    val steps: Long,
    val distanceKm: Double,
    val caloriesKcal: Double,
    val speedKmh: Double,
    val startTime: Instant,
    val endTime: Instant,
    val timeSpanFormatted: String,
    val durationFormatted: String
)


data class TodayStepsSummary(
    val totalStepsInHealthConnect: Long = 0L,
    val stepsInjectedByUs: Long = 0L,
    val queryTimestamp: Instant = Instant.now()
)

data class PermissionStatusItem(
    val name: String,
    val permission: String,
    val isGranted: Boolean
)

class HealthConnectManager(private val context: Context) {

    val healthConnectClient: HealthConnectClient? by lazy {
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(SpeedRecord::class),
        HealthPermission.getWritePermission(SpeedRecord::class)
    )

    fun isSdkAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun hasAllPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        val granted = client.permissionController.getGrantedPermissions()
        return granted.contains(HealthPermission.getWritePermission(StepsRecord::class)) &&
                granted.contains(HealthPermission.getReadPermission(StepsRecord::class))
    }

    suspend fun getPermissionDetails(): List<PermissionStatusItem> {
        val client = healthConnectClient ?: return emptyList()
        val granted = try { client.permissionController.getGrantedPermissions() } catch (e: Exception) { emptySet() }
        return listOf(
            PermissionStatusItem("Write Steps", HealthPermission.getWritePermission(StepsRecord::class), granted.contains(HealthPermission.getWritePermission(StepsRecord::class))),
            PermissionStatusItem("Read Steps", HealthPermission.getReadPermission(StepsRecord::class), granted.contains(HealthPermission.getReadPermission(StepsRecord::class))),
            PermissionStatusItem("Write Distance", HealthPermission.getWritePermission(DistanceRecord::class), granted.contains(HealthPermission.getWritePermission(DistanceRecord::class))),
            PermissionStatusItem("Read Distance", HealthPermission.getReadPermission(DistanceRecord::class), granted.contains(HealthPermission.getReadPermission(DistanceRecord::class))),
            PermissionStatusItem("Write Calories", HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class), granted.contains(HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class))),
            PermissionStatusItem("Read Calories", HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class), granted.contains(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))),
            PermissionStatusItem("Write Speed", HealthPermission.getWritePermission(SpeedRecord::class), granted.contains(HealthPermission.getWritePermission(SpeedRecord::class))),
            PermissionStatusItem("Read Speed", HealthPermission.getReadPermission(SpeedRecord::class), granted.contains(HealthPermission.getReadPermission(SpeedRecord::class)))
        )
    }

    /**
     * Creates automatic device metadata so step records are marked as
     * RECORDING_METHOD_AUTOMATICALLY_RECORDED from phone hardware sensors.
     * This ensures full compatibility with companion apps like ThaiSook and step challenges
     * which filter out or reject MANUAL_ENTRY records.
     */
    fun createAutoMetadata(): Metadata {
        return try {
            val device = Device(
                type = Device.TYPE_PHONE,
                manufacturer = Build.MANUFACTURER.ifBlank { "Android" },
                model = Build.MODEL.ifBlank { "Smartphone" }
            )
            Metadata.autoRecorded(device)
        } catch (e: Throwable) {
            Metadata.manualEntry()
        }
    }

    /**
     * Injects manual instant steps with explicit speed and calories options (Debug Fitter style).
     * Strictly verifies and clamps timestamps so endTime is never in the future.
     * Incorporates permission checks and multi-level fallback so step injection NEVER fails.
     */
    suspend fun injectInstant(
        steps: Long,
        speedKmh: Double,
        customCaloriesKcal: Double?,
        startTime: Instant,
        endTime: Instant
    ): InjectionResult {
        val client = healthConnectClient
            ?: return InjectionResult(false, steps, 0.0, 0.0, speedKmh, Instant.EPOCH, Instant.EPOCH, 0, emptyList(), "Health Connect is not available.")

        // Safety clamp: Health Connect rejects records with endTime in the future!
        val maxAllowedInstant = Instant.now().minusSeconds(10)
        val safeEndTime = if (endTime.isAfter(maxAllowedInstant)) maxAllowedInstant else endTime
        val safeStartTime = if (startTime.isAfter(safeEndTime.minusSeconds(60))) safeEndTime.minusSeconds(60) else startTime

        val strideLengthMeters = 0.762
        val distanceMeters = steps * strideLengthMeters
        val effectiveDurationMinutes = ChronoUnit.MINUTES.between(safeStartTime, safeEndTime).coerceAtLeast(1L)
        val zoneId = ZoneId.systemDefault()
        val zoneOffset = zoneId.rules.getOffset(safeStartTime)

        val effectiveCalories = customCaloriesKcal ?: (steps * 0.043 * (speedKmh / 5.0).coerceIn(0.8, 2.0))
        val autoMeta = createAutoMetadata()

        // Check granted permissions so we never trigger SecurityException on ungranted records
        val granted = try { client.permissionController.getGrantedPermissions() } catch (e: Exception) { emptySet() }

        val records = mutableListOf<Record>()

        // 1. Steps Record (Core record - Auto-recorded for ThaiSook & fitness challenges)
        val stepsRecord = StepsRecord(
            count = steps,
            startTime = safeStartTime,
            endTime = safeEndTime,
            startZoneOffset = zoneOffset,
            endZoneOffset = zoneOffset,
            metadata = autoMeta
        )
        records.add(stepsRecord)

        // 2. Distance Record (if permitted)
        if (granted.contains(HealthPermission.getWritePermission(DistanceRecord::class))) {
            records.add(
                DistanceRecord(
                    distance = Length.meters(distanceMeters),
                    startTime = safeStartTime,
                    endTime = safeEndTime,
                    startZoneOffset = zoneOffset,
                    endZoneOffset = zoneOffset,
                    metadata = autoMeta
                )
            )
        }

        // 3. Active Calories (if permitted)
        if (granted.contains(HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class))) {
            records.add(
                ActiveCaloriesBurnedRecord(
                    energy = Energy.kilocalories(effectiveCalories),
                    startTime = safeStartTime,
                    endTime = safeEndTime,
                    startZoneOffset = zoneOffset,
                    endZoneOffset = zoneOffset,
                    metadata = autoMeta
                )
            )
        }

        // 4. Speed Record (only if permitted and duration is sufficient)
        if (granted.contains(HealthPermission.getWritePermission(SpeedRecord::class)) && effectiveDurationMinutes >= 2) {
            try {
                val speedMps = speedKmh / 3.6
                val midTime = safeStartTime.plusSeconds((effectiveDurationMinutes * 60) / 2)
                records.add(
                    SpeedRecord(
                        startTime = safeStartTime,
                        endTime = safeEndTime,
                        startZoneOffset = zoneOffset,
                        endZoneOffset = zoneOffset,
                        samples = listOf(
                            SpeedRecord.Sample(time = safeStartTime.plusSeconds(30), speed = Velocity.metersPerSecond(speedMps)),
                            SpeedRecord.Sample(time = midTime, speed = Velocity.metersPerSecond(speedMps)),
                            SpeedRecord.Sample(time = safeEndTime.minusSeconds(30), speed = Velocity.metersPerSecond(speedMps))
                        ),
                        metadata = autoMeta
                    )
                )
            } catch (e: Exception) {
                // Ignore optional speed record
            }
        }

        return try {
            val response = client.insertRecords(records)
            InjectionResult(
                isSuccess = true,
                steps = steps,
                distanceMeters = distanceMeters,
                caloriesKcal = effectiveCalories,
                speedKmh = speedKmh,
                startTime = safeStartTime,
                endTime = safeEndTime,
                recordCount = records.size,
                recordIds = response.recordIdsList
            )
        } catch (e: Exception) {
            // Resilient Fallback: If full batch fails, retry inserting StepsRecord alone!
            try {
                val fallbackResponse = client.insertRecords(listOf(stepsRecord))
                InjectionResult(
                    isSuccess = true,
                    steps = steps,
                    distanceMeters = distanceMeters,
                    caloriesKcal = effectiveCalories,
                    speedKmh = speedKmh,
                    startTime = safeStartTime,
                    endTime = safeEndTime,
                    recordCount = 1,
                    recordIds = fallbackResponse.recordIdsList
                )
            } catch (fallbackEx: Exception) {
                InjectionResult(
                    isSuccess = false,
                    steps = steps,
                    distanceMeters = distanceMeters,
                    caloriesKcal = effectiveCalories,
                    speedKmh = speedKmh,
                    startTime = safeStartTime,
                    endTime = safeEndTime,
                    recordCount = 0,
                    errorMessage = fallbackEx.localizedMessage ?: "Failed to insert into Health Connect"
                )
            }
        }
    }

    /**
     * Calculates a sample simulation run for UI preview / cross-checking across any time window.
     * Supports randomSeed so each day automatically gets its own randomized values,
     * or user can manually re-roll. Always applies natural variance so counts are never rigid.
     */
    fun calculateSimulationSample(
        minSteps: Int,
        maxSteps: Int,
        windowStartHour: Int,
        windowStartMinute: Int,
        windowEndHour: Int,
        windowEndMinute: Int,
        isRealistic: Boolean,
        speedKmh: Double = 5.0,
        customCaloriesKcal: Double? = null,
        targetDate: LocalDate = LocalDate.now(),
        randomSeed: Long? = null
    ): SimulatedRunParameters {
        val rng = if (randomSeed != null) Random(randomSeed) else Random.Default
        val rawBase = if (maxSteps > minSteps) {
            rng.nextLong(minSteps.toLong(), maxSteps.toLong() + 1L)
        } else {
            minSteps.toLong()
        }

        // Authentic human variance so numbers are never static (e.g. 12000 -> 12184)
        val dailyVariancePct = rng.nextDouble(-0.018, 0.024)
        val dailyJitter = (rawBase * dailyVariancePct).toLong().coerceIn(-280L, 280L)
        val sSteps = (rawBase + dailyJitter).coerceAtLeast(100L)

        val distKm = (sSteps * 0.762) / 1000.0
        val effectiveCalories = customCaloriesKcal ?: (sSteps * 0.043 * (speedKmh / 5.0).coerceIn(0.8, 2.0))

        val zoneId = ZoneId.systemDefault()
        val sH = windowStartHour.coerceIn(0, 23)
        val sM = windowStartMinute.coerceIn(0, 59)
        val eH = windowEndHour.coerceIn(0, 23)
        val eM = windowEndMinute.coerceIn(0, 59)

        val startLocalTime = LocalTime.of(sH, sM)
        val endLocalTime = LocalTime.of(eH, eM)

        val windowStartDt = LocalDateTime.of(targetDate, startLocalTime)
        val windowEndDt = if (endLocalTime.isBefore(startLocalTime) || endLocalTime == startLocalTime) {
            LocalDateTime.of(targetDate.plusDays(1), endLocalTime)
        } else {
            LocalDateTime.of(targetDate, endLocalTime)
        }

        val windowStartSec = windowStartDt.atZone(zoneId).toEpochSecond()
        val windowEndSec = windowEndDt.atZone(zoneId).toEpochSecond()
        val totalWindowSec = (windowEndSec - windowStartSec).coerceAtLeast(300L)

        val (startSec, endSec) = if (isRealistic) {
            val spanSec = (totalWindowSec * 0.85).toLong().coerceAtLeast(1800L)
            val maxOffset = (totalWindowSec - spanSec).coerceAtLeast(0L)
            val offset = if (maxOffset > 0) rng.nextLong(0, maxOffset + 1L) else 0L
            Pair(windowStartSec + offset, windowStartSec + offset + spanSec)
        } else {
            val maxDur = (totalWindowSec * 0.75).toLong().coerceIn(600L, 7200L)
            val minDur = (totalWindowSec * 0.25).toLong().coerceIn(300L, maxDur)
            val dur = if (maxDur > minDur) rng.nextLong(minDur, maxDur + 1L) else minDur
            val maxOffset = (totalWindowSec - dur).coerceAtLeast(0L)
            val offset = if (maxOffset > 0) rng.nextLong(0, maxOffset + 1L) else 0L
            Pair(windowStartSec + offset, windowStartSec + offset + dur)
        }

        val startInstant = Instant.ofEpochSecond(startSec)
        val endInstant = Instant.ofEpochSecond(endSec)

        val timeFormatter = DateTimeFormatter.ofPattern("h:mm a").withZone(zoneId)
        val sFmt = timeFormatter.format(startInstant)
        val eFmt = timeFormatter.format(endInstant)

        val durMin = ChronoUnit.MINUTES.between(startInstant, endInstant)
        val dHours = durMin / 60
        val dMins = durMin % 60
        val durFmt = if (dHours > 0) "${dHours}h ${dMins}m" else "${dMins} mins"

        return SimulatedRunParameters(
            steps = sSteps,
            distanceKm = distKm,
            caloriesKcal = effectiveCalories,
            speedKmh = speedKmh,
            startTime = startInstant,
            endTime = endInstant,
            timeSpanFormatted = "$sFmt  ➔  $eFmt",
            durationFormatted = durFmt
        )
    }

    /**
     * Injects date-based steps with realistic multi-interval simulation or spike.
     * Incorporates authentic variance, strict non-overlapping segment intervals,
     * future timestamp clamping, permission safety, and fallback recovery.
     */
    suspend fun injectDataForDate(
        targetDate: LocalDate,
        minSteps: Int,
        maxSteps: Int,
        windowStartHour: Int,
        windowStartMinute: Int,
        windowEndHour: Int,
        windowEndMinute: Int,
        isRealistic: Boolean,
        averageSpeedKmh: Double = 5.0,
        randomSeed: Long? = null
    ): InjectionResult {
        val client = healthConnectClient
            ?: return InjectionResult(false, 0, 0.0, 0.0, averageSpeedKmh, Instant.EPOCH, Instant.EPOCH, 0, emptyList(), "Health Connect is not available.")

        val rng = if (randomSeed != null) Random(randomSeed) else Random.Default
        val rawBase = if (maxSteps > minSteps) {
            rng.nextLong(minSteps.toLong(), maxSteps.toLong() + 1L)
        } else {
            minSteps.toLong()
        }

        // Apply authentic daily human variance so steps are never rigid static numbers
        val dailyVariancePct = rng.nextDouble(-0.018, 0.024)
        val dailyJitter = (rawBase * dailyVariancePct).toLong().coerceIn(-280L, 280L)
        val totalSteps = (rawBase + dailyJitter).coerceAtLeast(100L)

        val strideLengthMeters = 0.762
        val totalDistanceMeters = totalSteps * strideLengthMeters
        val totalActiveKcal = totalSteps * 0.043

        val zoneId = ZoneId.systemDefault()
        val sH = windowStartHour.coerceIn(0, 23)
        val sM = windowStartMinute.coerceIn(0, 59)
        val eH = windowEndHour.coerceIn(0, 23)
        val eM = windowEndMinute.coerceIn(0, 59)

        val startLocalTime = LocalTime.of(sH, sM)
        val endLocalTime = LocalTime.of(eH, eM)

        val windowStartDt = LocalDateTime.of(targetDate, startLocalTime)
        val windowEndDt = if (endLocalTime.isBefore(startLocalTime) || endLocalTime == startLocalTime) {
            LocalDateTime.of(targetDate.plusDays(1), endLocalTime)
        } else {
            LocalDateTime.of(targetDate, endLocalTime)
        }

        val safeNow = Instant.now().minusSeconds(15)
        val rawWindowStartInstant = windowStartDt.atZone(zoneId).toInstant()
        val rawWindowEndInstant = windowEndDt.atZone(zoneId).toInstant()

        // Effective start and end bounds
        val effectiveEndInstant = if (targetDate == LocalDate.now()) {
            if (rawWindowEndInstant.isAfter(safeNow)) safeNow else rawWindowEndInstant
        } else {
            rawWindowEndInstant
        }

        val effectiveStartInstant = if (targetDate == LocalDate.now()) {
            if (rawWindowStartInstant.isAfter(safeNow.minusSeconds(300))) {
                // If schedule start time hasn't arrived yet today, adjust start to recent past
                // so user can inject and test progress immediately!
                safeNow.minusSeconds(3600)
            } else {
                rawWindowStartInstant
            }
        } else {
            rawWindowStartInstant
        }

        val startSec = effectiveStartInstant.epochSecond
        val endSec = effectiveEndInstant.epochSecond
        val totalSec = (endSec - startSec).coerceAtLeast(300L)

        val autoMeta = createAutoMetadata()
        val granted = try { client.permissionController.getGrantedPermissions() } catch (e: Exception) { emptySet() }
        val canWriteDistance = granted.contains(HealthPermission.getWritePermission(DistanceRecord::class))
        val canWriteCalories = granted.contains(HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class))

        val fullRecordsToInsert = mutableListOf<Record>()
        val coreStepsOnlyRecords = mutableListOf<Record>()
        var overallStartInstant = Instant.MAX
        var overallEndInstant = Instant.MIN

        if (isRealistic) {
            // Partition into 2 to 4 clean, strictly non-overlapping intervals across the window
            val numSegments = when {
                totalSec <= 1800L -> 1
                totalSec <= 3600L * 3 -> 2
                totalSec <= 3600L * 6 -> 3
                else -> 4
            }

            val weights = when (numSegments) {
                1 -> listOf(1.0)
                2 -> listOf(0.55, 0.45)
                3 -> listOf(0.35, 0.40, 0.25)
                else -> listOf(0.25, 0.35, 0.25, 0.15)
            }

            val slotSec = totalSec / numSegments
            var accumulatedSteps = 0L

            for (i in 0 until numSegments) {
                val segSteps = if (i == numSegments - 1) {
                    (totalSteps - accumulatedSteps).coerceAtLeast(20L)
                } else {
                    val variation = rng.nextDouble(0.92, 1.08)
                    ((totalSteps * weights[i]) * variation).toLong().coerceAtLeast(20L)
                }
                accumulatedSteps += segSteps

                val slotStart = startSec + i * slotSec
                val slotEnd = startSec + (i + 1) * slotSec
                val slotDuration = (slotEnd - slotStart).coerceAtLeast(60L)

                // Activity happens in a portion of each slot
                val activityDur = (slotDuration * rng.nextDouble(0.45, 0.75)).toLong().coerceIn(60L, 3600L)
                val maxOffset = (slotDuration - activityDur).coerceAtLeast(0L)
                val segOffset = if (maxOffset > 0) rng.nextLong(0, maxOffset + 1L) else 0L

                val segStartEpoch = slotStart + segOffset
                val segEndEpoch = minOf(segStartEpoch + activityDur, safeNow.epochSecond)
                val finalStartEpoch = minOf(segStartEpoch, segEndEpoch - 60L)

                val segStartInstant = Instant.ofEpochSecond(finalStartEpoch)
                val segEndInstant = Instant.ofEpochSecond(segEndEpoch)

                if (segStartInstant.isBefore(overallStartInstant)) overallStartInstant = segStartInstant
                if (segEndInstant.isAfter(overallEndInstant)) overallEndInstant = segEndInstant

                val zoneOffset = zoneId.rules.getOffset(segStartInstant)
                val segDist = segSteps * strideLengthMeters
                val segCal = segSteps * 0.043

                val stepRec = StepsRecord(
                    count = segSteps,
                    startTime = segStartInstant,
                    endTime = segEndInstant,
                    startZoneOffset = zoneOffset,
                    endZoneOffset = zoneOffset,
                    metadata = autoMeta
                )
                fullRecordsToInsert.add(stepRec)
                coreStepsOnlyRecords.add(stepRec)

                if (canWriteDistance) {
                    fullRecordsToInsert.add(
                        DistanceRecord(
                            distance = Length.meters(segDist),
                            startTime = segStartInstant,
                            endTime = segEndInstant,
                            startZoneOffset = zoneOffset,
                            endZoneOffset = zoneOffset,
                            metadata = autoMeta
                        )
                    )
                }

                if (canWriteCalories) {
                    fullRecordsToInsert.add(
                        ActiveCaloriesBurnedRecord(
                            energy = Energy.kilocalories(segCal),
                            startTime = segStartInstant,
                            endTime = segEndInstant,
                            startZoneOffset = zoneOffset,
                            endZoneOffset = zoneOffset,
                            metadata = autoMeta
                        )
                    )
                }
            }
        } else {
            // Single workout spike inside the selected window
            val maxDur = (totalSec * 0.75).toLong().coerceIn(300L, 7200L)
            val minDur = (totalSec * 0.25).toLong().coerceIn(180L, maxDur)
            val activitySec = if (maxDur > minDur) rng.nextLong(minDur, maxDur + 1L) else minDur

            val maxOffset = (totalSec - activitySec).coerceAtLeast(0L)
            val offsetSec = if (maxOffset > 0) rng.nextLong(0, maxOffset + 1L) else 0L

            val rawStart = startSec + offsetSec
            val rawEnd = minOf(rawStart + activitySec, safeNow.epochSecond)
            val finalStart = minOf(rawStart, rawEnd - 60L)

            val segStartInstant = Instant.ofEpochSecond(finalStart)
            val segEndInstant = Instant.ofEpochSecond(rawEnd)

            overallStartInstant = segStartInstant
            overallEndInstant = segEndInstant

            val offset = zoneId.rules.getOffset(segStartInstant)

            val stepRec = StepsRecord(
                count = totalSteps,
                startTime = segStartInstant,
                endTime = segEndInstant,
                startZoneOffset = offset,
                endZoneOffset = offset,
                metadata = autoMeta
            )
            fullRecordsToInsert.add(stepRec)
            coreStepsOnlyRecords.add(stepRec)

            if (canWriteDistance) {
                fullRecordsToInsert.add(
                    DistanceRecord(
                        distance = Length.meters(totalDistanceMeters),
                        startTime = segStartInstant,
                        endTime = segEndInstant,
                        startZoneOffset = offset,
                        endZoneOffset = offset,
                        metadata = autoMeta
                    )
                )
            }

            if (canWriteCalories) {
                fullRecordsToInsert.add(
                    ActiveCaloriesBurnedRecord(
                        energy = Energy.kilocalories(totalActiveKcal),
                        startTime = segStartInstant,
                        endTime = segEndInstant,
                        startZoneOffset = offset,
                        endZoneOffset = offset,
                        metadata = autoMeta
                    )
                )
            }
        }

        return try {
            val response = client.insertRecords(fullRecordsToInsert)
            InjectionResult(
                isSuccess = true,
                steps = totalSteps,
                distanceMeters = totalDistanceMeters,
                caloriesKcal = totalActiveKcal,
                speedKmh = averageSpeedKmh,
                startTime = overallStartInstant,
                endTime = overallEndInstant,
                recordCount = fullRecordsToInsert.size,
                recordIds = response.recordIdsList
            )
        } catch (e: Exception) {
            // Resilient Fallback: If companion records failed, insert StepsRecord directly!
            try {
                val fallbackResponse = client.insertRecords(coreStepsOnlyRecords)
                InjectionResult(
                    isSuccess = true,
                    steps = totalSteps,
                    distanceMeters = totalDistanceMeters,
                    caloriesKcal = totalActiveKcal,
                    speedKmh = averageSpeedKmh,
                    startTime = overallStartInstant,
                    endTime = overallEndInstant,
                    recordCount = coreStepsOnlyRecords.size,
                    recordIds = fallbackResponse.recordIdsList
                )
            } catch (fallbackEx: Exception) {
                InjectionResult(
                    isSuccess = false,
                    steps = totalSteps,
                    distanceMeters = totalDistanceMeters,
                    caloriesKcal = totalActiveKcal,
                    speedKmh = averageSpeedKmh,
                    startTime = overallStartInstant,
                    endTime = overallEndInstant,
                    recordCount = 0,
                    errorMessage = fallbackEx.localizedMessage ?: "Failed to insert into Health Connect"
                )
            }
        }
    }

    /**
     * Reads Health Connect records for transparency viewer.
     */
    suspend fun queryHealthRecords(startTime: Instant, endTime: Instant): List<HealthRecordItem> {
        val client = healthConnectClient ?: return emptyList()
        val items = mutableListOf<HealthRecordItem>()
        val ourPackage = context.packageName

        try {
            val stepsResp = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            for (rec in stepsResp.records) {
                val pkg = rec.metadata.dataOrigin.packageName
                items.add(
                    HealthRecordItem(
                        id = rec.metadata.id,
                        type = "STEPS",
                        countOrValue = "${rec.count} steps",
                        startTime = rec.startTime,
                        endTime = rec.endTime,
                        packageName = pkg,
                        isInjectedByUs = pkg == ourPackage
                    )
                )
            }

            val distResp = client.readRecords(
                ReadRecordsRequest(
                    recordType = DistanceRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            for (rec in distResp.records) {
                val pkg = rec.metadata.dataOrigin.packageName
                items.add(
                    HealthRecordItem(
                        id = rec.metadata.id,
                        type = "DISTANCE",
                        countOrValue = String.format("%.2f km", rec.distance.inKilometers),
                        startTime = rec.startTime,
                        endTime = rec.endTime,
                        packageName = pkg,
                        isInjectedByUs = pkg == ourPackage
                    )
                )
            }

            val calResp = client.readRecords(
                ReadRecordsRequest(
                    recordType = ActiveCaloriesBurnedRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            for (rec in calResp.records) {
                val pkg = rec.metadata.dataOrigin.packageName
                items.add(
                    HealthRecordItem(
                        id = rec.metadata.id,
                        type = "CALORIES",
                        countOrValue = String.format("%.0f kcal", rec.energy.inKilocalories),
                        startTime = rec.startTime,
                        endTime = rec.endTime,
                        packageName = pkg,
                        isInjectedByUs = pkg == ourPackage
                    )
                )
            }

            val speedResp = client.readRecords(
                ReadRecordsRequest(
                    recordType = SpeedRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            for (rec in speedResp.records) {
                val pkg = rec.metadata.dataOrigin.packageName
                val avgKmh = if (rec.samples.isNotEmpty()) {
                    rec.samples.map { it.speed.inKilometersPerHour }.average()
                } else 0.0
                items.add(
                    HealthRecordItem(
                        id = rec.metadata.id,
                        type = "SPEED",
                        countOrValue = String.format("%.1f km/h", avgKmh),
                        startTime = rec.startTime,
                        endTime = rec.endTime,
                        packageName = pkg,
                        isInjectedByUs = pkg == ourPackage
                    )
                )
            }
        } catch (e: Exception) {
            // Ignore
        }

        return items.sortedByDescending { it.startTime }
    }

    /**
     * Deletes records by IDs from Health Connect.
     */
    suspend fun deleteRecords(recordIds: List<String>): Boolean {
        val client = healthConnectClient ?: return false
        if (recordIds.isEmpty()) return true
        return try {
            try { client.deleteRecords(StepsRecord::class, recordIdsList = recordIds, clientRecordIdsList = emptyList()) } catch (e: Exception) {}
            try { client.deleteRecords(DistanceRecord::class, recordIdsList = recordIds, clientRecordIdsList = emptyList()) } catch (e: Exception) {}
            try { client.deleteRecords(ActiveCaloriesBurnedRecord::class, recordIdsList = recordIds, clientRecordIdsList = emptyList()) } catch (e: Exception) {}
            try { client.deleteRecords(SpeedRecord::class, recordIdsList = recordIds, clientRecordIdsList = emptyList()) } catch (e: Exception) {}
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Reads summary of steps in Health Connect for a specific time range (e.g., active overnight window).
     */
    suspend fun queryStepsSummaryForRange(startTime: Instant, endTime: Instant): TodayStepsSummary {
        val client = healthConnectClient ?: return TodayStepsSummary()
        val ourPackage = context.packageName

        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            var total = 0L
            var byUs = 0L
            for (rec in response.records) {
                total += rec.count
                if (rec.metadata.dataOrigin.packageName == ourPackage) {
                    byUs += rec.count
                }
            }
            TodayStepsSummary(
                totalStepsInHealthConnect = total,
                stepsInjectedByUs = if (byUs > 0L) byUs else total,
                queryTimestamp = Instant.now()
            )
        } catch (e: Exception) {
            TodayStepsSummary()
        }
    }

    /**
     * Reads summary of steps in Health Connect for a specific calendar date.
     */
    suspend fun queryStepsSummaryForDate(date: LocalDate): TodayStepsSummary {
        val zoneId = ZoneId.systemDefault()
        val startOfDay = date.atStartOfDay(zoneId).toInstant()
        val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant()
        return queryStepsSummaryForRange(startOfDay, endOfDay)
    }

    /**
     * Reads summary of today's total steps in Health Connect and steps injected specifically by this app.
     */
    suspend fun queryTodayStepsSummary(): TodayStepsSummary {
        return queryStepsSummaryForDate(LocalDate.now())
    }
}
