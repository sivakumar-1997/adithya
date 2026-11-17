package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActiveWarningBanner
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.CockpitObsidian
import com.example.ui.theme.CriticalCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun NonBlockingWarningBanner(
    warning: ActiveWarningBanner?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = warning != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        warning?.let { alert ->
            val accent = if (alert.severityScore >= 80) CriticalCrimson else CautionAmber
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CockpitObsidian.copy(alpha = 0.96f))
                    .border(1.5.dp, accent, RoundedCornerShape(16.dp))
                    .padding(12.dp)
                    .testTag("active_warning_banner")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "Warning alert",
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = alert.title.uppercase(),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("dismiss_warning_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss warning",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                WarningDetailLine(label = "WHAT", text = alert.whatHappened, labelColor = TextPrimary)
                Spacer(modifier = Modifier.height(3.dp))
                WarningDetailLine(label = "IMPACT", text = alert.mechanicalImpact, labelColor = accent)
                Spacer(modifier = Modifier.height(3.dp))
                WarningDetailLine(label = "PART LIFE", text = alert.sparePartSummary, labelColor = CautionAmber)
                Spacer(modifier = Modifier.height(3.dp))
                WarningDetailLine(label = "ACTION", text = alert.correctiveAction, labelColor = NeonCyan)
            }
        }
    }
}

@Composable
private fun WarningDetailLine(
    label: String,
    text: String,
    labelColor: androidx.compose.ui.graphics.Color
) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = labelColor
            ),
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
            color = TextPrimary
        )
    }
}
