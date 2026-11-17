package com.example.ai

import android.util.Log
import com.example.data.model.AiCoachInsight
import com.example.data.model.SparePartWearStatus
import com.example.engine.SparePartWearEngine
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class DriveAiCoachRepository {

    companion object {
        private const val TAG = "DriveAiCoachRepo"
    }

    private val coachSchema = Schema.obj(
        properties = mapOf(
            "summary" to Schema.string(description = "2-sentence behavioral and predictive telemetry summary."),
            "primaryRiskFactor" to Schema.string(description = "Primary driving risk or mechanical stress driver observed."),
            "mechanicalInsight" to Schema.string(description = "Simulated vehicle stress insight (clearly non-diagnostic)."),
            "sparePartLifeForecast" to Schema.string(
                description = "Customer-facing spare-part wear report explaining prior lost life + current lost life = total cumulative life lost and remaining useful life."
            ),
            "coachingTips" to Schema.array(
                items = Schema.string(),
                description = "3 concrete smooth-driving corrective coaching tips."
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = coachSchema
            }
        )
    }

    suspend fun analyzeDriveTelemetry(
        vehicleName: String,
        durationSec: Long,
        distanceKm: Float,
        maxSpeedKmh: Float,
        speedLimitKmh: Int,
        safetyScore: Float,
        overallStress: Float,
        engineStress: Float,
        brakeStress: Float,
        tyreStress: Float,
        hardAccelCount: Int,
        hardBrakeCount: Int,
        sharpSteerCount: Int,
        overspeedCount: Int,
        ruleRisk: Float,
        trendRisk: Float,
        projected5MinRisk: Float,
        spareParts: List<SparePartWearStatus>
    ): AiCoachInsight {
        val partsBreakdown = if (spareParts.isEmpty()) {
            "No excess spare-part wear recorded."
        } else {
            spareParts.joinToString("\n") { p ->
                "- ${p.part.displayName}: Prior lost=${SparePartWearEngine.formatMonthsHuman(p.priorLostMonths)}, " +
                    "This session=${SparePartWearEngine.formatMonthsHuman(p.sessionLostMonths)}, " +
                    "Total lost=${SparePartWearEngine.formatMonthsHuman(p.totalLostMonths)}, " +
                    "Remaining=${SparePartWearEngine.formatMonthsHuman(p.remainingMonths)} (Health ${p.healthPercent.toInt()}%)"
            }
        }

        val prompt = """
            You are an intelligent automotive driving telemetry coach inside a driving simulation app called DriveIQ.
            Analyze the following simulated session metrics concisely and constructively for the driver/customer.
            IMPORTANT:
            1) Never claim to diagnose real physical vehicle mechanical failures or human medical/fatigue conditions; label insights as simulated telemetry estimates.
            2) In 'sparePartLifeForecast', explicitly state which spare parts are impacted by the driver's behavior and quantify cumulative life lost as "already X lost + this session Y lost = total Z life lost" using the exact numbers below.

            Vehicle: $vehicleName
            Duration: ${durationSec}s | Distance: ${String.format("%.2f", distanceKm)} km
            Max Speed: ${maxSpeedKmh.toInt()} km/h (Speed Limit: $speedLimitKmh km/h)
            Human Safety Score: ${safetyScore.toInt()}/100
            Simulated Vehicle Stress: ${overallStress.toInt()}/100 (Engine ${engineStress.toInt()}%, Braking ${brakeStress.toInt()}%, Tyres ${tyreStress.toInt()}%)
            Aggressive Events: Hard Accel=$hardAccelCount, Hard Brake=$hardBrakeCount, Sharp Steer=$sharpSteerCount, Overspeed=$overspeedCount
            Predictive Risk: Rule-Based=${ruleRisk.toInt()}/100, Trend-Based=${trendRisk.toInt()}/100, 5-Min Projected=${projected5MinRisk.toInt()}/100

            Cumulative Spare-Part Lifespan Impact:
            $partsBreakdown
        """.trimIndent()

        return try {
            val response = withTimeout(45_000L) {
                model.generateContent(prompt)
            }
            val jsonText = response.text ?: throw IllegalStateException("AI coach returned an empty response.")
            val json = JSONObject(jsonText)
            val tipsJson = json.getJSONArray("coachingTips")
            val tips = (0 until tipsJson.length()).map { tipsJson.getString(it) }
            AiCoachInsight(
                summary = json.getString("summary"),
                primaryRiskFactor = json.getString("primaryRiskFactor"),
                mechanicalInsight = json.getString("mechanicalInsight"),
                sparePartLifeForecast = json.getString("sparePartLifeForecast"),
                coachingTips = tips
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI coach timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI coach generation failed", e)
            throw e
        }
    }
}
