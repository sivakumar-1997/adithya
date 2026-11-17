package com.example.engine

import com.example.data.model.PredictiveProjection
import com.example.data.model.TelemetryPoint
import kotlin.math.abs

class PredictiveRiskEngine {

    /**
     * Instantaneous Rule-Based Risk (0..100):
     * Reacts immediately to deterministic threshold violations (speed vs limit, G-forces, pedal extremes).
     */
    fun computeRuleBasedRisk(
        speedKmh: Float,
        speedLimitKmh: Int,
        throttlePercent: Float,
        brakePercent: Float,
        steeringAngleDeg: Float,
        longitudinalG: Float,
        lateralG: Float
    ): Float {
        if (speedKmh < 1f && throttlePercent < 5f) return 0f
        val overspeedRatio = ((speedKmh - speedLimitKmh) / speedLimitKmh.toFloat().coerceAtLeast(40f)).coerceAtLeast(0f)
        val speedRisk = (overspeedRatio * 130f).coerceIn(0f, 45f)
        val accelRisk = if (throttlePercent > 75f) ((throttlePercent - 75f) * 1.1f) else 0f
        val brakeRisk = if (brakePercent > 65f && speedKmh > 20f) ((brakePercent - 65f) * 1.25f) else 0f
        val steerRisk = if (abs(steeringAngleDeg) > 22f && speedKmh > 30f) {
            ((abs(steeringAngleDeg) - 22f) * 1.35f)
        } else 0f
        val gForceRisk = ((abs(longitudinalG) + abs(lateralG)) * 18f).coerceIn(0f, 35f)

        return (speedRisk + accelRisk + brakeRisk + steerRisk + gForceRisk).coerceIn(0f, 100f)
    }

    /**
     * Trend-Based Risk & Multi-Horizon Projection:
     * Combines Exponentially Weighted Moving Average (EWMA) + linear regression slope over recent window
     * + cumulative event momentum to forecast +1m, +3m, +5m risk trajectories.
     */
    fun computeTrendProjection(
        history: List<TelemetryPoint>,
        currentRuleRisk: Float,
        totalAggressiveEvents: Int,
        sessionDurationSec: Long
    ): PredictiveProjection {
        if (history.isEmpty()) {
            return PredictiveProjection(
                currentRuleRisk = currentRuleRisk,
                currentTrendRisk = currentRuleRisk * 0.5f,
                riskSlopePerMin = 0f,
                projected1MinRisk = currentRuleRisk * 0.5f,
                projected3MinRisk = currentRuleRisk * 0.5f,
                projected5MinRisk = currentRuleRisk * 0.5f,
                projectionSummary = "Collecting baseline telemetry samples for trend regression..."
            )
        }

        val window = history.takeLast(24)
        // Compute EWMA of rule risk
        var ewma = window.first().ruleRisk
        val alpha = 0.24f
        for (i in 1 until window.size) {
            ewma = alpha * window[i].ruleRisk + (1f - alpha) * ewma
        }
        ewma = alpha * currentRuleRisk + (1f - alpha) * ewma

        // Event frequency momentum penalty
        val minutesElapsed = (sessionDurationSec / 60f).coerceAtLeast(0.25f)
        val eventRatePerMin = totalAggressiveEvents / minutesElapsed
        val momentumBoost = (eventRatePerMin * 4.5f).coerceIn(0f, 22f)
        val currentTrendRisk = (ewma * 0.85f + momentumBoost).coerceIn(0f, 100f)

        // Linear regression slope over recent window (risk points per minute)
        val n = window.size
        val slopePerMin = if (n >= 4) {
            val t0 = window.first().timestampMs
            val xs = window.map { (it.timestampMs - t0) / 60_000f }
            val ys = window.map { it.trendRisk }
            val meanX = xs.average().toFloat()
            val meanY = ys.average().toFloat()
            var num = 0f
            var den = 0f
            for (i in 0 until n) {
                val dx = xs[i] - meanX
                num += dx * (ys[i] - meanY)
                den += dx * dx
            }
            if (den > 0.0001f) (num / den).coerceIn(-35f, 35f) else 0f
        } else {
            0f
        }

        // Damped horizon projections (+1m, +3m, +5m)
        val proj1 = (currentTrendRisk + slopePerMin * 0.75f).coerceIn(0f, 100f)
        val proj3 = (currentTrendRisk + slopePerMin * 1.85f).coerceIn(0f, 100f)
        val proj5 = (currentTrendRisk + slopePerMin * 2.65f).coerceIn(0f, 100f)

        val summary = when {
            slopePerMin > 8f -> "Escalating risk trend (+${slopePerMin.toInt()}/min): aggressive inputs compounding."
            slopePerMin < -5f -> "Stabilizing risk trend (${slopePerMin.toInt()}/min): smoother inputs reducing future risk."
            currentRuleRisk > currentTrendRisk + 18f -> "Sudden rule spike (${currentRuleRisk.toInt()}) above smoothed trend (${currentTrendRisk.toInt()})."
            else -> "Steady telemetry trend (${currentTrendRisk.toInt()}/100); projected 5-min risk ${proj5.toInt()}/100."
        }

        return PredictiveProjection(
            currentRuleRisk = currentRuleRisk,
            currentTrendRisk = currentTrendRisk,
            riskSlopePerMin = slopePerMin,
            projected1MinRisk = proj1,
            projected3MinRisk = proj3,
            projected5MinRisk = proj5,
            projectionSummary = summary
        )
    }
}
