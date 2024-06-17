package io.tryvital.vitalhealthconnect.records

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
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
import io.tryvital.client.services.data.IngestibleTimeseriesResource
import io.tryvital.client.services.data.LocalActivity
import io.tryvital.client.services.data.LocalBloodPressureSample
import io.tryvital.client.services.data.LocalQuantitySample
import io.tryvital.client.services.data.LocalSleep
import io.tryvital.client.services.data.LocalWorkout
import io.tryvital.client.services.data.SampleType
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalhealthconnect.SupportedSleepApps
import io.tryvital.vitalhealthconnect.model.processedresource.SummaryData
import io.tryvital.vitalhealthconnect.model.processedresource.TimeSeriesData
import io.tryvital.vitalhealthconnect.model.quantitySample
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.TimeZone
import kotlin.math.roundToInt

data class ProcessorOptions(
    val perDeviceActivityTS: Boolean = false
)

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

    suspend fun processActivitiesFromRecords(
        timeZone: TimeZone,
        activeEnergyBurned: List<ActiveCaloriesBurnedRecord>,
        basalMetabolicRate: List<BasalMetabolicRateRecord>,
        steps: List<StepsRecord>,
        distance: List<DistanceRecord>,
        floorsClimbed: List<FloorsClimbedRecord>,
        vo2Max: List<Vo2MaxRecord>,
        options: ProcessorOptions,
    ): SummaryData.Activities
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
                    recordAggregator.aggregateWorkoutSummary(exercise.startTime, exercise.endTime)
                val heartRateRecord =
                    recordReader.readHeartRate(exercise.startTime, exercise.endTime)
                val respiratoryRateRecord =
                    recordReader.readRespiratoryRate(exercise.startTime, exercise.endTime)

                LocalWorkout(
                    id = exercise.metadata.id,
                    startDate = exercise.startTime,
                    endDate = exercise.endTime,
                    sport = EXERCISE_TYPE_INT_TO_STRING_MAP[exercise.exerciseType] ?: "workout",
                    caloriesInKiloJules = summary.caloriesBurned ?: 0.0,
                    distanceInMeter = summary.distance ?: 0.0,
                    heartRate = mapHearthRate(
                        heartRateRecord,
                    ),
                    respiratoryRate = mapRespiratoryRate(
                        respiratoryRateRecord,
                    ),
                    sourceBundle = exercise.metadata.dataOrigin.packageName,
                    deviceModel = exercise.metadata.device?.model
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
            val heartRateRecord =
                recordReader.readHeartRate(sleepSession.startTime, sleepSession.endTime)
            val restingHeartRateRecord =
                recordReader.readRestingHeartRate(sleepSession.startTime, sleepSession.endTime)
            val respiratoryRateRecord =
                recordReader.readRespiratoryRate(sleepSession.startTime, sleepSession.endTime)
            val readHeartRateVariabilityRmssdRecord =
                recordReader.readHeartRateVariabilityRmssd(
                    sleepSession.startTime,
                    sleepSession.endTime
                )
            val oxygenSaturationRecord =
                recordReader.readOxygenSaturation(sleepSession.startTime, sleepSession.endTime)

            LocalSleep(
                id = sleepSession.metadata.id,
                startDate = sleepSession.startTime,
                endDate = sleepSession.endTime,
                sourceBundle = sleepSession.metadata.dataOrigin.packageName,
                deviceModel = sleepSession.metadata.device?.model,
                heartRate = mapHearthRate(heartRateRecord),
                restingHeartRate = mapRestingHearthRate(
                    restingHeartRateRecord,
                ),
                respiratoryRate = mapRespiratoryRate(respiratoryRateRecord),
                heartRateVariability = mapHeartRateVariabilityRmssdRecord(
                    readHeartRateVariabilityRmssdRecord,
                ),
                oxygenSaturation = mapOxygenSaturationRecord(
                    oxygenSaturationRecord,
                ),
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

    override suspend fun processActivitiesFromRecords(
        timeZone: TimeZone,
        activeEnergyBurned: List<ActiveCaloriesBurnedRecord>,
        basalMetabolicRate: List<BasalMetabolicRateRecord>,
        steps: List<StepsRecord>,
        distance: List<DistanceRecord>,
        floorsClimbed: List<FloorsClimbedRecord>,
        vo2Max: List<Vo2MaxRecord>,
        options: ProcessorOptions,
    ): SummaryData.Activities = coroutineScope {
        val zoneId = timeZone.toZoneId()

        val activeEnergyBurnedByDate = quantitySamplesByDate(activeEnergyBurned, zoneId, { it.startTime }) {
            quantitySample(
                value = it.energy.inKilocalories,
                unit = SampleType.ActiveCaloriesBurned.unit,
                startDate = it.startTime,
                endDate = it.endTime,
                metadata = it.metadata,
            )
        }
        val basalMetabolicRateByDate = quantitySamplesByDate(basalMetabolicRate, zoneId, { it.time }) {
            quantitySample(
                value = it.basalMetabolicRate.inKilocaloriesPerDay,
                unit = SampleType.BasalMetabolicRate.unit,
                startDate = it.time,
                endDate = it.time,
                metadata = it.metadata,
            )
        }
        val distanceByDate = quantitySamplesByDate(distance, zoneId, { it.startTime }) {
            quantitySample(
                value = it.distance.inMeters,
                unit = SampleType.Distance.unit,
                startDate = it.startTime,
                endDate = it.endTime,
                metadata = it.metadata,
            )
        }
        val floorsClimbedByDate = quantitySamplesByDate(floorsClimbed, zoneId, { it.startTime }) {
            quantitySample(
                value = it.floors,
                unit = SampleType.FloorsClimbed.unit,
                startDate = it.startTime,
                endDate = it.endTime,
                metadata = it.metadata,
            )
        }
        val stepsByDate = quantitySamplesByDate(steps, zoneId, { it.startTime }) {
            quantitySample(
                value = it.count.toDouble(),
                unit = SampleType.Steps.unit,
                startDate = it.startTime,
                endDate = it.endTime,
                metadata = it.metadata,
            )

        }
        val vo2MaxByDate = quantitySamplesByDate(vo2Max, zoneId, { it.time }) {
            quantitySample(
                value = it.vo2MillilitersPerMinuteKilogram,
                unit = SampleType.Vo2Max.unit,
                startDate = it.time,
                endDate = it.time,
                metadata = it.metadata,
            )
        }

        val startDate = listOfNotNull(
            activeEnergyBurnedByDate.keys.minOrNull(),
            basalMetabolicRateByDate.keys.minOrNull(),
            floorsClimbedByDate.keys.minOrNull(),
            distanceByDate.keys.minOrNull(),
            stepsByDate.keys.minOrNull(),
            vo2MaxByDate.keys.minOrNull(),
        ).minOrNull()
        val endDate = listOfNotNull(
            activeEnergyBurnedByDate.keys.maxOrNull(),
            basalMetabolicRateByDate.keys.maxOrNull(),
            floorsClimbedByDate.keys.maxOrNull(),
            distanceByDate.keys.maxOrNull(),
            stepsByDate.keys.maxOrNull(),
            vo2MaxByDate.keys.maxOrNull()
        ).maxOrNull()

        if (startDate != null && endDate != null) {
            assert(startDate <= endDate)

            // between is start-inclusive, end-exclusive
            val numberOfDays = ChronoUnit.DAYS.between(startDate, endDate.plusDays(1)).toInt()
            VitalLogger.getOrCreate().info { "activity: summarizing $numberOfDays ($startDate ... $endDate)" }

            val summaryAggregators = Array(numberOfDays) { offset ->
                async {
                    val date = startDate.plusDays(offset.toLong())
                    val summary = recordAggregator.aggregateActivityDaySummary(
                        date = date,
                        timeZone = timeZone
                    )
                    return@async date to summary.toDatedPayload(date)
                }
            }
            val daySummariesByDate = awaitAll(*summaryAggregators)

            fun merge(
                discovered: Map<LocalDate, List<LocalQuantitySample>>,
                hourlyTotals: Map<LocalDate, List<LocalQuantitySample>>,
                date: LocalDate,
                options: ProcessorOptions,
            ): List<LocalQuantitySample> {
                return if (options.perDeviceActivityTS) {
                    (discovered[date] ?: emptyList()) + (hourlyTotals[date] ?: emptyList())
                } else {
                    hourlyTotals[date] ?: emptyList()
                }
            }

            // TODO: On-device computed hourly totals

            val activities = daySummariesByDate.map { (date, summary) ->
                LocalActivity(
                    daySummary = summary,
                    activeEnergyBurned = merge(activeEnergyBurnedByDate, emptyMap(), date, options),
                    basalEnergyBurned = merge(basalMetabolicRateByDate, emptyMap(), date, options),
                    distanceWalkingRunning = merge(distanceByDate, emptyMap(), date, options),
                    floorsClimbed = merge(floorsClimbedByDate, emptyMap(), date, options),
                    steps = merge(stepsByDate, emptyMap(), date, options),
                    vo2Max = vo2MaxByDate[date] ?: emptyList(),
                )
            }

            SummaryData.Activities(activities = activities)
        } else {
            SummaryData.Activities(activities = emptyList())
        }
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

inline fun <R: Record> quantitySamplesByDate(
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
