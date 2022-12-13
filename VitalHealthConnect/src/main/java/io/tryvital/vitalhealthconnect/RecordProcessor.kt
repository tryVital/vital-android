package io.tryvital.vitalhealthconnect

import android.annotation.SuppressLint
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_INT_TO_STRING_MAP
import androidx.health.connect.client.records.metadata.Device
import io.tryvital.client.VitalClient
import io.tryvital.client.services.data.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.roundToInt

interface RecordProcessor {

    suspend fun processWorkouts(
        startTime: Instant,
        endTime: Instant,
        fallbackDeviceModel: String
    ): List<WorkoutPayload>

    suspend fun processProfile(
        startTime: Instant,
        endTime: Instant,
    ): ProfilePayload

    suspend fun processBody(
        startTime: Instant,
        endTime: Instant,
        fallbackDeviceModel: String
    ): BodyPayload

    suspend fun processSleep(
        startTime: Instant,
        endTime: Instant,
        fallbackDeviceModel: String
    ): List<SleepPayload>

    suspend fun processActivities(
        startTime: Instant,
        endTime: Instant,
        currentDevice: String,
        hostTimeZone: TimeZone
    ): List<ActivityPayload>
}

internal class HealthConnectRecordProcessor(
    private val recordReader: RecordReader,
    private val recordAggregator: RecordAggregator,
    private val vitalClient: VitalClient
) :
    RecordProcessor {

    override suspend fun processWorkouts(
        startTime: Instant,
        endTime: Instant,
        fallbackDeviceModel: String
    ): List<WorkoutPayload> {
        val exercises = recordReader.readExerciseSessions(startTime, endTime)

        vitalClient.vitalLogger.logI("Found ${exercises.size} workouts")

        return exercises.map { exercise ->
            val aggregatedDistance = recordAggregator.aggregateDistance(startTime, endTime)
            val aggregatedActiveCaloriesBurned =
                recordAggregator.aggregateActiveEnergyBurned(startTime, endTime)
            val heartRateRecord = recordReader.readHeartRate(startTime, endTime)
            val respiratoryRateRecord = recordReader.readRespiratoryRate(startTime, endTime)

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

    override suspend fun processProfile(
        startTime: Instant,
        endTime: Instant,
    ): ProfilePayload {
        val height = recordReader.readHeights(startTime, endTime)

        return ProfilePayload(
            biologicalSex = "not_set", // this is not available in Health Connect
            dateOfBirth = Date(0), // this is not available in Health Connect
            heightInCm = (height.lastOrNull()?.height?.inMeters?.times(100))?.roundToInt() ?: 0
        )
    }

    override suspend fun processBody(
        startTime: Instant,
        endTime: Instant,
        fallbackDeviceModel: String
    ): BodyPayload {
        val weights = recordReader.readWeights(startTime, endTime)
        val bodyFat = recordReader.readBodyFat(startTime, endTime)

        return BodyPayload(
            bodyMass = weights.map {
                QuantitySample(
                    id = "weight-" + it.time,
                    value = it.weight.inKilograms.toString(),
                    unit = SampleType.Weight.unit,
                    startDate = Date.from(it.time),
                    endDate = Date.from(it.time),
                    sourceBundle = it.metadata.dataOrigin.packageName,
                    deviceModel = it.metadata.device?.model ?: fallbackDeviceModel,
                )
            },
            bodyFatPercentage = bodyFat.map {
                QuantitySample(
                    id = "bodyFat-" + it.time,
                    value = it.percentage.toString(),
                    unit = SampleType.BodyFat.unit,
                    startDate = Date.from(it.time),
                    endDate = Date.from(it.time),
                    sourceBundle = it.metadata.dataOrigin.packageName,
                    deviceModel = it.metadata.device?.model ?: fallbackDeviceModel,
                )
            }
        )
    }

    override suspend fun processSleep(
        startTime: Instant,
        endTime: Instant,
        fallbackDeviceModel: String
    ): List<SleepPayload> {
        val sleepSessions = recordReader.readSleepSession(startTime, endTime)

        vitalClient.vitalLogger.logI("Found ${sleepSessions.size} sleepSessions")

        return sleepSessions.map { sleepSession ->
            val heartRateRecord = recordReader.readHeartRate(startTime, endTime)
            val restingHeartRateRecord = recordReader.readRestingHeartRate(startTime, endTime)
            val respiratoryRateRecord = recordReader.readRespiratoryRate(startTime, endTime)
            val readHeartRateVariabilitySdnnRecord =
                recordReader.readHeartRateVariabilitySdnn(startTime, endTime)
            val oxygenSaturationRecord = recordReader.readOxygenSaturation(startTime, endTime)

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

    override suspend fun processActivities(
        startTime: Instant,
        endTime: Instant,
        currentDevice: String,
        hostTimeZone: TimeZone //TODO the day starts at a different time for everybody
    ): List<ActivityPayload> {
        val activities = mutableListOf<ActivityPayload>()
        var rangeStart = startTime
        var rangeEnd = nextDayOrRangeEnd(rangeStart, endTime)

        while (rangeStart < rangeEnd) {
            vitalClient.vitalLogger.logI(rangeStart.toString())
            val activeEnergyBurned = recordReader.readActiveEnergyBurned(startTime, endTime)
            val basalMetabolicRate = recordReader.readBasalMetabolicRate(startTime, endTime)
            val stepsRate = recordReader.readSteps(startTime, endTime)
            val distance = recordReader.readDistance(startTime, endTime)
            val floorsClimbed = recordReader.readFloorsClimbed(startTime, endTime)
            val vo2Max = recordReader.readVo2Max(startTime, endTime)

            activities.add(
                ActivityPayload(
                    activeEnergyBurned = activeEnergyBurned.map {
                        QuantitySample(
                            id = "activeEnergyBurned-" + it.startTime,
                            value = it.energy.inKilojoules.toString(),
                            unit = SampleType.ActiveCaloriesBurned.unit,
                            startDate = Date.from(it.startTime),
                            endDate = Date.from(it.endTime),
                        )
                    },
                    basalEnergyBurned = basalMetabolicRate.map {
                        QuantitySample(
                            id = "basalMetabolicRate-" + it.time,
                            value = (it.basalMetabolicRate.inWatts / 1000).toString(),
                            unit = SampleType.BasalMetabolicRate.unit,
                            startDate = Date.from(it.time),
                            endDate = Date.from(it.time),
                        )
                    },
                    steps = stepsRate.map {
                        QuantitySample(
                            id = "steps-" + it.startTime,
                            value = it.count.toString(),
                            unit = SampleType.Steps.unit,
                            startDate = Date.from(it.startTime),
                            endDate = Date.from(it.endTime),
                        )

                    },
                    distanceWalkingRunning = distance.map {
                        QuantitySample(
                            id = "distance-" + it.startTime,
                            value = it.distance.inMeters.toString(),
                            unit = SampleType.Distance.unit,
                            startDate = Date.from(it.startTime),
                            endDate = Date.from(it.endTime),
                        )
                    },
                    floorsClimbed = floorsClimbed.map {
                        QuantitySample(
                            id = "floorsClimbed-" + it.startTime,
                            value = it.floors.toString(),
                            unit = SampleType.FloorsClimbed.unit,
                            startDate = Date.from(it.startTime),
                            endDate = Date.from(it.endTime),
                        )
                    },
                    vo2Max = vo2Max.map {
                        QuantitySample(
                            id = "vo2Max-" + it.time,
                            value = it.vo2MillilitersPerMinuteKilogram.toString(),
                            unit = SampleType.Vo2Max.unit,
                            startDate = Date.from(it.time),
                            endDate = Date.from(it.time),
                        )
                    },
                )
            )



            rangeStart = rangeEnd
            rangeEnd = nextDayOrRangeEnd(rangeStart, endTime)
        }

        return activities
    }

    private fun mapOxygenSaturationRecord(
        oxygenSaturationRecords: List<OxygenSaturationRecord>,
        fallbackDeviceModel: String
    ): List<QuantitySample> {
        return oxygenSaturationRecords.map {
            QuantitySample(
                id = "oxygenSaturation-" + it.time,
                value = it.percentage.toString(),
                unit = SampleType.OxygenSaturation.unit,
                startDate = Date.from(it.time),
                endDate = Date.from(it.time),
                sourceBundle = it.metadata.dataOrigin.packageName,
                deviceModel = it.metadata.device?.model ?: fallbackDeviceModel,
            )
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
            QuantitySample(
                id = "readHeartRateVariabilitySdnn-" + it.time,
                value = it.heartRateVariabilityMillis.toString(),
                unit = SampleType.HeartRateVariabilitySdnn.unit,
                startDate = Date.from(it.time),
                endDate = Date.from(it.time),
                sourceBundle = it.metadata.dataOrigin.packageName,
                deviceModel = it.metadata.device?.model ?: fallbackDeviceModel,
            )
        }
    }

    private fun mapRespiratoryRate(
        respiratoryRateRecords: List<RespiratoryRateRecord>,
        fallbackDeviceModel: String
    ): List<QuantitySample> {
        return respiratoryRateRecords.map {
            QuantitySample(
                id = "respiratoryRate-" + it.time,
                value = it.rate.toString(),
                unit = SampleType.RespiratoryRate.unit,
                startDate = Date.from(it.time),
                endDate = Date.from(it.time),
                sourceBundle = it.metadata.dataOrigin.packageName,
                deviceModel = it.metadata.device?.model ?: fallbackDeviceModel,
            )

        }
    }

    private fun mapHearthRate(
        heartRateRecords: List<HeartRateRecord>,
        fallbackDeviceModel: String
    ): List<QuantitySample> {
        return heartRateRecords.map { heartRateRecord ->
            heartRateRecord.samples
                .map {
                    QuantitySample(
                        id = "heartRate-" + it.time,
                        value = it.beatsPerMinute.toString(),
                        unit = SampleType.HeartRate.unit,
                        startDate = Date.from(it.time),
                        endDate = Date.from(it.time),
                        sourceBundle = heartRateRecord.metadata.dataOrigin.packageName,
                        deviceModel = heartRateRecord.metadata.device?.type.toQuantitySampleDeviceModel()
                            ?: fallbackDeviceModel,
                    )
                }
        }.flatten()
    }

    private fun mapRestingHearthRate(
        heartRateRecords: List<RestingHeartRateRecord>,
        fallbackDeviceModel: String
    ): List<QuantitySample> {
        return heartRateRecords.map {
            QuantitySample(
                id = "restingHeartRate-" + it.time,
                value = it.beatsPerMinute.toString(),
                unit = SampleType.HeartRate.unit,
                startDate = Date.from(it.time),
                endDate = Date.from(it.time),
                sourceBundle = it.metadata.dataOrigin.packageName,
                deviceModel = it.metadata.device?.type?.toQuantitySampleDeviceModel()
                    ?: fallbackDeviceModel,
            )
        }
    }
}

private fun Int?.toQuantitySampleDeviceModel(): String? {
    return when (this) {
        Device.TYPE_WATCH -> "watch"
        Device.TYPE_PHONE -> "phone"
        Device.TYPE_SCALE -> "scale"
        Device.TYPE_RING -> "ring"
        Device.TYPE_HEAD_MOUNTED -> "headMounted"
        Device.TYPE_FITNESS_BAND -> "fitnessBand"
        Device.TYPE_CHEST_STRAP -> "chestStrap"
        Device.TYPE_SMART_DISPLAY -> "smartDisplay"
        Device.TYPE_UNKNOWN -> null
        else -> null
    }
}

private fun nextDayOrRangeEnd(rangeStart: Instant, endTime: Instant) =
    if (rangeStart.dayStart == endTime.dayStart)
        endTime
    else
        rangeStart.dayStart.plus(1, ChronoUnit.DAYS)


private val Instant.dayStart: Instant
    get() = truncatedTo(ChronoUnit.DAYS)
