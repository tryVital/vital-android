package io.tryvital.vitalhealthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import io.tryvital.client.VitalClient
import io.tryvital.client.services.data.QuantitySample
import io.tryvital.client.services.data.WorkoutPayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class HealthConnectRecordProcessorTest {

    @Test
    fun `process workouts heart rate`() = runTest {
        val healthConnectRecordProcessor = setupProcessor()
        val response =
            healthConnectRecordProcessor.processWorkouts(startTime, endTime, "not Iphone")

        assertEquals(2, response.size)
        assertEquals(
            expectedData.heartRate[0],
            response[0].heartRate[0]
        )
    }

    @Test
    fun `process workouts respiratory rate`() = runTest {
        val healthConnectRecordProcessor = setupProcessor()
        val response =
            healthConnectRecordProcessor.processWorkouts(startTime, endTime, "not Iphone")

        assertEquals(2, response.size)
        assertEquals(
            expectedData.respiratoryRate,
            response[0].respiratoryRate
        )

    }

    @Test
    fun `process workouts with samples`() = runTest {
        val healthConnectRecordProcessor = setupProcessor()
        val response =
            healthConnectRecordProcessor.processWorkouts(startTime, endTime, "not Iphone")

        assertEquals(2, response.size)
        assertEquals(expectedData, response[0])
    }

    @Test
    fun `process workouts without samples`() = runTest {
        val healthConnectRecordProcessor = setupProcessor()
        val response =
            healthConnectRecordProcessor.processWorkouts(startTime, endTime, "not Iphone")

        assertEquals(2, response.size)
        assertEquals(expectedData2, response[1])
    }

    private suspend fun setupProcessor(): HealthConnectRecordProcessor {
        val recordAggregator = mock<RecordAggregator>()
        whenever(recordAggregator.aggregateActiveEnergyBurned(startTime, endTime)).thenReturn(
            101,
            102
        )
        whenever(recordAggregator.aggregateDistance(startTime, endTime)).thenReturn(301, 302)

        val recordReader = mock<RecordReader>()
        whenever(recordReader.readExerciseSessions(startTime, endTime)).thenReturn(
            testRawExercise
        )
        whenever(recordReader.readHeartRate(startTime, endTime)).thenReturn(
            testRawHealthRate, emptyList()
        )
        whenever(recordReader.readRespiratoryRate(startTime, endTime)).thenReturn(
            testRawRespiratoryRate, emptyList()
        )

        val vitalClient = mock<VitalClient>()
        whenever(vitalClient.vitalLogger).thenReturn(mock())

        return HealthConnectRecordProcessor(
            recordReader,
            recordAggregator,
            vitalClient
        )
    }
}

val startTime: Instant = Instant.parse("2007-01-01T00:00:00.00Z")
val endTime: Instant = Instant.parse("2007-01-10T00:00:00.00Z")

val expectedData = WorkoutPayload(
    id = "test raw supersize",
    startDate = Date.from(Instant.parse("2007-01-01T00:00:00.00Z")),
    endDate = Date.from(Instant.parse("2007-01-05T00:00:00.00Z")),
    sourceBundle = "fit",
    deviceModel = "not Iphone",
    sport = "walking",
    caloriesInKiloJules = 101,
    distanceInMeter = 301,
    heartRate = listOf(
        QuantitySample(
            id = "heartRate-2007-01-01T00:00:00Z",
            value = "1",
            unit = "bpm",
            startDate = Date.from(Instant.parse("2007-01-01T00:00:00Z")),
            endDate = Date.from(Instant.parse("2007-01-01T00:00:00Z")),
            deviceModel = "not Iphone",
            sourceBundle = "fit"
        ),
        QuantitySample(
            id = "heartRate-2007-01-01T00:01:00Z",
            value = "2",
            unit = "bpm",
            startDate = Date.from(Instant.parse("2007-01-01T00:01:00Z")),
            endDate = Date.from(Instant.parse("2007-01-01T00:01:00Z")),
            deviceModel = "not Iphone",
            sourceBundle = "fit"
        )

    ),
    respiratoryRate = listOf(
        QuantitySample(
            id = "respiratoryRate-2007-01-01T00:00:00Z",
            value = "21.0",
            unit = "bpm",
            startDate = Date.from(Instant.parse("2007-01-01T00:00:00Z")),
            endDate = Date.from(Instant.parse("2007-01-01T00:00:00Z")),
            deviceModel = "not Iphone",
            sourceBundle = "fit"
        ),
        QuantitySample(
            id = "respiratoryRate-2007-01-01T00:01:00Z",
            value = "22.0",
            unit = "bpm",
            startDate = Date.from(Instant.parse("2007-01-01T00:01:00Z")),
            endDate = Date.from(Instant.parse("2007-01-01T00:01:00Z")),
            deviceModel = "not Iphone",
            sourceBundle = "fit"
        )

    )
)

val expectedData2 = WorkoutPayload(
    id = "test raw supersize2",
    startDate = Date.from(Instant.parse("2007-01-06T00:00:00.00Z")),
    endDate = Date.from(Instant.parse("2007-01-10T00:00:00.00Z")),
    sourceBundle = "shealth",
    deviceModel = "not Iphone",
    sport = "running",
    caloriesInKiloJules = 102,
    distanceInMeter = 302,
    heartRate = emptyList(),
    respiratoryRate = emptyList(),
)


val testRawExercise = listOf(
    ExerciseSessionRecord(
        startTime,
        null,
        endTime.minus(5, ChronoUnit.DAYS), null,
        "walking",
        "exercise 1",
        null,
        metadata = Metadata(
            "test raw supersize",
            dataOrigin = DataOrigin("fit")
        )
    ),
    ExerciseSessionRecord(
        startTime.plus(5, ChronoUnit.DAYS),
        null,
        endTime, null,
        "running",
        "exercise 2",
        metadata = Metadata(
            "test raw supersize2",
            dataOrigin = DataOrigin("shealth")
        ),
    )
)

val testRawHealthRate = listOf(
    HeartRateRecord(
        startTime,
        null,
        startTime.plus(2, ChronoUnit.DAYS),
        null,
        listOf(
            HeartRateRecord.Sample(startTime, 1),
            HeartRateRecord.Sample(startTime.plus(1, ChronoUnit.MINUTES), 2),
        ),
        metadata = Metadata(
            device = Device(model = "iphone"),
            dataOrigin = DataOrigin("fit")
        )
    )
)

val testRawRespiratoryRate = listOf(
    RespiratoryRateRecord(
        startTime,
        null,
        21.0,
        Metadata(dataOrigin = DataOrigin("fit"))
    ),
    RespiratoryRateRecord(
        startTime.plus(1, ChronoUnit.MINUTES),
        null,
        22.0,
        Metadata(dataOrigin = DataOrigin("fit"))
    ),
)

