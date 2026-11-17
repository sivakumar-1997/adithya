package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AiCoachUiState
import com.example.data.model.SimulatorUiState
import com.example.ui.components.AiCoachPanel
import com.example.ui.components.HumanSafetyPanel
import com.example.ui.components.RuleVsTrendRiskChart
import com.example.ui.components.VehicleIntelligencePanel
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun IntelligenceScreen(
    uiState: SimulatorUiState,
    aiCoachState: AiCoachUiState,
    onAnalyzeLiveClick: () -> Unit,
    onResetSpareParts: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 680.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    text = "DUAL INTELLIGENCE MATRIX",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Simultaneous live vehicle subsystem stress, cumulative spare-part lifespan impact & behavioral safety analytics.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VehicleIntelligencePanel(
                        state = uiState.vehicleIntelligence,
                        vehicleName = uiState.selectedVehicle.displayName,
                        onResetSpareParts = onResetSpareParts,
                        modifier = Modifier.weight(1f)
                    )
                    HumanSafetyPanel(
                        state = uiState.humanSafety,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RuleVsTrendRiskChart(
                        points = uiState.liveTelemetryHistory,
                        projection = uiState.predictiveProjection,
                        modifier = Modifier.weight(1f)
                    )
                    AiCoachPanel(
                        aiState = aiCoachState,
                        buttonLabel = stringResource(R.string.ai_analyze_live),
                        onAnalyzeClick = onAnalyzeLiveClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                VehicleIntelligencePanel(
                    state = uiState.vehicleIntelligence,
                    vehicleName = uiState.selectedVehicle.displayName,
                    onResetSpareParts = onResetSpareParts
                )
                HumanSafetyPanel(state = uiState.humanSafety)
                RuleVsTrendRiskChart(
                    points = uiState.liveTelemetryHistory,
                    projection = uiState.predictiveProjection
                )
                AiCoachPanel(
                    aiState = aiCoachState,
                    buttonLabel = stringResource(R.string.ai_analyze_live),
                    onAnalyzeClick = onAnalyzeLiveClick
                )
            }
        }
    }
}
