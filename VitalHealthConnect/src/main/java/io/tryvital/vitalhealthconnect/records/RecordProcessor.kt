package io.tryvital.vitalhealthconnect.records

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_INT_TO_STRING_MAP
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import io.tryvital.client.services.data.HealthConnectRecordCollection
import io.tryvital.client.services.data.IngestibleTimeseriesResource
import io.tryvital.client.services.data.LocalActivity
import io.tryvital.client.services.data.LocalBloodPressureSample
import io.tryvital.client.services.data.LocalQuantitySample
import io.tryvital.client.services.data.LocalSleep
import io.tryvital.client.services.data.LocalWorkout
import io.tryvital.client.services.data.ManualMealCreation
import io.tryvital.client.services.data.NutritionRecord
import io.tryvital.client.services.data.SampleType
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalhealthconnect.SupportedSleepApps
import io.tryvital.vitalhealthconnect.ext.toDate
import io.tryvital.vitalhealthconnect.model.inferredSourceType
import io.tryvital.vitalhealthconnect.model.processedresource.SummaryData
import io.tryvital.vitalhealthconnect.model.processedresource.TimeSeriesData
import io.tryvital.vitalhealthconnect.model.quantitySample
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.TimeZone
import kotlin.math.roundToInt

data class ProcessorOptions(
    val perDeviceActivityTS: Boolean = false
)

const val ACTIVITY_STATS_DAYS_TO_LOOKBACK = 3L
const val NUTRITION_STATS_DAYS_TO_LOOKBACK = 5L

sealed class TimeRangeOrRecords<out R: Record> {
    data class TimeRange<out R: Record>(val start: Instant, val end: Instant): TimeRangeOrRecords<R>()
    data class Records<out R: Record>(val records: List<R>): TimeRangeOrRecords<R>()
}

interface RecordProcessor {

    suspend fun processBloodPressureFromRecords(
        readBloodPressure: List<BloodPressureRecord>
    ): TimeSeriesData.BloodPressure

    suspend fun processGlucoseFromRecords(
        readBloodGlucose: List<BloodGlucoseRecord>
    ): TimeSeriesData.QuantitySamples

    suspend fun processHeartRateFromRecords(
        heartRateRecords: List<HeartRateRecord>
    ): TimeSeriesData.QuantitySamples

    fun processHeartRateVariabilityRmssFromRecords(
        heartRateRecords: List<HeartRateVariabilityRmssdRecord>
    ): TimeSeriesData.QuantitySamples

    fun processWaterFromRecords(
        readHydration: List<HydrationRecord>
    ): TimeSeriesData.QuantitySamples

    suspend fun processBodyFromRecords(
        weightRecords: List<WeightRecord>,
        bodyFatRecords: List<BodyFatRecord>,
    ): SummaryData.Body

    suspend fun processProfileFromRecords(
        heightRecords: List<HeightRecord>,
    ): SummaryData.Profile


    suspend fun processWorkoutsFromRecords(
        exerciseRecords: List<ExerciseSessionRecord>
    ): SummaryData.Workouts

    suspend fun processSleepFromRecords(
        sleepSessionRecords: List<SleepSessionRecord>,
    ): SummaryData.Sleeps

    suspend fun processActivities(
        lastSynced: Instant?,
        timeZone: TimeZone,
    ): SummaryData.Activities

    suspend fun processMeals(
        lastSynced: Instant?,
        timeZone: TimeZone,
    ): SummaryData.Meals

    suspend fun processActiveCaloriesBurnedRecords(
        activeEnergyBurned: TimeRangeOrRecords<ActiveCaloriesBurnedRecord>,
        options: ProcessorOptions,
    ): TimeSeriesData.QuantitySamples

    suspend fun processBasalMetabolicRateRecords(
        basalMetabolicRate: List<BasalMetabolicRateRecord>,
        options: ProcessorOptions,
    ): TimeSeriesData.QuantitySamples

    suspend fun processStepsRecords(
        steps: TimeRangeOrRecords<StepsRecord>,
        options: ProcessorOptions,
    ): TimeSeriesData.QuantitySamples

    suspend fun processDistanceRecords(
        distance: TimeRangeOrRecords<DistanceRecord>,
        options: ProcessorOptions,
    ): TimeSeriesData.QuantitySamples

    suspend fun processFloorsClimbedRecords(
        floorsClimbed: TimeRangeOrRecords<FloorsClimbedRecord>,
        options: ProcessorOptions,
    ): TimeSeriesData.QuantitySamples

    suspend fun processVo2MaxRecords(
        vo2Max: List<Vo2MaxRecord>,
        options: ProcessorOptions,
    ): TimeSeriesData.QuantitySamples

    suspend fun processRespiratoryRateRecords(
        respiratoryRates: List<RespiratoryRateRecord>,
    ): TimeSeriesData.QuantitySamples

    suspend fun processOxygenSaturationRecords(
        oxygenSaturations: List<OxygenSaturationRecord>,
    ): TimeSeriesData.QuantitySamples

    suspend fun processBodyTemperatureRecords(
        temperatures: List<BodyTemperatureRecord>,
    ): TimeSeriesData.QuantitySamples

    suspend fun processMenstrualCyclesFromRecords(
        endDate: LocalDate,
        startDate: LocalDate?,
        timeZone: TimeZone,
    ): SummaryData.MenstrualCycles
}

internal class HealthConnectRecordProcessor(
    private val recordReader: RecordReader,
    private val recordAggregator: RecordAggregator,
) : RecordProcessor {

    override suspend fun processBloodPressureFromRecords(
        readBloodPressure: List<BloodPressureRecord>
    ): TimeSeriesData.BloodPressure {
        return TimeSeriesData.BloodPressure(
            readBloodPressure.map {
                LocalBloodPressureSample(
                    systolic = quantitySample(
                        value = it.systolic.inMillimetersOfMercury,
                        unit = SampleType.BloodPressureSystolic.unit,
                        startDate = it.time,
                        endDate = it.time,
                        metadata = it.metadata,
                    ),
                    diastolic = quantitySample(
                        value = it.diastolic.inMillimetersOfMercury,
                        unit = SampleType.BloodPressureDiastolic.unit,
                        startDate = it.time,
                        endDate = it.time,
                        metadata = it.metadata,
                    ),
                    pulse = null,
                )
            }
        )
    }

    override suspend fun processGlucoseFromRecords(
        readBloodGlucose: List<BloodGlucoseRecord>
    ): TimeSeriesData.QuantitySamples {
        return TimeSeriesData.QuantitySamples(
            IngestibleTimeseriesResource.BloodGlucose,
            readBloodGlucose.map {
                quantitySample(
                    value = it.level.inMilligramsPerDeciliter,
                    unit = SampleType.GlucoseConcentrationMilligramPerDecilitre.unit,
                    startDate = it.time,
                    endDate = it.time,
                    metadata = it.metadata,
                )
            })
    }

    override suspend fun processHeartRateFromRecords(
        heartRateRecords: List<HeartRateRecord>
    ): TimeSeriesData.QuantitySamples {
        return TimeSeriesData.QuantitySamples(
            IngestibleTimeseriesResource.HeartRate,
            mapHearthRate(heartRateRecords)
        )
    }

    override fun processHeartRateVariabilityRmssFromRecords(
        heartRateRecords: List<HeartRateVariabilityRmssdRecord>
    ): TimeSeriesData.QuantitySamples {
        return TimeSeriesData.QuantitySamples(
            IngestibleTimeseriesResource.HeartRateVariability,
            heartRateRecords.map {
                quantitySample(
                    value = it.heartRateVariabilityMillis,
                    unit = SampleType.HeartRateVariabilityRmssd.unit,
                    startDate = it.time,
                    endDate = it.time,
                    metadata = it.metadata,
                )
            }
        )
    }


    override fun processWaterFromRecords(
        readHydration: List<HydrationRecord>
    ): TimeSeriesData.QuantitySamples {
        return TimeSeriesData.QuantitySamples(
            IngestibleTimeseriesResource.Water,
            readHydration.map {
                quantitySample(
                    value = it.volume.inMilliliters,
                    unit = SampleType.Water.unit,
                    startDate = it.startTime,
                    endDate = it.endTime,
                    metadata = it.metadata,
                )
            }
        )
    }

    override suspend fun processWorkoutsFromRecords(
        exerciseRecords: List<ExerciseSessionRecord>
    ): SummaryData.Workouts {
        return SummaryData.Workouts(
            exerciseRecords.map { exercise ->
                val summary =
                    recordAggregator.aggregateWorkoutSummary(exercise.startTime, exercise.endTime, exercise.metadata.dataOrigin)

                LocalWorkout(
                    id = exercise.metadata.id,
                    startDate = exercise.startTime,
                    endDate = exercise.endTime,
                    sport = EXERCISE_TYPE_INT_TO_STRING_MAP[exercise.exerciseType] ?: "workout",
                    calories = summary.caloriesBurned ?: 0.0,
                    distance = summary.distanceMeter ?: 0.0,
                    heartRateMinimum = summary.heartRateMinimum,
                    heartRateMaximum = summary.heartRateMaximum,
                    heartRateMean = summary.heartRateMean,
                    heartRateZone1 = summary.heartRateZone1,
                    heartRateZone2 = summary.heartRateZone2,
                    heartRateZone3 = summary.heartRateZone3,
                    heartRateZone4 = summary.heartRateZone4,
                    heartRateZone5 = summary.heartRateZone5,
                    heartRateZone6 = summary.heartRateZone6,
                    sourceBundle = exercise.metadata.dataOrigin.packageName,
                    deviceModel = exercise.metadata.device?.model,
                    sourceType = exercise.metadata.inferredSourceType,
                )
            }
        )
    }

    override suspend fun processProfileFromRecords(
        heightRecords: List<HeightRecord>,
    ) =
        SummaryData.Profile(
            biologicalSex = "not_set", // this is not available in Health Connect
            dateOfBirth = null, // this is not available in Health Connect
            heightInCm = (heightRecords.lastOrNull()?.height?.inMeters?.times(100))?.roundToInt()
                ?: 0,
        )

    override suspend fun processBodyFromRecords(
        weightRecords: List<WeightRecord>,
        bodyFatRecords: List<BodyFatRecord>
    ) = SummaryData.Body(
        bodyMass = weightRecords.map {
            quantitySample(
                value = it.weight.inKilograms,
                unit = SampleType.Weight.unit,
                startDate = it.time,
                endDate = it.time,
                metadata = it.metadata,
            )
        },
        bodyFatPercentage = bodyFatRecords.map {
            quantitySample(
                value = it.percentage.value,
                unit = SampleType.BodyFat.unit,
                startDate = it.time,
                endDate = it.time,
                metadata = it.metadata,
            )
        }
    )

    override suspend fun processSleepFromRecords(
        sleepSessionRecords: List<SleepSessionRecord>,
    ): SummaryData.Sleeps {
        return SummaryData.Sleeps(
            processSleeps(sleepSessionRecords)
        )
    }

    private suspend fun processSleeps(
        sleeps: List<SleepSessionRecord>,
    ): List<LocalSleep> {
        return sleeps.filterForAcceptedSleepDataSources().map { sleepSession ->
            val statistics = recordAggregator.aggregateSleepSummary(
                sleepSession.startTime,
                sleepSession.endTime,
                sleepSession.metadata.dataOrigin
            )

            LocalSleep(
                id = sleepSession.metadata.id,
                startDate = sleepSession.startTime,
                endDate = sleepSession.endTime,
                sourceBundle = sleepSession.metadata.dataOrigin.packageName,
                deviceModel = sleepSession.metadata.device?.model,
                sourceType = sleepSession.metadata.inferredSourceType,
                heartRateMean = statistics.heartRateMean,
                heartRateMaximum = statistics.heartRateMaximum,
                heartRateMinimum = statistics.heartRateMinimum,
                hrvMeanSdnn = statistics.hrvMeanSdnn,
                respiratoryRateMean = statistics.respiratoryRateMean,
                sleepStages = LocalSleep.Stages(
                    awakeSleepSamples = sleepSession.stages.filter { it.stage == SleepSessionRecord.STAGE_TYPE_AWAKE }
                        .map { sleepStage ->
                            quantitySample(
                                value = LocalSleep.Stage.Awake.id.toDouble(),
                                unit = "stage",
                                startDate = sleepStage.startTime,
                                endDate = sleepStage.endTime,
                                metadata = sleepSession.metadata,
                            )
                        },
                    deepSleepSamples = sleepSession.stages.filter { it.stage == SleepSessionRecord.STAGE_TYPE_DEEP }
                        .map { sleepStage ->
                            quantitySample(
                                value = LocalSleep.Stage.Deep.id.toDouble(),
                                unit = "stage",
                                startDate = sleepStage.startTime,
                                endDate = sleepStage.endTime,
                                metadata = sleepSession.metadata,
                            )
                        },
                    lightSleepSamples = sleepSession.stages.filter { it.stage == SleepSessionRecord.STAGE_TYPE_LIGHT }
                        .map { sleepStage ->
                            quantitySample(
                                value = LocalSleep.Stage.Light.id.toDouble(),
                                unit = "stage",
                                startDate = sleepStage.startTime,
                                endDate = sleepStage.endTime,
                                metadata = sleepSession.metadata,
                            )
                        },
                    remSleepSamples = sleepSession.stages.filter { it.stage == SleepSessionRecord.STAGE_TYPE_REM }
                        .map { sleepStage ->
                            quantitySample(
                                value = LocalSleep.Stage.Rem.id.toDouble(),
                                unit = "stage",
                                startDate = sleepStage.startTime,
                                endDate = sleepStage.endTime,
                                metadata = sleepSession.metadata,
                            )
                        },
                    unknownSleepSamples = sleepSession.stages.filter { it.stage == SleepSessionRecord.STAGE_TYPE_UNKNOWN }
                        .map { sleepStage ->
                            quantitySample(
                                value = LocalSleep.Stage.Unknown.id.toDouble(),
                                unit = "stage",
                                startDate = sleepStage.startTime,
                                endDate = sleepStage.endTime,
                                metadata = sleepSession.metadata,
                            )
                        },
                    outOfBedSleepSamples = sleepSession.stages.filter { it.stage == SleepSessionRecord.STAGE_TYPE_OUT_OF_BED }
                        .map { sleepStage ->
                            quantitySample(
                                value = LocalSleep.Stage.OutOfBed.id.toDouble(),
                                unit = "stage",
                                startDate = sleepStage.startTime,
                                endDate = sleepStage.endTime,
                                metadata = sleepSession.metadata,
                            )
                        },
                )
            )
        }
    }

    override suspend fun processActiveCaloriesBurnedRecords(
        activeEnergyBurned: TimeRangeOrRecords<ActiveCaloriesBurnedRecord>,
        options: ProcessorOptions
    ): TimeSeriesData.QuantitySamples {
        val caloriesActive = if (options.perDeviceActivityTS) {
            val records = when (activeEnergyBurned) {
                is TimeRangeOrRecords.Records ->
                    activeEnergyBurned.records

                is TimeRangeOrRecords.TimeRange ->
                    recordReader.readActiveEnergyBurned(
                        activeEnergyBurned.start,
                        activeEnergyBurned.end
                    )
            }
            records.map {
                quantitySample(
                    value = it.energy.inKilocalories,
                    unit = SampleType.ActiveCaloriesBurned.unit,
                    startDate = it.startTime,
                    endDate = it.endTime,
                    metadata = it.metadata,
                )
            }
        } else emptyList()

        val (startInstant, endInstant) = when (activeEnergyBurned) {
            is TimeRangeOrRecords.TimeRange -> activeEnergyBurned.start to activeEnergyBurned.end
            is TimeRangeOrRecords.Records -> activeEnergyBurned.records.minOfOrNull { it.startTime } to activeEnergyBurned.records.maxOfOrNull { it.endTime }
        }

        if (startInstant == null) {
            return TimeSeriesData.QuantitySamples(
                IngestibleTimeseriesResource.CaloriesActive,
                emptyList()
            )
        }
        checkNotNull(endInstant)

        val hourlyTotals = recordAggregator.aggregateCaloriesActiveHourlyTotals(
            start = startInstant.truncatedTo(ChronoUnit.HOURS),
            end = endInstant.plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS),
        )

        return TimeSeriesData.QuantitySamples(
            IngestibleTimeseriesResource.CaloriesActive,
            merge(caloriesActive, hourlyTotals, options)
        )
    }

    override suspend fun processBasalMetabolicRateRecords(
        basalMetabolicRate: List<BasalMetabolicRateRecord>,
        options: ProcessorOptions
    ): TimeSeriesData.QuantitySamples {
        val caloriesBasal = basalMetabolicRate.map {
            quantitySample(
                value = it.basalMetabolicRate.inKilocaloriesPerDay,
                unit = SampleType.BasalMetabolicRate.unit,
                startDate = it.time,
                endDate = it.time,
                metadata = it.metadata,
            )
        }

        return TimeSeriesData.QuantitySamples(
            IngestibleTimeseriesResource.CaloriesBasal,
            caloriesBasal
        )
    }
    override suspend fun processDistanceRecords(
        distance: TimeRangeOrRecords<DistanceRecord>,
        options: ProcessorOptions
    ): TimeSeriesData.QuantitySamples {
        val distanceSamples = if (options.perDeviceActivityTS) {
            val records = when (distance) {
                is TimeRangeOrRecords.Records ->
                    distance.records

                is TimeRangeOrRecords.TimeRange ->
                    recordReader.readDistance(distance.start, distance.end)
            }
            records.map {
                quantitySample(
                    value = it.distance.inMeters,
                    unit = SampleType.Distance.unit,
                    startDate = it.startTime,
                    endDate = it.endTime,
                    metadata = it.metadata,
                )
            }
        }
        else emptyList()

        val (startInstant, endInstant) = when (distance) {
            is TimeRangeOrRecords.TimeRange -> distance.start to distance.end
            is TimeRangeOrRecords.Records -> distance.records.minOfOrNull { it.startTime } to distance.records.maxOfOrNull { it.endTime }
        }

        if (startInstant == null) {
            return TimeSeriesData.QuantitySamples(
                IngestibleTimeseriesResource.Distance, emptyList()
            )
        }
        checkNotNull(endInstant)

        val hourlyTotals = recordAggregator.aggregateDistanceHourlyTotals(
            start = startInstant.truncatedTo(ChronoUnit.HOURS),
            end = endInstant.plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS),
        )

        return TimeSeriesData.QuantitySamples(
            IngestibleTimeseriesResource.Distance,
            merge(distanceSamples, hourlyTotals, options)
        )
    }

    override suspend fun processFloorsClimbedRecords(
        floorsClimbed: TimeRangeOrRecords<FloorsClimbedRecord>,
        options: ProcessorOptions
    ): TimeSeriesData.QuantitySamples {
        val floorsClimbedSamples = if (options.perDeviceActivityTS) {
            val records = when (floorsClimbed) {
                is TimeRangeOrRecords.Records ->
                    floorsClimbed.records
                is TimeRangeOrRecords.TimeRange ->
                    recordReader.readFloorsClimbed(floorsClimbed.start, floorsClimbed.end)
            }
            records.map {
                quantitySample(
                    value = it.floors,
                    unit = SampleType.FloorsClimbed.unit,
                    startDate = it.startTime,
                    endDate = it.endTime,
                    metadata = it.metadata,
                )
            }
        }
        else emptyList()

        val (startInstant, endInstant) = when (floorsClimbed) {
            is TimeRangeOrRecords.TimeRange -> floorsClimbed.start to floorsClimbed.end
            is TimeRangeOrRecords.Records -> floorsClimbed.records.minOfOrNull { it.startTime } to floorsClimbed.records.maxOfOrNull { it.endTime }
        }


        if (startInstant == null) {
            return TimeSeriesData.QuantitySamples(
                IngestibleTimeseriesResource.FloorsClimbed, emptyList()
            )
        }
        checkNotNull(endInstant)

        val hourlyTotals = recordAggregator.aggregateFloorsClimbedHourlyTotals(
            start = startInstant.truncatedTo(ChronoUnit.HOURS),
            end = endInstant.plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS),
        )

        return TimeSeriesData.QuantitySamples(
            IngestibleTimeseriesResource.FloorsClimbed,
            merge(floorsClimbedSamples, hourlyTotals, options)
        )
    }
    override suspend fun processStepsRecords(
        steps: TimeRangeOrRecords<StepsRecord>,
        options: ProcessorOptions
    ): TimeSeriesData.QuantitySamples {
        val stepsSamples = if (options.perDeviceActivityTS) {
            val records = when (steps) {
                is TimeRangeOrRecords.Records ->
                    steps.records
                is TimeRangeOrRecords.TimeRange ->
                    recordReader.readSteps(steps.start, steps.end)
            }
            records.map {
                quantitySample(
                    value = it.count.toDouble(),
                    unit = SampleType.Steps.unit,
                    startDate = it.startTime,
                    endDate = it.endTime,
                    metadata = it.metadata,
                )
            }
        }
        else emptyList()

        val (startInstant, endInstant) = when (steps) {
            is TimeRangeOrRecords.TimeRange -> steps.start to steps.end
            is TimeRangeOrRecords.Records -> steps.records.minOfOrNull { it.startTime } to steps.records.maxOfOrNull { it.endTime }
        }

        if (startInstant == null) {
            return TimeSeriesData.QuantitySamples(IngestibleTimeseriesResource.Steps, emptyList())
        }
        checkNotNull(endInstant)

        val hourlyTotals = recordAggregator.aggregateStepHourlyTotals(
            start = startInstant.truncatedTo(ChronoUnit.HOURS),
            end = endInstant.plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS),
        )

        return TimeSeriesData.QuantitySamples(
            IngestibleTimeseriesResource.Steps,
            merge(stepsSamples, hourlyTotals, options)
        )
    }

    override suspend fun processVo2MaxRecords(
        vo2Max: List<Vo2MaxRecord>,
        options: ProcessorOptions
    ): TimeSeriesData.QuantitySamples {
        val vo2MaxSamples = vo2Max.map {
            quantitySample(
                value = it.vo2MillilitersPerMinuteKilogram,
                unit = SampleType.Vo2Max.unit,
                startDate = it.time,
                endDate = it.time,
                metadata = it.metadata,
            )
        }

        return TimeSeriesData.QuantitySamples(
            IngestibleTimeseriesResource.Vo2Max,
            vo2MaxSamples
        )
    }

    override suspend fun processBodyTemperatureRecords(temperatures: List<BodyTemperatureRecord>) = TimeSeriesData.QuantitySamples(
        IngestibleTimeseriesResource.Temperature,
        temperatures.map {
            quantitySample(
                value = it.temperature.inCelsius,
                unit = SampleType.Temperature.unit,
                startDate = it.time,
                endDate = it.time,
                metadata = it.metadata,
            )
        }
    )

    override suspend fun processRespiratoryRateRecords(respiratoryRates: List<RespiratoryRateRecord>) = TimeSeriesData.QuantitySamples(
        IngestibleTimeseriesResource.RespiratoryRate,
        respiratoryRates.map {
            quantitySample(
                value = it.rate,
                unit = SampleType.RespiratoryRate.unit,
                startDate = it.time,
                endDate = it.time,
                metadata = it.metadata,
            )
        }
    )

    override suspend fun processOxygenSaturationRecords(oxygenSaturations: List<OxygenSaturationRecord>) = TimeSeriesData.QuantitySamples(
        IngestibleTimeseriesResource.BloodOxygen,
        oxygenSaturations.map {
            quantitySample(
                value = it.percentage.value,
                unit = SampleType.OxygenSaturation.unit,
                startDate = it.time,
                endDate = it.time,
                metadata = it.metadata,
            )
        }
    )

    override suspend fun processActivities(
        lastSynced: Instant?,
        timeZone: TimeZone,
    ): SummaryData.Activities = coroutineScope {
        val zoneId = timeZone.toZoneId()
        val now = ZonedDateTime.now(zoneId)
        val summaryStart = minOf(
            lastSynced?.atZone(zoneId) ?: now,
            now.minusDays(ACTIVITY_STATS_DAYS_TO_LOOKBACK)
        )

        val daySummariesByDate = recordAggregator.aggregateActivityDaySummaries(
            startDate = summaryStart.toLocalDate(),
            endDate = now.toLocalDate(),
            timeZone = timeZone
        )

        SummaryData.Activities(
            activities = daySummariesByDate.map { (date, summary) ->
                LocalActivity(
                    daySummary = summary.toDatedPayload(date),
                )
            }
        )
    }

    override suspend fun processMeals(
        lastSynced: Instant?,
        timeZone: TimeZone
    ): SummaryData.Meals = coroutineScope {
        val zoneId = timeZone.toZoneId()
        val now = ZonedDateTime.now(zoneId)
        val startInstant = minOf(
            lastSynced?.atZone(zoneId) ?: now,
            now.minusDays(ACTIVITY_STATS_DAYS_TO_LOOKBACK)
        ).toInstant()

        val nutritionRecords = recordReader.readNutritionRecords(startInstant, now.toInstant())

        val meals = nutritionRecords.groupBy { Triple(
            it.metadata.dataOrigin.packageName,
            it.mealType,
            it.startTime.atZone(it.startZoneOffset).toLocalDate().atStartOfDay()
        ) }

        SummaryData.Meals(
            meals = meals.map{
                ManualMealCreation(
                    healthConnect = HealthConnectRecordCollection(
                        nutritionRecords = it.value.map{record ->
                            NutritionRecord(
                                startTime = record.startTime,
                                startZoneOffset = record.startZoneOffset,
                                endTime = record.endTime,
                                endZoneOffset = record.endZoneOffset,
                                biotin = record.biotin?.inMicrograms,
                                caffeine = record.caffeine?.inMilligrams,
                                calcium = record.calcium?.inMilligrams,
                                energy = record.energy?.inKilocalories,
                                energyFromFat = record.energyFromFat?.inKilocalories,
                                chloride = record.chloride?.inMilligrams,
                                cholesterol = record.cholesterol?.inMilligrams,
                                chromium = record.chromium?.inMicrograms,
                                copper = record.copper?.inMilligrams,
                                dietaryFiber = record.dietaryFiber?.inGrams,
                                folate = record.folate?.inMicrograms,
                                folicAcid = record.folicAcid?.inMicrograms,
                                iodine = record.iodine?.inMicrograms,
                                iron = record.iron?.inMilligrams,
                                magnesium = record.magnesium?.inMilligrams,
                                manganese = record.manganese?.inMilligrams,
                                molybdenum = record.molybdenum?.inMicrograms,
                                monounsaturatedFat = record.monounsaturatedFat?.inGrams,
                                niacin = record.niacin?.inMilligrams,
                                pantothenicAcid = record.pantothenicAcid?.inMilligrams,
                                phosphorus = record.phosphorus?.inMilligrams,
                                polyunsaturatedFat = record.polyunsaturatedFat?.inGrams,
                                potassium = record.potassium?.inMilligrams,
                                protein = record.protein?.inGrams,
                                riboflavin = record.riboflavin?.inMilligrams,
                                saturatedFat = record.saturatedFat?.inGrams,
                                selenium = record.selenium?.inMicrograms,
                                sodium = record.sodium?.inMilligrams,
                                sugar = record.sugar?.inGrams,
                                thiamin = record.thiamin?.inMilligrams,
                                totalCarbohydrate = record.totalCarbohydrate?.inGrams,
                                totalFat = record.totalFat?.inGrams,
                                transFat = record.transFat?.inGrams,
                                unsaturatedFat = record.unsaturatedFat?.inGrams,
                                vitaminA = record.vitaminA?.inMicrograms,
                                vitaminB12 = record.vitaminB12?.inMicrograms,
                                vitaminB6 = record.vitaminB6?.inMilligrams,
                                vitaminC = record.vitaminC?.inMilligrams,
                                vitaminD = record.vitaminD?.inMicrograms,
                                vitaminE = record.vitaminE?.inMilligrams,
                                vitaminK = record.vitaminK?.inMicrograms,
                                zinc = record.zinc?.inMilligrams,
                                name = record.name,
                                mealType = record.mealType,
                                metadata = mapOf("dataOrigin" to mapOf("packageName" to record.metadata.dataOrigin.packageName))
                            )
                        },
                        sourceBundle = it.key.first
                    )
                )
            }
        )
    }

    /**
     * We ignore any delta record inputs. We recompute cycle boundaries on the fly every time,
     * and then grouping the record scraps into [MenstrualCycle]s based on the boundaries.
     */
    override suspend fun processMenstrualCyclesFromRecords(
        endDate: LocalDate,
        startDate: LocalDate?,
        timeZone: TimeZone,
    ): SummaryData.MenstrualCycles {
        // Look ~3 cycles back
        val realStartDate = (startDate ?: endDate).minusDays(90)

        val startInstant = realStartDate.atStartOfDay(timeZone.toZoneId()).toInstant()
        val endInstant = endDate.plusDays(1).atStartOfDay(timeZone.toZoneId()).toInstant()

        VitalLogger.getOrCreate().info {
            "menstrualCycle: query range [${startInstant} ..< ${endInstant}]"
        }

        val periods = recordReader.menstruationPeriod(startInstant, endInstant)
        val flows = recordReader.menstruationFlow(startInstant, endInstant)
        val intermenstrualBleeding = recordReader.intermenstrualBleeding(startInstant, endInstant)
        val ovulationTest = recordReader.ovulationTest(startInstant, endInstant)
        val sexualActivity = recordReader.sexualActivity(startInstant, endInstant)
        val cervicalMucus = recordReader.cervicalMucus(startInstant, endInstant)

        val cycles = processMenstrualCycle(
            periods, flows, cervicalMucus, intermenstrualBleeding, ovulationTest, sexualActivity
        )

        return SummaryData.MenstrualCycles(cycles = cycles)
    }


    private fun mapOxygenSaturationRecord(
        oxygenSaturationRecords: List<OxygenSaturationRecord>,
    ): List<LocalQuantitySample> {
        return oxygenSaturationRecords.map {
            quantitySample(
                value = it.percentage.value,
                unit = SampleType.OxygenSaturation.unit,
                startDate = it.time,
                endDate = it.time,
                metadata = it.metadata,
            )
        }
    }

    private fun mapHeartRateVariabilityRmssdRecord(
        readHeartRateVariabilityRmssdRecords: List<HeartRateVariabilityRmssdRecord>,
    ): List<LocalQuantitySample> {
        return readHeartRateVariabilityRmssdRecords.map {
            quantitySample(
                value = it.heartRateVariabilityMillis,
                unit = SampleType.HeartRateVariabilityRmssd.unit,
                startDate = it.time,
                endDate = it.time,
                metadata = it.metadata,
            )
        }
    }

    private fun mapRespiratoryRate(
        respiratoryRateRecords: List<RespiratoryRateRecord>,
    ): List<LocalQuantitySample> {
        return respiratoryRateRecords.map {
            quantitySample(
                value = it.rate,
                unit = SampleType.RespiratoryRate.unit,
                startDate = it.time,
                endDate = it.time,
                metadata = it.metadata,
            )

        }
    }

    private fun mapHearthRate(
        heartRateRecords: List<HeartRateRecord>,
    ): List<LocalQuantitySample> {
        return heartRateRecords.map { heartRateRecord ->
            heartRateRecord.samples.windowed(5)
                .map {
                    val averagedSample =
                        it.fold(0L) { acc, sample -> acc + sample.beatsPerMinute } / it.size

                    quantitySample(
                        value = averagedSample.toDouble(),
                        unit = SampleType.HeartRate.unit,
                        startDate = it.first().time,
                        endDate = it.last().time,
                        metadata = heartRateRecord.metadata,
                    )
                }
        }.flatten()
    }

    private fun mapRestingHearthRate(
        heartRateRecords: List<RestingHeartRateRecord>,
    ): List<LocalQuantitySample> {
        return heartRateRecords.map {
            quantitySample(
                value = it.beatsPerMinute.toDouble(),
                unit = SampleType.HeartRate.unit,
                startDate = it.time,
                endDate = it.time,
                metadata = it.metadata,
            )
        }
    }
}

private fun List<SleepSessionRecord>.filterForAcceptedSleepDataSources(): List<SleepSessionRecord> {
    return this.filter {
        SupportedSleepApps.values().any { supportedSleepApp ->
            it.metadata.dataOrigin.packageName == supportedSleepApp.packageName
        }
    }
}

internal inline fun <R: Record> quantitySamplesByDate(
    records: Iterable<R>,
    zoneId: ZoneId,
    timeSelector: (R) -> Instant,
    transform: (R) -> LocalQuantitySample
): Map<LocalDate, List<LocalQuantitySample>> {
    return records.groupBy(
        keySelector = { timeSelector(it).atZone(zoneId).toLocalDate() },
        valueTransform = transform
    )
}


internal fun merge(
    discovered: List<LocalQuantitySample>,
    hourlyTotals: List<LocalQuantitySample>,
    options: ProcessorOptions,
): List<LocalQuantitySample> {
    return if (options.perDeviceActivityTS) {
        discovered + hourlyTotals
    } else {
        hourlyTotals
    }
}
