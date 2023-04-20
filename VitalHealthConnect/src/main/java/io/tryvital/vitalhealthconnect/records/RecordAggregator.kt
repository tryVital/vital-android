package io.tryvital.vitalhealthconnect.records

import android.content.Context
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.tryvital.vitalhealthconnect.HealthConnectClientProvider
import io.tryvital.vitalhealthconnect.model.HCActivitySummary
import io.tryvital.vitalhealthconnect.model.HCWorkoutSummary
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.SimpleTimeZone

interface RecordAggregator {

    suspend fun aggregateWorkoutSummary(
        startTime: Instant,
        endTime: Instant
    ): HCWorkoutSummary

    suspend fun aggregateActivityDaySummary(
        date: LocalDate,
        timeZone: SimpleTimeZone
    ): HCActivitySummary
}


internal class HealthConnectRecordAggregator(
    private val context: Context,
    private val healthConnectClientProvider: HealthConnectClientProvider,
) : RecordAggregator {

    private val healthConnectClient by lazy {
        healthConnectClientProvider.getHealthConnectClient(context)
    }

    override suspend fun aggregateWorkoutSummary(
        startTime: Instant,
        endTime: Instant
    ): HCWorkoutSummary {
        val response = healthConnectClient.aggregate(
            AggregateRequest(
                metrics = setOf(
                    DistanceRecord.DISTANCE_TOTAL,
                    ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                    // TODO: ManualWorkoutCreation missing full workoutv2 coverage
                    //HeartRateRecord.BPM_MAX,
                    //HeartRateRecord.BPM_AVG,
                    //ElevationGainedRecord.ELEVATION_GAINED_TOTAL,
                    //SpeedRecord.SPEED_MAX,
                    //SpeedRecord.SPEED_AVG,
                    //PowerRecord.POWER_MAX,
                    //PowerRecord.POWER_AVG,
                ),
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )

        return HCWorkoutSummary(
            distance = response[DistanceRecord.DISTANCE_TOTAL]?.inMeters,
            caloriesBurned = response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
            maxHeartRate = null,
            averageWatts = null,
            averageHeartRate = null,
            elevationGained = null,
            maxSpeed = null,
            averageSpeed = null,
            maxWatts = null,
            // TODO: ManualWorkoutCreation missing full workoutv2 coverage
            // maxHeartRate = response[HeartRateRecord.BPM_MAX],
            // averageHeartRate = response[HeartRateRecord.BPM_AVG],
            // elevationGained = response[ElevationGainedRecord.ELEVATION_GAINED_TOTAL]?.inMeters,
            // maxSpeed = response[SpeedRecord.SPEED_MAX]?.inMetersPerSecond,
            // averageSpeed = response[SpeedRecord.SPEED_AVG]?.inMetersPerSecond,
            // maxWatts = response[PowerRecord.POWER_MAX]?.inWatts,
            // averageWatts = response[PowerRecord.POWER_AVG]?.inWatts,
        )
    }

    override suspend fun aggregateActivityDaySummary(
        date: LocalDate,
        timeZone: SimpleTimeZone
    ): HCActivitySummary {
        val response = healthConnectClient.aggregate(
            AggregateRequest(
                metrics = setOf(
                    TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                    ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                    BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL,
                    StepsRecord.COUNT_TOTAL,
                    DistanceRecord.DISTANCE_TOTAL,
                    FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL,
                    ExerciseSessionRecord.EXERCISE_DURATION_TOTAL,
                ),
                // Inclusive-exclusive
                timeRangeFilter = TimeRangeFilter.between(
                    LocalDateTime.of(date, LocalTime.MIDNIGHT),
                    LocalDateTime.of(date.plusDays(1), LocalTime.MIDNIGHT),
                )
            )
        )

        val totalCaloriesBurned = response[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
        var activeCaloriesBurned = response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
        var basalCaloriesBurned = response[BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL]?.inKilocalories

        if (totalCaloriesBurned != null && basalCaloriesBurned != null && activeCaloriesBurned == null) {
            activeCaloriesBurned = totalCaloriesBurned - basalCaloriesBurned
        }

        if (totalCaloriesBurned != null && activeCaloriesBurned != null && basalCaloriesBurned == null) {
            basalCaloriesBurned = totalCaloriesBurned - activeCaloriesBurned
        }

        return HCActivitySummary(
            steps = response[StepsRecord.COUNT_TOTAL],
            activeCaloriesBurned = activeCaloriesBurned,
            basalCaloriesBurned = basalCaloriesBurned,
            distance = response[DistanceRecord.DISTANCE_TOTAL]?.inMeters,
            floorsClimbed = response[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL],
            totalExerciseDuration = response[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]?.toMinutes(),
        )
    }
}