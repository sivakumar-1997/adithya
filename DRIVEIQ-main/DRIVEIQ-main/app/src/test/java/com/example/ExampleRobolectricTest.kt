package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DriveIqDatabase
import com.example.data.local.DrivingEventEntity
import com.example.data.local.DrivingSessionEntity
import com.example.data.local.TelemetryRepository
import com.example.data.local.TelemetrySampleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: DriveIqDatabase
    private lateinit var repository: TelemetryRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DriveIqDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TelemetryRepository(db.driveIqDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `app name matches DriveIQ`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("DriveIQ", context.getString(R.string.app_name))
    }

    @Test
    fun `room repository persists sessions events and telemetry samples`() = runTest {
        val now = 1_700_000_000_000L
        val sessionId = repository.startSession(
            DrivingSessionEntity(
                vehicleId = "sport_sedan",
                vehicleName = "Sport Sedan",
                startedAtMs = now,
                speedLimitKmh = 80
            )
        )
        assertTrue(sessionId > 0L)

        repository.logEvent(
            DrivingEventEntity(
                sessionId = sessionId,
                timestampMs = now + 2000L,
                eventType = "Hard Braking",
                severityScore = 84,
                speedKmh = 92f,
                gForce = 0.91f,
                title = "Hard Braking Detected",
                whatHappened = "Sudden 95% brake input at 92 km/h",
                mechanicalImpact = "High rotor thermal spike",
                correctiveAction = "Brake progressively earlier"
            )
        )

        repository.logTelemetrySample(
            TelemetrySampleEntity(
                sessionId = sessionId,
                timestampMs = now + 1000L,
                speedKmh = 75f,
                rpm = 4200,
                throttlePercent = 60f,
                brakePercent = 0f,
                steeringDeg = 5f,
                longitudinalG = 0.4f,
                lateralG = 0.1f,
                vehicleStress = 32f,
                safetyScore = 95f,
                ruleRisk = 25f,
                trendRisk = 20f
            )
        )

        val sessions = repository.allSessions.first()
        val events = repository.observeEventsForSession(sessionId).first()
        val samples = repository.observeTelemetryForSession(sessionId).first()

        assertEquals(1, sessions.size)
        assertEquals("Sport Sedan", sessions.first().vehicleName)
        assertEquals(1, events.size)
        assertEquals("Hard Braking", events.first().eventType)
        assertEquals(1, samples.size)
        assertEquals(75f, samples.first().speedKmh, 0.01f)
    }

    @Test
    fun `carla bridge parses external json frame and room persists spare part wear`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bridge = com.example.bridge.CarlaAndWebBridge(context)
        val ok = bridge.parseAndEmitFrame(
            """{"speed_kmh":104.5,"rpm":4800,"throttle":0.9,"brake":0.0,"steer_angle":-15.0,"accel_x":4.2,"accel_y":-1.8,"gear":"D4","source":"CARLA 0.9.15"}""",
            defaultSource = "CARLA"
        )
        assertTrue(ok)
        val frame = bridge.latestFrame.value
        assertEquals(104.5f, frame?.speedKmh ?: 0f, 0.01f)
        assertEquals(90f, frame?.throttlePercent ?: 0f, 0.01f)

        repository.saveSparePartWear(
            listOf(
                com.example.data.local.SparePartWearEntity(
                    vehicleId = "sport_sedan",
                    partId = "brakes",
                    cumulativeLostMonths = 14f
                )
            )
        )
        val loaded = repository.getSparePartWear("sport_sedan")
        assertEquals(1, loaded.size)
        assertEquals(14f, loaded.first().cumulativeLostMonths, 0.01f)
    }
}
