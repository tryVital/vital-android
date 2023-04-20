package io.tryvital.vitalhealthconnect.records

import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_INT_TO_STRING_MAP
import io.tryvital.client.services.data.SampleType
import io.tryvital.vitalhealthconnect.SupportedSleepApps
import io.tryvital.vitalhealthconnect.ext.toDate
import io.tryvital.vitalhealthconnect.model.HCQuantitySample
import io.tryvital.vitalhealthconnect.model.processedresource.*
import java.time.Instant
import java.util.*
import kotlin.math.roundToInt

interface RecordProcessor {

    suspend fun processBloodPressureFromRecords(
        startDate: Instant,
        endDate: Instant,
        currentDevice: String,
        readBloodPressure: List<BloodPressureRecord>
    ): TimeSeriesData.BloodPressure

    suspend fun processGlucoseFromRecords(
        startDate: Instant,
        endDate: Instant,
        currentDevice: String,
        readBloodGlucose: List<BloodGlucoseRecord>
    ): TimeSeriesData.Glucose

    suspend fun processHeartRateFromRecords(
        startDate: Instant,
        endDate: Instant,
        currentDevice: String,
        heartRateRecords: List<HeartRateRecord>
    ): TimeSeriesData.HeartRate


    fun processHeartRateVariabilityRmssFromRecords(
        startDate: Instant,
        endDate: Instant,
        currentDevice: String,
        heartRateRecords: List<HeartRateVariabilityRmssdRecord>
    ): TimeSeriesData.HeartRateVariabilityRmssd

    fun processWaterFromRecords(
        startDate: Instant,
        endDate: Instant,
        currentDevice: String,
        readHydration: List<HydrationRecord>
    ): TimeSeriesData.Water

    suspend fun processBodyFromRecords(
        startTime: Instant,
        endTime: Instant,
        fallbackDeviceModel: String,
        weightRecords: List<WeightRecord>,
        bodyFatRecords: List<BodyFatRecord>,
    ): SummaryData.Body

    suspend fun processProfileFromRecords(
        startTime: Instant,
        endTime: Instant,
        heightRecords: List<HeightRecord>,
    ): SummaryData.Profile


    suspend fun processWorkoutsFromRecords(
        startTime: Instant,
        endTime: Instant,
        fallbackDeviceModel: String,
        exerciseRecords: List<ExerciseSessionRecord>
    ): SummaryData.Workouts

    suspend fun processSleepFromRecords(
        startTime: Instant,
        endTime: Instant,
        fallbackDeviceModel: String,
        sleepSessionRecords: List<SleepSessionRecord>,
        readSleepStages: List<SleepStageRecord>
    ): SummaryData.Sleeps

    suspend fun processActivitiesFromRecords(
        startTime: Instant,
        endTime: Instant,
        currentDevice: String,
        activeEnergyBurned: List<ActiveCaloriesBurnedRecord>,
        basalMetabolicRate: List<BasalMetabolicRateRecord>,
        stepsRate: List<StepsRecord>,
        distance: List<DistanceRecord>,
        floorsClimbed: List<FloorsClimbedRecord>,
        vo2Max: List<Vo2MaxRecord>,
    ): SummaryData.Activities
}

internal class HealthConnectRecordProcessor(
    private val recordReader: RecordReader,
    private val recordAggregator: RecordAggregator,
) : RecordProcessor {

    override suspend fun processBloodPressureFromRecords(
        startDate: Instant,
        endDate: Instant,
        currentDevice: String,
        readBloodPressure: List<BloodPressureRecord>
    ): TimeSeriesData.BloodPressure {
        return TimeSeriesData.BloodPressure(
            readBloodPressure.map {
                BloodPressureSample(
                    systolic = HCQuantitySample(
                        value = it.systolic.inMillimetersOfMercury.toString(),
                        unit = SampleType.BloodPressureSystolic.unit,
                        startDate = Date.from(it.time),
                        endDate = Date.from(it.time),
                        metadata = it.metadata,
                    ).toQuantitySample(currentDevice),
                    diastolic = HCQuantitySample(
                        value = it.diastolic.inMillimetersOfMercury.toString(),
                        unit = SampleType.BloodPressureDiastolic.unit,
                        startDate = Date.from(it.time),
                        endDate = Date.from(it.time),
                        metadata = it.metadata,
                    ).toQuantitySample(currentDevice),
                    pulse = null,
                )
            }
        )
    }

    override suspend fun processGlucoseFromRecords(
        startDate: Instant,
        endDate: Instant,
        currentDevice: String,
        readBloodGlucose: List<BloodGlucoseRecord>
    ): TimeSeriesData.Glucose {
        return TimeSeriesData.Glucose(
            readBloodGlucose.map {
                HCQuantitySample(
                    value = it.level.inMilligramsPerDeciliter.toString(),
                    unit = SampleType.GlucoseConcentrationMilligramPerDecilitre.unit,
                    startDate = Date.from(it.time),
                    endDate = Date.from(it.time),
                    metadata = it.metadata,
                ).toQuantitySample(currentDevice)
            })
    }

    override suspend fun processHeartRateFromRecords(
        startDate: Instant,
        endDate: Instant,
        currentDevice: String,
        heartRateRecords: List<HeartRateRecord>
    ): TimeSeriesData.HeartRate {
        return TimeSeriesData.HeartRate(
            mapHearthRate(heartRateRecords, currentDevice)
        )
    }

    override fun processHeartRateVariabilityRmssFromRecords(
        startDate: Instant,
        endDate: Instant,
        currentDevice: String,
        heartRateRecords: List<HeartRateVariabilityRmssdRecord>
    ): TimeSeriesData.HeartRateVariabilityRmssd {
        return TimeSeriesData.HeartRateVariabilityRmssd(
            heartRateRecords.map {
                HCQuantitySample(
                    value = it.heartRateVariabilityMillis.toString(),
                    unit = SampleType.HeartRateVariabilityRmssd.unit,
                    startDate = Date.from(it.time),
                    endDate = Date.from(it.time),
                    metadata = it.metadata,
                ).toQuantitySample(currentDevice)
            }
        )
    }


    override fun processWaterFromRecords(
        startDate: Instant,
        endDate: Instant,
        currentDevice: String,
        readHydration: List<HydrationRecord>
    ): TimeSeriesData.Water {
        return TimeSeriesData.Water(
            readHydration.map {
                HCQuantitySample(
                    value = it.volume.inMilliliters.toString(),
                    unit = SampleType.Water.unit,
                    startDate = Date.from(it.startTime),
                    endDate = Date.from(it.endTime),
                    metadata = it.metadata,
                ).toQuantitySample(currentDevice)
            }
        )
    }

    override suspend fun processWorkoutsFromRecords(
        startTime: Instant,
        endTime: Instant,
        fallbackDeviceModel: String,
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

                Workout(
                    id = exercise.metadata.id,
                    startDate = Date.from(exercise.startTime),
                    endDate = Date.from(exercise.endTime),
                    sourceBundle = exercise.metadata.dataOrigin.packageName,
                    sport = EXERCISE_TYPE_INT_TO_STRING_MAP[exercise.exerciseType] ?: "workout",
                    caloriesInKiloJules = summary.caloriesBurned,
                    distanceInMeter = summary.distance,
                    heartRate = mapHearthRate(
                        heartRateRecord,
                        fallbackDeviceModel
                    ),
                    respiratoryRate = mapRespiratoryRate(
                        respiratoryRateRecord,
                        fallbackDeviceModel
                    ),
                    deviceModel = fallbackDeviceModel
                )
            }
        )
    }

    override suspend fun processProfileFromRecords(
        startTime: Instant,
        endTime: Instant,
        heightRecords: List<HeightRecord>,
    ) =
        SummaryData.Profile(
            biologicalSex = "not_set", // this is not available in Health Connect
            dateOfBirth = Date(0), // this is not available in Health Connect
            heightInCm = (heightRecords.lastOrNull()?.height?.inMeters?.times(100))?.roundToInt()
                ?: 0,
        )

    override suspend fun processBodyFromRecords(
        startTime: Instant,
        endTime: Instant,
        fallbackDeviceModel: String,
        weightRecords: List<WeightRecord>,
        bodyFatRecords: List<BodyFatRecord>
    ) = SummaryData.Body(
        bodyMass = weightRecords.map {
            HCQuantitySample(
                value = it.weight.inKilograms.toString(),
                unit = SampleType.Weight.unit,
                startDate = Date.from(it.time),
                endDate = Date.from(it.time),
                metadata = it.metadata,
            ).toQuantitySample(fallbackDeviceModel)
        },
        bodyFatPercentage = bodyFatRecords.map {
            HCQuantitySample(
                value = it.percentage.value.toString(),
                unit = SampleType.BodyFat.unit,
                startDate = Date.from(it.time),
                endDate = Date.from(it.time),
                metadata = it.metadata,
            ).toQuantitySample(fallbackDeviceModel)
        }
    )

    override suspend fun processSleepFromRecords(
        startTime: Instant,
        endTime: Instant,
        fallbackDeviceModel: String,
        sleepSessionRecords: List<SleepSessionRecord>,
        readSleepStages: List<SleepStageRecord>
    ): SummaryData.Sleeps {
        return SummaryData.Sleeps(
            processSleeps(fallbackDeviceModel, sleepSessionRecords, readSleepStages)
        )
    }

    private suspend fun processSleeps(
        fallbackDeviceModel: String,
        sleeps: List<SleepSessionRecord>,
        sleepStages: List<SleepStageRecord>,
    ): List<Sleep> {
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

            Sleep(
                id = sleepSession.metadata.id,
                startDate = Date.from(sleepSession.startTime),
                endDate = Date.from(sleepSession.endTime),
                sourceBundle = sleepSession.metadata.dataOrigin.packageName,
                deviceModel = fallbackDeviceModel,
                heartRate = mapHearthRate(heartRateRecord, fallbackDeviceModel),
                restingHeartRate = mapRestingHearthRate(
                    restingHeartRateRecord,
                    fallbackDeviceModel
                ),
                respiratoryRate = mapRespiratoryRate(respiratoryRateRecord, fallbackDeviceModel),
                heartRateVariability = mapHeartRateVariabilityRmssdRecord(
                    readHeartRateVariabilityRmssdRecord,
                    fallbackDeviceModel
                ),
                oxygenSaturation = mapOxygenSaturationRecord(
                    oxygenSaturationRecord,
                    fallbackDeviceModel
                ),
                stages = SleepStages(
                    awakeSleepSamples = sleepStages.filter { it.stage == SleepStageRecord.STAGE_TYPE_AWAKE }
                        .map { sleepStage ->
                            QuantitySample(
                                id = sleepStage.metadata.id,
                                value = SleepStage.Awake.id.toString(),
                                unit = "stage",
                                startDate = Date.from(sleepSession.startTime),
                                endDate = Date.from(sleepSession.endTime),
                                sourceBundle = sleepStage.metadata.dataOrigin.packageName,
                                deviceModel = fallbackDeviceModel,
                            )
                        },
                    deepSleepSamples = sleepStages.filter { it.stage == SleepStageRecord.STAGE_TYPE_DEEP }
                        .map { sleepStage ->
                            QuantitySample(
                                id = sleepStage.metadata.id,
                                value = SleepStage.Deep.id.toString(),
                                unit = "stage",
                                startDate = Date.from(sleepSession.startTime),
                                endDate = Date.from(sleepSession.endTime),
                                sourceBundle = sleepStage.metadata.dataOrigin.packageName,
                                deviceModel = fallbackDeviceModel,
                            )
                        },
                    lightSleepSamples = sleepStages.filter { it.stage == SleepStageRecord.STAGE_TYPE_LIGHT }
                        .map { sleepStage ->
                            QuantitySample(
                                id = sleepStage.metadata.id,
                                value = SleepStage.Light.id.toString(),
                                unit = "stage",
                                startDate = Date.from(sleepSession.startTime),
                                endDate = Date.from(sleepSession.endTime),
                                sourceBundle = sleepStage.metadata.dataOrigin.packageName,
                                deviceModel = fallbackDeviceModel,
                            )
                        },
                    remSleepSamples = sleepStages.filter { it.stage == SleepStageRecord.STAGE_TYPE_REM }
                        .map { sleepStage ->
                            QuantitySample(
                                id = sleepStage.metadata.id,
                                value = SleepStage.Rem.id.toString(),
                                unit = "stage",
                                startDate = Date.from(sleepSession.startTime),
                                endDate = Date.from(sleepSession.endTime),
                                sourceBundle = sleepStage.metadata.dataOrigin.packageName,
                                deviceModel = fallbackDeviceModel,
                            )
                        },
                    unknownSleepSamples = sleepStages.filter { it.stage == SleepStageRecord.STAGE_TYPE_UNKNOWN }
                        .map { sleepStage ->
                            QuantitySample(
                                id = sleepStage.metadata.id,
                                value = SleepStage.Unknown.id.toString(),
                                unit = "stage",
                                startDate = Date.from(sleepSession.startTime),
                                endDate = Date.from(sleepSession.endTime),
                                sourceBundle = sleepStage.metadata.dataOrigin.packageName,
                                deviceModel = fallbackDeviceModel,
                            )
                        },
                    outOfBedSleepSamples = sleepStages.filter { it.stage == SleepStageRecord.STAGE_TYPE_OUT_OF_BED }
                        .map { sleepStage ->
                            QuantitySample(
                                id = sleepStage.metadata.id,
                                value = SleepStage.OutOfBed.id.toString(),
                                unit = "stage",
                                startDate = Date.from(sleepSession.startTime),
                                endDate = Date.from(sleepSession.endTime),
                                sourceBundle = sleepStage.metadata.dataOrigin.packageName,
                                deviceModel = fallbackDeviceModel,
                            )
                        },
                )
            )
        }
    }

    override suspend fun processActivitiesFromRecords(
        startTime: Instant,
        endTime: Instant,
        currentDevice: String,
        activeEnergyBurned: List<ActiveCaloriesBurnedRecord>,
        basalMetabolicRate: List<BasalMetabolicRateRecord>,
        stepsRate: List<StepsRecord>,
        distance: List<DistanceRecord>,
        floorsClimbed: List<FloorsClimbedRecord>,
        vo2Max: List<Vo2MaxRecord>
    ): SummaryData.Activities {
        return SummaryData.Activities(
            listOf(
                Activity(
                    activeEnergyBurned = activeEnergyBurned.map {
                        HCQuantitySample(
                            value = it.energy.inKilojoules.toString(),
                            unit = SampleType.ActiveCaloriesBurned.unit,
                            startDate = it.startTime.toDate(),
                            endDate = it.endTime.toDate(),
                            metadata = it.metadata,
                        ).toQuantitySample(currentDevice)
                    },
                    basalEnergyBurned = basalMetabolicRate.map {
                        HCQuantitySample(
                            value = (it.basalMetabolicRate.inWatts / 1000).toString(),
                            unit = SampleType.BasalMetabolicRate.unit,
                            startDate = it.time.toDate(),
                            endDate = it.time.toDate(),
                            metadata = it.metadata,
                        ).toQuantitySample(currentDevice)
                    },
                    steps = stepsRate.map {
                        HCQuantitySample(
                            value = it.count.toString(),
                            unit = SampleType.Steps.unit,
                            startDate = it.startTime.toDate(),
                            endDate = it.endTime.toDate(),
                            metadata = it.metadata,
                        ).toQuantitySample(currentDevice)

                    },
                    distanceWalkingRunning = distance.map {
                        HCQuantitySample(
                            value = it.distance.inMeters.toString(),
                            unit = SampleType.Distance.unit,
                            startDate = it.startTime.toDate(),
                            endDate = it.endTime.toDate(),
                            metadata = it.metadata,
                        ).toQuantitySample(currentDevice)
                    },
                    floorsClimbed = floorsClimbed.map {
                        HCQuantitySample(
                            value = it.floors.toString(),
                            unit = SampleType.FloorsClimbed.unit,
                            startDate = it.startTime.toDate(),
                            endDate = it.endTime.toDate(),
                            metadata = it.metadata,
                        ).toQuantitySample(currentDevice)
                    },
                    vo2Max = vo2Max.map {
                        HCQuantitySample(
                            value = it.vo2MillilitersPerMinuteKilogram.toString(),
                            unit = SampleType.Vo2Max.unit,
                            startDate = it.time.toDate(),
                            endDate = it.time.toDate(),
                            metadata = it.metadata,
                        ).toQuantitySample(currentDevice)
                    },
                )
            )
        )
    }


    private fun mapOxygenSaturationRecord(
        oxygenSaturationRecords: List<OxygenSaturationRecord>,
        fallbackDeviceModel: String
    ): List<QuantitySample> {
        return oxygenSaturationRecords.map {
            HCQuantitySample(
                value = it.percentage.value.toString(),
                unit = SampleType.OxygenSaturation.unit,
                startDate = Date.from(it.time),
                endDate = Date.from(it.time),
                metadata = it.metadata,
            ).toQuantitySample(fallbackDeviceModel)
        }
    }

    private fun mapHeartRateVariabilityRmssdRecord(
        readHeartRateVariabilityRmssdRecords: List<HeartRateVariabilityRmssdRecord>,
        fallbackDeviceModel: String
    ): List<QuantitySample> {
        return readHeartRateVariabilityRmssdRecords.map {
            HCQuantitySample(
                value = it.heartRateVariabilityMillis.toString(),
                unit = SampleType.HeartRateVariabilityRmssd.unit,
                startDate = it.time.toDate(),
                endDate = it.time.toDate(),
                metadata = it.metadata,
            ).toQuantitySample(fallbackDeviceModel)
        }
    }

    private fun mapRespiratoryRate(
        respiratoryRateRecords: List<RespiratoryRateRecord>,
        fallbackDeviceModel: String
    ): List<QuantitySample> {
        return respiratoryRateRecords.map {
            HCQuantitySample(
                value = it.rate.toString(),
                unit = SampleType.RespiratoryRate.unit,
                startDate = it.time.toDate(),
                endDate = it.time.toDate(),
                metadata = it.metadata,
            ).toQuantitySample(fallbackDeviceModel)

        }
    }

    private fun mapHearthRate(
        heartRateRecords: List<HeartRateRecord>,
        fallbackDeviceModel: String
    ): List<QuantitySample> {
        return heartRateRecords.map { heartRateRecord ->
            heartRateRecord.samples.windowed(5)
                .map {
                    val averagedSample =
                        it.fold(0L) { acc, sample -> acc + sample.beatsPerMinute } / it.size

                    HCQuantitySample(
                        value = averagedSample.toString(),
                        unit = SampleType.HeartRate.unit,
                        startDate = it.first().time.toDate(),
                        endDate = it.last().time.toDate(),
                        metadata = heartRateRecord.metadata,
                    ).toQuantitySample(fallbackDeviceModel)
                }
        }.flatten()
    }

    private fun mapRestingHearthRate(
        heartRateRecords: List<RestingHeartRateRecord>,
        fallbackDeviceModel: String
    ): List<QuantitySample> {
        return heartRateRecords.map {
            HCQuantitySample(
                value = it.beatsPerMinute.toString(),
                unit = SampleType.HeartRate.unit,
                startDate = it.time.toDate(),
                endDate = it.time.toDate(),
                metadata = it.metadata,
            ).toQuantitySample(fallbackDeviceModel)
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
