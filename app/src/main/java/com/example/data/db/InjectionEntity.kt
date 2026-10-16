package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "injection_logs")
data class InjectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val targetDate: String, // e.g. "2026-10-14"
    val stepCount: Long,
    val distanceMeters: Double,
    val caloriesKcal: Double,
    val speedKmh: Double = 0.0,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val isBackfill: Boolean,
    val simulationMode: String, // "NATURAL", "SPIKE", "CUSTOM"
    val status: String, // "SUCCESS", "DUPLICATE_SKIPPED", "ERROR"
    val notes: String = "",
    val healthRecordIds: String = "" // comma separated IDs for deletion
)
