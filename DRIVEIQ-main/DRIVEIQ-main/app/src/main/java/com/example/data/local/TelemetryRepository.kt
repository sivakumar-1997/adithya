package com.example.data.local

import kotlinx.coroutines.flow.Flow

class TelemetryRepository(private val dao: DriveIqDao) {
    val allSessions: Flow<List<DrivingSessionEntity>> = dao.observeAllSessions()
    val recentEvents: Flow<List<DrivingEventEntity>> = dao.observeRecentEvents()

    suspend fun startSession(session: DrivingSessionEntity): Long =
        dao.insertSession(session)

    suspend fun updateSession(session: DrivingSessionEntity) =
        dao.updateSession(session)

    suspend fun getSessionById(sessionId: Long): DrivingSessionEntity? =
        dao.getSessionById(sessionId)

    suspend fun saveAiSummary(sessionId: Long, summary: String) =
        dao.updateSessionAiSummary(sessionId, summary)

    suspend fun deleteSession(sessionId: Long) =
        dao.deleteSessionById(sessionId)

    suspend fun clearAllSessions() =
        dao.clearAllSessions()

    suspend fun logEvent(event: DrivingEventEntity): Long =
        dao.insertEvent(event)

    fun observeEventsForSession(sessionId: Long): Flow<List<DrivingEventEntity>> =
        dao.observeEventsForSession(sessionId)

    suspend fun getEventsForSession(sessionId: Long): List<DrivingEventEntity> =
        dao.getEventsForSession(sessionId)

    suspend fun logTelemetrySample(sample: TelemetrySampleEntity) =
        dao.insertTelemetrySample(sample)

    fun observeTelemetryForSession(sessionId: Long): Flow<List<TelemetrySampleEntity>> =
        dao.observeTelemetryForSession(sessionId)

    suspend fun getTelemetryForSession(sessionId: Long): List<TelemetrySampleEntity> =
        dao.getTelemetryForSession(sessionId)

    fun observeSparePartWear(vehicleId: String): Flow<List<SparePartWearEntity>> =
        dao.observeSparePartWear(vehicleId)

    suspend fun getSparePartWear(vehicleId: String): List<SparePartWearEntity> =
        dao.getSparePartWear(vehicleId)

    suspend fun saveSparePartWear(items: List<SparePartWearEntity>) =
        dao.upsertAllSparePartWear(items)

    suspend fun resetSparePartWear(vehicleId: String) =
        dao.resetSparePartWearForVehicle(vehicleId)
}
