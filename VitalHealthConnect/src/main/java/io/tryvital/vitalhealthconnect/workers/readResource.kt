package io.tryvital.vitalhealthconnect.workers

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import io.tryvital.client.services.data.DataStage
import io.tryvital.vitalhealthconnect.model.RemappedVitalResource
import io.tryvital.vitalhealthconnect.model.VitalResource
import io.tryvital.vitalhealthconnect.model.processedresource.ProcessedResourceData
import io.tryvital.vitalhealthconnect.model.processedresource.TimeSeriesData
import io.tryvital.vitalhealthconnect.records.ProcessorOptions
import io.tryvital.vitalhealthconnect.records.RecordProcessor
import io.tryvital.vitalhealthconnect.records.RecordReader
import io.tryvital.vitalhealthconnect.records.TimeRangeOrRecords
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
    suspend fun <Record, T: TimeSeriesData> readTimeseries(
        read: suspend (Instant, Instant) -> List<Record>,
        process: suspend (List<Record>) -> T
    ): ProcessedResourceData = process(
        read(startTime, endTime)
    ).let(ProcessedResourceData::TimeSeries)

    return when (resource.wrapped) {
        VitalResource.Activity -> processor.processActivities(
            lastSynced = startTime,
            timeZone = timeZone,
        ).let(ProcessedResourceData::Summary)

        VitalResource.ActiveEnergyBurned -> processor.processActiveCaloriesBurnedRecords(
            TimeRangeOrRecords.TimeRange(start = startTime, end = endTime),
            processorOptions
        ).let(ProcessedResourceData::TimeSeries)

        VitalResource.BasalEnergyBurned -> processor.processBasalMetabolicRateRecords(
            reader.readBasalMetabolicRate(startTime, endTime),
            processorOptions
        ).let(ProcessedResourceData::TimeSeries)

        VitalResource.DistanceWalkingRunning -> processor.processDistanceRecords(
            TimeRangeOrRecords.TimeRange(start = startTime, end = endTime),
            processorOptions
        ).let(ProcessedResourceData::TimeSeries)

        VitalResource.FloorsClimbed -> processor.processFloorsClimbedRecords(
            TimeRangeOrRecords.TimeRange(start = startTime, end = endTime),
            processorOptions
        ).let(ProcessedResourceData::TimeSeries)

        VitalResource.Steps -> processor.processStepsRecords(
            TimeRangeOrRecords.TimeRange(start = startTime, end = endTime),
            processorOptions
        ).let(ProcessedResourceData::TimeSeries)

        VitalResource.Vo2Max -> processor.processVo2MaxRecords(
            reader.readVo2Max(startTime, endTime),
            processorOptions
        ).let(ProcessedResourceData::TimeSeries)

        VitalResource.Workout -> processor.processWorkoutsFromRecords(
            exerciseRecords = reader.readExerciseSessions(startTime, endTime)
        ).let(ProcessedResourceData::Summary)

        VitalResource.Sleep -> reader.readSleepSession(startTime, endTime).let { sessions ->
            processor.processSleepFromRecords(
                sleepSessionRecords = sessions,
            ).let(ProcessedResourceData::Summary)
        }

        VitalResource.Body -> processor.processBodyFromRecords(
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

        VitalResource.MenstrualCycle -> processor.processMenstrualCyclesFromRecords(
            endTime.atZone(timeZone.toZoneId()).toLocalDate(),
            if (stage == DataStage.Historical)
                endTime.atZone(timeZone.toZoneId()).toLocalDate()
            else null,
            timeZone,
        ).let(ProcessedResourceData::Summary)
    }
}
