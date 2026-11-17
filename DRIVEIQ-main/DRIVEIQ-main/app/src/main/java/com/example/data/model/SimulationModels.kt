package com.example.data.model

enum class DrivingEventType(val label: String) {
    HARD_ACCELERATION("Hard Acceleration"),
    HARD_BRAKING("Hard Braking"),
    SHARP_STEERING("Sharp Steering"),
    OVERSPEEDING("Overspeeding")
}

enum class RiskLevel(val label: String) {
    LOW("LOW RISK"),
    MODERATE("MODERATE"),
    HIGH("HIGH RISK"),
    CRITICAL("CRITICAL")
}

enum class SparePartType(
    val id: String,
    val displayName: String,
    val baselineLifeMonths: Float
) {
    BRAKE_PADS_ROTORS("brakes", "Brake Pads & Rotors", 60f),
    TYRES("tyres", "Tyres & Sidewalls", 48f),
    POWERTRAIN("powertrain", "Engine / EV Inverter", 120f),
    TRANSMISSION("transmission", "Transmission / Reducer", 108f),
    SUSPENSION("suspension", "Suspension & Bushings", 84f);

    companion object {
        fun fromId(id: String): SparePartType =
            entries.find { it.id == id } ?: POWERTRAIN
    }
}

data class SparePartWearStatus(
    val part: SparePartType,
    val baselineMonths: Float,
    val priorLostMonths: Float,
    val sessionLostMonths: Float,
    val totalLostMonths: Float,
    val remainingMonths: Float,
    val healthPercent: Float,
    val impactSummary: String
)

enum class SimulationInputSource(val label: String) {
    INTERNAL_TOUCH("Internal Touch Sim"),
    EXTERNAL_CARLA("CARLA / External Bridge")
}

enum class BridgeConnectionState(val label: String) {
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting..."),
    LISTENING("Listening (LAN Server)"),
    CONNECTED_LIVE("Live CARLA Stream"),
    ERROR("Connection Error")
}

data class CarlaBridgeStatus(
    val inputSource: SimulationInputSource = SimulationInputSource.INTERNAL_TOUCH,
    val connectionState: BridgeConnectionState = BridgeConnectionState.DISCONNECTED,
    val endpointUrl: String = "ws://10.0.2.2:8765",
    val localServerRunning: Boolean = false,
    val localWebUrl: String = "http://127.0.0.1:8765",
    val externalFramesReceived: Long = 0L,
    val lastPacketLatencyMs: Long = 0L,
    val lastSourceTag: String = "None",
    val statusMessage: String = "Ready for CARLA WebSocket/HTTP stream or local Touch Simulator."
)

data class ActiveWarningBanner(
    val id: Long,
    val eventType: DrivingEventType,
    val severityScore: Int,
    val title: String,
    val whatHappened: String,
    val mechanicalImpact: String,
    val correctiveAction: String,
    val affectedSparePart: String,
    val priorPartLossMonths: Float,
    val eventPartLossMonths: Float,
    val totalPartLossMonths: Float,
    val sparePartSummary: String,
    val timestampMs: Long
)

data class VehicleIntelligenceState(
    val overallStressScore: Float = 0f,
    val engineStress: Float = 0f,
    val brakingStress: Float = 0f,
    val tyreStress: Float = 0f,
    val suspensionStress: Float = 0f,
    val transmissionStress: Float = 0f,
    val peakStressScore: Float = 0f,
    val healthRecommendation: String = "Powertrain thermal and mechanical loads are within nominal cruising range.",
    val sparePartWearList: List<SparePartWearStatus> = emptyList(),
    val worstSparePartAdvisory: String = "No excess spare-part lifespan loss recorded yet."
)

data class HumanSafetyState(
    val safetyScore: Float = 100f,
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val hardAccelCount: Int = 0,
    val hardBrakeCount: Int = 0,
    val sharpSteerCount: Int = 0,
    val overspeedCount: Int = 0,
    val behavioralSummary: String = "Engine standby. Start simulation to monitor live behavioral telemetry.",
    val smoothDrivingTip: String = "Apply gradual throttle ramps and maintain steady steering inputs."
) {
    val totalAggressiveEvents: Int
        get() = hardAccelCount + hardBrakeCount + sharpSteerCount + overspeedCount
}

data class TelemetryPoint(
    val timestampMs: Long,
    val speedKmh: Float,
    val rpm: Int,
    val vehicleStress: Float,
    val safetyScore: Float,
    val ruleRisk: Float,
    val trendRisk: Float
)

data class PredictiveProjection(
    val currentRuleRisk: Float = 0f,
    val currentTrendRisk: Float = 0f,
    val riskSlopePerMin: Float = 0f,
    val projected1MinRisk: Float = 0f,
    val projected3MinRisk: Float = 0f,
    val projected5MinRisk: Float = 0f,
    val projectionSummary: String = "Insufficient telemetry window for trend projection."
)

data class SimulatorUiState(
    val selectedVehicle: VehicleProfile = VehicleProfile.COMPACT_EV,
    val isEngineRunning: Boolean = false,
    val activeSessionId: Long? = null,
    val speedLimitKmh: Int = 80,
    val speedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val rpm: Int = 0,
    val gearDisplay: String = "P",
    val throttlePercent: Float = 0f,
    val brakePercent: Float = 0f,
    val steeringAngleDeg: Float = 0f,
    val longitudinalG: Float = 0f,
    val lateralG: Float = 0f,
    val brakingDistanceMeters: Float = 0f,
    val distanceKm: Float = 0f,
    val sessionDurationSec: Long = 0L,
    val isAudioMuted: Boolean = false,
    val activeAudioCueLabel: String? = null,
    val autoCenterSteering: Boolean = true,
    val activeWarning: ActiveWarningBanner? = null,
    val vehicleIntelligence: VehicleIntelligenceState = VehicleIntelligenceState(),
    val humanSafety: HumanSafetyState = HumanSafetyState(),
    val predictiveProjection: PredictiveProjection = PredictiveProjection(),
    val liveTelemetryHistory: List<TelemetryPoint> = emptyList(),
    val liveEvents: List<ActiveWarningBanner> = emptyList(),
    val bridgeStatus: CarlaBridgeStatus = CarlaBridgeStatus()
)

data class AiCoachInsight(
    val summary: String,
    val primaryRiskFactor: String,
    val mechanicalInsight: String,
    val sparePartLifeForecast: String,
    val coachingTips: List<String>
)

sealed interface AiCoachUiState {
    data object Idle : AiCoachUiState
    data object Loading : AiCoachUiState
    data class Success(val insight: AiCoachInsight, val sourceLabel: String) : AiCoachUiState
    data class Error(val message: String) : AiCoachUiState
}
