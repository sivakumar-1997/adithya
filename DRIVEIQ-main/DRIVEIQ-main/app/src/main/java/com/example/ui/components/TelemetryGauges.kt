package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SpeedometerGauge(
    speedKmh: Float,
    topSpeedKmh: Float,
    speedLimitKmh: Int,
    gearDisplay: String,
    brakingDistanceMeters: Float,
    modifier: Modifier = Modifier
) {
    val maxScale = topSpeedKmh.coerceAtLeast(160f)
    val animatedSpeed by animateFloatAsState(
        targetValue = speedKmh,
        animationSpec = tween(durationMillis = 80),
        label = "speedAnim"
    )
    val isOverspeed = speedKmh > speedLimitKmh + 2f
    val arcColor by animateColorAsState(
        targetValue = when {
            speedKmh > speedLimitKmh + 20f -> CriticalCrimson
            isOverspeed -> CautionAmber
            else -> NeonCyan
        },
        label = "speedArcColor"
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(CockpitCardSurface)
            .border(1.dp, CockpitBorder, RoundedCornerShape(20.dp))
            .padding(12.dp)
            .testTag("speedometer_gauge"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 14.dp.toPx()
            val inset = strokePx / 2f + 10.dp.toPx()
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            val topLeft = Offset(inset, inset)
            val startAngle = 145f
            val sweepTotal = 250f

            // Background track
            drawArc(
                color = CockpitElevated,
                startAngle = startAngle,
                sweepAngle = sweepTotal,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Active speed sweep
            val progress = (animatedSpeed / maxScale).coerceIn(0f, 1f)
            if (progress > 0.002f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(NeonCyan.copy(alpha = 0.65f), arcColor, arcColor)
                    ),
                    startAngle = startAngle,
                    sweepAngle = sweepTotal * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }

            // Tick marks around rim
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerR = size.minDimension / 2f - 4.dp.toPx()
            val innerR = outerR - 8.dp.toPx()
            val ticks = 10
            for (i in 0..ticks) {
                val angleDeg = startAngle + (sweepTotal / ticks) * i
                val rad = Math.toRadians(angleDeg.toDouble())
                val p1 = Offset(
                    center.x + (innerR * cos(rad)).toFloat(),
                    center.y + (innerR * sin(rad)).toFloat()
                )
                val p2 = Offset(
                    center.x + (outerR * cos(rad)).toFloat(),
                    center.y + (outerR * sin(rad)).toFloat()
                )
                drawLine(
                    color = CockpitBorder.copy(alpha = 0.9f),
                    start = p1,
                    end = p2,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Speed limit marker on arc
            val limitRatio = (speedLimitKmh / maxScale).coerceIn(0f, 1f)
            val limitAngleRad = Math.toRadians((startAngle + sweepTotal * limitRatio).toDouble())
            val markerInner = size.minDimension / 2f - strokePx - 14.dp.toPx()
            val markerOuter = size.minDimension / 2f - 2.dp.toPx()
            drawLine(
                color = CautionAmber,
                start = Offset(
                    center.x + (markerInner * cos(limitAngleRad)).toFloat(),
                    center.y + (markerInner * sin(limitAngleRad)).toFloat()
                ),
                end = Offset(
                    center.x + (markerOuter * cos(limitAngleRad)).toFloat(),
                    center.y + (markerOuter * sin(limitAngleRad)).toFloat()
                ),
                strokeWidth = 3.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CockpitElevated)
                    .border(1.dp, arcColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    text = gearDisplay,
                    style = TelemetryNumberStyle.copy(fontSize = 13.sp, color = arcColor)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${animatedSpeed.toInt()}",
                style = TelemetryNumberStyle.copy(
                    fontSize = 44.sp,
                    color = if (isOverspeed) arcColor else TextPrimary
                )
            )
            Text(
                text = "KM/H  •  LIM $speedLimitKmh",
                style = MaterialTheme.typography.labelSmall,
                color = if (isOverspeed) arcColor else TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "STOP ${String.format("%.1f", brakingDistanceMeters)}m",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
}

@Composable
fun RpmAndGForceCluster(
    rpm: Int,
    redlineRpm: Int,
    longitudinalG: Float,
    lateralG: Float,
    throttlePercent: Float,
    brakePercent: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // RPM Card
        val rpmRatio = if (redlineRpm > 0) (rpm.toFloat() / redlineRpm).coerceIn(0f, 1f) else 0f
        val rpmColor = when {
            rpmRatio > 0.85f -> CriticalCrimson
            rpmRatio > 0.65f -> CautionAmber
            else -> NeonCyan
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CockpitCardSurface)
                .border(1.dp, CockpitBorder, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ENGINE RPM", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(
                    text = "$rpm",
                    style = TelemetryNumberStyle.copy(fontSize = 18.sp, color = rpmColor)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CockpitElevated)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(rpmRatio)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(rpmColor)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "THR ${throttlePercent.toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (throttlePercent > 75f) CautionAmber else NeonCyan
                )
                Text(
                    text = "BRK ${brakePercent.toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (brakePercent > 65f) CriticalCrimson else TextSecondary
                )
            }
        }

        // G-Force Vector Radar Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CockpitCardSurface)
                .border(1.dp, CockpitBorder, RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("G-FORCE", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "LON ${String.format("%+.2f", longitudinalG)}G",
                    style = TelemetryNumberStyle.copy(fontSize = 13.sp, color = TextPrimary)
                )
                Text(
                    text = "LAT ${String.format("%+.2f", lateralG)}G",
                    style = TelemetryNumberStyle.copy(fontSize = 13.sp, color = NeonCyan)
                )
            }
            GForceRadar(
                longitudinalG = longitudinalG,
                lateralG = lateralG,
                modifier = Modifier.size(68.dp)
            )
        }
    }
}

@Composable
fun GForceRadar(
    longitudinalG: Float,
    lateralG: Float,
    modifier: Modifier = Modifier
) {
    val animLon by animateFloatAsState(longitudinalG, tween(70), label = "lonG")
    val animLat by animateFloatAsState(lateralG, tween(70), label = "latG")
    val totalG = kotlin.math.hypot(animLon.toDouble(), animLat.toDouble()).toFloat()
    val dotColor = when {
        totalG > 0.75f -> CriticalCrimson
        totalG > 0.45f -> CautionAmber
        else -> NeonCyan
    }

    Canvas(modifier = modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f - 2.dp.toPx()

        drawCircle(color = CockpitElevated, radius = r, center = c)
        drawCircle(color = CockpitBorder, radius = r, center = c, style = Stroke(1.dp.toPx()))
        drawCircle(color = CockpitBorder.copy(alpha = 0.6f), radius = r * 0.5f, center = c, style = Stroke(1.dp.toPx()))

        drawLine(
            color = CockpitBorder.copy(alpha = 0.6f),
            start = Offset(c.x - r, c.y),
            end = Offset(c.x + r, c.y),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = CockpitBorder.copy(alpha = 0.6f),
            start = Offset(c.x, c.y - r),
            end = Offset(c.x, c.y + r),
            strokeWidth = 1.dp.toPx()
        )

        val maxG = 1.2f
        val dx = (animLat / maxG).coerceIn(-1f, 1f) * (r * 0.85f)
        val dy = -(animLon / maxG).coerceIn(-1f, 1f) * (r * 0.85f)
        val ballPos = Offset(c.x + dx, c.y + dy)

        drawCircle(color = dotColor.copy(alpha = 0.25f), radius = 8.dp.toPx(), center = ballPos)
        drawCircle(color = dotColor, radius = 4.dp.toPx(), center = ballPos)
    }
}

@Composable
fun ScoreRingGauge(
    score: Float,
    maxScore: Float = 100f,
    label: String,
    isHigherBetter: Boolean,
    size: Dp = 92.dp,
    modifier: Modifier = Modifier
) {
    val animScore by animateFloatAsState(score, tween(150), label = "scoreRing")
    val ratio = (animScore / maxScore).coerceIn(0f, 1f)
    val ringColor by animateColorAsState(
        targetValue = if (isHigherBetter) {
            when {
                animScore >= 82f -> SafeEmerald
                animScore >= 62f -> CautionAmber
                else -> CriticalCrimson
            }
        } else {
            when {
                animScore < 30f -> SafeEmerald
                animScore < 58f -> NeonCyan
                animScore < 78f -> CautionAmber
                else -> CriticalCrimson
            }
        },
        label = "ringColor"
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2f
            drawArc(
                color = CockpitElevated,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * ratio,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${animScore.toInt()}",
                style = TelemetryNumberStyle.copy(fontSize = 22.sp, color = TextPrimary)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = ringColor
            )
        }
    }
}

@Composable
fun SubsystemStressRow(
    name: String,
    stressPercent: Float,
    modifier: Modifier = Modifier
) {
    val animVal by animateFloatAsState(stressPercent.coerceIn(0f, 100f), tween(140), label = "subStress")
    val barColor by animateColorAsState(
        targetValue = when {
            animVal >= 75f -> CriticalCrimson
            animVal >= 50f -> CautionAmber
            animVal >= 25f -> NeonCyan
            else -> SafeEmerald
        },
        label = "subColor"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
            Text(
                text = "${animVal.toInt()}%",
                style = TelemetryNumberStyle.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = barColor
                )
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CockpitElevated)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((animVal / 100f).coerceIn(0.02f, 1f))
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor)
            )
        }
    }
}
