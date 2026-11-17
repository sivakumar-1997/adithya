package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "driving_sessions")
data class DrivingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val vehicleId: String,
    val vehicleName: String,
    val startedAtMs: Long,
    val endedAtMs: Long? = null,
    val durationSec: Long = 0L,
    val distanceKm: Float = 0f,
    val avgSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val finalSafetyScore: Float = 100f,
    val peakVehicleStress: Float = 0f,
    val riskLevel: String = "LOW RISK",
    val hardAccelCount: Int = 0,
    val hardBrakeCount: Int = 0,
    val sharpSteerCount: Int = 0,
    val overspeedCount: Int = 0,
    val speedLimitKmh: Int = 80,
    val totalPartMonthsLostThisSession: Float = 0f,
    val aiCoachSummary: String? = null
)

@Entity(
    tableName = "driving_events",
    foreignKeys = [
        ForeignKey(
            entity = DrivingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class DrivingEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val timestampMs: Long,
    val eventType: String,
    val severityScore: Int,
    val speedKmh: Float,
    val gForce: Float,
    val title: String,
    val whatHappened: String,
    val mechanicalImpact: String,
    val correctiveAction: String,
    val affectedSparePart: String = "",
    val priorPartLossMonths: Float = 0f,
    val eventPartLossMonths: Float = 0f,
    val totalPartLossMonths: Float = 0f,
    val sparePartSummary: String = ""
)

@Entity(
    tableName = "telemetry_samples",
    foreignKeys = [
        ForeignKey(
            entity = DrivingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class TelemetrySampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val timestampMs: Long,
    val speedKmh: Float,
    val rpm: Int,
    val throttlePercent: Float,
    val brakePercent: Float,
    val steeringDeg: Float,
    val longitudinalG: Float,
    val lateralG: Float,
    val vehicleStress: Float,
    val safetyScore: Float,
    val ruleRisk: Float,
    val trendRisk: Float
)

@Entity(
    tableName = "spare_part_wear",
    primaryKeys = ["vehicleId", "partId"]
)
data class SparePartWearEntity(
    val vehicleId: String,
    val partId: String,
    val cumulativeLostMonths: Float = 0f,
    val lastUpdatedMs: Long = System.currentTimeMillis()
)
