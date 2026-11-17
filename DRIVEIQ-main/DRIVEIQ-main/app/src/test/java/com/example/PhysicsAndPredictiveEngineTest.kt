package com.example

import com.example.data.model.DrivingEventType
import com.example.data.model.TelemetryPoint
import com.example.data.model.VehicleProfile
import com.example.engine.PhysicsEngine
import com.example.engine.PhysicsInternalState
import com.example.engine.PhysicsStepInput
import com.example.engine.PredictiveRiskEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicsAndPredictiveEngineTest {

    private val physics = PhysicsEngine()
    private val riskEngine = PredictiveRiskEngine()

    @Test
    fun `sport sedan accelerates faster than heavy suv`() {
        var sedanState = PhysicsInternalState()
        var suvState = PhysicsInternalState()
        var t = 1000L

        repeat(40) {
            t += 50L
            sedanState = physics.step(
                sedanState,
                PhysicsStepInput(
                    dtSec = 0.05f,
                    nowMs = t,
                    vehicle = VehicleProfile.SPORT_SEDAN,
                    isEngineRunning = true,
                    speedLimitKmh = 120,
                    throttlePercent = 100f,
                    brakePercent = 0f,
                    steeringAngleDeg = 0f
                )
            ).nextState

            suvState = physics.step(
                suvState,
                PhysicsStepInput(
                    dtSec = 0.05f,
                    nowMs = t,
                    vehicle = VehicleProfile.HEAVY_SUV,
                    isEngineRunning = true,
                    speedLimitKmh = 120,
                    throttlePercent = 100f,
                    brakePercent = 0f,
                    steeringAngleDeg = 0f
                )
            ).nextState
        }

        assertTrue(sedanState.speedMs > suvState.speedMs)
    }

    @Test
    fun `hard braking triggers event once and respects cooldown`() {
        var state = PhysicsInternalState(speedMs = 28f) // ~100 km/h
        var triggeredCount = 0
        var t = 10_000L

        repeat(10) {
            t += 100L
            val out = physics.step(
                state,
                PhysicsStepInput(
                    dtSec = 0.1f,
                    nowMs = t,
                    vehicle = VehicleProfile.COMPACT_EV,
                    isEngineRunning = true,
                    speedLimitKmh = 80,
                    throttlePercent = 0f,
                    brakePercent = 95f,
                    steeringAngleDeg = 0f
                )
            )
            state = out.nextState
            if (out.triggeredEvent?.eventType == DrivingEventType.HARD_BRAKING) {
                triggeredCount++
            }
        }

        assertEquals(1, triggeredCount)
        assertEquals(1, state.hardBrakeCount)
        assertTrue(state.brakingStress > 10f)
        assertTrue(state.safetyScore < 100f)
    }

    @Test
    fun `sustained hard acceleration triggers after threshold`() {
        var state = PhysicsInternalState(speedMs = 5f)
        var t = 1000L
        var firedBanner: DrivingEventType? = null

        repeat(20) {
            t += 50L
            val out = physics.step(
                state,
                PhysicsStepInput(
                    dtSec = 0.05f,
                    nowMs = t,
                    vehicle = VehicleProfile.COMPACT_EV,
                    isEngineRunning = true,
                    speedLimitKmh = 120,
                    throttlePercent = 95f,
                    brakePercent = 0f,
                    steeringAngleDeg = 0f
                )
            )
            state = out.nextState
            if (out.triggeredEvent != null) {
                firedBanner = out.triggeredEvent.eventType
            }
        }

        assertNotNull(firedBanner)
        assertEquals(DrivingEventType.HARD_ACCELERATION, firedBanner)
    }

    @Test
    fun `engine off prevents acceleration and avoids false events`() {
        val out = physics.step(
            PhysicsInternalState(),
            PhysicsStepInput(
                dtSec = 0.1f,
                nowMs = 5000L,
                vehicle = VehicleProfile.SPORT_SEDAN,
                isEngineRunning = false,
                speedLimitKmh = 80,
                throttlePercent = 100f,
                brakePercent = 0f,
                steeringAngleDeg = 0f
            )
        )
        assertEquals(0f, out.speedKmh, 0.01f)
        assertNull(out.triggeredEvent)
    }

    @Test
    fun `predictive risk engine distinguishes rising trend from baseline`() {
        val history = (0 until 10).map { idx ->
            val risk = 15f + idx * 6f
            TelemetryPoint(
                timestampMs = 1000L + idx * 1000L,
                speedKmh = 60f + idx * 4f,
                rpm = 2500 + idx * 200,
                vehicleStress = 20f + idx * 3f,
                safetyScore = 95f - idx * 2f,
                ruleRisk = risk,
                trendRisk = risk * 0.9f
            )
        }

        val ruleNow = riskEngine.computeRuleBasedRisk(
            speedKmh = 105f,
            speedLimitKmh = 80,
            throttlePercent = 90f,
            brakePercent = 0f,
            steeringAngleDeg = 10f,
            longitudinalG = 0.6f,
            lateralG = 0.1f
        )
        val projection = riskEngine.computeTrendProjection(
            history = history,
            currentRuleRisk = ruleNow,
            totalAggressiveEvents = 3,
            sessionDurationSec = 45L
        )

        assertTrue(ruleNow > 40f)
        assertTrue(projection.riskSlopePerMin > 0f)
        assertTrue(projection.projected5MinRisk >= projection.currentTrendRisk)
    }

    @Test
    fun `spare part wear formats prior plus current into years and months`() {
        val formatted = com.example.engine.SparePartWearEngine.formatMonthsHuman(14f)
        assertEquals("1 year 2 months", formatted)

        val msg = com.example.engine.SparePartWearEngine.buildCumulativeStatement(
            partName = "Engine / EV Inverter",
            priorMonths = 2f,
            addedMonths = 12f,
            totalMonths = 14f
        )
        assertTrue(msg.contains("Already 2 months lost"))
        assertTrue(msg.contains("this time 1 year lost"))
        assertTrue(msg.contains("Total 1 year 2 months life lost"))
    }
}
