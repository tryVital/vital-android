package io.tryvital.client.healthconnect

import android.content.Context
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.tryvital.client.dependencies.HealthConnectClientProvider
import java.time.Instant

interface RecordReader {
    suspend fun readExerciseSessions(
        startTime: Instant,
        endTime: Instant
    ): List<ExerciseSessionRecord>

    suspend fun readHeartRate(
        startTime: Instant,
        endTime: Instant
    ): List<HeartRateRecord>

    suspend fun readRespiratoryRate(
        startTime: Instant,
        endTime: Instant
    ): List<RespiratoryRateRecord>

    suspend fun aggregateDistance(
        startTime: Instant,
        endTime: Instant
    ): Long

    suspend fun aggregateCalories(
        startTime: Instant,
        endTime: Instant
    ): Long
}

internal class HealthConnectRecordReader(
    private val context: Context,
    private val healthConnectClientProvider: HealthConnectClientProvider,
) : RecordReader {

    private val healthConnectClient by lazy {
        healthConnectClientProvider.getHealthConnectClient(context)
    }

    override suspend fun readExerciseSessions(
        startTime: Instant,
        endTime: Instant
    ): List<ExerciseSessionRecord> {
        return try {
            healthConnectClient.readRecords(
                ReadRecordsRequest(
                    ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            ).records
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun readHeartRate(
        startTime: Instant,
        endTime: Instant
    ): List<HeartRateRecord> {
        return try {
            healthConnectClient.readRecords(
                ReadRecordsRequest(
                    HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            ).records
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun readRespiratoryRate(
        startTime: Instant,
        endTime: Instant
    ): List<RespiratoryRateRecord> {
        return try {
            healthConnectClient.readRecords(
                ReadRecordsRequest(
                    RespiratoryRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            ).records
        } catch (e: Exception) {
            emptyList()
        }

    }


    override suspend fun aggregateDistance(
        startTime: Instant,
        endTime: Instant
    ): Long {
        return try {
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )

            (response[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0L).toLong()
        } catch (e: Exception) {
            0
        }
    }

    override suspend fun aggregateCalories(
        startTime: Instant,
        endTime: Instant
    ): Long {
        return try {
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )

            (response[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilojoules ?: 0L).toLong()
        } catch (e: Exception) {
            0
        }
    }
}