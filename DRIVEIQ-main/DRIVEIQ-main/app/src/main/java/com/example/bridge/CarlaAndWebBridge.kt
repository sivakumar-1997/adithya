package com.example.bridge

import android.content.Context
import android.util.Log
import com.example.data.model.BridgeConnectionState
import com.example.data.model.CarlaBridgeStatus
import com.example.data.model.SimulationInputSource
import com.example.data.model.SimulatorUiState
import com.example.engine.SparePartWearEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

data class ExternalCarlaFrame(
    val speedKmh: Float,
    val rpm: Int,
    val throttlePercent: Float,
    val brakePercent: Float,
    val steeringAngleDeg: Float,
    val longitudinalG: Float,
    val lateralG: Float,
    val gearDisplay: String?,
    val speedLimitKmh: Int?,
    val sourceTag: String,
    val receivedAtMs: Long
)

class CarlaAndWebBridge(
    private val context: Context
) {
    companion object {
        private const val TAG = "CarlaAndWebBridge"
        const val DEFAULT_PORT = 8765
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(6, TimeUnit.SECONDS)
        .build()

    private val _bridgeStatus = MutableStateFlow(
        CarlaBridgeStatus(
            localWebUrl = "http://${resolveLocalIpv4()}:$DEFAULT_PORT"
        )
    )
    val bridgeStatus: StateFlow<CarlaBridgeStatus> = _bridgeStatus.asStateFlow()

    private val _latestFrame = MutableStateFlow<ExternalCarlaFrame?>(null)
    val latestFrame: StateFlow<ExternalCarlaFrame?> = _latestFrame.asStateFlow()

    @Volatile
    private var latestTelemetryJson: String = "{}"

    private var webSocket: WebSocket? = null
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    fun setInputSource(source: SimulationInputSource) {
        _bridgeStatus.update {
            it.copy(
                inputSource = source,
                statusMessage = if (source == SimulationInputSource.INTERNAL_TOUCH) {
                    "Internal Touch Simulator active."
                } else {
                    "External CARLA / CAN Bridge active. Waiting for frames or connect WebSocket."
                }
            )
        }
    }

    fun updateEndpointUrl(url: String) {
        _bridgeStatus.update { it.copy(endpointUrl = url) }
    }

    fun connectWebSocket(url: String) {
        disconnectWebSocket()
        val trimmed = url.trim()
        if (!trimmed.startsWith("ws://") && !trimmed.startsWith("wss://")) {
            _bridgeStatus.update {
                it.copy(
                    connectionState = BridgeConnectionState.ERROR,
                    statusMessage = "Invalid WebSocket URL. Must start with ws:// or wss://"
                )
            }
            return
        }

        _bridgeStatus.update {
            it.copy(
                endpointUrl = trimmed,
                inputSource = SimulationInputSource.EXTERNAL_CARLA,
                connectionState = BridgeConnectionState.CONNECTING,
                statusMessage = "Connecting to CARLA bridge at $trimmed..."
            )
        }

        val request = Request.Builder().url(trimmed).build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _bridgeStatus.update {
                    it.copy(
                        connectionState = BridgeConnectionState.CONNECTED_LIVE,
                        statusMessage = "Connected to CARLA WebSocket stream ($trimmed)."
                    )
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseAndEmitFrame(text, defaultSource = "CARLA WebSocket")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                _bridgeStatus.update {
                    it.copy(
                        connectionState = BridgeConnectionState.ERROR,
                        statusMessage = "CARLA WS error: ${t.localizedMessage ?: "Connection refused"}. Verify CARLA bridge is running."
                    )
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _bridgeStatus.update {
                    it.copy(
                        connectionState = if (it.localServerRunning) BridgeConnectionState.LISTENING else BridgeConnectionState.DISCONNECTED,
                        statusMessage = "CARLA WebSocket closed ($reason)."
                    )
                }
            }
        })
    }

    fun disconnectWebSocket() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _bridgeStatus.update {
            it.copy(
                connectionState = if (it.localServerRunning) BridgeConnectionState.LISTENING else BridgeConnectionState.DISCONNECTED,
                statusMessage = if (it.localServerRunning) "Web Dashboard & HTTP Ingest listening." else "Bridge disconnected."
            )
        }
    }

    fun startLocalWebAndIngestServer(port: Int = DEFAULT_PORT) {
        if (serverJob?.isActive == true) return
        serverJob = scope.launch {
            try {
                val ss = ServerSocket(port)
                serverSocket = ss
                val hostIp = resolveLocalIpv4()
                _bridgeStatus.update {
                    it.copy(
                        localServerRunning = true,
                        localWebUrl = "http://$hostIp:$port",
                        connectionState = if (it.connectionState == BridgeConnectionState.CONNECTED_LIVE) {
                            BridgeConnectionState.CONNECTED_LIVE
                        } else {
                            BridgeConnectionState.LISTENING
                        },
                        statusMessage = "Web Dashboard & CARLA HTTP ingest active on http://$hostIp:$port"
                    )
                }

                while (isActive && !ss.isClosed) {
                    val client = ss.accept()
                    launch { handleHttpClient(client) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Local web server error", e)
                _bridgeStatus.update {
                    it.copy(
                        localServerRunning = false,
                        connectionState = BridgeConnectionState.ERROR,
                        statusMessage = "Server error: ${e.localizedMessage ?: "Port $port unavailable"}"
                    )
                }
            }
        }
    }

    fun stopLocalWebAndIngestServer() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        _bridgeStatus.update {
            it.copy(
                localServerRunning = false,
                connectionState = if (webSocket != null) BridgeConnectionState.CONNECTED_LIVE else BridgeConnectionState.DISCONNECTED,
                statusMessage = "Local Web Dashboard Server stopped."
            )
        }
    }

    private fun handleHttpClient(socket: Socket) {
        socket.use { s ->
            try {
                s.soTimeout = 4000
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                val method = parts.getOrNull(0) ?: "GET"
                val path = parts.getOrNull(1) ?: "/"

                var contentLength = 0
                while (true) {
                    val header = reader.readLine() ?: break
                    if (header.isEmpty()) break
                    if (header.lowercase().startsWith("content-length:")) {
                        contentLength = header.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                }

                val out = s.getOutputStream()
                when {
                    method == "OPTIONS" -> {
                        val resp = "HTTP/1.1 204 No Content\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                            "Access-Control-Allow-Headers: Content-Type\r\n\r\n"
                        out.write(resp.toByteArray())
                    }
                    method == "POST" && path.startsWith("/api/carla/telemetry") -> {
                        val bodyChars = CharArray(contentLength.coerceIn(0, 65536))
                        var totalRead = 0
                        while (totalRead < bodyChars.size) {
                            val read = reader.read(bodyChars, totalRead, bodyChars.size - totalRead)
                            if (read <= 0) break
                            totalRead += read
                        }
                        val body = String(bodyChars, 0, totalRead)
                        val ok = parseAndEmitFrame(body, defaultSource = "CARLA HTTP POST")
                        val jsonResp = if (ok) """{"status":"accepted"}""" else """{"status":"invalid_json"}"""
                        val code = if (ok) "200 OK" else "400 Bad Request"
                        writeHttpResponse(out, code, "application/json", jsonResp.toByteArray())
                    }
                    method == "GET" && path.startsWith("/api/telemetry") -> {
                        writeHttpResponse(out, "200 OK", "application/json", latestTelemetryJson.toByteArray())
                    }
                    method == "GET" -> {
                        val html = loadWebDashboardAsset()
                        writeHttpResponse(out, "200 OK", "text/html; charset=utf-8", html)
                    }
                    else -> {
                        writeHttpResponse(out, "404 Not Found", "text/plain", "Not Found".toByteArray())
                    }
                }
                out.flush()
            } catch (e: Exception) {
                Log.w(TAG, "Client request error: ${e.message}")
            }
        }
    }

    private fun writeHttpResponse(
        out: java.io.OutputStream,
        status: String,
        contentType: String,
        body: ByteArray
    ) {
        val headers = "HTTP/1.1 $status\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        out.write(headers.toByteArray())
        out.write(body)
    }

    private fun loadWebDashboardAsset(): ByteArray {
        return try {
            context.assets.open("web/index.html").use { it.readBytes() }
        } catch (e: Exception) {
            "<html><body><h1>DriveIQ Web Asset Missing</h1></body></html>".toByteArray()
        }
    }

    fun parseAndEmitFrame(rawJson: String, defaultSource: String): Boolean {
        return try {
            val obj = JSONObject(rawJson)
            val now = System.currentTimeMillis()
            val sentTs = obj.optLong("timestamp_ms", now)
            val latency = (now - sentTs).coerceIn(0L, 9999L)

            // Accept standard CARLA normalized [0..1] or percentage [0..100]
            val rawThrottle = obj.optDouble("throttle", 0.0).toFloat()
            val throttlePct = if (rawThrottle <= 1.0f && !obj.optBoolean("throttle_is_percent", false)) {
                rawThrottle * 100f
            } else rawThrottle

            val rawBrake = obj.optDouble("brake", 0.0).toFloat()
            val brakePct = if (rawBrake <= 1.0f && !obj.optBoolean("brake_is_percent", false)) {
                rawBrake * 100f
            } else rawBrake

            val rawSteer = obj.optDouble("steer_angle", obj.optDouble("steer", 0.0)).toFloat()
            val steerDeg = if (kotlin.math.abs(rawSteer) <= 1.0f && obj.has("steer")) {
                rawSteer * 45f
            } else rawSteer

            val accelX = obj.optDouble("accel_x", 0.0).toFloat()
            val accelY = obj.optDouble("accel_y", 0.0).toFloat()
            val speedKmh = obj.optDouble("speed_kmh", 0.0).toFloat().coerceAtLeast(0f)
            val rpm = obj.optInt("rpm", 0).coerceAtLeast(0)
            val gear = if (obj.has("gear")) obj.optString("gear") else null
            val limit = if (obj.has("speed_limit_kmh")) obj.optInt("speed_limit_kmh") else null
            val sourceTag = obj.optString("source", defaultSource)

            val frame = ExternalCarlaFrame(
                speedKmh = speedKmh,
                rpm = rpm,
                throttlePercent = throttlePct.coerceIn(0f, 100f),
                brakePercent = brakePct.coerceIn(0f, 100f),
                steeringAngleDeg = steerDeg.coerceIn(-45f, 45f),
                longitudinalG = (accelX / 9.81f).coerceIn(-1.5f, 1.5f),
                lateralG = (accelY / 9.81f).coerceIn(-1.5f, 1.5f),
                gearDisplay = gear,
                speedLimitKmh = limit,
                sourceTag = sourceTag,
                receivedAtMs = now
            )
            _latestFrame.value = frame
            _bridgeStatus.update {
                it.copy(
                    inputSource = SimulationInputSource.EXTERNAL_CARLA,
                    connectionState = BridgeConnectionState.CONNECTED_LIVE,
                    externalFramesReceived = it.externalFramesReceived + 1,
                    lastPacketLatencyMs = latency,
                    lastSourceTag = sourceTag,
                    statusMessage = "Streaming live from $sourceTag (${frame.speedKmh.toInt()} km/h)"
                )
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse CARLA JSON frame: ${e.message}")
            false
        }
    }

    fun publishTelemetrySnapshot(state: SimulatorUiState) {
        val root = JSONObject().apply {
            put("vehicle", state.selectedVehicle.displayName)
            put("powertrain", state.selectedVehicle.powertrainType)
            put("engineRunning", state.isEngineRunning)
            put("speedKmh", state.speedKmh)
            put("speedLimitKmh", state.speedLimitKmh)
            put("rpm", state.rpm)
            put("gear", state.gearDisplay)
            put("throttlePercent", state.throttlePercent)
            put("brakePercent", state.brakePercent)
            put("steeringDeg", state.steeringAngleDeg)
            put("longitudinalG", state.longitudinalG)
            put("lateralG", state.lateralG)
            put("brakingDistanceM", state.brakingDistanceMeters)
            put("distanceKm", state.distanceKm)
            put("durationSec", state.sessionDurationSec)
            put("overallStress", state.vehicleIntelligence.overallStressScore)
            put("engineStress", state.vehicleIntelligence.engineStress)
            put("brakingStress", state.vehicleIntelligence.brakingStress)
            put("tyreStress", state.vehicleIntelligence.tyreStress)
            put("suspensionStress", state.vehicleIntelligence.suspensionStress)
            put("transmissionStress", state.vehicleIntelligence.transmissionStress)
            put("safetyScore", state.humanSafety.safetyScore)
            put("riskLevel", state.humanSafety.riskLevel.label)
            put("ruleRisk", state.predictiveProjection.currentRuleRisk)
            put("trendRisk", state.predictiveProjection.currentTrendRisk)
            put("projected5MinRisk", state.predictiveProjection.projected5MinRisk)
            put("worstSparePartAdvisory", state.vehicleIntelligence.worstSparePartAdvisory)
            put("inputSource", state.bridgeStatus.inputSource.label)
            put("framesReceived", state.bridgeStatus.externalFramesReceived)

            val partsArr = JSONArray()
            state.vehicleIntelligence.sparePartWearList.forEach { sp ->
                partsArr.put(JSONObject().apply {
                    put("name", sp.part.displayName)
                    put("priorLost", SparePartWearEngine.formatMonthsHuman(sp.priorLostMonths))
                    put("sessionLost", SparePartWearEngine.formatMonthsHuman(sp.sessionLostMonths))
                    put("totalLost", SparePartWearEngine.formatMonthsHuman(sp.totalLostMonths))
                    put("remaining", SparePartWearEngine.formatMonthsHuman(sp.remainingMonths))
                    put("healthPercent", sp.healthPercent.toInt())
                    put("summary", sp.impactSummary)
                })
            }
            put("spareParts", partsArr)

            state.activeWarning?.let { warn ->
                put("activeWarning", JSONObject().apply {
                    put("title", warn.title)
                    put("whatHappened", warn.whatHappened)
                    put("mechanicalImpact", warn.mechanicalImpact)
                    put("correctiveAction", warn.correctiveAction)
                    put("sparePartSummary", warn.sparePartSummary)
                })
            }
        }
        latestTelemetryJson = root.toString()
    }

    private fun resolveLocalIpv4(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
            "127.0.0.1"
        } catch (_: Exception) {
            "127.0.0.1"
        }
    }

    fun release() {
        disconnectWebSocket()
        stopLocalWebAndIngestServer()
    }
}
