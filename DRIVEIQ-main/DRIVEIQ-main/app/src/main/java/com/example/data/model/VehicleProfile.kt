package com.example.data.model

import androidx.annotation.DrawableRes
import com.example.R

enum class VehicleProfile(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val powertrainType: String,
    val massKg: Int,
    val topSpeedKmh: Float,
    val maxAccelMs2: Float,
    val maxBrakeDecelMs2: Float,
    val idleRpm: Int,
    val redlineRpm: Int,
    val gearCount: Int,
    val steeringRatio: Float,
    val stressMultiplier: Float,
    val brakeStressFactor: Float,
    val tyreStressFactor: Float,
    val suspensionStressFactor: Float,
    val hardAccelThresholdG: Float,
    val hardBrakeThresholdG: Float,
    val sharpTurnThresholdG: Float,
    @DrawableRes val imageRes: Int
) {
    COMPACT_EV(
        id = "compact_ev",
        displayName = "Compact EV",
        subtitle = "Urban Pulse EV • Single-Speed",
        powertrainType = "210 kW Dual-Inverter EV",
        massKg = 1520,
        topSpeedKmh = 165f,
        maxAccelMs2 = 6.9f,
        maxBrakeDecelMs2 = 9.4f,
        idleRpm = 0,
        redlineRpm = 14000,
        gearCount = 1,
        steeringRatio = 1.15f,
        stressMultiplier = 1.05f,
        brakeStressFactor = 0.85f,
        tyreStressFactor = 1.10f,
        suspensionStressFactor = 1.00f,
        hardAccelThresholdG = 0.56f,
        hardBrakeThresholdG = 0.72f,
        sharpTurnThresholdG = 0.58f,
        imageRes = R.drawable.img_veh_compact_ev
    ),
    SPORT_SEDAN(
        id = "sport_sedan",
        displayName = "Sport Sedan",
        subtitle = "Apex GT-S • 7-Speed DCT",
        powertrainType = "3.0L Twin-Turbo V6",
        massKg = 1680,
        topSpeedKmh = 265f,
        maxAccelMs2 = 9.3f,
        maxBrakeDecelMs2 = 11.6f,
        idleRpm = 850,
        redlineRpm = 7800,
        gearCount = 7,
        steeringRatio = 1.25f,
        stressMultiplier = 0.90f,
        brakeStressFactor = 0.78f,
        tyreStressFactor = 0.95f,
        suspensionStressFactor = 0.85f,
        hardAccelThresholdG = 0.68f,
        hardBrakeThresholdG = 0.85f,
        sharpTurnThresholdG = 0.68f,
        imageRes = R.drawable.img_veh_sport_sedan
    ),
    HEAVY_SUV(
        id = "heavy_suv",
        displayName = "Heavy SUV",
        subtitle = "Titan X-Haul • 8-Speed AWD",
        powertrainType = "3.5L Hybrid V6 AWD",
        massKg = 2580,
        topSpeedKmh = 195f,
        maxAccelMs2 = 5.4f,
        maxBrakeDecelMs2 = 7.4f,
        idleRpm = 700,
        redlineRpm = 5800,
        gearCount = 8,
        steeringRatio = 0.85f,
        stressMultiplier = 1.32f,
        brakeStressFactor = 1.45f,
        tyreStressFactor = 1.38f,
        suspensionStressFactor = 1.42f,
        hardAccelThresholdG = 0.45f,
        hardBrakeThresholdG = 0.58f,
        sharpTurnThresholdG = 0.46f,
        imageRes = R.drawable.img_veh_heavy_suv
    );

    companion object {
        fun fromId(id: String): VehicleProfile =
            entries.find { it.id == id } ?: COMPACT_EV
    }
}
