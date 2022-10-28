package io.tryvital.client.healthconnect

import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import io.tryvital.client.services.data.AddWorkoutRequestData
import io.tryvital.client.services.data.QuantitySample
import io.tryvital.client.services.data.SampleType
import java.time.Instant
import java.util.*

interface RecordProcessor {
    /**
     * @param deviceType if the data doesn't contain the source device we use this
     */
    suspend fun processWorkouts(
        startTime: Instant,
        endTime: Instant,
        deviceType: String
    ): List<AddWorkoutRequestData>
}

class HealthConnectRecordProcessor(private val recordReader: RecordReader) : RecordProcessor {

    override suspend fun processWorkouts(
        startTime: Instant,
        endTime: Instant,
        deviceType: String
    ): List<AddWorkoutRequestData> {
        val exercises = recordReader.readExerciseSessions(startTime, endTime)

        return exercises.map { exerciseRecord ->
            val aggregatedDistance = recordReader.aggregateDistance(startTime, endTime)
            val aggregatedActiveCaloriesBurned = recordReader.aggregateCalories(startTime, endTime)
            val heartRateRecord = recordReader.readHeartRate(startTime, endTime)
            val respiratoryRateRecord = recordReader.readRespiratoryRate(startTime, endTime)

            AddWorkoutRequestData(
                id = exerciseRecord.metadata.id,
                startDate = Date.from(exerciseRecord.startTime),
                endDate = Date.from(exerciseRecord.endTime),
                sourceBundle = exerciseRecord.metadata.dataOrigin.packageName,
                sport = exerciseRecord.exerciseType,
                caloriesInKiloJules = aggregatedActiveCaloriesBurned,
                distanceInMeter = aggregatedDistance,
                heartRate = mapHearthRate(heartRateRecord, deviceType),
                respiratoryRate = mapRespiratoryRate(respiratoryRateRecord, deviceType),
                deviceType = deviceType
            )
        }
    }

    private fun mapRespiratoryRate(
        respiratoryRateRecords: List<RespiratoryRateRecord>,
        deviceType: String
    ): List<QuantitySample> {
        return respiratoryRateRecords.map {
            QuantitySample(
                id = "respiratoryRate-" + it.time,
                value = it.rate.toString(),
                unit = SampleType.RespiratoryRate.unit,
                startDate = Date.from(it.time),
                endDate = Date.from(it.time),
                sourceBundle = it.metadata.dataOrigin.packageName,
                deviceType = it.metadata.device?.type ?: deviceType,
            )

        }
    }

    private fun mapHearthRate(
        heartRateRecords: List<HeartRateRecord>,
        deviceType: String
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
                        deviceType = heartRateRecord.metadata.device?.type ?: deviceType,
                    )
                }
        }.flatten()
    }
}