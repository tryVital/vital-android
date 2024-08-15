package io.tryvital.vitalhealthconnect.workers

import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.response.ChangesResponse
import io.tryvital.vitalhealthconnect.model.RemappedVitalResource
import io.tryvital.vitalhealthconnect.model.VitalResource
import io.tryvital.vitalhealthconnect.model.processedresource.ProcessedResourceData
import io.tryvital.vitalhealthconnect.model.processedresource.TimeSeriesData
import io.tryvital.vitalhealthconnect.records.ProcessorOptions
import io.tryvital.vitalhealthconnect.records.RecordProcessor
import io.tryvital.vitalhealthconnect.records.TimeRangeOrRecords
import java.time.Instant
import java.time.LocalDate
import java.util.*
import kotlin.reflect.KClass

internal suspend fun processChangesResponse(
    resource: RemappedVitalResource,
    responses: ChangesResponse,
    timeZone: TimeZone,
    processor: RecordProcessor,
    processorOptions: ProcessorOptions,
    end: Instant? = null,
): ProcessedResourceData {
    val records = responses.changes
        .filterIsInstance<UpsertionChange>()
        .groupBy(keySelector = { it.record::class }, valueTransform = { it.record })

    val endAdjusted = end ?: Instant.MAX;


    suspend fun <Record, T : TimeSeriesData> readTimeseries(
        records: List<Record>,
        process: suspend (List<Record>) -> T
    ): ProcessedResourceData = process(records)
        .let(ProcessedResourceData::TimeSeries)

    return when (resource.wrapped) {
        VitalResource.Activity ->
            throw IllegalArgumentException("Activity does not work with processChangesResponse")

        VitalResource.ActiveEnergyBurned -> processor.processActiveCaloriesBurnedRecords(
            TimeRangeOrRecords.Records(
                records.get<ActiveCaloriesBurnedRecord>()
                    .filter { it.endTime <= endAdjusted }
            ),
            processorOptions
        ).let(ProcessedResourceData::TimeSeries)

        VitalResource.BasalEnergyBurned -> processor.processBasalMetabolicRateRecords(
            records.get<BasalMetabolicRateRecord>()
                .filter { it.time <= endAdjusted },
            processorOptions
        ).let(ProcessedResourceData::TimeSeries)

        VitalResource.DistanceWalkingRunning -> processor.processDistanceRecords(
            TimeRangeOrRecords.Records(
                records.get<DistanceRecord>()
                .filter { it.endTime <= endAdjusted }
            ),
            processorOptions
        ).let(ProcessedResourceData::TimeSeries)

        VitalResource.FloorsClimbed -> processor.processFloorsClimbedRecords(
            TimeRangeOrRecords.Records(
                records.get<FloorsClimbedRecord>()
                .filter { it.endTime <= endAdjusted }
            ),
            processorOptions
        ).let(ProcessedResourceData::TimeSeries)

        VitalResource.Steps -> processor.processStepsRecords(
            TimeRangeOrRecords.Records(
                records.get<StepsRecord>()
                .filter { it.endTime <= endAdjusted }
            ),
            processorOptions
        ).let(ProcessedResourceData::TimeSeries)

        VitalResource.Vo2Max -> processor.processVo2MaxRecords(
            records.get<Vo2MaxRecord>()
                .filter { it.time <= endAdjusted },
            processorOptions
        ).let(ProcessedResourceData::TimeSeries)

        VitalResource.Workout -> processor.processWorkoutsFromRecords(
            exerciseRecords = records.get<ExerciseSessionRecord>()
                .filter { it.endTime <= endAdjusted }
        ).let(ProcessedResourceData::Summary)

        VitalResource.Sleep -> records.get<SleepSessionRecord>()
            .filter { it.endTime <= endAdjusted }.let { sessions ->
            processor.processSleepFromRecords(
                sleepSessionRecords = sessions,
            ).let(ProcessedResourceData::Summary)
        }

        VitalResource.Body -> processor.processBodyFromRecords(
            weightRecords = records.get<WeightRecord>().filter { it.time <= endAdjusted },
            bodyFatRecords = records.get<BodyFatRecord>().filter { it.time <= endAdjusted },
        ).let(ProcessedResourceData::Summary)

        VitalResource.Profile -> processor.processProfileFromRecords(
            heightRecords = records.get<HeightRecord>().filter { it.time <= endAdjusted }
        ).let(ProcessedResourceData::Summary)

        VitalResource.HeartRate ->
            readTimeseries(
                records.get<HeartRateRecord>().filter { it.endTime <= endAdjusted },
                processor::processHeartRateFromRecords
            )

        VitalResource.HeartRateVariability ->
            readTimeseries(
                records.get<HeartRateVariabilityRmssdRecord>().filter { it.time <= endAdjusted },
                processor::processHeartRateVariabilityRmssFromRecords
            )

        VitalResource.Glucose ->
            readTimeseries(
                records.get<BloodGlucoseRecord>().filter { it.time <= endAdjusted },
                processor::processGlucoseFromRecords
            )

        VitalResource.BloodPressure ->
            readTimeseries(
                records.get<BloodPressureRecord>().filter { it.time <= endAdjusted },
                processor::processBloodPressureFromRecords
            )

        VitalResource.Water ->
            readTimeseries(
                records.get<HydrationRecord>().filter { it.endTime <= endAdjusted },
                processor::processWaterFromRecords
            )

        VitalResource.MenstrualCycle -> processor.processMenstrualCyclesFromRecords(
            LocalDate.now(),
            null,
            timeZone,
        ).let(ProcessedResourceData::Summary)
    }
}

inline fun <reified T : Record> Map<KClass<out Record>, List<Record>>.get(): List<T> {
    @Suppress("UNCHECKED_CAST")
    return (this[T::class] ?: emptyList()) as List<T>
}
