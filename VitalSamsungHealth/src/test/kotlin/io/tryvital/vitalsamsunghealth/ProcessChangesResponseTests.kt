package io.tryvital.vitalsamsunghealth

import com.samsung.android.sdk.health.data.data.Change
import com.samsung.android.sdk.health.data.data.ChangeType
import com.samsung.android.sdk.health.data.data.AggregatedData
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.data.entries.SleepSession
import com.samsung.android.sdk.health.data.request.DataType
import io.tryvital.client.services.data.IngestibleTimeseriesResource
import io.tryvital.vitalhealthcore.model.RemappedVitalResource
import io.tryvital.vitalhealthcore.model.VitalResource
import io.tryvital.vitalsamsunghealth.model.processedresource.SummaryData
import io.tryvital.vitalsamsunghealth.model.processedresource.TimeSeriesData
import io.tryvital.vitalsamsunghealth.records.ProcessorOptions
import io.tryvital.vitalsamsunghealth.records.RecordProcessor
import io.tryvital.vitalsamsunghealth.records.RecordReader
import io.tryvital.vitalsamsunghealth.records.TimeRangeOrRecords
import io.tryvital.vitalsamsunghealth.workers.processChangesResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.Duration
import java.time.ZoneOffset
import java.util.TimeZone

class ProcessChangesResponseTests {
    @Test
    fun `changed sleeps are processed directly without a time range read`() = runTest {
        val start = Instant.parse("2026-09-15T22:00:00Z")
        val end = Instant.parse("2026-09-16T06:00:00Z")
        val point = dataPoint(start, end).withUid("sleep-1")
        val reader = mock<RecordReader>()
        val processor = mock<RecordProcessor>()
        whenever(reader.readSleepSkinTemperature(listOf("sleep-1"))).thenReturn(emptyMap())
        whenever(processor.processSleepFromRecords(listOf(point), emptyMap()))
            .thenReturn(SummaryData.Sleeps(emptyList()))

        processChangesResponse(
            resource = RemappedVitalResource(VitalResource.Sleep),
            changes = listOf(upsert(point)),
            timeZone = TimeZone.getTimeZone("UTC"),
            reader = reader,
            processor = processor,
            processorOptions = ProcessorOptions(),
        )

        verify(reader, never()).readSleepSession(any(), any())
        verify(reader).readSleepSkinTemperature(listOf("sleep-1"))
        verify(processor).processSleepFromRecords(listOf(point), emptyMap())
    }

    @Test
    fun `changed workouts are processed directly without a time range read`() = runTest {
        val point = dataPoint(
            start = Instant.parse("2026-09-15T22:00:00Z"),
            end = Instant.parse("2026-09-16T06:00:00Z"),
        )
        val reader = mock<RecordReader>()
        val processor = mock<RecordProcessor>()
        whenever(processor.processWorkoutsFromRecords(listOf(point)))
            .thenReturn(SummaryData.Workouts(emptyList()))

        processChangesResponse(
            resource = RemappedVitalResource(VitalResource.Workout),
            changes = listOf(upsert(point)),
            timeZone = TimeZone.getTimeZone("UTC"),
            reader = reader,
            processor = processor,
            processorOptions = ProcessorOptions(),
        )

        verify(reader, never()).readExerciseSessions(any(), any())
        verify(processor).processWorkoutsFromRecords(listOf(point))
    }

    @Test
    fun `aggregated resources include the changed record end in their range read`() = runTest {
        val start = Instant.parse("2026-09-15T22:00:00Z")
        val end = Instant.parse("2026-09-16T06:00:00Z")
        val point = dataPoint(start, end)
        val reader = mock<RecordReader>()
        val processor = mock<RecordProcessor>()
        whenever(processor.processFloorsClimbedRecords(any(), any())).thenReturn(
            TimeSeriesData.QuantitySamples(IngestibleTimeseriesResource.FloorsClimbed, emptyList())
        )

        processChangesResponse(
            resource = RemappedVitalResource(VitalResource.FloorsClimbed),
            changes = listOf(upsert(point)),
            timeZone = TimeZone.getTimeZone("UTC"),
            reader = reader,
            processor = processor,
            processorOptions = ProcessorOptions(),
        )

        val range = argumentCaptor<TimeRangeOrRecords<AggregatedData<Float>>>()
        verify(processor).processFloorsClimbedRecords(range.capture(), any())
        assertEquals(
            TimeRangeOrRecords.TimeRange<AggregatedData<Float>>(start, end.plusMillis(1)),
            range.firstValue,
        )
    }

    private fun dataPoint(start: Instant, end: Instant): HealthDataPoint =
        HealthDataPoint.builder()
            .setDeviceId("test-device")
            .setStartTime(start, ZoneOffset.UTC)
            .setEndTime(end, ZoneOffset.UTC)
            .addFieldData(
                DataType.SleepType.SESSIONS,
                listOf(SleepSession.of(start, end, Duration.between(start, end), emptyList())),
            )
            .build()

    @Suppress("UNCHECKED_CAST")
    private fun upsert(point: HealthDataPoint): Change<HealthDataPoint> {
        val constructor = Change::class.java.declaredConstructors.single { it.parameterCount == 5 }
        return constructor.newInstance(
            ChangeType.UPSERT,
            Instant.parse("2026-09-18T10:00:00Z"),
            point,
            null,
            null,
        ) as Change<HealthDataPoint>
    }

    private fun HealthDataPoint.withUid(uid: String): HealthDataPoint = apply {
        // The public builder represents data before insertion and cannot assign Samsung's UID.
        HealthDataPoint::class.java.getDeclaredField("b").apply {
            isAccessible = true
            set(this@withUid, uid)
        }
    }
}
