package io.tryvital.client.healthconnect

import android.content.Context
import androidx.health.connect.client.records.*
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

    suspend fun readRestingHeartRate(
        startTime: Instant,
        endTime: Instant
    ): List<RestingHeartRateRecord>

    suspend fun readHeartRateVariabilitySdnn(
        startTime: Instant,
        endTime: Instant
    ): List<HeartRateVariabilitySdnnRecord>

    suspend fun readRespiratoryRate(
        startTime: Instant,
        endTime: Instant
    ): List<RespiratoryRateRecord>

    suspend fun readHeights(
        startTime: Instant,
        endTime: Instant
    ): List<HeightRecord>

    suspend fun readWeights(
        startTime: Instant,
        endTime: Instant
    ): List<WeightRecord>

    suspend fun readBodyFat(
        startTime: Instant,
        endTime: Instant
    ): List<BodyFatRecord>

    suspend fun readSleepSession(
        startTime: Instant,
        endTime: Instant
    ): List<SleepSessionRecord>

    suspend fun readOxygenSaturation(
        startTime: Instant,
        endTime: Instant
    ): List<OxygenSaturationRecord>
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
        return returnEmptyIfException {
            healthConnectClient.readRecords(
                ReadRecordsRequest(
                    ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            ).records
        }
    }

    override suspend fun readHeartRate(
        startTime: Instant,
        endTime: Instant
    ): List<HeartRateRecord> {
        return returnEmptyIfException {
            healthConnectClient.readRecords(
                ReadRecordsRequest(
                    HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            ).records
        }
    }

    override suspend fun readRestingHeartRate(
        startTime: Instant,
        endTime: Instant
    ): List<RestingHeartRateRecord> {
        return returnEmptyIfException {
            healthConnectClient.readRecords(
                ReadRecordsRequest(
                    RestingHeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            ).records
        }

    }

    override suspend fun readHeartRateVariabilitySdnn(
        startTime: Instant,
        endTime: Instant
    ): List<HeartRateVariabilitySdnnRecord> {
        return returnEmptyIfException {
            healthConnectClient.readRecords(
                ReadRecordsRequest(
                    HeartRateVariabilitySdnnRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            ).records
        }

    }

    override suspend fun readRespiratoryRate(
        startTime: Instant,
        endTime: Instant
    ): List<RespiratoryRateRecord> {
        return returnEmptyIfException {
            healthConnectClient.readRecords(
                ReadRecordsRequest(
                    RespiratoryRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            ).records
        }
    }

    override suspend fun readHeights(
        startTime: Instant,
        endTime: Instant
    ): List<HeightRecord> {
        return returnEmptyIfException {
            healthConnectClient.readRecords(
                ReadRecordsRequest(
                    HeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            ).records
        }
    }

    override suspend fun readWeights(startTime: Instant, endTime: Instant): List<WeightRecord> {
        return returnEmptyIfException {
            healthConnectClient.readRecords(
                ReadRecordsRequest(
                    WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            ).records
        }
    }

    override suspend fun readBodyFat(startTime: Instant, endTime: Instant): List<BodyFatRecord> {
        return returnEmptyIfException {
            healthConnectClient.readRecords(
                ReadRecordsRequest(
                    BodyFatRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            ).records
        }
    }

    override suspend fun readSleepSession(
        startTime: Instant,
        endTime: Instant
    ): List<SleepSessionRecord> {
        return returnEmptyIfException {
            healthConnectClient.readRecords(
                ReadRecordsRequest(
                    SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            ).records

        }
    }

    override suspend fun readOxygenSaturation(
        startTime: Instant,
        endTime: Instant
    ): List<OxygenSaturationRecord> {
        return returnEmptyIfException {
            healthConnectClient.readRecords(
                ReadRecordsRequest(
                    OxygenSaturationRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            ).records

        }

    }
}

private suspend fun <T> returnEmptyIfException(block: suspend () -> List<T>): List<T> {
    return try {
        block()
    } catch (exception: Exception) {
        emptyList()
    }
}
