package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InjectionDao {
    @Query("SELECT * FROM injection_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<InjectionEntity>>

    @Query("SELECT * FROM injection_logs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): InjectionEntity?

    @Query("SELECT * FROM injection_logs WHERE targetDate = :date AND status = 'SUCCESS' LIMIT 1")
    suspend fun getSuccessForDate(date: String): InjectionEntity?

    @Query("SELECT COUNT(*) FROM injection_logs WHERE targetDate = :date AND status = 'SUCCESS'")
    suspend fun countSuccessForDate(date: String): Int

    @Query("SELECT * FROM injection_logs WHERE status = 'SUCCESS' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSuccess(): InjectionEntity?

    @Query("SELECT * FROM injection_logs WHERE status = 'SUCCESS' ORDER BY timestamp DESC LIMIT 1")
    fun getLatestSuccessFlow(): Flow<InjectionEntity?>

    @Query("SELECT * FROM injection_logs WHERE (targetDate = :date1 OR targetDate = :date2) AND status = 'SUCCESS' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSuccessForDates(date1: String, date2: String): InjectionEntity?

    @Query("SELECT COALESCE(SUM(stepCount), 0) FROM injection_logs WHERE targetDate = :date AND status = 'SUCCESS'")
    fun getTodayInjectedStepsFlow(date: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(stepCount), 0) FROM injection_logs WHERE targetDate = :date AND status = 'SUCCESS'")
    suspend fun getTodayInjectedSteps(date: String): Long

    @Query("SELECT COALESCE(SUM(stepCount), 0) FROM injection_logs WHERE (targetDate = :date1 OR targetDate = :date2) AND status = 'SUCCESS'")
    suspend fun getStepsForDates(date1: String, date2: String): Long

    @Query("SELECT COALESCE(SUM(stepCount), 0) FROM injection_logs WHERE (targetDate = :date1 OR targetDate = :date2) AND status = 'SUCCESS'")
    fun getStepsForDatesFlow(date1: String, date2: String): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: InjectionEntity): Long

    @Query("DELETE FROM injection_logs")
    suspend fun clearAllLogs()

    @Query("DELETE FROM injection_logs WHERE id = :id")
    suspend fun deleteById(id: Long)
}
