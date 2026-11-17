package com.example.engine

import com.example.data.model.ActiveWarningBanner
import com.example.data.model.DrivingEventType
import com.example.data.model.HumanSafetyState
import com.example.data.model.RiskLevel
import com.example.data.model.SparePartType
import com.example.data.model.VehicleIntelligenceState
import com.example.data.model.VehicleProfile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

data class PhysicsStepInput(
    val dtSec: Float,
    val nowMs: Long,
    val vehicle: VehicleProfile,
    val isEngineRunning: Boolean,
    val speedLimitKmh: Int,
    val throttlePercent: Float,
    val brakePercent: Float,
    val steeringAngleDeg: Float
)

data class PhysicsInternalState(
    val speedMs: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val rpm: Int = 0,
    val gearDisplay: String = "P",
    val longitudinalG: Float = 0f,
    val lateralG: Float = 0f,
    val distanceKm: Float = 0f,
    val engineStress: Float = 0f,
    val brakingStress: Float = 0f,
    val tyreStress: Float = 0f,
    val suspensionStress: Float = 0f,
    val transmissionStress: Float = 0f,
    val peakStress: Float = 0f,
    val safetyScore: Float = 100f,
    val hardAccelCount: Int = 0,
    val hardBrakeCount: Int = 0,
    val sharpSteerCount: Int = 0,
    val overspeedCount: Int = 0,
    val sustainedHighThrottleMs: Long = 0L,
    val lastEventTimestampByType: Map<DrivingEventType, Long> = emptyMap(),
    val priorPartWearMonths: Map<SparePartType, Float> = emptyMap(),
    val sessionPartWearMonths: Map<SparePartType, Float> = emptyMap()
)

data class PhysicsStepOutput(
    val nextState: PhysicsInternalState,
    val speedKmh: Float,
    val brakingDistanceMeters: Float,
    val vehicleIntelligence: VehicleIntelligenceState,
    val humanSafety: HumanSafetyState,
    val triggeredEvent: ActiveWarningBanner?,
    val isRevvingCueActive: Boolean,
    val isBrakeSquealTriggered: Boolean,
    val isOverspeedChimeTriggered: Boolean
)

class PhysicsEngine {

    companion object {
        private const val GRAVITY = 9.81f
        private const val EVENT_COOLDOWN_MS = 5000L
        private const val OVERSPEED_COOLDOWN_MS = 7500L
    }

    fun step(
        state: PhysicsInternalState,
        input: PhysicsStepInput
    ): PhysicsStepOutput {
        val dt = input.dtSec.coerceIn(0.005f, 0.2f)
        val v = input.vehicle
        val throttle = if (input.isEngineRunning) (input.throttlePercent / 100f).coerceIn(0f, 1f) else 0f
        val brake = (input.brakePercent / 100f).coerceIn(0f, 1f)

        val topSpeedMs = v.topSpeedKmh / 3.6f
        val speedRatio = (state.speedMs / topSpeedMs).coerceIn(0f, 1.05f)

        val torqueCurveFactor = when (v) {
            VehicleProfile.COMPACT_EV -> (1.05f - 0.55f * speedRatio.pow(1.4f)).coerceAtLeast(0.15f)
            VehicleProfile.SPORT_SEDAN -> (0.85f + 0.35f * sin(speedRatio * Math.PI.toFloat()) - 0.3f * speedRatio).coerceIn(0.25f, 1.15f)
            VehicleProfile.HEAVY_SUV -> (0.95f - 0.65f * speedRatio).coerceAtLeast(0.15f)
        }

        val driveAccel = if (input.isEngineRunning && brake < 0.05f) {
            throttle * v.maxAccelMs2 * torqueCurveFactor
        } else {
            0f
        }

        val aeroDrag = 0.00055f * state.speedMs * state.speedMs * (1650f / v.massKg)
        val rollingDrag = if (state.speedMs > 0.05f) 0.24f else 0f
        val coastRegenDrag = if (state.speedMs > 0.2f && throttle < 0.02f && brake < 0.02f) {
            if (v == VehicleProfile.COMPACT_EV) 0.72f else 0.35f
        } else 0f

        val brakeDecel = if (state.speedMs > 0.05f) {
            brake * v.maxBrakeDecelMs2
        } else {
            0f
        }

        val netAccel = if (!input.isEngineRunning) {
            -(aeroDrag + rollingDrag + brakeDecel + 0.4f)
        } else {
            driveAccel - aeroDrag - rollingDrag - coastRegenDrag - brakeDecel
        }

        val nextSpeedMs = (state.speedMs + netAccel * dt).coerceIn(0f, topSpeedMs * 1.03f)
        val effectiveAccel = if (nextSpeedMs <= 0.01f && netAccel < 0f) 0f else (nextSpeedMs - state.speedMs) / dt
        val speedKmh = nextSpeedMs * 3.6f
        val maxSpeedKmh = max(state.maxSpeedKmh, speedKmh)
        val distanceKm = state.distanceKm + (nextSpeedMs * dt) / 1000f

        val (gearDisplay, rpm) = computeGearAndRpm(
            vehicle = v,
            isEngineRunning = input.isEngineRunning,
            speedKmh = speedKmh,
            throttle = throttle
        )

        val longitudinalG = (effectiveAccel / GRAVITY).coerceIn(-1.4f, 1.4f)
        val lateralAccel = if (nextSpeedMs > 1.5f) {
            val steerRad = Math.toRadians((input.steeringAngleDeg * v.steeringRatio * 0.65f).toDouble()).toFloat()
            (nextSpeedMs * nextSpeedMs * kotlin.math.tan(steerRad)) / 22.0f
        } else 0f
        val lateralG = (lateralAccel / GRAVITY).coerceIn(-1.35f, 1.35f)
        val absLatG = abs(lateralG)

        val brakingDistanceMeters = if (nextSpeedMs > 0.5f) {
            (nextSpeedMs * nextSpeedMs) / (2f * v.maxBrakeDecelMs2 * 0.88f)
        } else 0f

        val rpmRatio = if (v.redlineRpm > 0) (rpm.toFloat() / v.redlineRpm).coerceIn(0f, 1f) else 0f
        val engineTarget = if (!input.isEngineRunning) 0f else {
            ((rpmRatio * 55f) + (throttle * 42f) + (if (speedKmh > input.speedLimitKmh) 12f else 0f)) * v.stressMultiplier
        }.coerceIn(0f, 100f)

        val brakeLoadInstant = if (speedKmh > 5f) {
            (brake * 78f + (abs(min(0f, longitudinalG)) * 45f)) * v.brakeStressFactor
        } else 0f
        val brakingTarget = brakeLoadInstant.coerceIn(0f, 100f)

        val tyreLoadInstant = ((absLatG * 74f) + (max(0f, longitudinalG) * 38f) + (brake * 28f * (speedKmh / 100f))) * v.tyreStressFactor
        val tyreTarget = tyreLoadInstant.coerceIn(0f, 100f)

        val suspensionLoadInstant = ((absLatG * 62f) + (abs(longitudinalG) * 48f)) * v.suspensionStressFactor
        val suspensionTarget = suspensionLoadInstant.coerceIn(0f, 100f)

        val transmissionTarget = if (!input.isEngineRunning) 0f else {
            ((throttle * 48f) + (rpmRatio * 34f) + (abs(longitudinalG) * 26f)) * v.stressMultiplier
        }.coerceIn(0f, 100f)

        val nextEngineStress = approachStress(state.engineStress, engineTarget, dt, riseRate = 1.6f, coolRate = 0.45f)
        val nextBrakingStress = approachStress(state.brakingStress, brakingTarget, dt, riseRate = 2.6f, coolRate = 0.55f)
        val nextTyreStress = approachStress(state.tyreStress, tyreTarget, dt, riseRate = 2.4f, coolRate = 0.60f)
        val nextSuspensionStress = approachStress(state.suspensionStress, suspensionTarget, dt, riseRate = 2.5f, coolRate = 0.70f)
        val nextTransmissionStress = approachStress(state.transmissionStress, transmissionTarget, dt, riseRate = 1.8f, coolRate = 0.50f)

        val overallStress = (
            nextEngineStress * 0.24f +
            nextBrakingStress * 0.24f +
            nextTyreStress * 0.20f +
            nextSuspensionStress * 0.16f +
            nextTransmissionStress * 0.16f
        ).coerceIn(0f, 100f)
        val peakStress = max(state.peakStress, overallStress)

        // Update continuous spare-part wear if high sustained stress
        val updatedSessionWear = state.sessionPartWearMonths.toMutableMap()
        if (input.isEngineRunning) {
            val tickDeltas = SparePartWearEngine.computeContinuousTickWear(
                dtSec = dt,
                engineStress = nextEngineStress,
                brakingStress = nextBrakingStress,
                tyreStress = nextTyreStress,
                suspensionStress = nextSuspensionStress,
                transmissionStress = nextTransmissionStress
            )
            tickDeltas.forEach { (part, delta) ->
                val prior = state.priorPartWearMonths[part] ?: 0f
                val curr = updatedSessionWear[part] ?: 0f
                updatedSessionWear[part] = (curr + delta).coerceAtMost((part.baselineLifeMonths - prior).coerceAtLeast(0f))
            }
        }

        val currentTotalByPart = SparePartType.entries.associateWith { part ->
            ((state.priorPartWearMonths[part] ?: 0f) + (updatedSessionWear[part] ?: 0f))
                .coerceAtMost(part.baselineLifeMonths)
        }

        // Event Detection with Cooldown
        val sustainedHighThrottleMs = if (input.isEngineRunning && throttle >= 0.80f) {
            state.sustainedHighThrottleMs + (dt * 1000f).toLong()
        } else {
            0L
        }

        val lastEvents = state.lastEventTimestampByType.toMutableMap()
        var triggeredEvent: ActiveWarningBanner? = null
        var hardAccelCount = state.hardAccelCount
        var hardBrakeCount = state.hardBrakeCount
        var sharpSteerCount = state.sharpSteerCount
        var overspeedCount = state.overspeedCount
        var safetyPenalty = 0f
        var brakeSquealTriggered = false
        var overspeedChimeTriggered = false

        fun canFire(type: DrivingEventType, cooldownMs: Long): Boolean {
            val last = lastEvents[type] ?: return true
            return (input.nowMs - last) >= cooldownMs
        }

        fun applyEventAndBuildBanner(
            type: DrivingEventType,
            severityScore: Int,
            title: String,
            whatHappened: String,
            mechanicalImpact: String,
            correctiveAction: String,
            gForce: Float
        ): ActiveWarningBanner {
            val impact = SparePartWearEngine.computeEventPartImpact(
                eventType = type,
                vehicle = v,
                speedKmh = speedKmh,
                gForce = gForce,
                priorCumulativeByPart = currentTotalByPart
            )
            impact.perPartDeltaMonths.forEach { (part, delta) ->
                val prior = state.priorPartWearMonths[part] ?: 0f
                val curr = updatedSessionWear[part] ?: 0f
                updatedSessionWear[part] = (curr + delta).coerceAtMost((part.baselineLifeMonths - prior).coerceAtLeast(0f))
            }
            return ActiveWarningBanner(
                id = input.nowMs,
                eventType = type,
                severityScore = severityScore,
                title = title,
                whatHappened = whatHappened,
                mechanicalImpact = mechanicalImpact,
                correctiveAction = correctiveAction,
                affectedSparePart = impact.primaryPart.displayName,
                priorPartLossMonths = impact.priorLostMonths,
                eventPartLossMonths = impact.eventLossMonths,
                totalPartLossMonths = impact.totalLostMonths,
                sparePartSummary = impact.customerMessage,
                timestampMs = input.nowMs
            )
        }

        if (input.isEngineRunning && brake >= 0.72f && speedKmh >= 24f &&
            (abs(min(0f, longitudinalG)) >= v.hardBrakeThresholdG || brake >= 0.85f)
        ) {
            if (canFire(DrivingEventType.HARD_BRAKING, EVENT_COOLDOWN_MS)) {
                lastEvents[DrivingEventType.HARD_BRAKING] = input.nowMs
                hardBrakeCount++
                safetyPenalty += 7.5f
                brakeSquealTriggered = true
                triggeredEvent = applyEventAndBuildBanner(
                    type = DrivingEventType.HARD_BRAKING,
                    severityScore = 84,
                    title = "Hard Braking Detected",
                    whatHappened = "Sudden ${(brake * 100).toInt()}% brake input at ${speedKmh.toInt()} km/h (${String.format("%.2f", longitudinalG)} G).",
                    mechanicalImpact = "High rotor thermal spike and forward suspension dive; est. ${String.format("%.1f", brakingDistanceMeters)}m stopping distance.",
                    correctiveAction = "Scan farther ahead and apply progressive 30–55% braking pressure earlier.",
                    gForce = abs(longitudinalG)
                )
            }
        } else if (input.isEngineRunning && abs(input.steeringAngleDeg) >= 26f && speedKmh >= 36f &&
            (absLatG >= v.sharpTurnThresholdG || abs(input.steeringAngleDeg) >= 34f)
        ) {
            if (canFire(DrivingEventType.SHARP_STEERING, EVENT_COOLDOWN_MS)) {
                lastEvents[DrivingEventType.SHARP_STEERING] = input.nowMs
                sharpSteerCount++
                safetyPenalty += 6.5f
                triggeredEvent = applyEventAndBuildBanner(
                    type = DrivingEventType.SHARP_STEERING,
                    severityScore = 78,
                    title = "Sharp Steering Maneuver",
                    whatHappened = "Abrupt ${input.steeringAngleDeg.toInt()}° steering angle at ${speedKmh.toInt()} km/h (${String.format("%.2f", absLatG)} G lateral).",
                    mechanicalImpact = "Accelerated sidewall tyre scrub and high lateral roll force on ${v.displayName} suspension.",
                    correctiveAction = "Decelerate before corner entry and feed steering angle smoothly under 20°.",
                    gForce = absLatG
                )
            }
        } else if (input.isEngineRunning && sustainedHighThrottleMs >= 550L &&
            (longitudinalG >= v.hardAccelThresholdG || throttle >= 0.90f)
        ) {
            if (canFire(DrivingEventType.HARD_ACCELERATION, EVENT_COOLDOWN_MS)) {
                lastEvents[DrivingEventType.HARD_ACCELERATION] = input.nowMs
                hardAccelCount++
                safetyPenalty += 5.0f
                triggeredEvent = applyEventAndBuildBanner(
                    type = DrivingEventType.HARD_ACCELERATION,
                    severityScore = 68,
                    title = "Hard Acceleration Sustained",
                    whatHappened = "Sustained ${(throttle * 100).toInt()}% throttle launch (${String.format("%.2f", longitudinalG)} G) at $rpm RPM.",
                    mechanicalImpact = "Elevated drivetrain torque load, thermal surge, and tyre traction scrub.",
                    correctiveAction = "Modulate throttle between 35–65% for smoother torque delivery and part longevity.",
                    gForce = abs(longitudinalG)
                )
            }
        } else if (input.isEngineRunning && speedKmh > input.speedLimitKmh + 4f) {
            if (canFire(DrivingEventType.OVERSPEEDING, OVERSPEED_COOLDOWN_MS)) {
                lastEvents[DrivingEventType.OVERSPEEDING] = input.nowMs
                overspeedCount++
                safetyPenalty += 6.0f
                overspeedChimeTriggered = true
                val overBy = (speedKmh - input.speedLimitKmh).toInt()
                triggeredEvent = applyEventAndBuildBanner(
                    type = DrivingEventType.OVERSPEEDING,
                    severityScore = if (overBy > 25) 92 else 74,
                    title = "Speed Limit Exceeded (+$overBy km/h)",
                    whatHappened = "Vehicle reached ${speedKmh.toInt()} km/h in an active ${input.speedLimitKmh} km/h zone.",
                    mechanicalImpact = "Kinetic energy scales quadratically; stopping distance jumped to ${String.format("%.1f", brakingDistanceMeters)}m.",
                    correctiveAction = "Ease off accelerator and feather brake gently to return below ${input.speedLimitKmh} km/h.",
                    gForce = max(abs(longitudinalG), absLatG)
                )
            }
        }

        val wearStatuses = SparePartWearEngine.buildWearStatuses(
            priorBaselineByPart = state.priorPartWearMonths,
            sessionLostByPart = updatedSessionWear
        )
        val worstPart = wearStatuses.maxByOrNull { it.totalLostMonths }
        val worstAdvisory = if (worstPart == null || worstPart.totalLostMonths <= 0.02f) {
            "All spare parts at baseline health. Smooth inputs preserve full factory lifespan."
        } else {
            worstPart.impactSummary
        }

        val continuousRiskDrain = if (!input.isEngineRunning) 0f else {
            var drain = 0f
            if (speedKmh > input.speedLimitKmh + 2f) drain += 1.2f * dt
            if (throttle > 0.85f) drain += 0.7f * dt
            if (brake > 0.80f && speedKmh > 20f) drain += 0.9f * dt
            if (abs(input.steeringAngleDeg) > 30f && speedKmh > 35f) drain += 0.8f * dt
            drain
        }
        val smoothRecovery = if (input.isEngineRunning && nextSpeedMs > 2f &&
            speedKmh <= input.speedLimitKmh && throttle <= 0.65f && brake <= 0.45f && absLatG < 0.35f
        ) {
            0.45f * dt
        } else 0f

        val nextSafetyScore = (state.safetyScore - safetyPenalty - continuousRiskDrain + smoothRecovery)
            .coerceIn(0f, 100f)

        val riskLevel = when {
            nextSafetyScore >= 82f && overallStress < 45f -> RiskLevel.LOW
            nextSafetyScore >= 62f && overallStress < 65f -> RiskLevel.MODERATE
            nextSafetyScore >= 38f -> RiskLevel.HIGH
            else -> RiskLevel.CRITICAL
        }

        val healthRecommendation = buildVehicleRecommendation(
            vehicle = v,
            overall = overallStress,
            engine = nextEngineStress,
            braking = nextBrakingStress,
            tyres = nextTyreStress,
            suspension = nextSuspensionStress,
            transmission = nextTransmissionStress
        )

        val (behaviorSummary, smoothTip) = buildHumanCoaching(
            isEngineRunning = input.isEngineRunning,
            safetyScore = nextSafetyScore,
            hardAccel = hardAccelCount,
            hardBrake = hardBrakeCount,
            sharpSteer = sharpSteerCount,
            overspeed = overspeedCount,
            speedKmh = speedKmh,
            speedLimit = input.speedLimitKmh
        )

        val nextState = PhysicsInternalState(
            speedMs = nextSpeedMs,
            maxSpeedKmh = maxSpeedKmh,
            rpm = rpm,
            gearDisplay = gearDisplay,
            longitudinalG = longitudinalG,
            lateralG = lateralG,
            distanceKm = distanceKm,
            engineStress = nextEngineStress,
            brakingStress = nextBrakingStress,
            tyreStress = nextTyreStress,
            suspensionStress = nextSuspensionStress,
            transmissionStress = nextTransmissionStress,
            peakStress = peakStress,
            safetyScore = nextSafetyScore,
            hardAccelCount = hardAccelCount,
            hardBrakeCount = hardBrakeCount,
            sharpSteerCount = sharpSteerCount,
            overspeedCount = overspeedCount,
            sustainedHighThrottleMs = sustainedHighThrottleMs,
            lastEventTimestampByType = lastEvents,
            priorPartWearMonths = state.priorPartWearMonths,
            sessionPartWearMonths = updatedSessionWear
        )

        return PhysicsStepOutput(
            nextState = nextState,
            speedKmh = speedKmh,
            brakingDistanceMeters = brakingDistanceMeters,
            vehicleIntelligence = VehicleIntelligenceState(
                overallStressScore = overallStress,
                engineStress = nextEngineStress,
                brakingStress = nextBrakingStress,
                tyreStress = nextTyreStress,
                suspensionStress = nextSuspensionStress,
                transmissionStress = nextTransmissionStress,
                peakStressScore = peakStress,
                healthRecommendation = healthRecommendation,
                sparePartWearList = wearStatuses,
                worstSparePartAdvisory = worstAdvisory
            ),
            humanSafety = HumanSafetyState(
                safetyScore = nextSafetyScore,
                riskLevel = riskLevel,
                hardAccelCount = hardAccelCount,
                hardBrakeCount = hardBrakeCount,
                sharpSteerCount = sharpSteerCount,
                overspeedCount = overspeedCount,
                behavioralSummary = behaviorSummary,
                smoothDrivingTip = smoothTip
            ),
            triggeredEvent = triggeredEvent,
            isRevvingCueActive = input.isEngineRunning && (throttle > 0.55f || rpm > v.redlineRpm * 0.65f),
            isBrakeSquealTriggered = brakeSquealTriggered,
            isOverspeedChimeTriggered = overspeedChimeTriggered
        )
    }

    private fun approachStress(
        current: Float,
        target: Float,
        dt: Float,
        riseRate: Float,
        coolRate: Float
    ): Float {
        val rate = if (target > current) riseRate else coolRate
        val factor = (rate * dt).coerceIn(0f, 1f)
        return (current + (target - current) * factor).coerceIn(0f, 100f)
    }

    private fun computeGearAndRpm(
        vehicle: VehicleProfile,
        isEngineRunning: Boolean,
        speedKmh: Float,
        throttle: Float
    ): Pair<String, Int> {
        if (!isEngineRunning) return "P" to 0
        if (vehicle == VehicleProfile.COMPACT_EV) {
            val evRpm = ((speedKmh / vehicle.topSpeedKmh) * vehicle.redlineRpm * 0.92f + throttle * 900f)
                .toInt().coerceIn(0, vehicle.redlineRpm)
            val gear = if (speedKmh < 0.5f && throttle < 0.02f) "D0" else "D"
            return gear to evRpm
        }

        if (speedKmh < 1.5f && throttle < 0.05f) {
            return "D1" to vehicle.idleRpm
        }

        val speedPerGear = vehicle.topSpeedKmh / vehicle.gearCount.toFloat()
        val gearNum = ((speedKmh / speedPerGear).toInt() + 1).coerceIn(1, vehicle.gearCount)
        val gearMinSpeed = (gearNum - 1) * speedPerGear * 0.78f
        val gearSpan = (speedPerGear * 1.15f).coerceAtLeast(18f)
        val inGearRatio = ((speedKmh - gearMinSpeed) / gearSpan).coerceIn(0.08f, 0.96f)
        val throttleKick = throttle * 950f
        val rpm = (vehicle.idleRpm + inGearRatio * (vehicle.redlineRpm - vehicle.idleRpm) + throttleKick)
            .toInt().coerceIn(vehicle.idleRpm, vehicle.redlineRpm)
        return "D$gearNum" to rpm
    }

    private fun buildVehicleRecommendation(
        vehicle: VehicleProfile,
        overall: Float,
        engine: Float,
        braking: Float,
        tyres: Float,
        suspension: Float,
        transmission: Float
    ): String {
        if (overall < 22f) {
            return "${vehicle.displayName} subsystems operating at low simulated thermal & mechanical load."
        }
        val maxSub = maxOf(engine, braking, tyres, suspension, transmission)
        return when (maxSub) {
            braking -> "Simulated brake thermal load elevated (${braking.toInt()}%). Lengthen following gap and coast before braking."
            tyres -> "High lateral/traction scrub on ${vehicle.displayName} tyres (${tyres.toInt()}%). Reduce entry speed into turns."
            suspension -> "Elevated chassis weight-transfer load (${suspension.toInt()}%). Avoid combined hard braking and sharp steering."
            transmission -> "High drivetrain torque load (${transmission.toInt()}%). Smooth out rapid throttle transitions."
            else -> "Elevated powertrain/motor load (${engine.toInt()}%). Cruise at steady throttle to allow thermal recovery."
        }
    }

    private fun buildHumanCoaching(
        isEngineRunning: Boolean,
        safetyScore: Float,
        hardAccel: Int,
        hardBrake: Int,
        sharpSteer: Int,
        overspeed: Int,
        speedKmh: Float,
        speedLimit: Int
    ): Pair<String, String> {
        if (!isEngineRunning) {
            return "Engine standby. Start simulation to monitor live behavioral telemetry." to
                "Keep throttle gradual (30–60%) and steer progressively below 20° at speed."
        }
        val total = hardAccel + hardBrake + sharpSteer + overspeed
        if (total == 0 && speedKmh <= speedLimit) {
            return "Controlled, predictive driving pattern with smooth pedal modulation (${safetyScore.toInt()}/100)." to
                "Maintain steady cruise speed and anticipate corners early."
        }
        val dominant = listOf(
            "hard braking" to hardBrake,
            "sharp steering" to sharpSteer,
            "overspeeding" to overspeed,
            "hard acceleration" to hardAccel
        ).maxByOrNull { it.second }

        val summary = when {
            speedKmh > speedLimit + 5f -> "Currently exceeding ${speedLimit} km/h limit; $total aggressive event(s) logged this run."
            dominant != null && dominant.second > 0 ->
                "Primary pattern: repeated ${dominant.first} (${dominant.second}x). Safety index at ${safetyScore.toInt()}/100."
            else -> "Moderate telemetry variance detected across recent inputs."
        }

        val tip = when (dominant?.first) {
            "hard braking" -> "Lift off throttle 2–3 seconds earlier and apply smooth, progressive brake pressure."
            "sharp steering" -> "Complete braking in a straight line before turning; keep steering inputs under ±20°."
            "overspeeding" -> "Watch the ${speedLimit} km/h HUD threshold and feather accelerator on straights."
            else -> "Roll onto the accelerator gradually instead of pinning 90–100% throttle."
        }
        return summary to tip
    }
}
