package io.tryvital.client.healthconnect

import android.annotation.SuppressLint
import androidx.health.connect.client.records.*
import io.tryvital.client.services.data.*
import java.time.Instant
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
}

internal class HealthConnectRecordProcessor(
    private val recordReader: RecordReader,
    private val recordAggregator: RecordAggregator
) :
    RecordProcessor {

    override suspend fun processWorkouts(
        startTime: Instant,
        endTime: Instant,
        fallbackDeviceModel: String
    ): List<WorkoutPayload> {
        val exercises = recordReader.readExerciseSessions(startTime, endTime)

        return exercises.map { exerciseRecord ->
            val aggregatedDistance = recordAggregator.aggregateDistance(startTime, endTime)
            val aggregatedActiveCaloriesBurned =
                recordAggregator.aggregateCalories(startTime, endTime)
            val heartRateRecord = recordReader.readHeartRate(startTime, endTime)
            val respiratoryRateRecord = recordReader.readRespiratoryRate(startTime, endTime)

            WorkoutPayload(
                id = exerciseRecord.metadata.id,
                startDate = Date.from(exerciseRecord.startTime),
                endDate = Date.from(exerciseRecord.endTime),
                sourceBundle = exerciseRecord.metadata.dataOrigin.packageName,
                sport = exerciseRecord.exerciseType,
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
            biologicalSex = "not_set",
            dateOfBirth = Date(0),
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
        val sleepSession = recordReader.readSleepSession(startTime, endTime)

        return sleepSession.map { sleepSessionRecord ->
            val heartRateRecord = recordReader.readHeartRate(startTime, endTime)
            val restingHeartRateRecord = recordReader.readRestingHeartRate(startTime, endTime)
            val respiratoryRateRecord = recordReader.readRespiratoryRate(startTime, endTime)
            val readHeartRateVariabilitySdnnRecord =
                recordReader.readHeartRateVariabilitySdnn(startTime, endTime)
            val oxygenSaturationRecord = recordReader.readOxygenSaturation(startTime, endTime)

            SleepPayload(
                id = sleepSessionRecord.metadata.id,
                startDate = Date.from(sleepSessionRecord.startTime),
                endDate = Date.from(sleepSessionRecord.endTime),
                sourceBundle = sleepSessionRecord.metadata.dataOrigin.packageName,
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
    HeartRateVariabilitySdnnRecord is marked as RestrictedApi as the plugin is still alpha
    We assume it's a mistake, if later this stays the same we have to move to a different
    hearth rate
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
                        deviceModel = heartRateRecord.metadata.device?.type ?: fallbackDeviceModel,
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
                deviceModel = it.metadata.device?.type ?: fallbackDeviceModel,
            )
        }
    }
}