package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AiCoachUiState
import com.example.data.model.HumanSafetyState
import com.example.data.model.RiskLevel
import com.example.data.model.SparePartWearStatus
import com.example.data.model.VehicleIntelligenceState
import com.example.engine.SparePartWearEngine
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitCardSurface
import com.example.ui.theme.CockpitElevated
import com.example.ui.theme.CockpitObsidian
import com.example.ui.theme.CriticalCrimson
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SafeEmerald
import com.example.ui.theme.TelemetryNumberStyle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun VehicleIntelligencePanel(
    state: VehicleIntelligenceState,
    vehicleName: String,
    onResetSpareParts: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CockpitCardSurface)
            .border(1.dp, CockpitBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
            .testTag("vehicle_intelligence_panel")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "VEHICLE INTELLIGENCE",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = vehicleName,
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CockpitElevated)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "PEAK ${state.peakStressScore.toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ScoreRingGauge(
                score = state.overallStressScore,
                label = "STRESS",
                isHigherBetter = false,
                size = 86.dp
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                SubsystemStressRow("Engine / Motor", state.engineStress)
                SubsystemStressRow("Braking System", state.brakingStress)
                SubsystemStressRow("Tyres", state.tyreStress)
                SubsystemStressRow("Suspension", state.suspensionStress)
                SubsystemStressRow("Transmission", state.transmissionStress)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CockpitElevated)
                .padding(10.dp)
        ) {
            Text(
                text = state.healthRecommendation,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
        }

        if (state.sparePartWearList.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            SparePartWearBreakdown(
                wearList = state.sparePartWearList,
                worstAdvisory = state.worstSparePartAdvisory,
                onResetParts = onResetSpareParts
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.disclaimer_vehicle),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = TextMuted
            )
        }
    }
}

@Composable
fun SparePartWearBreakdown(
    wearList: List<SparePartWearStatus>,
    worstAdvisory: String,
    onResetParts: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CockpitElevated.copy(alpha = 0.72f))
            .border(1.dp, CockpitBorder, RoundedCornerShape(14.dp))
            .padding(10.dp)
            .testTag("spare_part_wear_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Handyman,
                    contentDescription = null,
                    tint = CautionAmber,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SPARE-PART LIFESPAN IMPACT",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = CautionAmber
                )
            }
            if (onResetParts != null) {
                TextButton(
                    onClick = onResetParts,
                    modifier = Modifier.testTag("reset_parts_button")
                ) {
                    Text(
                        text = "Reset Service",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan
                    )
                }
            }
        }

        Text(
            text = worstAdvisory,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
            color = TextPrimary
        )

        wearList.forEach { sp ->
            val healthColor = when {
                sp.healthPercent < 65f -> CriticalCrimson
                sp.healthPercent < 85f -> CautionAmber
                else -> SafeEmerald
            }
            val priorTxt = SparePartWearEngine.formatMonthsHuman(sp.priorLostMonths)
            val sessionTxt = SparePartWearEngine.formatMonthsHuman(sp.sessionLostMonths)
            val totalTxt = SparePartWearEngine.formatMonthsHuman(sp.totalLostMonths)
            val remTxt = SparePartWearEngine.formatMonthsHuman(sp.remainingMonths)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CockpitCardSurface)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sp.part.displayName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = TextPrimary
                    )
                    Text(
                        text = "${sp.healthPercent.toInt()}% • $remTxt left",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = healthColor
                    )
                }
                Text(
                    text = "Prior: $priorTxt + Now: $sessionTxt = Total Lost: $totalTxt",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (sp.totalLostMonths > 0.02f) CautionAmber else TextMuted
                )
            }
        }
    }
}

@Composable
fun HumanSafetyPanel(
    state: HumanSafetyState,
    modifier: Modifier = Modifier
) {
    val riskColor = when (state.riskLevel) {
        RiskLevel.LOW -> SafeEmerald
        RiskLevel.MODERATE -> CautionAmber
        RiskLevel.HIGH -> Color(0xFFF97316)
        RiskLevel.CRITICAL -> CriticalCrimson
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CockpitCardSurface)
            .border(1.dp, CockpitBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
            .testTag("human_safety_panel")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = riskColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "HUMAN SAFETY INTELLIGENCE",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(riskColor.copy(alpha = 0.16f))
                    .border(1.dp, riskColor.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = state.riskLevel.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = riskColor
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ScoreRingGauge(
                score = state.safetyScore,
                label = "SAFETY",
                isHigherBetter = true,
                size = 86.dp
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EventCounterChip("ACCEL", state.hardAccelCount, CautionAmber, Modifier.weight(1f))
                    EventCounterChip("BRAKE", state.hardBrakeCount, CriticalCrimson, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EventCounterChip("STEER", state.sharpSteerCount, ElectricBlue, Modifier.weight(1f))
                    EventCounterChip("SPEED", state.overspeedCount, CriticalCrimson, Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CockpitElevated)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = state.behavioralSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
            Text(
                text = "Smooth Tip: ${state.smoothDrivingTip}",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = NeonCyan
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.disclaimer_human),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = TextMuted
            )
        }
    }
}

@Composable
private fun EventCounterChip(
    label: String,
    count: Int,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CockpitElevated)
            .border(1.dp, if (count > 0) accent.copy(alpha = 0.45f) else CockpitBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextSecondary)
        Text(
            text = "$count",
            style = TelemetryNumberStyle.copy(
                fontSize = 15.sp,
                color = if (count > 0) accent else TextPrimary
            )
        )
    }
}

@Composable
fun AiCoachPanel(
    aiState: AiCoachUiState,
    buttonLabel: String,
    onAnalyzeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CockpitCardSurface)
            .border(1.dp, NeonCyan.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .padding(14.dp)
            .testTag("ai_coach_panel")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "AI TELEMETRY & SPARE-PART REPORT",
                    style = MaterialTheme.typography.labelLarge,
                    color = NeonCyan
                )
            }
            Text(
                text = "Gemini 3.8 Flash",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (aiState) {
            is AiCoachUiState.Idle -> {
                Text(
                    text = "Generate an AI behavioral, mechanical-stress, and cumulative spare-part life-loss briefing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            is AiCoachUiState.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(
                        color = NeonCyan,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Analyzing telemetry patterns, risk slope & part wear...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
            }
            is AiCoachUiState.Success -> {
                val insight = aiState.insight
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = aiState.sourceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan
                    )
                    Text(
                        text = insight.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CautionAmber.copy(alpha = 0.12f))
                            .border(1.dp, CautionAmber.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "SPARE-PART LIFESPAN FORECAST",
                                style = MaterialTheme.typography.labelSmall,
                                color = CautionAmber
                            )
                            Text(
                                text = insight.sparePartLifeForecast,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                color = TextPrimary
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CockpitElevated)
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "PRIMARY FACTOR: ${insight.primaryRiskFactor}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CautionAmber
                            )
                            Text(
                                text = insight.mechanicalInsight,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                color = TextSecondary
                            )
                        }
                    }
                    insight.coachingTips.forEachIndexed { idx, tip ->
                        Text(
                            text = "${idx + 1}. $tip",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = SafeEmerald
                        )
                    }
                }
            }
            is AiCoachUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CriticalCrimson.copy(alpha = 0.14f))
                        .border(1.dp, CriticalCrimson.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = aiState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CriticalCrimson
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onAnalyzeClick,
            enabled = aiState !is AiCoachUiState.Loading,
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonCyan,
                contentColor = CockpitObsidian
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_analyze_button")
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = buttonLabel, style = MaterialTheme.typography.labelLarge)
        }
    }
}
