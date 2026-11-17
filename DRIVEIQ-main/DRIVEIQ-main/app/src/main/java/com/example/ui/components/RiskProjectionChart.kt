package com.example.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PredictiveProjection
import com.example.data.model.TelemetryPoint
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

@Composable
fun RuleVsTrendRiskChart(
    points: List<TelemetryPoint>,
    projection: PredictiveProjection,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CockpitCardSurface)
            .border(1.dp, CockpitBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RULE-BASED VS. TREND-BASED RISK",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LegendChip(color = CautionAmber, label = "Rule")
                LegendChip(color = NeonCyan, label = "Trend")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (points.size < 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CockpitElevated.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Start driving to stream live rule vs. trend risk curves",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CockpitElevated.copy(alpha = 0.45f))
                    .padding(10.dp)
            ) {
                val w = size.width
                val h = size.height
                // Reserve right 22% for +1m, +3m, +5m projection horizon
                val histWidth = w * 0.78f
                val stepX = histWidth / (points.size - 1).coerceAtLeast(1)

                // Grid lines at 25, 50, 75
                listOf(0.25f, 0.5f, 0.75f).forEach { frac ->
                    val y = h * (1f - frac)
                    drawLine(
                        color = CockpitBorder.copy(alpha = 0.5f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Divider separating live history from future forecast
                drawLine(
                    color = TextMuted.copy(alpha = 0.5f),
                    start = Offset(histWidth, 0f),
                    end = Offset(histWidth, h),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                )

                val rulePath = Path()
                val trendPath = Path()

                points.forEachIndexed { idx, pt ->
                    val x = idx * stepX
                    val yRule = h - (pt.ruleRisk.coerceIn(0f, 100f) / 100f) * h
                    val yTrend = h - (pt.trendRisk.coerceIn(0f, 100f) / 100f) * h
                    if (idx == 0) {
                        rulePath.moveTo(x, yRule)
                        trendPath.moveTo(x, yTrend)
                    } else {
                        rulePath.lineTo(x, yRule)
                        trendPath.lineTo(x, yTrend)
                    }
                }

                // Draw Rule-Based Risk (amber)
                drawPath(
                    path = rulePath,
                    color = CautionAmber.copy(alpha = 0.85f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                // Draw Trend-Based Risk (neon cyan)
                drawPath(
                    path = trendPath,
                    color = NeonCyan,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Draw future projection (+1m, +3m, +5m) from last trend point
                val lastTrendY = h - (points.last().trendRisk.coerceIn(0f, 100f) / 100f) * h
                val projWidth = w - histWidth
                val projPoints = listOf(
                    Offset(histWidth, lastTrendY),
                    Offset(histWidth + projWidth * 0.33f, h - (projection.projected1MinRisk / 100f) * h),
                    Offset(histWidth + projWidth * 0.66f, h - (projection.projected3MinRisk / 100f) * h),
                    Offset(w, h - (projection.projected5MinRisk / 100f) * h)
                )
                val projPath = Path().apply {
                    moveTo(projPoints[0].x, projPoints[0].y)
                    for (i in 1 until projPoints.size) {
                        lineTo(projPoints[i].x, projPoints[i].y)
                    }
                }
                val projColor = if (projection.projected5MinRisk >= 60f) CriticalCrimson else SafeEmerald
                drawPath(
                    path = projPath,
                    color = projColor,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
                    )
                )
                projPoints.drop(1).forEach { pt ->
                    drawCircle(color = projColor, radius = 3.5.dp.toPx(), center = pt)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProjectionStatPill("RULE NOW", "${projection.currentRuleRisk.toInt()}", CautionAmber, Modifier.weight(1f))
            ProjectionStatPill("TREND NOW", "${projection.currentTrendRisk.toInt()}", NeonCyan, Modifier.weight(1f))
            ProjectionStatPill("+1M", "${projection.projected1MinRisk.toInt()}", TextPrimary, Modifier.weight(1f))
            ProjectionStatPill("+5M", "${projection.projected5MinRisk.toInt()}",
                if (projection.projected5MinRisk >= 60f) CriticalCrimson else SafeEmerald, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = projection.projectionSummary,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

@Composable
private fun LegendChip(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun ProjectionStatPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CockpitElevated)
            .padding(vertical = 6.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextMuted)
        Text(value, style = TelemetryNumberStyle.copy(fontSize = 15.sp, color = color))
    }
}
