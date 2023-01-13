package io.tryvital.vitalhealthconnect.records

import android.annotation.SuppressLint
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_INT_TO_STRING_MAP
import io.tryvital.client.VitalClient
import io.tryvital.client.services.data.*
import io.tryvital.vitalhealthconnect.ext.dayStart
import io.tryvital.vitalhealthconnect.ext.toDate
import io.tryvital.vitalhealthconnect.model.HCQuantitySample
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.roundToInt

interface RecordProcessor {

    suspend fun processWorkoutsFromTimeRange(
        startTime: Instant,
        endTime: Instant,
        fallbackDeviceModel: String,
    ): List<WorkoutPayload>

    suspend fun processWorkoutsFromRecords(
        startTime: Instant,
        endTime: Instant,
        fallbackDeviceModel: String,
        exerciseRecords: List<ExerciseSessionRecord>
    ): List<WorkoutPayload>

    suspend fun processProfileFromTimeRange(
        startTime: Instant,
        endTime: Instant,
    ): ProfilePayload

    suspend fun processProfileFromRecords(
        startTime: Instant,
        endTime: Instant,
        heightRecord: HeightRecord,
    ): ProfilePayload

    suspend fun processBodyFromTimeRange(
        startTime: Instant,
        endTime: Instant,
        fallbackDeviceModel: String,
    ): BodyPayload

    suspend fun processBodyFromRecords(
        startTime: Instant,
        endTime: Instant,
        fallbackDeviceModel: String,
        weightRecords: List<WeightRecord>,
        bodyFatRecords: List<BodyFatRecord>,
    ): BodyPayload

    suspend fun processSleepFromTimeRange(
        startTime: Instant,
        endTime: Instant,
        fallbackDeviceModel: String,
    ): List<SleepPayload>

    suspend fun processSleepFromRecords(
        startTime: Instant,
        endTime: Instant,
        fallbackDeviceModel: String,
        sleepSessionRecords: List<SleepSessionRecord>
    ): List<SleepPayload>

    suspend fun processActivitiesFromTimeRange(
        startTime: Instant,
        endTime: Instant,
        currentDevice: String,
    ): List<ActivityPayload>

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
    ): List<ActivityPayload>
}

internal class HealthConnectRecordProcessor(
    private val recordReader: RecordReader,
    private val recordAggregator: RecordAggregator,
    private val vitalClient: VitalClient
) :
    RecordProcessor {

    override suspend fun processWorkoutsFromTimeRange(
        startTime: Instant,
        endTime: Instant,
        fallbackDeviceModel: String,
    ) = processWorkout(
        fallbackDeviceModel,
        recordReader.readExerciseSessions(startTime, endTime),
    )

    override suspend fun processWorkoutsFromRecords(
        startTime: Instant,
        endTime: Instant,
        fallbackDeviceModel: String,
        exerciseRecords: List<ExerciseSessionRecord>
    ) = processWorkout(fallbackDeviceModel, exerciseRecords)

    private suspend fun processWorkout(
        fallbackDeviceModel: String,
        exercises: List<ExerciseSessionRecord>,
    ): List<WorkoutPayload> {
        vitalClient.vitalLogger.logI("Found ${exercises.size} workouts")

        return exercises.map { exercise ->
            val aggregatedDistance =
                recordAggregator.aggregateDistance(exercise.startTime, exercise.endTime)
            val aggregatedActiveCaloriesBurned =
                recordAggregator.aggregateActiveEnergyBurned(exercise.startTime, exercise.endTime)
            val heartRateRecord = recordReader.readHeartRate(exercise.startTime, exercise.endTime)
            val respiratoryRateRecord =
                recordReader.readRespiratoryRate(exercise.startTime, exercise.endTime)

            WorkoutPayload(
                id = exercise.metadata.id,
                startDate = Date.from(exercise.startTime),
                endDate = Date.from(exercise.endTime),
                sourceBundle = exercise.metadata.dataOrigin.packageName,
                sport = EXERCISE_TYPE_INT_TO_STRING_MAP[exercise.exerciseType] ?: "workout",
                caloriesInKiloJules = aggregatedActiveCaloriesBurned,
                distanceInMeter = aggregatedDistance,
                heartRate = mapHearthRate(heartRateRecord, fallbackDeviceModel),
                respiratoryRate = mapRespiratoryRate(respiratoryRateRecord, fallbackDeviceModel),
                deviceModel = fallbackDeviceModel
            )
        }
    }


    override suspend fun processProfileFromTimeRange(
        startTime: Instant,
        endTime: Instant,
    ) = processProfile(recordReader.readHeights(startTime, endTime).lastOrNull())

    override suspend fun processProfileFromRecords(
        startTime: Instant,
        endTime: Instant,
        heightRecord: HeightRecord
    ) = processProfile(heightRecord)

    private fun processProfile(height: HeightRecord?) =
        ProfilePayload(
            biologicalSex = "not_set", // this is not available in Health Connect
            dateOfBirth = Date(0), // this is not available in Health Connect
            heightInCm = (height?.height?.inMeters?.times(100))?.roundToInt() ?: 0
        )

    override suspend fun processBodyFromTimeRange(
        startTime: Instant,
        endTime: Instant,
        fallbackDeviceModel: String,
    ) = processBody(
        fallbackDeviceModel,
        recordReader.readWeights(startTime, endTime),
        recordReader.readBodyFat(startTime, endTime)
    )

    override suspend fun processBodyFromRecords(
        startTime: Instant,
        endTime: Instant,
        fallbackDeviceModel: String,
        weightRecords: List<WeightRecord>,
        bodyFatRecords: List<BodyFatRecord>
    ) = processBody(fallbackDeviceModel, weightRecords, bodyFatRecords)

    private fun processBody(
        fallbackDeviceModel: String,
        weights: List<WeightRecord>,
        bodyFat: List<BodyFatRecord>
    ) = BodyPayload(
        bodyMass = weights.map {
            HCQuantitySample(
                value = it.weight.inKilograms.toString(),
                unit = SampleType.Weight.unit,
                startDate = Date.from(it.time),
                endDate = Date.from(it.time),
                metadata = it.metadata,
            ).toQuantitySample(fallbackDeviceModel)
        },
        bodyFatPercentage = bodyFat.map {
            HCQuantitySample(
                value = it.percentage.value.toString(),
                unit = SampleType.BodyFat.unit,
                startDate = Date.from(it.time),
                endDate = Date.from(it.time),
                metadata = it.metadata,
            ).toQuantitySample(fallbackDeviceModel)
        }
    )

    override suspend fun processSleepFromTimeRange(
        startTime: Instant,
        endTime: Instant,
        fallbackDeviceModel: String,
    ) =
        processSleep(fallbackDeviceModel, recordReader.readSleepSession(startTime, endTime))

    override suspend fun processSleepFromRecords(
        startTime: Instant,
        endTime: Instant,
        fallbackDeviceModel: String,
        sleepSessionRecords: List<SleepSessionRecord>
    ) = processSleep(fallbackDeviceModel, sleepSessionRecords)

    private suspend fun processSleep(
        fallbackDeviceModel: String,
        sleeps: List<SleepSessionRecord>,
    ): List<SleepPayload> {
        vitalClient.vitalLogger.logI("Found ${sleeps.size} sleepSessions")

        return sleeps.map { sleepSession ->
            val heartRateRecord =
                recordReader.readHeartRate(sleepSession.startTime, sleepSession.endTime)
            val restingHeartRateRecord =
                recordReader.readRestingHeartRate(sleepSession.startTime, sleepSession.endTime)
            val respiratoryRateRecord =
                recordReader.readRespiratoryRate(sleepSession.startTime, sleepSession.endTime)
            val readHeartRateVariabilitySdnnRecord =
                recordReader.readHeartRateVariabilitySdnn(
                    sleepSession.startTime,
                    sleepSession.endTime
                )
            val oxygenSaturationRecord =
                recordReader.readOxygenSaturation(sleepSession.startTime, sleepSession.endTime)

            SleepPayload(
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
                heartRateVariability = mapHeartRateVariabilitySdnnRecord(
                    readHeartRateVariabilitySdnnRecord,
                    fallbackDeviceModel
                ),
                oxygenSaturation = mapOxygenSaturationRecord(
                    oxygenSaturationRecord,
                    fallbackDeviceModel
                ),
            )
        }
    }

    override suspend fun processActivitiesFromTimeRange(
        startTime: Instant,
        endTime: Instant,
        currentDevice: String,
    ): List<ActivityPayload> {
        val activities = mutableListOf<ActivityPayload>()
        var rangeStart = startTime
        var rangeEnd = nextDayOrRangeEnd(rangeStart, endTime)

        while (rangeStart < rangeEnd) {
            val activeEnergyBurned = recordReader.readActiveEnergyBurned(startTime, endTime)
            val basalMetabolicRate = recordReader.readBasalMetabolicRate(startTime, endTime)
            val stepsRate = recordReader.readSteps(startTime, endTime)
            val distance = recordReader.readDistance(startTime, endTime)
            val floorsClimbed = recordReader.readFloorsClimbed(startTime, endTime)
            val vo2Max = recordReader.readVo2Max(startTime, endTime)

            activities.add(
                processActivityPayload(
                    activeEnergyBurned,
                    currentDevice,
                    basalMetabolicRate,
                    stepsRate,
                    distance,
                    floorsClimbed,
                    vo2Max
                )
            )

            rangeStart = rangeEnd
            rangeEnd = nextDayOrRangeEnd(rangeStart, endTime)
        }

        return activities
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
    ) = listOf(
        processActivityPayload(
            activeEnergyBurned,
            currentDevice,
            basalMetabolicRate,
            stepsRate,
            distance,
            floorsClimbed,
            vo2Max
        )
    )

    private fun processActivityPayload(
        activeEnergyBurned: List<ActiveCaloriesBurnedRecord>,
        currentDevice: String,
        basalMetabolicRate: List<BasalMetabolicRateRecord>,
        stepsRate: List<StepsRecord>,
        distance: List<DistanceRecord>,
        floorsClimbed: List<FloorsClimbedRecord>,
        vo2Max: List<Vo2MaxRecord>
    ) = ActivityPayload(
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

    /**
    HeartRateVariabilitySdnnRecord is marked as RestrictedApi. The plugin is still alpha therefor
    We assume it's a mistake, if later this stays the same we have to move to a different
    hearth rate.
     */
    @SuppressLint("RestrictedApi")
    private fun mapHeartRateVariabilitySdnnRecord(
        readHeartRateVariabilitySdnnRecords: List<HeartRateVariabilitySdnnRecord>,
        fallbackDeviceModel: String
    ): List<QuantitySample> {
        return readHeartRateVariabilitySdnnRecords.map {
            HCQuantitySample(
                value = it.heartRateVariabilityMillis.toString(),
                unit = SampleType.HeartRateVariabilitySdnn.unit,
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

private fun nextDayOrRangeEnd(rangeStart: Instant, endTime: Instant) =
    if (rangeStart.dayStart == endTime.dayStart) {
        endTime
    } else {
        rangeStart.dayStart.plus(1, ChronoUnit.DAYS)
    }

