package io.tryvital.vitalhealthconnect.records

import android.content.Context
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.tryvital.client.services.data.LocalQuantitySample
import io.tryvital.client.services.data.SourceType
import io.tryvital.vitalhealthconnect.HealthConnectClientProvider
import io.tryvital.vitalhealthconnect.model.HCActivityHourlyTotals
import io.tryvital.vitalhealthconnect.model.HCActivitySummary
import io.tryvital.vitalhealthconnect.model.HCWorkoutSummary
import io.tryvital.vitalhealthconnect.model.quantitySample
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period
import java.util.TimeZone
import kotlin.math.floor
import kotlin.reflect.KClass

internal interface RecordAggregator {

    suspend fun aggregateWorkoutSummary(
        startTime: Instant,
        endTime: Instant
    ): HCWorkoutSummary

    suspend fun aggregateActivityDaySummaries(
        startDate: LocalDate,
        endDate: LocalDate,
        timeZone: TimeZone
    ): Map<LocalDate, HCActivitySummary>

    suspend fun aggregateActivityHourlyTotals(
        start: Instant,
        end: Instant,
        timeZone: TimeZone,
    ): HCActivityHourlyTotals
}


internal class HealthConnectRecordAggregator(
    private val context: Context,
    private val healthConnectClientProvider: HealthConnectClientProvider,
) : RecordAggregator {

    private val healthConnectClient by lazy {
        healthConnectClientProvider.getHealthConnectClient(context)
    }

    suspend fun permittedMetrics(
        vararg pairs: Pair<KClass<out Record>, AggregateMetric<*>>
    ): Set<AggregateMetric<*>> {
        val grantedPermissions = healthConnectClient.permissionController.getGrantedPermissions()
        return pairs.mapNotNullTo(mutableSetOf()) { (recordType, metric) ->
            if (HealthPermission.getReadPermission(recordType) in grantedPermissions)
                metric
            else
                null
        }
    }

    override suspend fun aggregateWorkoutSummary(
        startTime: Instant,
        endTime: Instant
    ): HCWorkoutSummary {
        val metricsToRequest = permittedMetrics(
            DistanceRecord::class to DistanceRecord.DISTANCE_TOTAL,
            ActiveCaloriesBurnedRecord::class to ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
            // TODO: ManualWorkoutCreation missing full workoutv2 coverage
            //HeartRateRecord::class to HeartRateRecord.BPM_MAX,
            //HeartRateRecord::class to HeartRateRecord.BPM_AVG,
            //ElevationGainedRecord::class to ElevationGainedRecord.ELEVATION_GAINED_TOTAL,
            //SpeedRecord::class to SpeedRecord.SPEED_MAX,
            //SpeedRecord::class to SpeedRecord.SPEED_AVG,
            //PowerRecord::class to PowerRecord.POWER_MAX,
            //PowerRecord::class to PowerRecord.POWER_AVG,
        )

        // No permission for anything; fail gracefully by returning an empty summary.
        if (metricsToRequest.isEmpty()) {
            return HCWorkoutSummary()
        }

        val response = healthConnectClient.aggregate(
            AggregateRequest(
                metricsToRequest,
                // Inclusive-exclusive
                TimeRangeFilter.between(startTime, endTime)
            )
        )

        return HCWorkoutSummary(
            distance = response[DistanceRecord.DISTANCE_TOTAL]?.inMeters,
            caloriesBurned = response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
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

    override suspend fun aggregateActivityDaySummaries(
        startDate: LocalDate,
        endDate: LocalDate,
        timeZone: TimeZone
    ): Map<LocalDate, HCActivitySummary> {
        val startOfDay = LocalDateTime.of(startDate, LocalTime.MIDNIGHT)
        val endOfDay = LocalDateTime.of(endDate.plusDays(1), LocalTime.MIDNIGHT)

        val metricsToRequest = permittedMetrics(
            TotalCaloriesBurnedRecord::class to TotalCaloriesBurnedRecord.ENERGY_TOTAL,
            ActiveCaloriesBurnedRecord::class to ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
            BasalMetabolicRateRecord::class to BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL,
            StepsRecord::class to StepsRecord.COUNT_TOTAL,
            DistanceRecord::class to DistanceRecord.DISTANCE_TOTAL,
            FloorsClimbedRecord::class to FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL,
            ExerciseSessionRecord::class to ExerciseSessionRecord.EXERCISE_DURATION_TOTAL,
        )

        // No permission for anything; fail gracefully by returning an empty summary.
        if (metricsToRequest.isEmpty()) {
            return emptyMap()
        }

        val responses = healthConnectClient.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metricsToRequest,
                // Inclusive-exclusive
                timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay),
                timeRangeSlicer = Period.ofDays(1),
            )
        )

        return responses.associateBy(
            keySelector = { response ->
                response.startTime.toLocalDate()
            },
            valueTransform = { response ->
                val totalCaloriesBurned = response.result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories?.let { floor(it) }
                var activeCaloriesBurned = response.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.let { floor(it) }
                var basalCaloriesBurned = response.result[BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL]?.inKilocalories?.let { floor(it) }

                if (totalCaloriesBurned != null && basalCaloriesBurned != null && activeCaloriesBurned == null) {
                    activeCaloriesBurned = totalCaloriesBurned - basalCaloriesBurned
                }

                if (totalCaloriesBurned != null && activeCaloriesBurned != null && basalCaloriesBurned == null) {
                    basalCaloriesBurned = totalCaloriesBurned - activeCaloriesBurned
                }

                HCActivitySummary(
                    steps = response.result[StepsRecord.COUNT_TOTAL],
                    activeCaloriesBurned = activeCaloriesBurned,
                    basalCaloriesBurned = basalCaloriesBurned,
                    distance = response.result[DistanceRecord.DISTANCE_TOTAL]?.inMeters?.let { floor(it) },
                    floorsClimbed = response.result[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL]?.let { floor(it) },
                    totalExerciseDuration = response.result[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]?.toMinutes(),
                )
            }
        )
    }

    override suspend fun aggregateActivityHourlyTotals(
        start: Instant,
        end: Instant,
        timeZone: TimeZone,
    ): HCActivityHourlyTotals {

        val metricsToRequest = permittedMetrics(
            ActiveCaloriesBurnedRecord::class to ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
            StepsRecord::class to StepsRecord.COUNT_TOTAL,
            DistanceRecord::class to DistanceRecord.DISTANCE_TOTAL,
            FloorsClimbedRecord::class to FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL,
        )

        // No permission for anything; fail gracefully by returning an empty summary.
        if (metricsToRequest.isEmpty()) {
            return HCActivityHourlyTotals()
        }

        val results = healthConnectClient.aggregateGroupByDuration(
            AggregateGroupByDurationRequest(
                metricsToRequest,
                // Inclusive-exclusive
                timeRangeFilter = TimeRangeFilter.between(start, end),
                timeRangeSlicer = Duration.ofHours(1),
            )
        )

        val activeCaloriesByDate = mutableMapOf<LocalDate, MutableList<LocalQuantitySample>>()
        val stepsByDate = mutableMapOf<LocalDate, MutableList<LocalQuantitySample>>()
        val distanceByDate = mutableMapOf<LocalDate, MutableList<LocalQuantitySample>>()
        val floorsClimbedByDate = mutableMapOf<LocalDate, MutableList<LocalQuantitySample>>()

        for (response in results) {
            val date = response.startTime.atZone(timeZone.toZoneId()).toLocalDate()

            response.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.let { activeCalories ->
                activeCaloriesByDate.getOrPut(date) { mutableListOf() }.add(
                    quantitySample(
                        value = floor(activeCalories),
                        unit = "kcal",
                        startDate = response.startTime,
                        endDate = response.endTime,
                        metadata = null,
                        sourceType = SourceType.MultipleSources,
                    )
                )
            }
            response.result[StepsRecord.COUNT_TOTAL]?.let { steps ->
                stepsByDate.getOrPut(date) { mutableListOf() }.add(
                    quantitySample(
                        value = steps.toDouble(),
                        unit = "count",
                        startDate = response.startTime,
                        endDate = response.endTime,
                        metadata = null,
                        sourceType = SourceType.MultipleSources,
                    )
                )
            }
            response.result[DistanceRecord.DISTANCE_TOTAL]?.inMeters?.let { distance ->
                distanceByDate.getOrPut(date) { mutableListOf() }.add(
                    quantitySample(
                        value = floor(distance),
                        unit = "m",
                        startDate = response.startTime,
                        endDate = response.endTime,
                        metadata = null,
                        sourceType = SourceType.MultipleSources,
                    )
                )
            }
            response.result[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL]?.let { floorsClimbed ->
                floorsClimbedByDate.getOrPut(date) { mutableListOf() }.add(
                    quantitySample(
                        value = floor(floorsClimbed),
                        unit = "count",
                        startDate = response.startTime,
                        endDate = response.endTime,
                        metadata = null,
                        sourceType = SourceType.MultipleSources,
                    )
                )
            }
        }

        return HCActivityHourlyTotals(
            activeCalories = activeCaloriesByDate,
            steps = stepsByDate,
            distance = distanceByDate,
            floorsClimbed = floorsClimbedByDate
        )
    }
}
