package io.tryvital.vitalsamsunghealth.workers

import io.tryvital.client.services.data.DataStage
import io.tryvital.vitalhealthcore.model.RemappedVitalResource
import io.tryvital.vitalhealthcore.model.VitalResource
import io.tryvital.vitalsamsunghealth.model.processedresource.ProcessedResourceData
import io.tryvital.vitalsamsunghealth.model.processedresource.TimeSeriesData
import io.tryvital.vitalsamsunghealth.records.ProcessorOptions
import io.tryvital.vitalsamsunghealth.records.RecordProcessor
import io.tryvital.vitalsamsunghealth.records.RecordReader
import io.tryvital.vitalsamsunghealth.records.TimeRangeOrRecords
import java.time.Instant
import java.util.TimeZone

internal suspend fun readResourceByTimeRange(
    resource: RemappedVitalResource,
    startTime: Instant,
    endTime: Instant,
    stage: DataStage,
    timeZone: TimeZone,
    reader: RecordReader,
    processor: RecordProcessor,
    processorOptions: ProcessorOptions,
): ProcessedResourceData {
    suspend fun <Record, T : TimeSeriesData> readTimeseries(
        read: suspend (Instant, Instant) -> List<Record>,
        process: suspend (List<Record>) -> T,
    ): ProcessedResourceData = process(read(startTime, endTime)).let(ProcessedResourceData::TimeSeries)

    return when (resource.wrapped) {
        VitalResource.Activity -> processor.processActivities(lastSynced = startTime, timeZone = timeZone).let(ProcessedResourceData::Summary)

        VitalResource.Meal -> processor.processMeals(lastSynced = startTime, timeZone = timeZone).let(ProcessedResourceData::Summary)

        VitalResource.ActiveEnergyBurned -> processor.processActiveCaloriesBurnedRecords(
            TimeRangeOrRecords.TimeRange(start = startTime, end = endTime),
            processorOptions,
        ).let(ProcessedResourceData::TimeSeries)

        VitalResource.BasalEnergyBurned -> processor.processBasalMetabolicRateRecords(
            reader.readBasalMetabolicRate(startTime, endTime),
            processorOptions,
        ).let(ProcessedResourceData::TimeSeries)

        VitalResource.DistanceWalkingRunning -> processor.processDistanceRecords(
            TimeRangeOrRecords.TimeRange(start = startTime, end = endTime),
            processorOptions,
        ).let(ProcessedResourceData::TimeSeries)

        VitalResource.FloorsClimbed -> processor.processFloorsClimbedRecords(
            TimeRangeOrRecords.TimeRange(start = startTime, end = endTime),
            processorOptions,
        ).let(ProcessedResourceData::TimeSeries)

        VitalResource.Steps -> processor.processStepsRecords(
            TimeRangeOrRecords.TimeRange(start = startTime, end = endTime),
            processorOptions,
        ).let(ProcessedResourceData::TimeSeries)

        VitalResource.Vo2Max -> processor.processVo2MaxRecords(
            reader.readVo2Max(startTime, endTime),
            processorOptions,
        ).let(ProcessedResourceData::TimeSeries)

        VitalResource.Temperature -> processor.processBodyTemperatureRecords(
            reader.readBodyTemperatures(startTime, endTime),
        ).let(ProcessedResourceData::TimeSeries)

        VitalResource.BloodOxygen -> processor.processOxygenSaturationRecords(
            reader.readOxygenSaturation(startTime, endTime),
        ).let(ProcessedResourceData::TimeSeries)

        VitalResource.Workout -> processor.processWorkoutsFromRecords(
            exerciseRecords = reader.readExerciseSessions(startTime, endTime),
        ).let(ProcessedResourceData::Summary)

        VitalResource.Sleep -> reader.readSleepSession(startTime, endTime).let { sessions ->
            val skinTemperature = reader.readSleepSkinTemperature(
                sessions.map { it.uid }
            )

            processor.processSleepFromRecords(sleepSessionRecords = sessions, skinTemperature = skinTemperature)
                .let(ProcessedResourceData::Summary)
        }

        VitalResource.Body -> processor.processBodyFromRecords(
            reader.readBodyCompositions(startTime, endTime),
        ).let(ProcessedResourceData::Summary)

        VitalResource.Profile -> processor.processProfileFromRecords(
            heightRecords = reader.readHeights(startTime, endTime),
        ).let(ProcessedResourceData::Summary)

        VitalResource.HeartRate -> readTimeseries(reader::readHeartRate, processor::processHeartRateFromRecords)
        VitalResource.Glucose -> readTimeseries(reader::readBloodGlucose, processor::processGlucoseFromRecords)
        VitalResource.BloodPressure -> readTimeseries(reader::readBloodPressure, processor::processBloodPressureFromRecords)
        VitalResource.Water -> readTimeseries(reader::readHydration, processor::processWaterFromRecords)

        VitalResource.MenstrualCycle, VitalResource.RespiratoryRate, VitalResource.HeartRateVariability -> throw IllegalArgumentException("${resource.wrapped} is unavailable with Samsung Health")
    }
}
