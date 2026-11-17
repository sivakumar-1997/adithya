package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.RiskLevel
import com.example.data.model.SimulatorUiState
import com.example.data.model.VehicleProfile
import com.example.ui.components.HumanSafetyPanel
import com.example.ui.components.InteractiveSteeringDeck
import com.example.ui.components.NonBlockingWarningBanner
import com.example.ui.components.PedalDeck
import com.example.ui.components.RpmAndGForceCluster
import com.example.ui.components.SpeedometerGauge
import com.example.ui.components.VehicleIntelligencePanel
import com.example.ui.components.VehicleSelectorBottomSheet
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitCardSurface
import com.example.ui.theme.CockpitElevated
import com.example.ui.theme.CockpitObsidian
import com.example.ui.theme.CriticalCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SafeEmerald
import com.example.ui.theme.TelemetryNumberStyle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CockpitScreen(
    uiState: SimulatorUiState,
    onToggleEngine: () -> Unit,
    onSelectVehicle: (VehicleProfile) -> Unit,
    onCycleSpeedLimit: () -> Unit,
    onToggleMuteAudio: () -> Unit,
    onAcceleratorInput: (Boolean, Float) -> Unit,
    onBrakeInput: (Boolean, Float) -> Unit,
    onSteeringInput: (Float, Boolean) -> Unit,
    onCenterSteering: () -> Unit,
    onToggleAutoCenter: () -> Unit,
    onDismissWarning: () -> Unit,
    onResetSpareParts: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showVehicleSheet by remember { mutableStateOf(false) }

    if (showVehicleSheet) {
        VehicleSelectorBottomSheet(
            selectedVehicle = uiState.selectedVehicle,
            onSelectVehicle = onSelectVehicle,
            onDismiss = { showVehicleSheet = false }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isLandscapeWide = maxWidth >= 680.dp

            if (isLandscapeWide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left Simulator Cockpit Column
                    Column(
                        modifier = Modifier
                            .weight(1.15f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CockpitTopBar(
                            uiState = uiState,
                            onOpenVehicleSelector = { showVehicleSheet = true },
                            onCycleSpeedLimit = onCycleSpeedLimit,
                            onToggleMuteAudio = onToggleMuteAudio,
                            onToggleEngine = onToggleEngine
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SpeedometerGauge(
                                speedKmh = uiState.speedKmh,
                                topSpeedKmh = uiState.selectedVehicle.topSpeedKmh,
                                speedLimitKmh = uiState.speedLimitKmh,
                                gearDisplay = uiState.gearDisplay,
                                brakingDistanceMeters = uiState.brakingDistanceMeters,
                                modifier = Modifier.weight(1f)
                            )
                            RpmAndGForceCluster(
                                rpm = uiState.rpm,
                                redlineRpm = uiState.selectedVehicle.redlineRpm,
                                longitudinalG = uiState.longitudinalG,
                                lateralG = uiState.lateralG,
                                throttlePercent = uiState.throttlePercent,
                                brakePercent = uiState.brakePercent,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        InteractiveSteeringDeck(
                            steeringAngleDeg = uiState.steeringAngleDeg,
                            autoCenterEnabled = uiState.autoCenterSteering,
                            onSteeringChange = onSteeringInput,
                            onCenterSteering = onCenterSteering,
                            onToggleAutoCenter = onToggleAutoCenter
                        )

                        PedalDeck(
                            throttlePercent = uiState.throttlePercent,
                            brakePercent = uiState.brakePercent,
                            isEngineRunning = uiState.isEngineRunning,
                            onAcceleratorInput = onAcceleratorInput,
                            onBrakeInput = onBrakeInput
                        )
                    }

                    // Right Dual Live Intelligence Column
                    Column(
                        modifier = Modifier
                            .weight(0.85f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SessionStatsStrip(uiState = uiState)
                        VehicleIntelligencePanel(
                            state = uiState.vehicleIntelligence,
                            vehicleName = uiState.selectedVehicle.displayName,
                            onResetSpareParts = onResetSpareParts
                        )
                        HumanSafetyPanel(state = uiState.humanSafety)
                    }
                }
            } else {
                // Portrait Cockpit Layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CockpitTopBar(
                        uiState = uiState,
                        onOpenVehicleSelector = { showVehicleSheet = true },
                        onCycleSpeedLimit = onCycleSpeedLimit,
                        onToggleMuteAudio = onToggleMuteAudio,
                        onToggleEngine = onToggleEngine
                    )

                    SessionStatsStrip(uiState = uiState)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SpeedometerGauge(
                            speedKmh = uiState.speedKmh,
                            topSpeedKmh = uiState.selectedVehicle.topSpeedKmh,
                            speedLimitKmh = uiState.speedLimitKmh,
                            gearDisplay = uiState.gearDisplay,
                            brakingDistanceMeters = uiState.brakingDistanceMeters,
                            modifier = Modifier.weight(1.05f)
                        )
                        RpmAndGForceCluster(
                            rpm = uiState.rpm,
                            redlineRpm = uiState.selectedVehicle.redlineRpm,
                            longitudinalG = uiState.longitudinalG,
                            lateralG = uiState.lateralG,
                            throttlePercent = uiState.throttlePercent,
                            brakePercent = uiState.brakePercent,
                            modifier = Modifier.weight(0.95f)
                        )
                    }

                    // Compact Dual Live Intelligence Glance Bar right above controls
                    DualIntelligenceGlanceStrip(uiState = uiState)

                    InteractiveSteeringDeck(
                        steeringAngleDeg = uiState.steeringAngleDeg,
                        autoCenterEnabled = uiState.autoCenterSteering,
                        onSteeringChange = onSteeringInput,
                        onCenterSteering = onCenterSteering,
                        onToggleAutoCenter = onToggleAutoCenter
                    )

                    PedalDeck(
                        throttlePercent = uiState.throttlePercent,
                        brakePercent = uiState.brakePercent,
                        isEngineRunning = uiState.isEngineRunning,
                        onAcceleratorInput = onAcceleratorInput,
                        onBrakeInput = onBrakeInput
                    )

                    // Full Dual Dynamic Intelligence Panels below controls in portrait scroll
                    VehicleIntelligencePanel(
                        state = uiState.vehicleIntelligence,
                        vehicleName = uiState.selectedVehicle.displayName,
                        onResetSpareParts = onResetSpareParts
                    )
                    HumanSafetyPanel(state = uiState.humanSafety)
                }
            }
        }

        // Non-blocking floating warning popup at top of cockpit
        NonBlockingWarningBanner(
            warning = uiState.activeWarning,
            onDismiss = onDismissWarning,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun CockpitTopBar(
    uiState: SimulatorUiState,
    onOpenVehicleSelector: () -> Unit,
    onCycleSpeedLimit: () -> Unit,
    onToggleMuteAudio: () -> Unit,
    onToggleEngine: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Vehicle Profile Switcher Pill
            Row(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CockpitCardSurface)
                    .border(1.dp, CockpitBorder, RoundedCornerShape(14.dp))
                    .clickable { onOpenVehicleSelector() }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .testTag("vehicle_selector_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(id = uiState.selectedVehicle.imageRes),
                    contentDescription = uiState.selectedVehicle.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 46.dp, height = 30.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.selectedVehicle.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = "${uiState.selectedVehicle.topSpeedKmh.toInt()} km/h max",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = NeonCyan
                    )
                }
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = "Switch vehicle",
                    tint = NeonCyan,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Engine Start/Stop Button
            Button(
                onClick = onToggleEngine,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isEngineRunning) CriticalCrimson else SafeEmerald,
                    contentColor = CockpitObsidian
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("engine_toggle_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (uiState.isEngineRunning) stringResource(R.string.engine_stop) else stringResource(R.string.engine_start),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        // Secondary Control Strip: Speed Limit + Simulated Audio Cues
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CockpitCardSurface)
                    .border(1.dp, CockpitBorder, RoundedCornerShape(12.dp))
                    .clickable { onCycleSpeedLimit() }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("speed_limit_button"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Change speed limit",
                    tint = CautionAmber,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "LIMIT ${uiState.speedLimitKmh} km/h",
                    style = MaterialTheme.typography.labelSmall,
                    color = CautionAmber
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CockpitCardSurface)
                    .border(
                        1.dp,
                        if (uiState.activeAudioCueLabel != null) NeonCyan else CockpitBorder,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onToggleMuteAudio() }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("audio_cue_toggle"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (uiState.isAudioMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = stringResource(R.string.audio_cue_label),
                        tint = if (uiState.isAudioMuted) TextMuted else NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = uiState.activeAudioCueLabel
                            ?: if (uiState.isAudioMuted) "Simulated Audio: Muted" else stringResource(R.string.audio_cue_label),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (uiState.activeAudioCueLabel != null) NeonCyan else TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionStatsStrip(uiState: SimulatorUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CockpitCardSurface)
            .border(1.dp, CockpitBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (uiState.isEngineRunning) SafeEmerald else TextMuted)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (uiState.isEngineRunning) "LIVE LOGGING" else "STANDBY",
                style = MaterialTheme.typography.labelSmall,
                color = if (uiState.isEngineRunning) SafeEmerald else TextSecondary
            )
        }
        Text(
            text = "DIST ${String.format("%.2f", uiState.distanceKm)} km",
            style = TelemetryNumberStyle.copy(fontSize = 12.sp, color = TextPrimary)
        )
        Text(
            text = "TIME ${uiState.sessionDurationSec}s",
            style = TelemetryNumberStyle.copy(fontSize = 12.sp, color = NeonCyan)
        )
    }
}

@Composable
private fun DualIntelligenceGlanceStrip(uiState: SimulatorUiState) {
    val riskColor = when (uiState.humanSafety.riskLevel) {
        RiskLevel.LOW -> SafeEmerald
        RiskLevel.MODERATE -> CautionAmber
        RiskLevel.HIGH -> CautionAmber
        RiskLevel.CRITICAL -> CriticalCrimson
    }
    val stressColor = when {
        uiState.vehicleIntelligence.overallStressScore >= 75f -> CriticalCrimson
        uiState.vehicleIntelligence.overallStressScore >= 45f -> CautionAmber
        else -> NeonCyan
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(CockpitElevated)
                .border(1.dp, CockpitBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("VEH STRESS", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(
                text = "${uiState.vehicleIntelligence.overallStressScore.toInt()}/100",
                style = TelemetryNumberStyle.copy(fontSize = 14.sp, color = stressColor)
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(CockpitElevated)
                .border(1.dp, CockpitBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SAFETY", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(
                text = "${uiState.humanSafety.safetyScore.toInt()} • ${uiState.humanSafety.riskLevel.name}",
                style = TelemetryNumberStyle.copy(fontSize = 13.sp, color = riskColor)
            )
        }
    }
}
