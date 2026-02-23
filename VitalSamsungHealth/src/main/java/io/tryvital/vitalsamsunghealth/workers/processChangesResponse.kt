package io.tryvital.vitalsamsunghealth.workers

import com.samsung.android.sdk.health.data.data.Change
import com.samsung.android.sdk.health.data.data.ChangeType
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import io.tryvital.client.services.data.DataStage
import io.tryvital.vitalhealthcore.model.RemappedVitalResource
import io.tryvital.vitalsamsunghealth.model.processedresource.ProcessedResourceData
import io.tryvital.vitalsamsunghealth.records.ProcessorOptions
import io.tryvital.vitalsamsunghealth.records.RecordProcessor
import io.tryvital.vitalsamsunghealth.records.RecordReader
import java.time.Instant
import java.util.TimeZone

internal suspend fun processChangesResponse(
    resource: RemappedVitalResource,
    changes: List<Change<HealthDataPoint>>,
    timeZone: TimeZone,
    reader: RecordReader,
    processor: RecordProcessor,
    processorOptions: ProcessorOptions,
    end: Instant? = null,
): ProcessedResourceData? {
    val endAdjusted = end ?: Instant.MAX

    val upsertedPoints = changes
        .asSequence()
        .filter { it.changeType == ChangeType.UPSERT }
        .mapNotNull { it.upsertDataPoint }
        .filter { (it.endTime ?: it.startTime) <= endAdjusted }
        .toList()

    if (upsertedPoints.isEmpty()) {
        return null
    }

    val startTime = upsertedPoints.minOf { it.startTime }
    val maxEndTime = upsertedPoints.maxOf { it.endTime ?: it.startTime }
    val boundedEnd = minOf(maxEndTime, endAdjusted)
    val endTime = if (boundedEnd.isAfter(startTime)) boundedEnd else startTime.plusMillis(1)

    return readResourceByTimeRange(
        resource = resource,
        startTime = startTime,
        endTime = endTime,
        stage = DataStage.Daily,
        timeZone = timeZone,
        reader = reader,
        processor = processor,
        processorOptions = processorOptions,
    )
}
