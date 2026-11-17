package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val CockpitDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = CockpitObsidian,
    primaryContainer = ColorTokens.PrimaryContainer,
    onPrimaryContainer = NeonCyan,
    secondary = ElectricBlue,
    onSecondary = TextPrimary,
    secondaryContainer = CockpitElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = CautionAmber,
    onTertiary = CockpitObsidian,
    background = CockpitObsidian,
    onBackground = TextPrimary,
    surface = CockpitDeepSlate,
    onSurface = TextPrimary,
    surfaceVariant = CockpitCardSurface,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = CockpitDeepSlate,
    surfaceContainer = CockpitCardSurface,
    surfaceContainerHigh = CockpitElevated,
    outline = CockpitBorder,
    outlineVariant = ColorTokens.SubtleBorder,
    error = CriticalCrimson,
    onError = TextPrimary
)

private object ColorTokens {
    val PrimaryContainer = androidx.compose.ui.graphics.Color(0xFF0A2A36)
    val SubtleBorder = androidx.compose.ui.graphics.Color(0xFF1C2838)
}

val CockpitShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun DriveIqTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CockpitDarkColorScheme,
        typography = DriveIqTypography,
        shapes = CockpitShapes,
        content = content
    )
}
