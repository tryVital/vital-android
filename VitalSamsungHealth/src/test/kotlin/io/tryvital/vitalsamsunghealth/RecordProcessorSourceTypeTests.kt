package io.tryvital.vitalsamsunghealth

import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.data.entries.SleepSession
import com.samsung.android.sdk.health.data.request.DataType
import io.tryvital.client.services.data.SourceType
import io.tryvital.vitalsamsunghealth.model.SHSleepSummary
import io.tryvital.vitalsamsunghealth.records.HealthConnectRecordProcessor
import io.tryvital.vitalsamsunghealth.records.RecordAggregator
import io.tryvital.vitalsamsunghealth.records.RecordReader
import io.tryvital.vitalsamsunghealth.records.SourceTypeResolver
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class RecordProcessorSourceTypeTests {
    private val aggregator = mock<RecordAggregator>()
    private val processor = HealthConnectRecordProcessor(
        mock<RecordReader>(),
        aggregator,
        SourceTypeResolver { SourceType.Phone },
    )

    @Test
    fun `sleep uses the resolved Samsung device type`() = runTest {
        val start = Instant.parse("2026-09-15T22:00:00Z")
        val end = Instant.parse("2026-09-16T06:00:00Z")
        val session = SleepSession.of(start, end, Duration.between(start, end), emptyList())
        val point = HealthDataPoint.builder()
            .setDeviceId("local-phone")
            .setStartTime(start, ZoneOffset.UTC)
            .setEndTime(end, ZoneOffset.UTC)
            .addFieldData(DataType.SleepType.SESSIONS, listOf(session))
            .build()
        whenever(aggregator.aggregateSleepSummary(any(), any())).thenReturn(SHSleepSummary())

        val sleep = processor.processSleepFromRecords(listOf(point), emptyMap()).samples.single()

        assertEquals(SourceType.Phone, sleep.sourceType)
    }

    @Test
    fun `quantity samples use the resolved Samsung device type`() = runTest {
        val time = Instant.parse("2026-09-18T08:00:00Z")
        val point = HealthDataPoint.builder()
            .setDeviceId("local-phone")
            .setStartTime(time, ZoneOffset.UTC)
            .setEndTime(time, ZoneOffset.UTC)
            .addFieldData(DataType.WaterIntakeType.AMOUNT, 250f)
            .build()

        val sample = processor.processWaterFromRecords(listOf(point)).samples.single()

        assertEquals(SourceType.Phone, sample.type)
    }

    @Test
    fun `quantity samples leave source type null when it cannot be resolved`() = runTest {
        val processor = HealthConnectRecordProcessor(
            mock<RecordReader>(),
            aggregator,
            SourceTypeResolver { null },
        )
        val time = Instant.parse("2026-09-18T08:00:00Z")
        val point = HealthDataPoint.builder()
            .setDeviceId("unknown-device")
            .setStartTime(time, ZoneOffset.UTC)
            .setEndTime(time, ZoneOffset.UTC)
            .addFieldData(DataType.WaterIntakeType.AMOUNT, 250f)
            .build()

        val sample = processor.processWaterFromRecords(listOf(point)).samples.single()

        assertEquals(null, sample.type)
    }
}
