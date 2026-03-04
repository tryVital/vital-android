package io.tryvital.vitalsamsunghealth.records

import android.annotation.SuppressLint
import com.samsung.android.sdk.health.data.data.AggregatedData
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.data.entries.BloodGlucose
import com.samsung.android.sdk.health.data.data.entries.HeartRate
import com.samsung.android.sdk.health.data.data.entries.OxygenSaturation
import com.samsung.android.sdk.health.data.data.entries.SleepSession
import com.samsung.android.sdk.health.data.request.DataType
import io.tryvital.client.services.data.IngestibleTimeseriesResource
import io.tryvital.client.services.data.LocalActivity
import io.tryvital.client.services.data.LocalBloodPressureSample
import io.tryvital.client.services.data.LocalQuantitySample
import io.tryvital.client.services.data.LocalSleep
import io.tryvital.client.services.data.LocalWorkout
import io.tryvital.client.services.data.SampleType
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalsamsunghealth.SupportedSleepApps
import io.tryvital.vitalsamsunghealth.model.inferredSourceType
import io.tryvital.vitalsamsunghealth.model.processedresource.SummaryData
import io.tryvital.vitalsamsunghealth.model.processedresource.TimeSeriesData
import io.tryvital.vitalsamsunghealth.model.quantitySample
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.TimeZone
import kotlin.math.roundToInt

data class ProcessorOptions(
    val perDeviceActivityTS: Boolean = false,
)

const val ACTIVITY_STATS_DAYS_TO_LOOKBACK = 3L

sealed class TimeRangeOrRecords<out R> {
    data class TimeRange<out R>(val start: Instant, val end: Instant) : TimeRangeOrRecords<R>()
    data class Records<out R>(val records: List<R>) : TimeRangeOrRecords<R>()
}

interface RecordProcessor {
    suspend fun processBloodPressureFromRecords(readBloodPressure: List<HealthDataPoint>): TimeSeriesData.BloodPressure
    suspend fun processGlucoseFromRecords(readBloodGlucose: List<HealthDataPoint>): TimeSeriesData.QuantitySamples
    suspend fun processHeartRateFromRecords(heartRateRecords: List<HealthDataPoint>): TimeSeriesData.QuantitySamples
    fun processHeartRateVariabilityRmssFromRecords(heartRateRecords: List<HealthDataPoint>): TimeSeriesData.QuantitySamples
    fun processWaterFromRecords(readHydration: List<HealthDataPoint>): TimeSeriesData.QuantitySamples
    suspend fun processBodyFromRecords(weightRecords: List<HealthDataPoint>, bodyFatRecords: List<HealthDataPoint>): SummaryData.Body
    suspend fun processProfileFromRecords(heightRecords: List<HealthDataPoint>): SummaryData.Profile
    suspend fun processWorkoutsFromRecords(exerciseRecords: List<HealthDataPoint>): SummaryData.Workouts
    suspend fun processSleepFromRecords(sleepSessionRecords: List<HealthDataPoint>): SummaryData.Sleeps
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

                LocalBloodPressureSample(
                    systolic = quantitySample(
                        value = systolic.toDouble(),
                        unit = SampleType.BloodPressureSystolic.unit,
                        startDate = point.startTime,
                        endDate = point.startTime,
                        dataPoint = point,
                    ),
                    diastolic = quantitySample(
                        value = diastolic.toDouble(),
                        unit = SampleType.BloodPressureDiastolic.unit,
                        startDate = point.startTime,
                        endDate = point.startTime,
                        dataPoint = point,
                    ),
                    pulse = null,
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
                        unit = SampleType.GlucoseConcentrationMilligramPerDecilitre.unit,
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
                        unit = SampleType.GlucoseConcentrationMilligramPerDecilitre.unit,
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

    override fun processHeartRateVariabilityRmssFromRecords(heartRateRecords: List<HealthDataPoint>): TimeSeriesData.QuantitySamples {
        return TimeSeriesData.QuantitySamples(IngestibleTimeseriesResource.HeartRateVariability, emptyList())
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
                sessions.map { session ->
                    val summary = recordAggregator.aggregateWorkoutSummary(session.startTime, session.endTime)

                    LocalWorkout(
                        id = point.uid,
                        startDate = session.startTime,
                        endDate = session.endTime,
                        sport = session.exerciseType.name.lowercase(),
                        calories = summary.caloriesBurned,
                        distance = summary.distanceMeter,
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
                        deviceModel = null,
                        sourceType = point.inferredSourceType,
                        metadata = emptyMap(),
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

    override suspend fun processBodyFromRecords(weightRecords: List<HealthDataPoint>, bodyFatRecords: List<HealthDataPoint>) = SummaryData.Body(
        bodyMass = weightRecords.mapNotNull { point ->
            val kg = point.getValue(DataType.BodyCompositionType.WEIGHT) ?: return@mapNotNull null
            quantitySample(
                value = kg.toDouble(),
                unit = SampleType.Weight.unit,
                startDate = point.startTime,
                endDate = point.startTime,
                dataPoint = point,
            )
        },
        bodyFatPercentage = bodyFatRecords.mapNotNull { point ->
            val bodyFat = point.getValue(DataType.BodyCompositionType.BODY_FAT) ?: return@mapNotNull null
            quantitySample(
                value = bodyFat.toDouble(),
                unit = SampleType.BodyFat.unit,
                startDate = point.startTime,
                endDate = point.startTime,
                dataPoint = point,
            )
        },
    )

    override suspend fun processSleepFromRecords(sleepSessionRecords: List<HealthDataPoint>): SummaryData.Sleeps {
        return SummaryData.Sleeps(processSleeps(sleepSessionRecords))
    }

    private suspend fun processSleeps(sleeps: List<HealthDataPoint>): List<LocalSleep> {
        return sleeps.filterForAcceptedSleepDataSources().flatMap { point ->
            val sessions = point.getValue(DataType.SleepType.SESSIONS) ?: emptyList<SleepSession>()
            sessions.map { sleepSession ->
                val statistics = recordAggregator.aggregateSleepSummary(sleepSession.startTime, sleepSession.endTime)
                val stages = sleepSession.stages ?: emptyList()

                LocalSleep(
                    id = point.uid,
                    startDate = sleepSession.startTime,
                    endDate = sleepSession.endTime,
                    sourceBundle = point.dataSource?.appId,
                    deviceModel = null,
                    metadata = emptyMap(),
                    sourceType = point.inferredSourceType,
                    heartRateMean = statistics.heartRateMean,
                    heartRateMaximum = statistics.heartRateMaximum,
                    heartRateMinimum = statistics.heartRateMinimum,
                    hrvMeanSdnn = statistics.hrvMeanSdnn,
                    respiratoryRateMean = statistics.respiratoryRateMean,
                    sleepStages = LocalSleep.Stages(
                        awakeSleepSamples = stages.filter { it.stage == DataType.SleepType.StageType.AWAKE }
                            .map { sleepStage -> stageSample(LocalSleep.Stage.Awake.id, sleepStage.startTime, sleepStage.endTime, point) },
                        deepSleepSamples = stages.filter { it.stage == DataType.SleepType.StageType.DEEP }
                            .map { sleepStage -> stageSample(LocalSleep.Stage.Deep.id, sleepStage.startTime, sleepStage.endTime, point) },
                        lightSleepSamples = stages.filter { it.stage == DataType.SleepType.StageType.LIGHT }
                            .map { sleepStage -> stageSample(LocalSleep.Stage.Light.id, sleepStage.startTime, sleepStage.endTime, point) },
                        remSleepSamples = stages.filter { it.stage == DataType.SleepType.StageType.REM }
                            .map { sleepStage -> stageSample(LocalSleep.Stage.Rem.id, sleepStage.startTime, sleepStage.endTime, point) },
                        unknownSleepSamples = stages.filter { it.stage == DataType.SleepType.StageType.UNDEFINED }
                            .map { sleepStage -> stageSample(LocalSleep.Stage.Unknown.id, sleepStage.startTime, sleepStage.endTime, point) },
                        outOfBedSleepSamples = emptyList(),
                    ),
                )
            }
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
                endDate = point.startTime,
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
                    startDate = session.endTime,
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
                endDate = point.startTime,
                dataPoint = point,
            )
        },
    )

    override suspend fun processOxygenSaturationRecords(oxygenSaturations: List<HealthDataPoint>) = TimeSeriesData.QuantitySamples(
        IngestibleTimeseriesResource.BloodOxygen,
        oxygenSaturations.flatMap { point ->
            val series = point.getValue(DataType.BloodOxygenType.SERIES_DATA) ?: emptyList<OxygenSaturation>()
            if (series.isNotEmpty()) {
                series.map {
                    quantitySample(
                        value = (it.oxygenSaturation / 100.0).toDouble(),
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
                        value = (value / 100.0).toDouble(),
                        unit = SampleType.OxygenSaturation.unit,
                        startDate = point.startTime,
                        endDate = point.startTime,
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
        return SummaryData.Meals(emptyList())
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

private fun List<HealthDataPoint>.filterForAcceptedSleepDataSources(): List<HealthDataPoint> {
    return filter { point ->
        SupportedSleepApps.values().any { it.packageName == point.dataSource?.appId }
    }
}

internal inline fun <R> quantitySamplesByDate(
    records: Iterable<R>,
    zoneId: ZoneId,
    timeSelector: (R) -> Instant,
    transform: (R) -> LocalQuantitySample,
): Map<LocalDate, List<LocalQuantitySample>> {
    return records.groupBy(
        keySelector = { timeSelector(it).atZone(zoneId).toLocalDate() },
        valueTransform = transform,
    )
}

internal fun merge(
    discovered: List<LocalQuantitySample>,
    hourlyTotals: List<LocalQuantitySample>,
    options: ProcessorOptions,
): List<LocalQuantitySample> {
    return if (options.perDeviceActivityTS) discovered + hourlyTotals else hourlyTotals
}
