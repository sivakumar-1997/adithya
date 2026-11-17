package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitCardSurface
import com.example.ui.theme.CockpitElevated
import com.example.ui.theme.CriticalCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.PedalBrakeRed
import com.example.ui.theme.PedalThrottleCyan
import com.example.ui.theme.TelemetryNumberStyle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.abs

@Composable
fun InteractiveSteeringDeck(
    steeringAngleDeg: Float,
    autoCenterEnabled: Boolean,
    onSteeringChange: (Float, Boolean) -> Unit,
    onCenterSteering: () -> Unit,
    onToggleAutoCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val steerColor by animateColorAsState(
        targetValue = when {
            abs(steeringAngleDeg) >= 28f -> CautionAmber
            abs(steeringAngleDeg) >= 10f -> NeonCyan
            else -> TextSecondary
        },
        label = "steerColor"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CockpitCardSurface)
            .border(1.dp, CockpitBorder, RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "STEERING WHEEL",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    text = "${if (steeringAngleDeg > 0.5f) "+" else ""}${steeringAngleDeg.toInt()}°",
                    style = TelemetryNumberStyle.copy(fontSize = 20.sp, color = steerColor)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (autoCenterEnabled) NeonCyan.copy(alpha = 0.16f) else CockpitElevated)
                        .border(
                            1.dp,
                            if (autoCenterEnabled) NeonCyan.copy(alpha = 0.5f) else CockpitBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onToggleAutoCenter() }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("auto_center_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (autoCenterEnabled) "AUTO-CTR ON" else "HOLD ANGLE",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (autoCenterEnabled) NeonCyan else TextSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CockpitElevated)
                        .border(1.dp, CockpitBorder, RoundedCornerShape(10.dp))
                        .clickable { onCenterSteering() }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("center_steering_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CenterFocusStrong,
                            contentDescription = "Center steering wheel",
                            tint = TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("0°", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Draggable Canvas Steering Wheel
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var currentAngle = steeringAngleDeg
                            var prevX = down.position.x
                            onSteeringChange(currentAngle, true)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) {
                                    onSteeringChange(currentAngle, false)
                                    break
                                }
                                val dx = change.position.x - prevX
                                prevX = change.position.x
                                currentAngle = (currentAngle + dx * 0.45f).coerceIn(-45f, 45f)
                                onSteeringChange(currentAngle, true)
                                change.consume()
                            }
                        }
                    }
                    .testTag("steering_wheel_canvas")
                    .semantics { contentDescription = "Interactive steering wheel" },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.minDimension / 2f - 6.dp.toPx()

                    rotate(degrees = steeringAngleDeg * 2f, pivot = c) {
                        // Outer rim
                        drawCircle(
                            color = CockpitElevated,
                            radius = radius,
                            center = c,
                            style = Stroke(width = 10.dp.toPx())
                        )
                        drawCircle(
                            color = steerColor.copy(alpha = 0.7f),
                            radius = radius,
                            center = c,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        // Top 12-o'clock center stripe
                        drawLine(
                            color = if (abs(steeringAngleDeg) >= 28f) CautionAmber else NeonCyan,
                            start = Offset(c.x, c.y - radius - 4.dp.toPx()),
                            end = Offset(c.x, c.y - radius + 5.dp.toPx()),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        // Spokes
                        drawLine(
                            color = CockpitBorder,
                            start = Offset(c.x - radius + 6.dp.toPx(), c.y),
                            end = Offset(c.x + radius - 6.dp.toPx(), c.y),
                            strokeWidth = 6.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = CockpitBorder,
                            start = c,
                            end = Offset(c.x, c.y + radius - 6.dp.toPx()),
                            strokeWidth = 6.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        // Hub
                        drawCircle(color = CockpitCardSurface, radius = 11.dp.toPx(), center = c)
                        drawCircle(
                            color = steerColor,
                            radius = 11.dp.toPx(),
                            center = c,
                            style = Stroke(2.dp.toPx())
                        )
                    }
                }
            }

            // Precision Steering Slider (-45 to +45)
            Column(modifier = Modifier.weight(1f)) {
                Slider(
                    value = steeringAngleDeg,
                    onValueChange = { onSteeringChange(it, true) },
                    onValueChangeFinished = { onSteeringChange(steeringAngleDeg, false) },
                    valueRange = -45f..45f,
                    colors = SliderDefaults.colors(
                        thumbColor = steerColor,
                        activeTrackColor = steerColor,
                        inactiveTrackColor = CockpitElevated
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("steering_slider")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("-45° LEFT", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text("0°", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text("+45° RIGHT", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
        }
    }
}

@Composable
fun PedalDeck(
    throttlePercent: Float,
    brakePercent: Float,
    isEngineRunning: Boolean,
    onAcceleratorInput: (Boolean, Float) -> Unit,
    onBrakeInput: (Boolean, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TouchHoldPedal(
            title = "BRAKE",
            subtitle = "HOLD / DRAG",
            pressurePercent = brakePercent,
            accentColor = PedalBrakeRed,
            enabled = true,
            isBrake = true,
            onInput = onBrakeInput,
            modifier = Modifier
                .weight(1f)
                .height(126.dp)
                .testTag("brake_pedal")
        )
        TouchHoldPedal(
            title = "ACCELERATOR",
            subtitle = if (isEngineRunning) "HOLD / DRAG" else "ENGINE OFF",
            pressurePercent = throttlePercent,
            accentColor = PedalThrottleCyan,
            enabled = isEngineRunning,
            isBrake = false,
            onInput = onAcceleratorInput,
            modifier = Modifier
                .weight(1f)
                .height(126.dp)
                .testTag("accelerator_pedal")
        )
    }
}

@Composable
fun TouchHoldPedal(
    title: String,
    subtitle: String,
    pressurePercent: Float,
    accentColor: Color,
    enabled: Boolean,
    isBrake: Boolean,
    onInput: (Boolean, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isHeld by remember { mutableStateOf(false) }
    val fillRatio = (pressurePercent / 100f).coerceIn(0f, 1f)
    val effectiveAccent = if (enabled) accentColor else TextMuted

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(18.dp))
            .background(CockpitCardSurface)
            .border(
                width = if (isHeld && enabled) 2.dp else 1.dp,
                color = if (isHeld && enabled) effectiveAccent else CockpitBorder,
                shape = RoundedCornerShape(18.dp)
            )
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isHeld = true
                    val startY = down.position.y
                    var draggedSignificantly = false
                    onInput(true, 100f)

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            isHeld = false
                            onInput(false, 0f)
                            break
                        }
                        if (abs(change.position.y - startY) > 18f) {
                            draggedSignificantly = true
                        }
                        if (draggedSignificantly && size.height > 0) {
                            // Top of pedal = 100%, bottom = 25%
                            val norm = 1f - (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                            val intensity = (25f + norm * 75f).coerceIn(25f, 100f)
                            onInput(true, intensity)
                        }
                        change.consume()
                    }
                }
            }
            .semantics { contentDescription = "$title pedal" }
    ) {
        // Vertical fill gauge inside pedal
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(fillRatio)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            effectiveAccent.copy(alpha = 0.45f),
                            effectiveAccent.copy(alpha = 0.16f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isBrake) Icons.Default.PauseCircle else Icons.Default.KeyboardDoubleArrowUp,
                    contentDescription = null,
                    tint = effectiveAccent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "${pressurePercent.toInt()}%",
                    style = TelemetryNumberStyle.copy(
                        fontSize = 18.sp,
                        color = if (pressurePercent > 75f) effectiveAccent else TextPrimary
                    )
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) TextPrimary else TextMuted
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = TextSecondary
                )
            }
        }
    }
}
