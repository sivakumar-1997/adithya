package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.DrivingEventEntity
import com.example.data.local.DrivingSessionEntity
import com.example.data.local.TelemetrySampleEntity
import com.example.data.model.AiCoachUiState
import com.example.data.model.PredictiveProjection
import com.example.data.model.TelemetryPoint
import com.example.engine.PredictiveRiskEngine
import com.example.ui.components.AiCoachPanel
import com.example.ui.components.RuleVsTrendRiskChart
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitCardSurface
import com.example.ui.theme.CockpitElevated
import com.example.ui.theme.CriticalCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SafeEmerald
import com.example.ui.theme.TelemetryNumberStyle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    sessions: List<DrivingSessionEntity>,
    inspectedSessionId: Long?,
    inspectedEvents: List<DrivingEventEntity>,
    inspectedTelemetry: List<TelemetrySampleEntity>,
    aiCoachState: AiCoachUiState,
    onInspectSession: (Long) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onClearAll: () -> Unit,
    onAnalyzeSession: (DrivingSessionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeSession = sessions.find { it.id == inspectedSessionId } ?: sessions.firstOrNull()
    val riskEngine = remember { PredictiveRiskEngine() }

    val chartPoints = remember(inspectedTelemetry) {
        inspectedTelemetry.map {
            TelemetryPoint(
                timestampMs = it.timestampMs,
                speedKmh = it.speedKmh,
                rpm = it.rpm,
                vehicleStress = it.vehicleStress,
                safetyScore = it.safetyScore,
                ruleRisk = it.ruleRisk,
                trendRisk = it.trendRisk
            )
        }
    }

    val sessionProjection = remember(chartPoints, activeSession) {
        if (chartPoints.isEmpty() || activeSession == null) {
            PredictiveProjection()
        } else {
            val totalEvents = activeSession.hardAccelCount + activeSession.hardBrakeCount +
                activeSession.sharpSteerCount + activeSession.overspeedCount
            riskEngine.computeTrendProjection(
                history = chartPoints,
                currentRuleRisk = chartPoints.last().ruleRisk,
                totalAggressiveEvents = totalEvents,
                sessionDurationSec = activeSession.durationSec
            )
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 680.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TELEMETRY & TRENDS",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Local Room/SQLite drive logs, rule vs. trend risk projections & AI debriefs.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                if (sessions.isNotEmpty()) {
                    TextButton(
                        onClick = onClearAll,
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Text("Clear All", color = CriticalCrimson)
                    }
                }
            }

            if (sessions.isEmpty()) {
                EmptyHistoryState()
            } else {
                // Aggregate Summary Banner
                val avgSafety = sessions.map { it.finalSafetyScore }.average().toInt()
                val totalEvents = sessions.sumOf {
                    it.hardAccelCount + it.hardBrakeCount + it.sharpSteerCount + it.overspeedCount
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SummaryMetricTile("DRIVES", "${sessions.size}", NeonCyan, Modifier.weight(1f))
                    SummaryMetricTile(
                        "AVG SAFETY",
                        "$avgSafety/100",
                        if (avgSafety >= 80) SafeEmerald else CautionAmber,
                        Modifier.weight(1f)
                    )
                    SummaryMetricTile("EVENTS LOGGED", "$totalEvents", TextPrimary, Modifier.weight(1f))
                }

                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(0.95f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SessionListSection(
                                sessions = sessions,
                                selectedId = activeSession?.id,
                                onSelect = onInspectSession,
                                onDelete = onDeleteSession
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1.05f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SessionDetailAnalytics(
                                session = activeSession,
                                chartPoints = chartPoints,
                                projection = sessionProjection,
                                events = inspectedEvents,
                                aiCoachState = aiCoachState,
                                onAnalyzeSession = onAnalyzeSession
                            )
                        }
                    }
                } else {
                    SessionListSection(
                        sessions = sessions,
                        selectedId = activeSession?.id,
                        onSelect = onInspectSession,
                        onDelete = onDeleteSession
                    )
                    SessionDetailAnalytics(
                        session = activeSession,
                        chartPoints = chartPoints,
                        projection = sessionProjection,
                        events = inspectedEvents,
                        aiCoachState = aiCoachState,
                        onAnalyzeSession = onAnalyzeSession
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionListSection(
    sessions: List<DrivingSessionEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "RECORDED SESSIONS",
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary
        )
        sessions.forEach { session ->
            val isSelected = session.id == selectedId
            val scoreColor = when {
                session.finalSafetyScore >= 82f -> SafeEmerald
                session.finalSafetyScore >= 62f -> CautionAmber
                else -> CriticalCrimson
            }
            val eventTotal = session.hardAccelCount + session.hardBrakeCount +
                session.sharpSteerCount + session.overspeedCount

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CockpitCardSurface)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) NeonCyan else CockpitBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelect(session.id) }
                    .padding(12.dp)
                    .testTag("session_item_${session.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = session.vehicleName,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isSelected) NeonCyan else TextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = dateFmt.format(Date(session.startedAtMs)),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${session.durationSec}s • ${String.format("%.2f", session.distanceKm)} km • Max ${session.maxSpeedKmh.toInt()} km/h • $eventTotal events",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = TextSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${session.finalSafetyScore.toInt()}",
                            style = TelemetryNumberStyle.copy(fontSize = 18.sp, color = scoreColor)
                        )
                        Text(
                            text = "SAFETY",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = TextMuted
                        )
                    }
                    IconButton(
                        onClick = { onDelete(session.id) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete session",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionDetailAnalytics(
    session: DrivingSessionEntity?,
    chartPoints: List<TelemetryPoint>,
    projection: PredictiveProjection,
    events: List<DrivingEventEntity>,
    aiCoachState: AiCoachUiState,
    onAnalyzeSession: (DrivingSessionEntity) -> Unit
) {
    if (session == null) return
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Timeline,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "SESSION #${session.id} PREDICTIVE PROJECTION",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary
            )
        }

        RuleVsTrendRiskChart(
            points = chartPoints,
            projection = projection
        )

        AiCoachPanel(
            aiState = aiCoachState,
            buttonLabel = stringResource(R.string.ai_analyze_session),
            onAnalyzeClick = { onAnalyzeSession(session) }
        )

        if (!session.aiCoachSummary.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CockpitElevated)
                    .border(1.dp, NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = "SAVED AI SUMMARY",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = session.aiCoachSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
            }
        }

        Text(
            text = "EVENT LOG (${events.size})",
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary
        )

        if (events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CockpitCardSurface)
                    .border(1.dp, CockpitBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "Clean drive — zero aggressive events triggered in this session.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SafeEmerald
                )
            }
        } else {
            events.forEach { evt ->
                val accent = if (evt.severityScore >= 80) CriticalCrimson else CautionAmber
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CockpitCardSurface)
                        .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = evt.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = accent
                            )
                        }
                        Text(
                            text = timeFmt.format(Date(evt.timestampMs)),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                    Text(evt.whatHappened, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Text("Impact: ${evt.mechanicalImpact}", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp), color = TextSecondary)
                    if (evt.sparePartSummary.isNotBlank()) {
                        Text("Spare Part Life: ${evt.sparePartSummary}", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp), color = CautionAmber)
                    }
                    Text("Action: ${evt.correctiveAction}", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp), color = NeonCyan)
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricTile(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CockpitCardSurface)
            .border(1.dp, CockpitBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextMuted)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = TelemetryNumberStyle.copy(fontSize = 20.sp, color = color))
    }
}

@Composable
private fun EmptyHistoryState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CockpitCardSurface)
            .border(1.dp, CockpitBorder, RoundedCornerShape(18.dp))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            tint = NeonCyan,
            modifier = Modifier.size(42.dp)
        )
        Text(
            text = "No Drive Sessions Logged Yet",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        Text(
            text = "Head to the Cockpit tab, tap START ENGINE, and drive using the accelerator, brake, and steering wheel to record local telemetry.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}
