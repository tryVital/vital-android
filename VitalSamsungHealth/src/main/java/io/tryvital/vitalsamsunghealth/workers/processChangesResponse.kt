package io.tryvital.vitalsamsunghealth.workers

import com.samsung.android.sdk.health.data.data.Change
import com.samsung.android.sdk.health.data.data.ChangeType
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.request.DataType
import io.tryvital.client.services.data.DataStage
import io.tryvital.vitalhealthcore.model.RemappedVitalResource
import io.tryvital.vitalhealthcore.model.VitalResource
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
        .sortedBy { it.startTime }
        .toList()

    if (upsertedPoints.isEmpty()) {
        return null
    }

    // Most resources can turn the upserted points returned by readChanges() directly into Vital
    // data. Each matching branch returns from this function, avoiding a second Samsung Health
    // read. Aggregate-backed resources cannot do that: a changed point only identifies the
    // event-time interval whose aggregate may have changed. Those resources fall through and
    // recompute the affected interval with readResourceByTimeRange() below.
    when (resource.wrapped) {
        VitalResource.Water -> return processor.processWaterFromRecords(upsertedPoints)
            .let(ProcessedResourceData::TimeSeries)
        VitalResource.BasalEnergyBurned -> {
            return processor.processBasalMetabolicRateRecords(upsertedPoints, processorOptions)
                .let(ProcessedResourceData::TimeSeries)
        }
        VitalResource.Vo2Max -> return processor.processVo2MaxRecords(upsertedPoints, processorOptions)
            .let(ProcessedResourceData::TimeSeries)
        VitalResource.BloodOxygen -> return processor.processOxygenSaturationRecords(upsertedPoints)
            .let(ProcessedResourceData::TimeSeries)
        VitalResource.BloodPressure -> return processor.processBloodPressureFromRecords(upsertedPoints)
            .let(ProcessedResourceData::TimeSeries)
        VitalResource.Body -> return processor.processBodyFromRecords(upsertedPoints)
            .let(ProcessedResourceData::Summary)
        VitalResource.Glucose -> return processor.processGlucoseFromRecords(upsertedPoints)
            .let(ProcessedResourceData::TimeSeries)
        VitalResource.HeartRate -> return processor.processHeartRateFromRecords(upsertedPoints)
            .let(ProcessedResourceData::TimeSeries)
        VitalResource.Profile -> {
            val heightPoints = upsertedPoints.filter {
                it.getValue(DataType.BodyCompositionType.HEIGHT) != null
            }
            if (heightPoints.isEmpty()) return null
            return processor.processProfileFromRecords(heightPoints).let(ProcessedResourceData::Summary)
        }
        VitalResource.Sleep -> {
            val skinTemperature = reader.readSleepSkinTemperature(upsertedPoints.map { it.uid })
            return processor.processSleepFromRecords(upsertedPoints, skinTemperature)
                .let(ProcessedResourceData::Summary)
        }
        VitalResource.Workout -> return processor.processWorkoutsFromRecords(upsertedPoints)
            .let(ProcessedResourceData::Summary)
        VitalResource.Temperature -> return processor.processBodyTemperatureRecords(upsertedPoints)
            .let(ProcessedResourceData::TimeSeries)
        VitalResource.Meal -> return processor.processMealsFromRecords(upsertedPoints, timeZone)
            .let(ProcessedResourceData::Summary)
        else -> Unit
    }

    val startTime = upsertedPoints.minOf { it.startTime }
    val maxEndTime = upsertedPoints.maxOf { it.endTime ?: it.startTime }
    val boundedEnd = minOf(maxEndTime, endAdjusted)
    // Event-time filters are end-exclusive. Aggregated resources still require a range read, so
    // advance by one millisecond (Samsung's timestamp precision) to include the changed record.
    val endTime = boundedEnd.plusMillis(1)

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
