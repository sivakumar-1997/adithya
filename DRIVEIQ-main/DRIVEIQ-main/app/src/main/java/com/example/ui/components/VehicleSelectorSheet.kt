package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VehicleProfile
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitCardSurface
import com.example.ui.theme.CockpitDeepSlate
import com.example.ui.theme.CockpitElevated
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SafeEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleSelectorBottomSheet(
    selectedVehicle: VehicleProfile,
    onSelectVehicle: (VehicleProfile) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CockpitDeepSlate,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "SELECT VEHICLE PROFILE",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Text(
                text = "Each chassis modifies acceleration torque curve, braking distance, top speed, and mechanical stress thresholds.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            VehicleProfile.entries.forEach { profile ->
                val isSelected = profile == selectedVehicle
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CockpitCardSurface)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) NeonCyan else CockpitBorder,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            onSelectVehicle(profile)
                            onDismiss()
                        }
                        .padding(12.dp)
                        .testTag("vehicle_option_${profile.id}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = profile.imageRes),
                            contentDescription = "${profile.displayName} profile visual",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(112.dp)
                                .height(68.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, CockpitBorder, RoundedCornerShape(10.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = profile.displayName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) NeonCyan else TextPrimary
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected vehicle",
                                        tint = NeonCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Text(
                                text = profile.subtitle,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                color = TextSecondary
                            )
                            Text(
                                text = "${profile.powertrainType} • ${profile.massKg} kg",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpecPill("TOP SPEED", "${profile.topSpeedKmh.toInt()} km/h", NeonCyan, Modifier.weight(1f))
                        SpecPill("PEAK ACCEL", "${profile.maxAccelMs2} m/s²", SafeEmerald, Modifier.weight(1f))
                        SpecPill("BRAKE POWER", "${profile.maxBrakeDecelMs2} m/s²", TextPrimary, Modifier.weight(1f))
                        SpecPill(
                            "STRESS SENS",
                            "${profile.stressMultiplier}x",
                            if (profile.stressMultiplier > 1.15f) CautionAmber else SafeEmerald,
                            Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecPill(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CockpitElevated)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = TextMuted)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = valueColor)
    }
}
