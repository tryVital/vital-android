package io.tryvital.vitalhealthconnect.workers

import io.tryvital.vitalhealthconnect.model.VitalResource
import io.tryvital.vitalhealthconnect.model.processedresource.ProcessedResourceData
import io.tryvital.vitalhealthconnect.model.processedresource.TimeSeriesData
import io.tryvital.vitalhealthconnect.model.remapped
import io.tryvital.vitalhealthconnect.records.ProcessorOptions
import io.tryvital.vitalhealthconnect.records.RecordProcessor
import io.tryvital.vitalhealthconnect.records.RecordReader
import java.time.Instant
import java.util.TimeZone

internal suspend fun readResourceByTimeRange(
    resource: VitalResource,
    startTime: Instant,
    endTime: Instant,
    timeZone: TimeZone,
    currentDevice: String,
    reader: RecordReader,
    processor: RecordProcessor,
    processorOptions: ProcessorOptions,
): ProcessedResourceData {
    suspend fun <Record, T: TimeSeriesData> readTimeseries(
        read: suspend (Instant, Instant) -> List<Record>,
        process: suspend (String, List<Record>) -> T
    ): ProcessedResourceData = process(
        currentDevice,
        read(startTime, endTime)
    ).let(ProcessedResourceData::TimeSeries)

    return when (resource.remapped()) {
        VitalResource.ActiveEnergyBurned, VitalResource.BasalEnergyBurned, VitalResource.Steps ->
            throw IllegalArgumentException("Unexpected resource post remapped(): $resource")

        VitalResource.Activity -> processor.processActivitiesFromRecords(
            timeZone = timeZone,
            currentDevice = currentDevice,
            activeEnergyBurned = reader.readActiveEnergyBurned(startTime, endTime),
            basalMetabolicRate = reader.readBasalMetabolicRate(startTime, endTime),
            floorsClimbed = reader.readFloorsClimbed(startTime, endTime),
            distance = reader.readDistance(startTime, endTime),
            steps = reader.readSteps(startTime, endTime),
            vo2Max = reader.readVo2Max(startTime, endTime),
            options = processorOptions,
        ).let(ProcessedResourceData::Summary)

        VitalResource.Workout -> processor.processWorkoutsFromRecords(
            fallbackDeviceModel = currentDevice,
            exerciseRecords = reader.readExerciseSessions(startTime, endTime)
        ).let(ProcessedResourceData::Summary)

        VitalResource.Sleep -> reader.readSleepSession(startTime, endTime).let { sessions ->
            processor.processSleepFromRecords(
                fallbackDeviceModel = currentDevice,
                sleepSessionRecords = sessions,
            ).let(ProcessedResourceData::Summary)
        }

        VitalResource.Body -> processor.processBodyFromRecords(
            fallbackDeviceModel = currentDevice,
            weightRecords = reader.readWeights(startTime, endTime),
            bodyFatRecords = reader.readBodyFat(startTime, endTime),
        ).let(ProcessedResourceData::Summary)

        VitalResource.Profile -> processor.processProfileFromRecords(
            heightRecords = reader.readHeights(startTime, endTime)
        ).let(ProcessedResourceData::Summary)

        VitalResource.HeartRate ->
            readTimeseries(reader::readHeartRate, processor::processHeartRateFromRecords)
        VitalResource.HeartRateVariability ->
            readTimeseries(reader::readHeartRateVariabilityRmssd, processor::processHeartRateVariabilityRmssFromRecords)
        VitalResource.Glucose ->
            readTimeseries(reader::readBloodGlucose, processor::processGlucoseFromRecords)
        VitalResource.BloodPressure ->
            readTimeseries(reader::readBloodPressure, processor::processBloodPressureFromRecords)
        VitalResource.Water ->
            readTimeseries(reader::readHydration, processor::processWaterFromRecords)
    }
}
