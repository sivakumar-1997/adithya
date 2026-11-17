package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DriveIqDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: DrivingSessionEntity): Long

    @Update
    suspend fun updateSession(session: DrivingSessionEntity)

    @Query("SELECT * FROM driving_sessions ORDER BY startedAtMs DESC")
    fun observeAllSessions(): Flow<List<DrivingSessionEntity>>

    @Query("SELECT * FROM driving_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): DrivingSessionEntity?

    @Query("UPDATE driving_sessions SET aiCoachSummary = :summary WHERE id = :sessionId")
    suspend fun updateSessionAiSummary(sessionId: Long, summary: String)

    @Query("DELETE FROM driving_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    @Query("DELETE FROM driving_sessions")
    suspend fun clearAllSessions()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: DrivingEventEntity): Long

    @Query("SELECT * FROM driving_events WHERE sessionId = :sessionId ORDER BY timestampMs DESC")
    fun observeEventsForSession(sessionId: Long): Flow<List<DrivingEventEntity>>

    @Query("SELECT * FROM driving_events WHERE sessionId = :sessionId ORDER BY timestampMs DESC")
    suspend fun getEventsForSession(sessionId: Long): List<DrivingEventEntity>

    @Query("SELECT * FROM driving_events ORDER BY timestampMs DESC LIMIT 100")
    fun observeRecentEvents(): Flow<List<DrivingEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTelemetrySample(sample: TelemetrySampleEntity)

    @Query("SELECT * FROM telemetry_samples WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun observeTelemetryForSession(sessionId: Long): Flow<List<TelemetrySampleEntity>>

    @Query("SELECT * FROM telemetry_samples WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getTelemetryForSession(sessionId: Long): List<TelemetrySampleEntity>

    // Spare Part Cumulative Wear
    @Query("SELECT * FROM spare_part_wear WHERE vehicleId = :vehicleId")
    fun observeSparePartWear(vehicleId: String): Flow<List<SparePartWearEntity>>

    @Query("SELECT * FROM spare_part_wear WHERE vehicleId = :vehicleId")
    suspend fun getSparePartWear(vehicleId: String): List<SparePartWearEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSparePartWear(wear: SparePartWearEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllSparePartWear(items: List<SparePartWearEntity>)

    @Query("DELETE FROM spare_part_wear WHERE vehicleId = :vehicleId")
    suspend fun resetSparePartWearForVehicle(vehicleId: String)
}
