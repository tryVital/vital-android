package io.tryvital.vitalhealthconnect

import android.content.Context
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

interface RecordAggregator {

    suspend fun aggregateDistance(
        startTime: Instant,
        endTime: Instant
    ): Long

    suspend fun aggregateActiveEnergyBurned(
        startTime: Instant,
        endTime: Instant
    ): Long
}


internal class HealthConnectRecordAggregator(
    private val context: Context,
    private val healthConnectClientProvider: HealthConnectClientProvider,
) : RecordAggregator {

    private val healthConnectClient by lazy {
        healthConnectClientProvider.getHealthConnectClient(context)
    }

    override suspend fun aggregateDistance(
        startTime: Instant,
        endTime: Instant
    ): Long {
        return returnZeroIfException {
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )

            (response[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0L).toLong()
        }
    }

    override suspend fun aggregateActiveEnergyBurned(
        startTime: Instant,
        endTime: Instant
    ): Long {
        return returnZeroIfException {
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )

            (response[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilojoules ?: 0L).toLong()
        }
    }
}

private suspend fun returnZeroIfException(block: suspend () -> Long): Long {
    return try {
        block()
    } catch (exception: Exception) {
        0
    }
}
