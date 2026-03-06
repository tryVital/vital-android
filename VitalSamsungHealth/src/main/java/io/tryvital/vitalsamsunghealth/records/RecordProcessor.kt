package io.tryvital.vitalsamsunghealth.records

import android.annotation.SuppressLint
import com.samsung.android.sdk.health.data.data.AggregatedData
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.data.entries.BloodGlucose
import com.samsung.android.sdk.health.data.data.entries.HeartRate
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataType.NutritionType.MealType
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
import io.tryvital.vitalsamsunghealth.model.inferredSourceType
import io.tryvital.vitalsamsunghealth.model.processedresource.SummaryData
import io.tryvital.vitalsamsunghealth.model.processedresource.TimeSeriesData
import io.tryvital.vitalsamsunghealth.model.quantitySample
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.TimeZone
import kotlin.math.roundToInt

data class ProcessorOptions(
    val perDeviceActivityTS: Boolean = false,
)

const val ACTIVITY_STATS_DAYS_TO_LOOKBACK = 3L
const val NUTRITION_STATS_DAYS_TO_LOOKBACK = 5L

sealed class TimeRangeOrRecords<out R> {
    data class TimeRange<out R>(val start: Instant, val end: Instant) : TimeRangeOrRecords<R>()
    data class Records<out R>(val records: List<R>) : TimeRangeOrRecords<R>()
}

interface RecordProcessor {
    suspend fun processBloodPressureFromRecords(readBloodPressure: List<HealthDataPoint>): TimeSeriesData.BloodPressure
    suspend fun processGlucoseFromRecords(readBloodGlucose: List<HealthDataPoint>): TimeSeriesData.QuantitySamples
    suspend fun processHeartRateFromRecords(heartRateRecords: List<HealthDataPoint>): TimeSeriesData.QuantitySamples
    fun processWaterFromRecords(readHydration: List<HealthDataPoint>): TimeSeriesData.QuantitySamples
    suspend fun processBodyFromRecords(records: List<HealthDataPoint>): SummaryData.Body
    suspend fun processProfileFromRecords(heightRecords: List<HealthDataPoint>): SummaryData.Profile
    suspend fun processWorkoutsFromRecords(exerciseRecords: List<HealthDataPoint>): SummaryData.Workouts
    suspend fun processSleepFromRecords(sleepSessionRecords: List<HealthDataPoint>, skinTemperature: Map<String, List<HealthDataPoint>>): SummaryData.Sleeps
    suspend fun processActivities(lastSynced: Instant?, timeZone: TimeZone): SummaryData.Activities
    suspend fun processMeals(lastSynced: Instant?, timeZone: TimeZone): SummaryData.Meals
    suspend fun processActiveCaloriesBurnedRecords(
        activeEnergyBurned: TimeRangeOrRecords<AggregatedData<Float>>,
        options: ProcessorOptions,
    ): TimeSeriesData.QuantitySamples

    suspend fun processBasalMetabolicRateRecords(
        basalMetabolicRate: List<HealthDataPoint>,
        options: ProcessorOptions,
    ): TimeSeriesData.QuantitySamples

    suspend fun processStepsRecords(
        steps: TimeRangeOrRecords<AggregatedData<Long>>,
        options: ProcessorOptions,
    ): TimeSeriesData.QuantitySamples

    suspend fun processDistanceRecords(
        distance: TimeRangeOrRecords<AggregatedData<Float>>,
        options: ProcessorOptions,
    ): TimeSeriesData.QuantitySamples

    suspend fun processFloorsClimbedRecords(
        floorsClimbed: TimeRangeOrRecords<AggregatedData<Float>>,
        options: ProcessorOptions,
    ): TimeSeriesData.QuantitySamples

    suspend fun processVo2MaxRecords(vo2Max: List<HealthDataPoint>, options: ProcessorOptions): TimeSeriesData.QuantitySamples
    suspend fun processOxygenSaturationRecords(oxygenSaturations: List<HealthDataPoint>): TimeSeriesData.QuantitySamples
    suspend fun processBodyTemperatureRecords(temperatures: List<HealthDataPoint>): TimeSeriesData.QuantitySamples
}

internal class HealthConnectRecordProcessor(
    private val recordReader: RecordReader,
    private val recordAggregator: RecordAggregator,
) : RecordProcessor {

    override suspend fun processBloodPressureFromRecords(readBloodPressure: List<HealthDataPoint>): TimeSeriesData.BloodPressure {
        return TimeSeriesData.BloodPressure(
            readBloodPressure.mapNotNull { point ->
                val systolic = point.getValue(DataType.BloodPressureType.SYSTOLIC) ?: return@mapNotNull null
                val diastolic = point.getValue(DataType.BloodPressureType.DIASTOLIC) ?: return@mapNotNull null
                val pulse = point.getValue(DataType.BloodPressureType.PULSE_RATE) ?: return@mapNotNull null

                LocalBloodPressureSample(
                    systolic = quantitySample(
                        value = systolic.toDouble(),
                        unit = SampleType.BloodPressureSystolic.unit,
                        startDate = point.startTime,
                        endDate = point.endTime ?: point.startTime,
                        dataPoint = point,
                    ),
                    diastolic = quantitySample(
                        value = diastolic.toDouble(),
                        unit = SampleType.BloodPressureDiastolic.unit,
                        startDate = point.startTime,
                        endDate = point.endTime ?: point.startTime,
                        dataPoint = point,
                    ),
                    pulse = quantitySample(
                        value = pulse.toDouble(),
                        unit = SampleType.HeartRate.unit,
                        startDate = point.startTime,
                        endDate = point.endTime ?: point.startTime,
                        dataPoint = point,
                    ),
                )
            }
        )
    }

    override suspend fun processGlucoseFromRecords(readBloodGlucose: List<HealthDataPoint>): TimeSeriesData.QuantitySamples {
        val samples = readBloodGlucose.flatMap { point ->
            val series = point.getValue(DataType.BloodGlucoseType.SERIES_DATA) ?: emptyList<BloodGlucose>()
            if (series.isNotEmpty()) {
                series.map {
                    quantitySample(
                        value = it.glucose.toDouble(),
                        unit = SampleType.GlucoseConcentrationMillimolePerLitre.unit,
                        startDate = it.timestamp,
                        endDate = it.timestamp,
                        dataPoint = point,
                    )
                }
            } else {
                val glucose = point.getValue(DataType.BloodGlucoseType.GLUCOSE_LEVEL) ?: return@flatMap emptyList()
                listOf(
                    quantitySample(
                        value = glucose.toDouble(),
                        unit = SampleType.GlucoseConcentrationMillimolePerLitre.unit,
                        startDate = point.startTime,
                        endDate = point.startTime,
                        dataPoint = point,
                    )
                )
            }
        }

        return TimeSeriesData.QuantitySamples(IngestibleTimeseriesResource.BloodGlucose, samples)
    }

    override suspend fun processHeartRateFromRecords(heartRateRecords: List<HealthDataPoint>): TimeSeriesData.QuantitySamples {
        return TimeSeriesData.QuantitySamples(IngestibleTimeseriesResource.HeartRate, mapHeartRate(heartRateRecords))
    }

    override fun processWaterFromRecords(readHydration: List<HealthDataPoint>): TimeSeriesData.QuantitySamples {
        return TimeSeriesData.QuantitySamples(
            IngestibleTimeseriesResource.Water,
            readHydration.mapNotNull { point ->
                val volumeMl = point.getValue(DataType.WaterIntakeType.AMOUNT) ?: return@mapNotNull null
                quantitySample(
                    value = volumeMl.toDouble(),
                    unit = SampleType.Water.unit,
                    startDate = point.startTime,
                    endDate = point.endTime ?: point.startTime,
                    dataPoint = point,
                )
            },
        )
    }

    @SuppressLint("RestrictedApi")
    override suspend fun processWorkoutsFromRecords(exerciseRecords: List<HealthDataPoint>): SummaryData.Workouts {
        return SummaryData.Workouts(
            exerciseRecords.flatMap { point ->
                val sessions = point.getValue(DataType.ExerciseType.SESSIONS) ?: emptyList()
                sessions.mapIndexed { index, session ->
                    val summary = recordAggregator.aggregateWorkoutSummary(session.startTime, session.endTime)

                    LocalWorkout(
                        id = "${point.uid}:${index}:${session.exerciseType.name}",
                        startDate = session.startTime,
                        endDate = session.endTime,
                        sport = session.exerciseType.name.lowercase(),
                        calories = session.calories.toDouble(),
                        distance = session.distance?.toDouble(),
                        heartRateMinimum = summary.heartRateMinimum,
                        heartRateMaximum = summary.heartRateMaximum,
                        heartRateMean = summary.heartRateMean,
                        heartRateZone1 = summary.heartRateZone1,
                        heartRateZone2 = summary.heartRateZone2,
                        heartRateZone3 = summary.heartRateZone3,
                        heartRateZone4 = summary.heartRateZone4,
                        heartRateZone5 = summary.heartRateZone5,
                        heartRateZone6 = summary.heartRateZone6,
                        sourceBundle = point.dataSource?.appId,
                        sourceType = point.inferredSourceType,
                        deviceModel = null,
                        metadata = point.dataSource?.deviceId?.let { deviceId ->
                            mapOf("_DID" to deviceId)
                        } ?: emptyMap(),
                    )
                }
            }
        )
    }

    override suspend fun processProfileFromRecords(heightRecords: List<HealthDataPoint>): SummaryData.Profile {
        val lastHeightCm = heightRecords.mapNotNull { it.getValue(DataType.BodyCompositionType.HEIGHT) }.lastOrNull() ?: 0f

        return SummaryData.Profile(
            biologicalSex = "not_set",
            dateOfBirth = null,
            heightInCm = lastHeightCm.roundToInt(),
        )
    }

    override suspend fun processBodyFromRecords(records: List<HealthDataPoint>) = SummaryData.Body(
        bodyMass = records.mapNotNull { point ->
            val kg = point.getValue(DataType.BodyCompositionType.WEIGHT) ?: return@mapNotNull null
            quantitySample(
                value = kg.toDouble(),
                unit = SampleType.Weight.unit,
                startDate = point.startTime,
                endDate = point.endTime ?: point.startTime,
                dataPoint = point,
            )
        },
        bodyFatPercentage = records.mapNotNull { point ->
            val bodyFat = point.getValue(DataType.BodyCompositionType.BODY_FAT) ?: return@mapNotNull null
            quantitySample(
                value = bodyFat.toDouble(),
                unit = SampleType.BodyFat.unit,
                startDate = point.startTime,
                endDate = point.endTime ?: point.startTime,
                dataPoint = point,
            )
        },
        leanBodyMass = records.mapNotNull { point ->
            val leanBodyMass = point.getValue(DataType.BodyCompositionType.FAT_FREE_MASS) ?: return@mapNotNull null
            quantitySample(
                value = leanBodyMass.toDouble(),
                unit = SampleType.LeanBodyMass.unit,
                startDate = point.startTime,
                endDate = point.endTime ?: point.startTime,
                dataPoint = point,
            )
        },
        bodyMassIndex = records.mapNotNull { point ->
            val bmi = point.getValue(DataType.BodyCompositionType.BODY_MASS_INDEX) ?: return@mapNotNull null
            quantitySample(
                value = bmi.toDouble(),
                unit = SampleType.BodyMassIndex.unit,
                startDate = point.startTime,
                endDate = point.endTime ?: point.startTime,
                dataPoint = point,
            )
        },
        waistCircumference = emptyList(),
    )

    override suspend fun processSleepFromRecords(sleepSessionRecords: List<HealthDataPoint>, skinTemperature: Map<String, List<HealthDataPoint>>): SummaryData.Sleeps {
        return SummaryData.Sleeps(processSleeps(sleepSessionRecords, skinTemperature))
    }

    private suspend fun processSleeps(sleeps: List<HealthDataPoint>, skinTemperature: Map<String, List<HealthDataPoint>>): List<LocalSleep> {
        return sleeps.map { sleep ->
            val sessions = checkNotNull(sleep.getValue(DataType.SleepType.SESSIONS))
            val score = sleep.getValue(DataType.SleepType.SLEEP_SCORE)

            val endTime = checkNotNull(sleep.endTime)
            val statistics = recordAggregator.aggregateSleepSummary(sleep.startTime, endTime)

            val stages = sessions.flatMap { it.stages ?: emptyList() }

            val temperatureSamples: List<LocalQuantitySample>? = skinTemperature[sleep.uid]?.flatMap { point ->
                val series = point.getValue(DataType.SkinTemperatureType.SERIES_DATA)
                if (!series.isNullOrEmpty()) {
                    series.map { datum ->
                        quantitySample(
                            value = datum.skinTemperature.toDouble(),
                            unit = SampleType.Temperature.unit,
                            startDate = datum.startTime,
                            endDate = datum.endTime,
                            dataPoint = point,
                        )
                    }
                } else {
                    val temperature = point.getValue(DataType.SkinTemperatureType.SKIN_TEMPERATURE) ?: return@flatMap emptyList()
                    listOf(
                        quantitySample(
                            value = temperature.toDouble(),
                            unit = SampleType.Temperature.unit,
                            startDate = point.startTime,
                            endDate = point.endTime ?: point.startTime,
                            dataPoint = point,
                        )

                    )
                }
            }

            LocalSleep(
                id = "",
                startDate = sleep.startTime,
                endDate = endTime,
                sourceBundle = sleep.dataSource?.appId,
                deviceModel = null,
                metadata = sleep.dataSource?.deviceId?.let { deviceId ->
                    mapOf("_DID" to deviceId)
                } ?: emptyMap(),
                sourceType = sleep.inferredSourceType,
                score = score,
                heartRateMean = statistics.heartRateMean,
                heartRateMaximum = statistics.heartRateMaximum,
                heartRateMinimum = statistics.heartRateMinimum,
                sleepStages = LocalSleep.Stages(
                    awakeSleepSamples = stages.filter { it.stage == DataType.SleepType.StageType.AWAKE }
                        .map { sleepStage -> stageSample(LocalSleep.Stage.Awake.id, sleepStage.startTime, sleepStage.endTime, sleep) },
                    deepSleepSamples = stages.filter { it.stage == DataType.SleepType.StageType.DEEP }
                        .map { sleepStage -> stageSample(LocalSleep.Stage.Deep.id, sleepStage.startTime, sleepStage.endTime, sleep) },
                    lightSleepSamples = stages.filter { it.stage == DataType.SleepType.StageType.LIGHT }
                        .map { sleepStage -> stageSample(LocalSleep.Stage.Light.id, sleepStage.startTime, sleepStage.endTime, sleep) },
                    remSleepSamples = stages.filter { it.stage == DataType.SleepType.StageType.REM }
                        .map { sleepStage -> stageSample(LocalSleep.Stage.Rem.id, sleepStage.startTime, sleepStage.endTime, sleep) },
                    unknownSleepSamples = stages.filter { it.stage == DataType.SleepType.StageType.UNDEFINED }
                        .map { sleepStage -> stageSample(LocalSleep.Stage.Unknown.id, sleepStage.startTime, sleepStage.endTime, sleep) },
                    outOfBedSleepSamples = emptyList(),
                ),
                wristTemperature = temperatureSamples ?: emptyList(),
            )
        }
    }

    override suspend fun processActiveCaloriesBurnedRecords(
        activeEnergyBurned: TimeRangeOrRecords<AggregatedData<Float>>,
        options: ProcessorOptions,
    ): TimeSeriesData.QuantitySamples {
        val caloriesActive = if (options.perDeviceActivityTS) {
            val records = when (activeEnergyBurned) {
                is TimeRangeOrRecords.Records -> activeEnergyBurned.records
                is TimeRangeOrRecords.TimeRange -> recordReader.readActiveEnergyBurned(activeEnergyBurned.start, activeEnergyBurned.end)
            }
            records.map {
                quantitySample(
                    value = (it.value ?: 0f).toDouble(),
                    unit = SampleType.ActiveCaloriesBurned.unit,
                    startDate = it.startTime,
                    endDate = it.endTime,
                )
            }
        } else {
            emptyList()
        }

        val (startInstant, endInstant) = when (activeEnergyBurned) {
            is TimeRangeOrRecords.TimeRange -> activeEnergyBurned.start to activeEnergyBurned.end
            is TimeRangeOrRecords.Records -> activeEnergyBurned.records.minOfOrNull { it.startTime } to activeEnergyBurned.records.maxOfOrNull { it.endTime }
        }

        if (startInstant == null) {
            return TimeSeriesData.QuantitySamples(IngestibleTimeseriesResource.CaloriesActive, emptyList())
        }
        checkNotNull(endInstant)

        val hourlyTotals = recordAggregator.aggregateCaloriesActiveHourlyTotals(
            start = startInstant.truncatedTo(ChronoUnit.HOURS),
            end = endInstant.plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS),
        )

        return TimeSeriesData.QuantitySamples(IngestibleTimeseriesResource.CaloriesActive, merge(caloriesActive, hourlyTotals, options))
    }

    override suspend fun processBasalMetabolicRateRecords(
        basalMetabolicRate: List<HealthDataPoint>,
        options: ProcessorOptions,
    ): TimeSeriesData.QuantitySamples {
        val caloriesBasal = basalMetabolicRate.mapNotNull { point ->
            val kcalPerDay = point.getValue(DataType.BodyCompositionType.BASAL_METABOLIC_RATE) ?: return@mapNotNull null
            quantitySample(
                value = kcalPerDay.toDouble(),
                unit = SampleType.BasalMetabolicRate.unit,
                startDate = point.startTime,
                endDate = point.endTime ?: point.startTime,
                dataPoint = point,
            )
        }

        return TimeSeriesData.QuantitySamples(IngestibleTimeseriesResource.CaloriesBasal, caloriesBasal)
    }

    override suspend fun processDistanceRecords(
        distance: TimeRangeOrRecords<AggregatedData<Float>>,
        options: ProcessorOptions,
    ): TimeSeriesData.QuantitySamples {
        val distanceSamples = if (options.perDeviceActivityTS) {
            val records = when (distance) {
                is TimeRangeOrRecords.Records -> distance.records
                is TimeRangeOrRecords.TimeRange -> recordReader.readDistance(distance.start, distance.end)
            }
            records.map {
                quantitySample(
                    value = (it.value ?: 0f).toDouble(),
                    unit = SampleType.Distance.unit,
                    startDate = it.startTime,
                    endDate = it.endTime,
                )
            }
        } else {
            emptyList()
        }

        val (startInstant, endInstant) = when (distance) {
            is TimeRangeOrRecords.TimeRange -> distance.start to distance.end
            is TimeRangeOrRecords.Records -> distance.records.minOfOrNull { it.startTime } to distance.records.maxOfOrNull { it.endTime }
        }

        if (startInstant == null) {
            return TimeSeriesData.QuantitySamples(IngestibleTimeseriesResource.Distance, emptyList())
        }
        checkNotNull(endInstant)

        val hourlyTotals = recordAggregator.aggregateDistanceHourlyTotals(
            start = startInstant.truncatedTo(ChronoUnit.HOURS),
            end = endInstant.plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS),
        )

        return TimeSeriesData.QuantitySamples(IngestibleTimeseriesResource.Distance, merge(distanceSamples, hourlyTotals, options))
    }

    override suspend fun processFloorsClimbedRecords(
        floorsClimbed: TimeRangeOrRecords<AggregatedData<Float>>,
        options: ProcessorOptions,
    ): TimeSeriesData.QuantitySamples {
        val floorsClimbedSamples = if (options.perDeviceActivityTS) {
            val records = when (floorsClimbed) {
                is TimeRangeOrRecords.Records -> floorsClimbed.records
                is TimeRangeOrRecords.TimeRange -> recordReader.readFloorsClimbed(floorsClimbed.start, floorsClimbed.end)
            }
            records.map {
                quantitySample(
                    value = (it.value ?: 0f).toDouble(),
                    unit = SampleType.FloorsClimbed.unit,
                    startDate = it.startTime,
                    endDate = it.endTime,
                )
            }
        } else {
            emptyList()
        }

        val (startInstant, endInstant) = when (floorsClimbed) {
            is TimeRangeOrRecords.TimeRange -> floorsClimbed.start to floorsClimbed.end
            is TimeRangeOrRecords.Records -> floorsClimbed.records.minOfOrNull { it.startTime } to floorsClimbed.records.maxOfOrNull { it.endTime }
        }

        if (startInstant == null) {
            return TimeSeriesData.QuantitySamples(IngestibleTimeseriesResource.FloorsClimbed, emptyList())
        }
        checkNotNull(endInstant)

        val hourlyTotals = recordAggregator.aggregateFloorsClimbedHourlyTotals(
            start = startInstant.truncatedTo(ChronoUnit.HOURS),
            end = endInstant.plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS),
        )

        return TimeSeriesData.QuantitySamples(IngestibleTimeseriesResource.FloorsClimbed, merge(floorsClimbedSamples, hourlyTotals, options))
    }

    override suspend fun processStepsRecords(
        steps: TimeRangeOrRecords<AggregatedData<Long>>,
        options: ProcessorOptions,
    ): TimeSeriesData.QuantitySamples {
        val stepsSamples = if (options.perDeviceActivityTS) {
            val records = when (steps) {
                is TimeRangeOrRecords.Records -> steps.records
                is TimeRangeOrRecords.TimeRange -> recordReader.readSteps(steps.start, steps.end)
            }
            records.map {
                quantitySample(
                    value = (it.value ?: 0L).toDouble(),
                    unit = SampleType.Steps.unit,
                    startDate = it.startTime,
                    endDate = it.endTime,
                )
            }
        } else {
            emptyList()
        }

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

        return TimeSeriesData.QuantitySamples(IngestibleTimeseriesResource.Steps, merge(stepsSamples, hourlyTotals, options))
    }

    override suspend fun processVo2MaxRecords(vo2Max: List<HealthDataPoint>, options: ProcessorOptions): TimeSeriesData.QuantitySamples {
        val vo2MaxSamples = vo2Max.flatMap { point ->
            val sessions = point.getValue(DataType.ExerciseType.SESSIONS) ?: emptyList()
            sessions.mapNotNull { session ->
                val value = session.vo2Max ?: return@mapNotNull null
                quantitySample(
                    value = value.toDouble(),
                    unit = SampleType.Vo2Max.unit,
                    startDate = session.startTime,
                    endDate = session.endTime,
                    dataPoint = point,
                )
            }
        }

        return TimeSeriesData.QuantitySamples(IngestibleTimeseriesResource.Vo2Max, vo2MaxSamples)
    }

    override suspend fun processBodyTemperatureRecords(temperatures: List<HealthDataPoint>) = TimeSeriesData.QuantitySamples(
        IngestibleTimeseriesResource.Temperature,
        temperatures.mapNotNull { point ->
            val celsius = point.getValue(DataType.BodyTemperatureType.BODY_TEMPERATURE) ?: return@mapNotNull null
            quantitySample(
                value = celsius.toDouble(),
                unit = SampleType.Temperature.unit,
                startDate = point.startTime,
                endDate = point.endTime ?: point.startTime,
                dataPoint = point,
            )
        },
    )

    override suspend fun processOxygenSaturationRecords(oxygenSaturations: List<HealthDataPoint>) = TimeSeriesData.QuantitySamples(
        IngestibleTimeseriesResource.BloodOxygen,
        oxygenSaturations.flatMap { point ->
            val series = point.getValue(DataType.BloodOxygenType.SERIES_DATA) ?: emptyList()
            if (series.isNotEmpty()) {
                series.map {
                    quantitySample(
                        value = it.oxygenSaturation.toDouble(),
                        unit = SampleType.OxygenSaturation.unit,
                        startDate = it.startTime,
                        endDate = it.endTime,
                        dataPoint = point,
                    )
                }
            } else {
                val value = point.getValue(DataType.BloodOxygenType.OXYGEN_SATURATION) ?: return@flatMap emptyList()
                listOf(
                    quantitySample(
                        value = value.toDouble(),
                        unit = SampleType.OxygenSaturation.unit,
                        startDate = point.startTime,
                        endDate = point.endTime ?: point.startTime,
                        dataPoint = point,
                    )
                )
            }
        },
    )

    override suspend fun processActivities(lastSynced: Instant?, timeZone: TimeZone): SummaryData.Activities = coroutineScope {
        val zoneId = timeZone.toZoneId()
        val now = ZonedDateTime.now(zoneId)
        val summaryStart = minOf(lastSynced?.atZone(zoneId) ?: now, now.minusDays(ACTIVITY_STATS_DAYS_TO_LOOKBACK))

        val daySummariesByDate = recordAggregator.aggregateActivityDaySummaries(
            startDate = summaryStart.toLocalDate(),
            endDate = now.toLocalDate(),
            timeZone = timeZone,
        )

        SummaryData.Activities(
            activities = daySummariesByDate.map { (date, summary) ->
                LocalActivity(daySummary = summary.toDatedPayload(date))
            }
        )
    }

    override suspend fun processMeals(lastSynced: Instant?, timeZone: TimeZone): SummaryData.Meals {
        val zoneId = timeZone.toZoneId()
        val now = ZonedDateTime.now(zoneId)

        val startInstant = minOf(
            lastSynced?.atZone(zoneId) ?: now,
            now.minusDays(NUTRITION_STATS_DAYS_TO_LOOKBACK)
        ).truncatedTo(ChronoUnit.DAYS).toInstant()

        val nutritionRecords = recordReader.readNutritionRecords(startInstant, now.toInstant())

        val meals = nutritionRecords.map { point ->
            val sourceBundle = point.dataSource?.appId
            val mealType = mealTypeToInt(point.getValue(DataType.NutritionType.MEAL_TYPE))
            val zoneOffset = point.zoneOffset
            val start = point.startTime
            val end = point.endTime ?: start

            Triple(
                Triple(sourceBundle, mealType, start.atZone(zoneOffset ?: zoneId).toLocalDate()),
                sourceBundle,
                NutritionRecord(
                    startTime = start,
                    startZoneOffset = zoneOffset?.toString(),
                    endTime = end,
                    endZoneOffset = zoneOffset?.toString(),
                    biotin = null,
                    caffeine = null,
                    // SH: mg, JUNC: mg
                    calcium = point.getValue(DataType.NutritionType.CALCIUM)?.toDouble(),
                    // SH: g, JUNC: g
                    energy = point.getValue(DataType.NutritionType.CALORIES)?.toDouble(),
                    energyFromFat = null,
                    chloride = null,
                    // SH: mg, JUNC: g
                    cholesterol = point.getValue(DataType.NutritionType.CHOLESTEROL)?.toDouble()?.let { it * 1000 },
                    chromium = null,
                    copper = null,
                    // SH: g, JUNC: g
                    dietaryFiber = point.getValue(DataType.NutritionType.DIETARY_FIBER)?.toDouble(),
                    folate = null,
                    folicAcid = null,
                    iodine = null,
                    // SH: mg, JUNC: mg
                    iron = point.getValue(DataType.NutritionType.IRON)?.toDouble(),
                    magnesium = null,
                    manganese = null,
                    molybdenum = null,
                    // SH: g, JUNC: g
                    monounsaturatedFat = point.getValue(DataType.NutritionType.MONOSATURATED_FAT)?.toDouble(),
                    niacin = null,
                    pantothenicAcid = null,
                    phosphorus = null,
                    // SH: g, JUNC: g
                    polyunsaturatedFat = point.getValue(DataType.NutritionType.POLYSATURATED_FAT)?.toDouble(),
                    // SH: mg, JUNC: mg
                    potassium = point.getValue(DataType.NutritionType.POTASSIUM)?.toDouble(),
                    // SH: g, JUNC: g
                    protein = point.getValue(DataType.NutritionType.PROTEIN)?.toDouble(),
                    riboflavin = null,
                    // SH: g, JUNC: g
                    saturatedFat = point.getValue(DataType.NutritionType.SATURATED_FAT)?.toDouble(),
                    selenium = null,
                    // SH: mg, JUNC: mg
                    sodium = point.getValue(DataType.NutritionType.SODIUM)?.toDouble(),
                    // SH: g, JUNC: g
                    sugar = point.getValue(DataType.NutritionType.SUGAR)?.toDouble(),
                    thiamin = null,
                    // SH: g, JUNC: g
                    totalCarbohydrate = point.getValue(DataType.NutritionType.CARBOHYDRATE)?.toDouble(),
                    // SH: g, JUNC: g
                    totalFat = point.getValue(DataType.NutritionType.TOTAL_FAT)?.toDouble(),
                    // SH: g, JUNC: g
                    transFat = point.getValue(DataType.NutritionType.TRANS_FAT)?.toDouble(),
                    unsaturatedFat = null,
                    // SH: ug, JUNC: mg
                    vitaminA = point.getValue(DataType.NutritionType.VITAMIN_A)?.toDouble()?.let { it * 1000 },
                    vitaminB12 = null,
                    vitaminB6 = null,
                    // SH: mg, JUNC: mg
                    vitaminC = point.getValue(DataType.NutritionType.VITAMIN_C)?.toDouble(),
                    vitaminD = null,
                    vitaminE = null,
                    vitaminK = null,
                    zinc = null,
                    name = point.getValue(DataType.NutritionType.TITLE),
                    mealType = mealType,
                    metadata = point.dataSource?.appId?.let { appId ->
                        mapOf("dataOrigin" to mapOf("packageName" to appId))
                    } ?: emptyMap(),
                ),
            )
        }.groupBy { it.first }

        return SummaryData.Meals(
            meals = meals.values.map { grouped ->
                val sourceBundle = grouped.first().second
                ManualMealCreation(
                    healthConnect = HealthConnectRecordCollection(
                        sourceBundle = sourceBundle ?: "",
                        nutritionRecords = grouped.map { it.third },
                    )
                )
            }
        )
    }

    private fun stageSample(stageId: Int, startTime: Instant, endTime: Instant, point: HealthDataPoint): LocalQuantitySample {
        return quantitySample(
            value = stageId.toDouble(),
            unit = "stage",
            startDate = startTime,
            endDate = endTime,
            dataPoint = point,
        )
    }

    private fun mapHeartRate(heartRateRecords: List<HealthDataPoint>): List<LocalQuantitySample> {
        return heartRateRecords.flatMap { point ->
            val series = point.getValue(DataType.HeartRateType.SERIES_DATA) ?: emptyList<HeartRate>()
            val collapsed = if (series.isNotEmpty()) {
                series.map { it.startTime to it.heartRate.roundToInt() }
            } else {
                val bpm = point.getValue(DataType.HeartRateType.HEART_RATE) ?: return@flatMap emptyList()
                listOf(point.startTime to bpm.roundToInt())
            }

            collapsed.windowed(5).map {
                val averaged = it.sumOf { sample -> sample.second } / it.size.toDouble()
                quantitySample(
                    value = averaged,
                    unit = SampleType.HeartRate.unit,
                    startDate = it.first().first,
                    endDate = it.last().first,
                    dataPoint = point,
                )
            }
        }
    }
}

private fun mealTypeToInt(mealType: MealType?): Int {
    return when (mealType) {
        MealType.BREAKFAST -> 1
        MealType.LUNCH -> 2
        MealType.DINNER -> 3
        MealType.MORNING_SNACK, MealType.AFTERNOON_SNACK, MealType.EVENING_SNACK  -> 4
        MealType.UNDEFINED, null -> 0
    }
}

internal fun merge(
    discovered: List<LocalQuantitySample>,
    hourlyTotals: List<LocalQuantitySample>,
    options: ProcessorOptions,
): List<LocalQuantitySample> {
    return if (options.perDeviceActivityTS) discovered + hourlyTotals else hourlyTotals
}
