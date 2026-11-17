package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.BridgeConnectionState
import com.example.data.model.CarlaBridgeStatus
import com.example.data.model.SimulationInputSource
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitCardSurface
import com.example.ui.theme.CockpitElevated
import com.example.ui.theme.CockpitObsidian
import com.example.ui.theme.CriticalCrimson
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SafeEmerald
import com.example.ui.theme.TelemetryNumberStyle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BridgeAndWebScreen(
    bridgeStatus: CarlaBridgeStatus,
    onSelectInputSource: (SimulationInputSource) -> Unit,
    onToggleLocalServer: () -> Unit,
    onConnectWebSocket: (String) -> Unit,
    onDisconnectWebSocket: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var wsUrlInput by remember(bridgeStatus.endpointUrl) { mutableStateOf(bridgeStatus.endpointUrl) }
    var showEmbeddedWebPortal by remember { mutableStateOf(false) }
    var copiedNotice by remember { mutableStateOf<String?>(null) }

    val statusColor = when (bridgeStatus.connectionState) {
        BridgeConnectionState.CONNECTED_LIVE -> SafeEmerald
        BridgeConnectionState.LISTENING -> NeonCyan
        BridgeConnectionState.CONNECTING -> CautionAmber
        BridgeConnectionState.ERROR -> CriticalCrimson
        BridgeConnectionState.DISCONNECTED -> TextMuted
    }

    val carlaPayloadExample = """
        {
          "speed_kmh": 92.4,
          "rpm": 4150,
          "throttle": 0.86,
          "brake": 0.0,
          "steer_angle": -14.2,
          "accel_x": 3.8,
          "accel_y": -1.4,
          "gear": "D4",
          "speed_limit_kmh": 80,
          "source": "CARLA 0.9.15 (VW/BMW Ego)"
        }
    """.trimIndent()

    val pythonQuickCmd = "python3 carla-bridge/driveiq_carla_bridge.py --target ${bridgeStatus.localWebUrl}/api/carla/telemetry"

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    text = "CARLA SIMULATOR & WEB PORTAL",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "OEM Demo Ready (Volkswagen / BMW): stream CARLA/CAN telemetry and mirror live analytics to any browser.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            // Status & Input Source Switcher
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CockpitCardSurface)
                    .border(1.dp, CockpitBorder, RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = bridgeStatus.connectionState.label.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = statusColor
                        )
                    }
                    Text(
                        text = "FRAMES: ${bridgeStatus.externalFramesReceived} • ${bridgeStatus.lastPacketLatencyMs}ms",
                        style = TelemetryNumberStyle.copy(fontSize = 12.sp, color = TextSecondary)
                    )
                }

                Text(
                    text = bridgeStatus.statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SimulationInputSource.entries.forEach { src ->
                        val active = bridgeStatus.inputSource == src
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) NeonCyan.copy(alpha = 0.16f) else CockpitElevated)
                                .border(
                                    1.dp,
                                    if (active) NeonCyan else CockpitBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelectInputSource(src) }
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                                .testTag("source_toggle_${src.name}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = src.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (active) NeonCyan else TextSecondary
                            )
                        }
                    }
                }
            }

            // Live Web Dashboard Server & Embedded Web App Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CockpitCardSurface)
                    .border(1.dp, CockpitBorder, RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "WEBSITE APP & LAN PROJECTION SERVER",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                }

                Text(
                    text = "Launch the built-in HTTP server to open the live DriveIQ Web Dashboard on any projector/laptop browser over Wi-Fi, or preview the Web App below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CockpitElevated)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("WEB DASHBOARD URL", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextMuted)
                        Text(
                            text = bridgeStatus.localWebUrl,
                            style = TelemetryNumberStyle.copy(fontSize = 13.sp, color = NeonCyan)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Web Dashboard URL",
                        tint = NeonCyan,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable {
                                copyToClipboard(context, "DriveIQ Web URL", bridgeStatus.localWebUrl)
                                copiedNotice = "Copied Web URL: ${bridgeStatus.localWebUrl}"
                            }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onToggleLocalServer,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (bridgeStatus.localServerRunning) CriticalCrimson else SafeEmerald,
                            contentColor = CockpitObsidian
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("toggle_web_server_button")
                    ) {
                        Text(
                            text = if (bridgeStatus.localServerRunning) "STOP WEB SERVER" else "START WEB SERVER",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    OutlinedButton(
                        onClick = { showEmbeddedWebPortal = !showEmbeddedWebPortal },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("toggle_webview_preview_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Web,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (showEmbeddedWebPortal) "HIDE WEB APP" else "PREVIEW WEB APP",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan
                        )
                    }
                }

                if (showEmbeddedWebPortal) {
                    val webUrl = if (bridgeStatus.localServerRunning) {
                        "http://127.0.0.1:8765/"
                    } else {
                        "file:///android_asset/web/index.html"
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    webViewClient = WebViewClient()
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    loadUrl(webUrl)
                                }
                            },
                            update = { wv ->
                                if (wv.url != webUrl) wv.loadUrl(webUrl)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Outbound CARLA WebSocket Connector + Python Ingest Schema
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CockpitCardSurface)
                    .border(1.dp, CockpitBorder, RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Cable,
                        contentDescription = null,
                        tint = CautionAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CARLA / CAN-BUS STREAM CONNECTOR",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                }

                OutlinedTextField(
                    value = wsUrlInput,
                    onValueChange = { wsUrlInput = it },
                    label = { Text("CARLA WebSocket Endpoint (ws://host:port)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = CockpitBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("carla_ws_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onConnectWebSocket(wsUrlInput) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = CockpitObsidian
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("connect_carla_ws_button")
                    ) {
                        Text("CONNECT WS", style = MaterialTheme.typography.labelLarge)
                    }
                    OutlinedButton(
                        onClick = onDisconnectWebSocket,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("DISCONNECT", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CARLA PYTHON BRIDGE & HTTP INGEST SCHEMA",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CockpitElevated)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "HTTP POST Endpoint: ${bridgeStatus.localWebUrl}/api/carla/telemetry",
                        style = MaterialTheme.typography.labelSmall,
                        color = SafeEmerald
                    )
                    Text(
                        text = pythonQuickCmd,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = carlaPayloadExample,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    OutlinedButton(
                        onClick = {
                            copyToClipboard(context, "CARLA Bridge Command", pythonQuickCmd)
                            copiedNotice = "Copied CARLA Python launch command!"
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Python Bridge Command", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                    }
                }

                copiedNotice?.let { msg ->
                    Text(msg, style = MaterialTheme.typography.labelSmall, color = SafeEmerald)
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cb?.setPrimaryClip(ClipData.newPlainText(label, text))
}
