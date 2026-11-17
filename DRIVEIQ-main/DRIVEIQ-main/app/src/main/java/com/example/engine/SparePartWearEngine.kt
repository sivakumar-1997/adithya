package com.example.engine

import com.example.data.model.DrivingEventType
import com.example.data.model.SparePartType
import com.example.data.model.SparePartWearStatus
import com.example.data.model.VehicleProfile
import kotlin.math.roundToInt

data class EventPartImpact(
    val primaryPart: SparePartType,
    val priorLostMonths: Float,
    val eventLossMonths: Float,
    val totalLostMonths: Float,
    val customerMessage: String,
    val perPartDeltaMonths: Map<SparePartType, Float>
)

object SparePartWearEngine {

    /**
     * Formats a duration in months cleanly as e.g. "2 months", "1 year", "1 year 2 months", or "18 days".
     */
    fun formatMonthsHuman(months: Float): String {
        if (months <= 0.02f) return "0 months"
        if (months < 1.0f) {
            val days = (months * 30f).roundToInt().coerceAtLeast(1)
            return if (days == 1) "1 day" else "$days days"
        }
        val roundedTotalMonths = months.roundToInt().coerceAtLeast(1)
        val years = roundedTotalMonths / 12
        val remMonths = roundedTotalMonths % 12
        return buildString {
            if (years > 0) {
                append(if (years == 1) "1 year" else "$years years")
            }
            if (remMonths > 0) {
                if (years > 0) append(" ")
                append(if (remMonths == 1) "1 month" else "$remMonths months")
            }
        }
    }

    /**
     * Calculates incremental spare-part life reduction for a detected aggressive driving event
     * and returns the cumulative "prior + this time = total" impact statement.
     */
    fun computeEventPartImpact(
        eventType: DrivingEventType,
        vehicle: VehicleProfile,
        speedKmh: Float,
        gForce: Float,
        priorCumulativeByPart: Map<SparePartType, Float>
    ): EventPartImpact {
        val speedScale = (speedKmh / 80f).coerceIn(0.6f, 2.0f)
        val gScale = (gForce / 0.6f).coerceIn(0.7f, 2.2f)
        val mult = vehicle.stressMultiplier

        val deltas: Map<SparePartType, Float> = when (eventType) {
            DrivingEventType.HARD_ACCELERATION -> mapOf(
                SparePartType.POWERTRAIN to (2.0f * mult * gScale),
                SparePartType.TRANSMISSION to (1.4f * mult * gScale),
                SparePartType.TYRES to (0.9f * vehicle.tyreStressFactor * gScale)
            )
            DrivingEventType.HARD_BRAKING -> mapOf(
                SparePartType.BRAKE_PADS_ROTORS to (2.4f * vehicle.brakeStressFactor * speedScale),
                SparePartType.TYRES to (1.0f * vehicle.tyreStressFactor * speedScale),
                SparePartType.SUSPENSION to (0.7f * vehicle.suspensionStressFactor * gScale)
            )
            DrivingEventType.SHARP_STEERING -> mapOf(
                SparePartType.TYRES to (2.0f * vehicle.tyreStressFactor * gScale),
                SparePartType.SUSPENSION to (1.6f * vehicle.suspensionStressFactor * gScale)
            )
            DrivingEventType.OVERSPEEDING -> mapOf(
                SparePartType.POWERTRAIN to (1.3f * mult * speedScale),
                SparePartType.TYRES to (0.8f * vehicle.tyreStressFactor * speedScale),
                SparePartType.BRAKE_PADS_ROTORS to (0.6f * vehicle.brakeStressFactor * speedScale)
            )
        }

        val primaryPart = deltas.maxByOrNull { it.value }?.key ?: SparePartType.POWERTRAIN
        val priorLost = priorCumulativeByPart[primaryPart] ?: 0f
        val eventLoss = deltas[primaryPart] ?: 0f
        val totalLost = (priorLost + eventLoss).coerceAtMost(primaryPart.baselineLifeMonths)

        val message = buildCumulativeStatement(
            partName = primaryPart.displayName,
            priorMonths = priorLost,
            addedMonths = eventLoss,
            totalMonths = totalLost
        )

        return EventPartImpact(
            primaryPart = primaryPart,
            priorLostMonths = priorLost,
            eventLossMonths = eventLoss,
            totalLostMonths = totalLost,
            customerMessage = message,
            perPartDeltaMonths = deltas
        )
    }

    /**
     * Continuous micro-wear during sustained high stress (>62%) even between discrete events.
     */
    fun computeContinuousTickWear(
        dtSec: Float,
        engineStress: Float,
        brakingStress: Float,
        tyreStress: Float,
        suspensionStress: Float,
        transmissionStress: Float
    ): Map<SparePartType, Float> {
        val deltas = mutableMapOf<SparePartType, Float>()
        if (engineStress > 62f) {
            deltas[SparePartType.POWERTRAIN] = ((engineStress - 62f) / 38f) * 0.14f * dtSec
        }
        if (brakingStress > 62f) {
            deltas[SparePartType.BRAKE_PADS_ROTORS] = ((brakingStress - 62f) / 38f) * 0.18f * dtSec
        }
        if (tyreStress > 62f) {
            deltas[SparePartType.TYRES] = ((tyreStress - 62f) / 38f) * 0.16f * dtSec
        }
        if (suspensionStress > 62f) {
            deltas[SparePartType.SUSPENSION] = ((suspensionStress - 62f) / 38f) * 0.13f * dtSec
        }
        if (transmissionStress > 62f) {
            deltas[SparePartType.TRANSMISSION] = ((transmissionStress - 62f) / 38f) * 0.12f * dtSec
        }
        return deltas
    }

    fun buildWearStatuses(
        priorBaselineByPart: Map<SparePartType, Float>,
        sessionLostByPart: Map<SparePartType, Float>
    ): List<SparePartWearStatus> {
        return SparePartType.entries.map { part ->
            val prior = (priorBaselineByPart[part] ?: 0f).coerceAtLeast(0f)
            val session = (sessionLostByPart[part] ?: 0f).coerceAtLeast(0f)
            val total = (prior + session).coerceAtMost(part.baselineLifeMonths)
            val remaining = (part.baselineLifeMonths - total).coerceAtLeast(0f)
            val healthPct = ((remaining / part.baselineLifeMonths) * 100f).coerceIn(0f, 100f)

            val summary = if (total <= 0.02f) {
                "Nominal wear • Full ${formatMonthsHuman(part.baselineLifeMonths)} design life intact."
            } else {
                buildCumulativeStatement(part.displayName, prior, session, total) +
                    " (${formatMonthsHuman(remaining)} remaining)."
            }

            SparePartWearStatus(
                part = part,
                baselineMonths = part.baselineLifeMonths,
                priorLostMonths = prior,
                sessionLostMonths = session,
                totalLostMonths = total,
                remainingMonths = remaining,
                healthPercent = healthPct,
                impactSummary = summary
            )
        }
    }

    fun buildCumulativeStatement(
        partName: String,
        priorMonths: Float,
        addedMonths: Float,
        totalMonths: Float
    ): String {
        val priorStr = formatMonthsHuman(priorMonths)
        val addedStr = formatMonthsHuman(addedMonths)
        val totalStr = formatMonthsHuman(totalMonths)
        return when {
            priorMonths <= 0.02f && addedMonths <= 0.02f ->
                "$partName: No excess life lost yet."
            priorMonths <= 0.02f ->
                "$partName: $addedStr life lost this run (total $totalStr lost)."
            addedMonths <= 0.02f ->
                "$partName: Already $priorStr lost previously (total $totalStr lost)."
            else ->
                "$partName: Already $priorStr lost + this time $addedStr lost = Total $totalStr life lost."
        }
    }
}
