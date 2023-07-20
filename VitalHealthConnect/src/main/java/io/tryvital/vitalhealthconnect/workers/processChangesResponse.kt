package io.tryvital.vitalhealthconnect.workers

import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.response.ChangesResponse
import io.tryvital.vitalhealthconnect.model.VitalResource
import io.tryvital.vitalhealthconnect.model.processedresource.ProcessedResourceData
import io.tryvital.vitalhealthconnect.model.processedresource.TimeSeriesData
import io.tryvital.vitalhealthconnect.model.remapped
import io.tryvital.vitalhealthconnect.records.RecordProcessor
import io.tryvital.vitalhealthconnect.records.RecordReader
import java.util.*
import kotlin.reflect.KClass

internal suspend fun processChangesResponse(
    resource: VitalResource,
    responses: ChangesResponse,
    timeZone: TimeZone,
    currentDevice: String,
    reader: RecordReader,
    processor: RecordProcessor,
): ProcessedResourceData {
    val records = responses.changes
        .filterIsInstance<UpsertionChange>()
        .groupBy(keySelector = { it.record::class }, valueTransform = { it.record })


    suspend fun <Record, T: TimeSeriesData> readTimeseries(
        records: List<Record>,
        process: suspend (String, List<Record>) -> T
    ): ProcessedResourceData = process(currentDevice, records)
        .let(ProcessedResourceData::TimeSeries)

    return when (resource.remapped()) {
        VitalResource.ActiveEnergyBurned, VitalResource.BasalEnergyBurned, VitalResource.Steps ->
            throw IllegalArgumentException("Unexpected resource post remapped(): $resource")

        VitalResource.Activity -> processor.processActivitiesFromRecords(
            timeZone = timeZone,
            currentDevice = currentDevice,
            activeEnergyBurned = records.get(),
            basalMetabolicRate = records.get(),
            floorsClimbed = records.get(),
            distance = records.get(),
            steps = records.get(),
            vo2Max = records.get(),
        ).let(ProcessedResourceData::Summary)

        VitalResource.Workout -> processor.processWorkoutsFromRecords(
            fallbackDeviceModel = currentDevice,
            exerciseRecords = records.get()
        ).let(ProcessedResourceData::Summary)

        VitalResource.Sleep -> records.get<SleepSessionRecord>().let { sessions ->
            processor.processSleepFromRecords(
                fallbackDeviceModel = currentDevice,
                sleepSessionRecords = sessions,
                readSleepStages = sessions.associateWith { session ->
                    reader.readSleepStages(
                        startTime = session.startTime,
                        endTime = session.endTime
                    )
                }
            ).let(ProcessedResourceData::Summary)
        }

        VitalResource.Body -> processor.processBodyFromRecords(
            fallbackDeviceModel = currentDevice,
            weightRecords = records.get(),
            bodyFatRecords = records.get(),
        ).let(ProcessedResourceData::Summary)

        VitalResource.Profile -> processor.processProfileFromRecords(
            heightRecords = records.get()
        ).let(ProcessedResourceData::Summary)

        VitalResource.HeartRate ->
            readTimeseries(records.get(), processor::processHeartRateFromRecords)
        VitalResource.HeartRateVariability ->
            readTimeseries(records.get(), processor::processHeartRateVariabilityRmssFromRecords)
        VitalResource.Glucose ->
            readTimeseries(records.get(), processor::processGlucoseFromRecords)
        VitalResource.BloodPressure ->
            readTimeseries(records.get(), processor::processBloodPressureFromRecords)
        VitalResource.Water ->
            readTimeseries(records.get(), processor::processWaterFromRecords)
    }
}

inline fun <reified T: Record> Map<KClass<out Record>, List<Record>>.get(): List<T> {
    @Suppress("UNCHECKED_CAST")
    return (this[T::class] ?: emptyList()) as List<T>
}
