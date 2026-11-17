package com.example.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.data.model.VehicleProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Real-time continuous PCM audio synthesizer differentiating EV electric inverter whine
 * vs Petrol multi-harmonic internal combustion engine sound, plus brake squeal & limit chimes.
 */
class SoundCueEngine(
    private val enabledAudioHardware: Boolean = true
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _activeCueLabel = MutableStateFlow<String?>(null)
    val activeCueLabel: StateFlow<String?> = _activeCueLabel.asStateFlow()

    @Volatile private var isMuted: Boolean = false
    @Volatile private var engineRunning: Boolean = false
    @Volatile private var currentVehicle: VehicleProfile = VehicleProfile.COMPACT_EV
    @Volatile private var currentRpm: Int = 0
    @Volatile private var currentSpeedKmh: Float = 0f
    @Volatile private var currentThrottle: Float = 0f
    @Volatile private var currentBrake: Float = 0f

    private var lastChimeCueMs = 0L
    private var lastSquealCueMs = 0L
    private var cueClearJob: Job? = null
    private var streamJob: Job? = null

    fun setMuted(muted: Boolean) {
        isMuted = muted
        if (muted) {
            _activeCueLabel.value = null
            stopContinuousEngineAudio()
        } else if (engineRunning) {
            startContinuousEngineAudio()
        }
    }

    fun updateTelemetry(
        vehicle: VehicleProfile,
        isRunning: Boolean,
        rpm: Int,
        speedKmh: Float,
        throttlePercent: Float,
        brakePercent: Float
    ) {
        currentVehicle = vehicle
        currentRpm = rpm
        currentSpeedKmh = speedKmh
        currentThrottle = throttlePercent
        currentBrake = brakePercent

        if (isRunning && !engineRunning) {
            engineRunning = true
            startContinuousEngineAudio()
        } else if (!isRunning && engineRunning) {
            engineRunning = false
            stopContinuousEngineAudio()
        }
    }

    fun playEngineStart(vehicle: VehicleProfile = currentVehicle) {
        currentVehicle = vehicle
        engineRunning = true
        val cueText = if (vehicle == VehicleProfile.COMPACT_EV) {
            "EV Audio: Electric Inverter Ready"
        } else {
            "Petrol Audio: ${vehicle.displayName} Ignition"
        }
        showCueLabel(cueText)

        if (!isMuted && enabledAudioHardware) {
            scope.launch {
                if (vehicle == VehicleProfile.COMPACT_EV) {
                    // Futuristic EV ascending harmonic startup chime
                    playDualTone(freq1 = 330.0, freq2 = 660.0, durationMs = 170, amplitude = 0.16)
                    delay(40)
                    playDualTone(freq1 = 440.0, freq2 = 880.0, durationMs = 220, amplitude = 0.18)
                } else {
                    // Petrol starter crank + exhaust bark
                    playPetrolCrankStartup(isHeavy = vehicle == VehicleProfile.HEAVY_SUV)
                }
                if (engineRunning && !isMuted) {
                    startContinuousEngineAudio()
                }
            }
        }
    }

    fun playEngineStop() {
        engineRunning = false
        stopContinuousEngineAudio()
        showCueLabel(
            if (currentVehicle == VehicleProfile.COMPACT_EV) "EV Audio: Inverter Standby"
            else "Petrol Audio: Engine Off"
        )
        if (isMuted || !enabledAudioHardware) return
        scope.launch {
            playSweepTone(
                startFreq = if (currentVehicle == VehicleProfile.COMPACT_EV) 380.0 else 135.0,
                endFreq = 50.0,
                durationMs = 240,
                amplitude = 0.14
            )
        }
    }

    fun playBrakeSqueal() {
        val now = System.currentTimeMillis()
        if (now - lastSquealCueMs < 1500L) return
        lastSquealCueMs = now
        showCueLabel("Simulated Cue: Brake Pad Squeal")
        if (isMuted || !enabledAudioHardware) return
        scope.launch {
            playDualTone(freq1 = 2480.0, freq2 = 2710.0, durationMs = 240, amplitude = 0.12)
        }
    }

    fun playSpeedLimitChime() {
        val now = System.currentTimeMillis()
        if (now - lastChimeCueMs < 2000L) return
        lastChimeCueMs = now
        showCueLabel("Simulated Cue: Speed-Limit Chime")
        if (isMuted || !enabledAudioHardware) return
        scope.launch {
            playDualTone(freq1 = 880.0, freq2 = 1320.0, durationMs = 130, amplitude = 0.16)
            delay(50)
            playDualTone(freq1 = 1046.5, freq2 = 1568.0, durationMs = 150, amplitude = 0.16)
        }
    }

    private fun startContinuousEngineAudio() {
        if (!enabledAudioHardware || isMuted) return
        if (streamJob?.isActive == true) return

        streamJob = scope.launch {
            val sampleRate = 22050
            val chunkSamples = 735 // ~33ms per audio frame
            val buffer = ShortArray(chunkSamples)
            var track: AudioTrack? = null

            try {
                val minBytes = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(chunkSamples * 4)

                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.play()

                var phase1 = 0.0
                var phase2 = 0.0
                var phase3 = 0.0
                var smoothedFreq = 100.0

                while (isActive && engineRunning && !isMuted) {
                    val veh = currentVehicle
                    val throttleNorm = (currentThrottle / 100f).coerceIn(0f, 1f).toDouble()
                    val brakeNorm = (currentBrake / 100f).coerceIn(0f, 1f).toDouble()
                    val speedNorm = (currentSpeedKmh / veh.topSpeedKmh).coerceIn(0f, 1f).toDouble()

                    if (veh == VehicleProfile.COMPACT_EV) {
                        // EV Electric Motor + Inverter Harmonics (no combustion rumble)
                        val targetFreq = 165.0 + speedNorm * 620.0 + throttleNorm * 140.0
                        val gain = if (currentSpeedKmh < 1f && throttleNorm < 0.02) {
                            0.018 // quiet EV pedestrian hum at idle
                        } else {
                            0.06 + throttleNorm * 0.08 + brakeNorm * 0.04 // subtle regen whine on brake
                        }

                        for (i in 0 until chunkSamples) {
                            smoothedFreq += (targetFreq - smoothedFreq) * 0.008
                            phase1 += 2.0 * PI * smoothedFreq / sampleRate
                            phase2 += 2.0 * PI * (smoothedFreq * 2.0) / sampleRate
                            phase3 += 2.0 * PI * (smoothedFreq * 4.2 + 280.0) / sampleRate // inverter carrier

                            if (phase1 > 2.0 * PI) phase1 -= 2.0 * PI
                            if (phase2 > 2.0 * PI) phase2 -= 2.0 * PI
                            if (phase3 > 2.0 * PI) phase3 -= 2.0 * PI

                            val sample = (
                                0.60 * sin(phase1) +
                                0.25 * sin(phase2) +
                                0.15 * sin(phase3)
                            ) * gain
                            buffer[i] = (sample * Short.MAX_VALUE).toInt()
                                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                        }
                    } else {
                        // Petrol Internal Combustion Engine (V6 firing frequency & exhaust harmonics)
                        val rpmDouble = currentRpm.coerceAtLeast(veh.idleRpm).toDouble()
                        // 6-cyl 4-stroke firing freq = (RPM / 60) * 3, scaled cleanly for audible phone speaker punch
                        val baseFiringHz = ((rpmDouble / 60.0) * 2.4).coerceIn(38.0, 340.0)
                        val targetFreq = if (veh == VehicleProfile.HEAVY_SUV) baseFiringHz * 0.82 else baseFiringHz
                        val gain = 0.065 + throttleNorm * 0.11 + (rpmDouble / veh.redlineRpm) * 0.045

                        for (i in 0 until chunkSamples) {
                            smoothedFreq += (targetFreq - smoothedFreq) * 0.01
                            phase1 += 2.0 * PI * smoothedFreq / sampleRate
                            phase2 += 2.0 * PI * (smoothedFreq * 2.0) / sampleRate
                            phase3 += 2.0 * PI * (smoothedFreq * 3.0) / sampleRate

                            if (phase1 > 2.0 * PI) phase1 -= 2.0 * PI
                            if (phase2 > 2.0 * PI) phase2 -= 2.0 * PI
                            if (phase3 > 2.0 * PI) phase3 -= 2.0 * PI

                            val rawCombustion = 0.55 * sin(phase1) +
                                0.35 * sin(phase2) +
                                0.22 * sin(phase3) +
                                0.12 * sin(phase1 * 0.5)
                            // Soft waveshaping gives authentic petrol exhaust growl
                            val shaped = tanh(rawCombustion * (1.2 + throttleNorm * 1.1)) * gain
                            buffer[i] = (shaped * Short.MAX_VALUE).toInt()
                                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                        }
                    }

                    track.write(buffer, 0, chunkSamples)
                }
            } catch (_: Throwable) {
                // Ignore audio errors cleanly in headless/test environments
            } finally {
                try {
                    track?.stop()
                    track?.release()
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun stopContinuousEngineAudio() {
        streamJob?.cancel()
        streamJob = null
    }

    private fun showCueLabel(label: String) {
        _activeCueLabel.value = label
        cueClearJob?.cancel()
        cueClearJob = scope.launch {
            delay(2000L)
            if (_activeCueLabel.value == label) {
                _activeCueLabel.value = null
            }
        }
    }

    private fun playPetrolCrankStartup(isHeavy: Boolean) {
        val sampleRate = 22050
        val count = (sampleRate * 0.42).toInt()
        val buffer = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / count
            val base = if (isHeavy) 48.0 else 64.0
            val freq = if (t < 0.45) {
                base + 18.0 * sin(t * 28.0) // starter crank pulses
            } else {
                base * 2.2 - (t - 0.45) * 45.0 // ignition flare settling to idle
            }
            val env = sin(PI * t)
            phase += 2.0 * PI * freq / sampleRate
            val raw = 0.6 * sin(phase) + 0.3 * sin(phase * 2.0) + 0.2 * sin(phase * 3.0)
            val sample = tanh(raw * 1.6) * env * 0.20
            buffer[i] = (sample * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        writeStaticPcm(sampleRate, buffer)
    }

    private fun playSweepTone(startFreq: Double, endFreq: Double, durationMs: Int, amplitude: Double) {
        val sampleRate = 22050
        val count = (sampleRate * (durationMs / 1000.0)).toInt().coerceAtLeast(1)
        val buffer = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / count
            val freq = startFreq + (endFreq - startFreq) * t
            val envelope = sin(PI * t)
            phase += 2.0 * PI * freq / sampleRate
            val sample = (sin(phase) + 0.35 * sin(phase * 2.0)) * envelope * amplitude
            buffer[i] = (sample * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        writeStaticPcm(sampleRate, buffer)
    }

    private fun playDualTone(freq1: Double, freq2: Double, durationMs: Int, amplitude: Double) {
        val sampleRate = 22050
        val count = (sampleRate * (durationMs / 1000.0)).toInt().coerceAtLeast(1)
        val buffer = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val env = sin(PI * (i.toDouble() / count))
            val sample = (0.5 * sin(2.0 * PI * freq1 * t) + 0.5 * sin(2.0 * PI * freq2 * t)) * env * amplitude
            buffer[i] = (sample * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        writeStaticPcm(sampleRate, buffer)
    }

    private fun writeStaticPcm(sampleRate: Int, samples: ShortArray) {
        var track: AudioTrack? = null
        try {
            val minBytes = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(samples.size * 2)

            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(samples, 0, samples.size)
            track.play()
            Thread.sleep((samples.size * 1000L / sampleRate) + 40L)
        } catch (_: Throwable) {
        } finally {
            try {
                track?.stop()
                track?.release()
            } catch (_: Throwable) {
            }
        }
    }
}
