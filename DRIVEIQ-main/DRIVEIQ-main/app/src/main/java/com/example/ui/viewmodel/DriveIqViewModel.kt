package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.DriveAiCoachRepository
import com.example.bridge.CarlaAndWebBridge
import com.example.data.local.DriveIqDatabase
import com.example.data.local.DrivingEventEntity
import com.example.data.local.DrivingSessionEntity
import com.example.data.local.SparePartWearEntity
import com.example.data.local.TelemetryRepository
import com.example.data.local.TelemetrySampleEntity
import com.example.data.model.AiCoachUiState
import com.example.data.model.SimulationInputSource
import com.example.data.model.SimulatorUiState
import com.example.data.model.SparePartType
import com.example.data.model.TelemetryPoint
import com.example.data.model.VehicleProfile
import com.example.engine.PhysicsEngine
import com.example.engine.PhysicsInternalState
import com.example.engine.PhysicsStepInput
import com.example.engine.PredictiveRiskEngine
import com.example.engine.SoundCueEngine
import com.example.engine.SparePartWearEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

class DriveIqViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: TelemetryRepository = TelemetryRepository(
        DriveIqDatabase.getInstance(application).driveIqDao()
    ),
    private val soundCueEngine: SoundCueEngine = SoundCueEngine(),
    private val aiCoachRepository: DriveAiCoachRepository = DriveAiCoachRepository(),
    private val carlaAndWebBridge: CarlaAndWebBridge = CarlaAndWebBridge(application)
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "DriveIqViewModel"
        private const val TICK_MS = 33L
        private const val TELEMETRY_LOG_INTERVAL_MS = 1000L
        private const val WARNING_AUTO_DISMISS_MS = 5500L
    }

    private val physicsEngine = PhysicsEngine()
    private val predictiveRiskEngine = PredictiveRiskEngine()

    private val _uiState = MutableStateFlow(SimulatorUiState())
    val uiState: StateFlow<SimulatorUiState> = _uiState.asStateFlow()

    private val _aiCoachState = MutableStateFlow<AiCoachUiState>(AiCoachUiState.Idle)
    val aiCoachState: StateFlow<AiCoachUiState> = _aiCoachState.asStateFlow()

    val pastSessions: StateFlow<List<DrivingSessionEntity>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentEvents: StateFlow<List<DrivingEventEntity>> = repository.recentEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _inspectedSessionId = MutableStateFlow<Long?>(null)
    val inspectedSessionId: StateFlow<Long?> = _inspectedSessionId.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val inspectedSessionEvents: StateFlow<List<DrivingEventEntity>> = _inspectedSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.observeEventsForSession(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val inspectedSessionTelemetry: StateFlow<List<TelemetrySampleEntity>> = _inspectedSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.observeTelemetryForSession(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var physicsState = PhysicsInternalState()
    private var simulationJob: Job? = null
    private var warningDismissJob: Job? = null

    @Volatile private var throttleHeld = false
    @Volatile private var throttleTarget = 0f
    @Volatile private var brakeHeld = false
    @Volatile private var brakeTarget = 0f
    @Volatile private var steeringDragging = false
    @Volatile private var targetSteeringDeg = 0f

    private var sessionStartMs = 0L
    private var lastTelemetryPersistMs = 0L
    private var speedAccumulatorSum = 0.0
    private var speedSampleCount = 0

    init {
        viewModelScope.launch {
            soundCueEngine.activeCueLabel.collect { cue ->
                _uiState.update { it.copy(activeAudioCueLabel = cue) }
            }
        }
        viewModelScope.launch {
            carlaAndWebBridge.bridgeStatus.collect { status ->
                _uiState.update { it.copy(bridgeStatus = status) }
            }
        }
        viewModelScope.launch {
            carlaAndWebBridge.latestFrame.collect { frame ->
                if (frame != null && _uiState.value.bridgeStatus.inputSource == SimulationInputSource.EXTERNAL_CARLA) {
                    if (!_uiState.value.isEngineRunning) {
                        startEngine()
                    }
                }
            }
        }
        loadSparePartWearForVehicle(_uiState.value.selectedVehicle)
    }

    private fun loadSparePartWearForVehicle(profile: VehicleProfile) {
        viewModelScope.launch {
            val stored = repository.getSparePartWear(profile.id)
            val priorMap = SparePartType.entries.associateWith { part ->
                stored.find { it.partId == part.id }?.cumulativeLostMonths ?: 0f
            }
            physicsState = physicsState.copy(
                priorPartWearMonths = priorMap,
                sessionPartWearMonths = emptyMap()
            )
            val wearStatuses = SparePartWearEngine.buildWearStatuses(priorMap, emptyMap())
            val worst = wearStatuses.maxByOrNull { it.totalLostMonths }
            val advisory = if (worst == null || worst.totalLostMonths <= 0.02f) {
                "All spare parts at baseline health. Smooth inputs preserve full factory lifespan."
            } else {
                worst.impactSummary
            }
            _uiState.update { state ->
                val updated = state.copy(
                    vehicleIntelligence = state.vehicleIntelligence.copy(
                        sparePartWearList = wearStatuses,
                        worstSparePartAdvisory = advisory
                    )
                )
                carlaAndWebBridge.publishTelemetrySnapshot(updated)
                updated
            }
        }
    }

    private fun persistCurrentSparePartWear(vehicleId: String) {
        val now = System.currentTimeMillis()
        val entities = SparePartType.entries.map { part ->
            val prior = physicsState.priorPartWearMonths[part] ?: 0f
            val session = physicsState.sessionPartWearMonths[part] ?: 0f
            val total = (prior + session).coerceAtMost(part.baselineLifeMonths)
            SparePartWearEntity(
                vehicleId = vehicleId,
                partId = part.id,
                cumulativeLostMonths = total,
                lastUpdatedMs = now
            )
        }
        viewModelScope.launch {
            repository.saveSparePartWear(entities)
        }
    }

    fun resetSparePartWearForSelectedVehicle() {
        val vehicle = _uiState.value.selectedVehicle
        viewModelScope.launch {
            repository.resetSparePartWear(vehicle.id)
            physicsState = physicsState.copy(
                priorPartWearMonths = emptyMap(),
                sessionPartWearMonths = emptyMap()
            )
            val wearStatuses = SparePartWearEngine.buildWearStatuses(emptyMap(), emptyMap())
            _uiState.update { state ->
                val updated = state.copy(
                    vehicleIntelligence = state.vehicleIntelligence.copy(
                        sparePartWearList = wearStatuses,
                        worstSparePartAdvisory = "Parts serviced/reset to 100% factory baseline."
                    )
                )
                carlaAndWebBridge.publishTelemetrySnapshot(updated)
                updated
            }
        }
    }

    fun selectVehicle(profile: VehicleProfile) {
        if (_uiState.value.isEngineRunning) {
            persistCurrentSparePartWear(_uiState.value.selectedVehicle.id)
            _uiState.update { it.copy(selectedVehicle = profile) }
            loadSparePartWearForVehicle(profile)
        } else {
            physicsState = PhysicsInternalState()
            _uiState.update {
                it.copy(
                    selectedVehicle = profile,
                    speedKmh = 0f,
                    rpm = 0,
                    gearDisplay = "P",
                    brakingDistanceMeters = 0f
                )
            }
            loadSparePartWearForVehicle(profile)
        }
    }

    fun setSpeedLimit(limitKmh: Int) {
        _uiState.update { it.copy(speedLimitKmh = limitKmh.coerceIn(40, 160)) }
    }

    fun toggleMuteAudio() {
        val nextMuted = !_uiState.value.isAudioMuted
        soundCueEngine.setMuted(nextMuted)
        _uiState.update { it.copy(isAudioMuted = nextMuted) }
    }

    fun toggleAutoCenterSteering() {
        _uiState.update { it.copy(autoCenterSteering = !it.autoCenterSteering) }
    }

    fun setAcceleratorInput(isPressed: Boolean, intensityPercent: Float = 100f) {
        throttleHeld = isPressed
        throttleTarget = if (isPressed) intensityPercent.coerceIn(15f, 100f) else 0f
    }

    fun setBrakeInput(isPressed: Boolean, intensityPercent: Float = 100f) {
        brakeHeld = isPressed
        brakeTarget = if (isPressed) intensityPercent.coerceIn(15f, 100f) else 0f
    }

    fun setSteeringInput(angleDeg: Float, isDragging: Boolean) {
        steeringDragging = isDragging
        targetSteeringDeg = angleDeg.coerceIn(-45f, 45f)
        _uiState.update { it.copy(steeringAngleDeg = targetSteeringDeg) }
    }

    fun centerSteering() {
        steeringDragging = false
        targetSteeringDeg = 0f
        _uiState.update { it.copy(steeringAngleDeg = 0f) }
    }

    fun dismissWarning() {
        warningDismissJob?.cancel()
        _uiState.update { it.copy(activeWarning = null) }
    }

    // Bridge & Web Controls
    fun setInputSource(source: SimulationInputSource) {
        carlaAndWebBridge.setInputSource(source)
    }

    fun connectCarlaWebSocket(url: String) {
        carlaAndWebBridge.connectWebSocket(url)
    }

    fun disconnectCarlaWebSocket() {
        carlaAndWebBridge.disconnectWebSocket()
    }

    fun toggleLocalWebServer() {
        if (_uiState.value.bridgeStatus.localServerRunning) {
            carlaAndWebBridge.stopLocalWebAndIngestServer()
        } else {
            carlaAndWebBridge.startLocalWebAndIngestServer()
            carlaAndWebBridge.publishTelemetrySnapshot(_uiState.value)
        }
    }

    fun toggleEngine() {
        if (_uiState.value.isEngineRunning) {
            stopEngine()
        } else {
            startEngine()
        }
    }

    private fun startEngine() {
        val now = System.currentTimeMillis()
        sessionStartMs = now
        lastTelemetryPersistMs = now
        speedAccumulatorSum = 0.0
        speedSampleCount = 0

        val vehicle = _uiState.value.selectedVehicle
        val speedLimit = _uiState.value.speedLimitKmh

        viewModelScope.launch {
            val stored = repository.getSparePartWear(vehicle.id)
            val priorMap = SparePartType.entries.associateWith { part ->
                stored.find { it.partId == part.id }?.cumulativeLostMonths ?: 0f
            }
            physicsState = PhysicsInternalState(
                priorPartWearMonths = priorMap,
                sessionPartWearMonths = emptyMap()
            )
            throttleHeld = false
            throttleTarget = 0f
            brakeHeld = false
            brakeTarget = 0f
            targetSteeringDeg = 0f

            soundCueEngine.playEngineStart(vehicle)

            val sessionId = repository.startSession(
                DrivingSessionEntity(
                    vehicleId = vehicle.id,
                    vehicleName = vehicle.displayName,
                    startedAtMs = now,
                    speedLimitKmh = speedLimit
                )
            )
            _inspectedSessionId.value = sessionId
            _uiState.update {
                it.copy(
                    isEngineRunning = true,
                    activeSessionId = sessionId,
                    speedKmh = 0f,
                    maxSpeedKmh = 0f,
                    rpm = vehicle.idleRpm,
                    gearDisplay = if (vehicle == VehicleProfile.COMPACT_EV) "D0" else "D1",
                    throttlePercent = 0f,
                    brakePercent = 0f,
                    steeringAngleDeg = 0f,
                    longitudinalG = 0f,
                    lateralG = 0f,
                    brakingDistanceMeters = 0f,
                    distanceKm = 0f,
                    sessionDurationSec = 0L,
                    activeWarning = null,
                    liveTelemetryHistory = emptyList(),
                    liveEvents = emptyList()
                )
            }
            startSimulationLoop()
        }
    }

    private fun stopEngine() {
        soundCueEngine.playEngineStop()
        val current = _uiState.value
        val activeId = current.activeSessionId
        val endMs = System.currentTimeMillis()

        throttleHeld = false
        throttleTarget = 0f
        brakeHeld = false
        brakeTarget = 0f

        persistCurrentSparePartWear(current.selectedVehicle.id)
        val totalSessionMonthsLost = physicsState.sessionPartWearMonths.values.sum()

        _uiState.update {
            it.copy(
                isEngineRunning = false,
                throttlePercent = 0f,
                brakePercent = 0f,
                rpm = 0,
                gearDisplay = "P"
            )
        }

        if (activeId != null) {
            val avgSpeed = if (speedSampleCount > 0) (speedAccumulatorSum / speedSampleCount).toFloat() else 0f
            val session = DrivingSessionEntity(
                id = activeId,
                vehicleId = current.selectedVehicle.id,
                vehicleName = current.selectedVehicle.displayName,
                startedAtMs = sessionStartMs,
                endedAtMs = endMs,
                durationSec = current.sessionDurationSec.coerceAtLeast(1L),
                distanceKm = current.distanceKm,
                avgSpeedKmh = avgSpeed,
                maxSpeedKmh = current.maxSpeedKmh,
                finalSafetyScore = current.humanSafety.safetyScore,
                peakVehicleStress = current.vehicleIntelligence.peakStressScore,
                riskLevel = current.humanSafety.riskLevel.label,
                hardAccelCount = current.humanSafety.hardAccelCount,
                hardBrakeCount = current.humanSafety.hardBrakeCount,
                sharpSteerCount = current.humanSafety.sharpSteerCount,
                overspeedCount = current.humanSafety.overspeedCount,
                speedLimitKmh = current.speedLimitKmh,
                totalPartMonthsLostThisSession = totalSessionMonthsLost
            )
            viewModelScope.launch {
                repository.updateSession(session)
            }
        }
    }

    private fun startSimulationLoop() {
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            var lastTickMs = System.currentTimeMillis()
            while (true) {
                delay(TICK_MS)
                val now = System.currentTimeMillis()
                val dtSec = ((now - lastTickMs) / 1000f).coerceIn(0.01f, 0.1f)
                lastTickMs = now

                val current = _uiState.value
                if (!current.isEngineRunning && physicsState.speedMs <= 0.05f) {
                    soundCueEngine.updateTelemetry(
                        vehicle = current.selectedVehicle,
                        isRunning = false,
                        rpm = 0,
                        speedKmh = 0f,
                        throttlePercent = 0f,
                        brakePercent = 0f
                    )
                    carlaAndWebBridge.publishTelemetrySnapshot(current)
                    break
                }

                // Check if external CARLA bridge is feeding live controls/telemetry
                val externalFrame = carlaAndWebBridge.latestFrame.value
                val usingExternal = current.bridgeStatus.inputSource == SimulationInputSource.EXTERNAL_CARLA &&
                    externalFrame != null &&
                    (now - externalFrame.receivedAtMs) < 3000L

                val nextThrottle: Float
                val nextBrake: Float
                val nextSteer: Float

                if (usingExternal && externalFrame != null) {
                    nextThrottle = externalFrame.throttlePercent
                    nextBrake = externalFrame.brakePercent
                    nextSteer = externalFrame.steeringAngleDeg
                    physicsState = physicsState.copy(speedMs = externalFrame.speedKmh / 3.6f)
                } else {
                    nextThrottle = if (current.isEngineRunning && throttleHeld) {
                        (current.throttlePercent + (throttleTarget - current.throttlePercent) * (dtSec * 6.5f).coerceIn(0f, 1f))
                            .coerceIn(0f, 100f)
                    } else {
                        (current.throttlePercent - 260f * dtSec).coerceAtLeast(0f)
                    }

                    nextBrake = if (brakeHeld) {
                        (current.brakePercent + (brakeTarget - current.brakePercent) * (dtSec * 8.0f).coerceIn(0f, 1f))
                            .coerceIn(0f, 100f)
                    } else {
                        (current.brakePercent - 300f * dtSec).coerceAtLeast(0f)
                    }

                    nextSteer = if (!steeringDragging && current.autoCenterSteering && abs(targetSteeringDeg) > 0.2f && current.speedKmh > 2f) {
                        val returnRate = (1.5f + (current.speedKmh / 60f)) * 22f * dtSec
                        if (abs(targetSteeringDeg) <= returnRate) {
                            targetSteeringDeg = 0f
                            0f
                        } else {
                            targetSteeringDeg -= kotlin.math.sign(targetSteeringDeg) * returnRate
                            targetSteeringDeg
                        }
                    } else {
                        targetSteeringDeg
                    }
                }

                val output = physicsEngine.step(
                    state = physicsState,
                    input = PhysicsStepInput(
                        dtSec = dtSec,
                        nowMs = now,
                        vehicle = current.selectedVehicle,
                        isEngineRunning = current.isEngineRunning,
                        speedLimitKmh = current.speedLimitKmh,
                        throttlePercent = nextThrottle,
                        brakePercent = nextBrake,
                        steeringAngleDeg = nextSteer
                    )
                )
                physicsState = output.nextState

                val effectiveSpeedKmh = if (usingExternal && externalFrame != null) externalFrame.speedKmh else output.speedKmh
                val effectiveRpm = if (usingExternal && externalFrame != null && externalFrame.rpm > 0) externalFrame.rpm else output.nextState.rpm
                val effectiveGear = if (usingExternal && externalFrame?.gearDisplay != null) externalFrame.gearDisplay else output.nextState.gearDisplay

                // Real-time continuous EV vs Petrol engine sound synthesis
                soundCueEngine.updateTelemetry(
                    vehicle = current.selectedVehicle,
                    isRunning = current.isEngineRunning,
                    rpm = effectiveRpm,
                    speedKmh = effectiveSpeedKmh,
                    throttlePercent = nextThrottle,
                    brakePercent = nextBrake
                )

                if (output.isBrakeSquealTriggered) {
                    soundCueEngine.playBrakeSqueal()
                }
                if (output.isOverspeedChimeTriggered) {
                    soundCueEngine.playSpeedLimitChime()
                }

                val ruleRisk = predictiveRiskEngine.computeRuleBasedRisk(
                    speedKmh = effectiveSpeedKmh,
                    speedLimitKmh = current.speedLimitKmh,
                    throttlePercent = nextThrottle,
                    brakePercent = nextBrake,
                    steeringAngleDeg = nextSteer,
                    longitudinalG = output.nextState.longitudinalG,
                    lateralG = output.nextState.lateralG
                )
                val projection = predictiveRiskEngine.computeTrendProjection(
                    history = current.liveTelemetryHistory,
                    currentRuleRisk = ruleRisk,
                    totalAggressiveEvents = output.humanSafety.totalAggressiveEvents,
                    sessionDurationSec = if (current.isEngineRunning) (now - sessionStartMs) / 1000L else current.sessionDurationSec
                )

                val newEventsList = if (output.triggeredEvent != null) {
                    scheduleWarningDismiss()
                    val banner = output.triggeredEvent
                    persistCurrentSparePartWear(current.selectedVehicle.id)
                    current.activeSessionId?.let { sId ->
                        viewModelScope.launch {
                            repository.logEvent(
                                DrivingEventEntity(
                                    sessionId = sId,
                                    timestampMs = banner.timestampMs,
                                    eventType = banner.eventType.label,
                                    severityScore = banner.severityScore,
                                    speedKmh = effectiveSpeedKmh,
                                    gForce = maxOf(abs(output.nextState.longitudinalG), abs(output.nextState.lateralG)),
                                    title = banner.title,
                                    whatHappened = banner.whatHappened,
                                    mechanicalImpact = banner.mechanicalImpact,
                                    correctiveAction = banner.correctiveAction,
                                    affectedSparePart = banner.affectedSparePart,
                                    priorPartLossMonths = banner.priorPartLossMonths,
                                    eventPartLossMonths = banner.eventPartLossMonths,
                                    totalPartLossMonths = banner.totalPartLossMonths,
                                    sparePartSummary = banner.sparePartSummary
                                )
                            )
                        }
                    }
                    (listOf(banner) + current.liveEvents).take(30)
                } else {
                    current.liveEvents
                }

                var updatedHistory = current.liveTelemetryHistory
                if (current.isEngineRunning && (now - lastTelemetryPersistMs) >= TELEMETRY_LOG_INTERVAL_MS) {
                    lastTelemetryPersistMs = now
                    speedAccumulatorSum += effectiveSpeedKmh
                    speedSampleCount++

                    val point = TelemetryPoint(
                        timestampMs = now,
                        speedKmh = effectiveSpeedKmh,
                        rpm = effectiveRpm,
                        vehicleStress = output.vehicleIntelligence.overallStressScore,
                        safetyScore = output.humanSafety.safetyScore,
                        ruleRisk = projection.currentRuleRisk,
                        trendRisk = projection.currentTrendRisk
                    )
                    updatedHistory = (updatedHistory + point).takeLast(60)

                    current.activeSessionId?.let { sId ->
                        viewModelScope.launch {
                            repository.logTelemetrySample(
                                TelemetrySampleEntity(
                                    sessionId = sId,
                                    timestampMs = now,
                                    speedKmh = effectiveSpeedKmh,
                                    rpm = effectiveRpm,
                                    throttlePercent = nextThrottle,
                                    brakePercent = nextBrake,
                                    steeringDeg = nextSteer,
                                    longitudinalG = output.nextState.longitudinalG,
                                    lateralG = output.nextState.lateralG,
                                    vehicleStress = output.vehicleIntelligence.overallStressScore,
                                    safetyScore = output.humanSafety.safetyScore,
                                    ruleRisk = projection.currentRuleRisk,
                                    trendRisk = projection.currentTrendRisk
                                )
                            )
                        }
                    }
                }

                val durationSec = if (current.isEngineRunning) {
                    ((now - sessionStartMs) / 1000L).coerceAtLeast(0L)
                } else {
                    current.sessionDurationSec
                }

                _uiState.update {
                    val nextUi = it.copy(
                        speedKmh = effectiveSpeedKmh,
                        maxSpeedKmh = maxOf(it.maxSpeedKmh, effectiveSpeedKmh),
                        rpm = effectiveRpm,
                        gearDisplay = effectiveGear,
                        throttlePercent = nextThrottle,
                        brakePercent = nextBrake,
                        steeringAngleDeg = nextSteer,
                        longitudinalG = output.nextState.longitudinalG,
                        lateralG = output.nextState.lateralG,
                        brakingDistanceMeters = output.brakingDistanceMeters,
                        distanceKm = output.nextState.distanceKm,
                        sessionDurationSec = durationSec,
                        activeWarning = output.triggeredEvent ?: it.activeWarning,
                        vehicleIntelligence = output.vehicleIntelligence,
                        humanSafety = output.humanSafety,
                        predictiveProjection = projection,
                        liveTelemetryHistory = updatedHistory,
                        liveEvents = newEventsList
                    )
                    carlaAndWebBridge.publishTelemetrySnapshot(nextUi)
                    nextUi
                }
            }
        }
    }

    private fun scheduleWarningDismiss() {
        warningDismissJob?.cancel()
        warningDismissJob = viewModelScope.launch {
            delay(WARNING_AUTO_DISMISS_MS)
            _uiState.update { it.copy(activeWarning = null) }
        }
    }

    fun inspectSession(sessionId: Long) {
        _inspectedSessionId.value = sessionId
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_inspectedSessionId.value == sessionId) {
                _inspectedSessionId.value = null
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllSessions()
            _inspectedSessionId.value = null
        }
    }

    fun analyzeLiveWithAi() {
        val state = _uiState.value
        viewModelScope.launch {
            _aiCoachState.value = AiCoachUiState.Loading
            try {
                val insight = aiCoachRepository.analyzeDriveTelemetry(
                    vehicleName = state.selectedVehicle.displayName,
                    durationSec = state.sessionDurationSec,
                    distanceKm = state.distanceKm,
                    maxSpeedKmh = state.maxSpeedKmh,
                    speedLimitKmh = state.speedLimitKmh,
                    safetyScore = state.humanSafety.safetyScore,
                    overallStress = state.vehicleIntelligence.overallStressScore,
                    engineStress = state.vehicleIntelligence.engineStress,
                    brakeStress = state.vehicleIntelligence.brakingStress,
                    tyreStress = state.vehicleIntelligence.tyreStress,
                    hardAccelCount = state.humanSafety.hardAccelCount,
                    hardBrakeCount = state.humanSafety.hardBrakeCount,
                    sharpSteerCount = state.humanSafety.sharpSteerCount,
                    overspeedCount = state.humanSafety.overspeedCount,
                    ruleRisk = state.predictiveProjection.currentRuleRisk,
                    trendRisk = state.predictiveProjection.currentTrendRisk,
                    projected5MinRisk = state.predictiveProjection.projected5MinRisk,
                    spareParts = state.vehicleIntelligence.sparePartWearList
                )
                _aiCoachState.value = AiCoachUiState.Success(
                    insight = insight,
                    sourceLabel = "Live Telemetry • ${state.selectedVehicle.displayName}"
                )
                state.activeSessionId?.let { id ->
                    repository.saveAiSummary(id, "${insight.summary} ${insight.sparePartLifeForecast}")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI live analysis timed out", e)
                _aiCoachState.value = AiCoachUiState.Error("Gemini AI coach timed out after 45 seconds. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI live analysis failed", e)
                _aiCoachState.value = AiCoachUiState.Error(e.localizedMessage ?: "Unable to reach Gemini AI Coach.")
            }
        }
    }

    fun analyzeSessionWithAi(session: DrivingSessionEntity) {
        viewModelScope.launch {
            _aiCoachState.value = AiCoachUiState.Loading
            try {
                val samples = repository.getTelemetryForSession(session.id)
                val lastSample = samples.lastOrNull()
                val storedWear = repository.getSparePartWear(session.vehicleId)
                val totalWearMap = SparePartType.entries.associateWith { part ->
                    storedWear.find { it.partId == part.id }?.cumulativeLostMonths ?: 0f
                }
                val wearList = SparePartWearEngine.buildWearStatuses(totalWearMap, emptyMap())

                val insight = aiCoachRepository.analyzeDriveTelemetry(
                    vehicleName = session.vehicleName,
                    durationSec = session.durationSec,
                    distanceKm = session.distanceKm,
                    maxSpeedKmh = session.maxSpeedKmh,
                    speedLimitKmh = session.speedLimitKmh,
                    safetyScore = session.finalSafetyScore,
                    overallStress = session.peakVehicleStress,
                    engineStress = session.peakVehicleStress * 0.9f,
                    brakeStress = session.peakVehicleStress * 0.85f,
                    tyreStress = session.peakVehicleStress * 0.8f,
                    hardAccelCount = session.hardAccelCount,
                    hardBrakeCount = session.hardBrakeCount,
                    sharpSteerCount = session.sharpSteerCount,
                    overspeedCount = session.overspeedCount,
                    ruleRisk = lastSample?.ruleRisk ?: 0f,
                    trendRisk = lastSample?.trendRisk ?: 0f,
                    projected5MinRisk = lastSample?.trendRisk ?: 0f,
                    spareParts = wearList
                )
                repository.saveAiSummary(session.id, "${insight.summary} ${insight.sparePartLifeForecast}")
                _aiCoachState.value = AiCoachUiState.Success(
                    insight = insight,
                    sourceLabel = "Session #${session.id} • ${session.vehicleName}"
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI session debrief timed out", e)
                _aiCoachState.value = AiCoachUiState.Error("Gemini AI debrief timed out. Please retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI session debrief failed", e)
                _aiCoachState.value = AiCoachUiState.Error(e.localizedMessage ?: "Failed to generate AI debrief.")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        simulationJob?.cancel()
        warningDismissJob?.cancel()
        carlaAndWebBridge.release()
    }
}
