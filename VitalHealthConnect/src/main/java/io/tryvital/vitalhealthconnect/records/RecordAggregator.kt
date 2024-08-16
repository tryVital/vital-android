package io.tryvital.vitalhealthconnect.records

import android.content.Context
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.platform.client.request.AggregateDataRequest
import io.tryvital.client.services.data.LocalQuantitySample
import io.tryvital.client.services.data.SourceType
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalhealthconnect.HealthConnectClientProvider
import io.tryvital.vitalhealthconnect.model.HCActivityHourlyTotals
import io.tryvital.vitalhealthconnect.model.HCActivitySummary
import io.tryvital.vitalhealthconnect.model.HCSleepSummary
import io.tryvital.vitalhealthconnect.model.HCWorkoutSummary
import io.tryvital.vitalhealthconnect.model.quantitySample
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period
import java.util.TimeZone
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.reflect.KClass

internal interface RecordAggregator {

    suspend fun aggregateSleepSummary(
        startTime: Instant,
        endTime: Instant,
        dataOrigin: DataOrigin
    ): HCSleepSummary

    suspend fun aggregateWorkoutSummary(
        startTime: Instant,
        endTime: Instant,
        dataOrigin: DataOrigin
    ): HCWorkoutSummary

    suspend fun aggregateActivityDaySummaries(
        startDate: LocalDate,
        endDate: LocalDate,
        timeZone: TimeZone
    ): Map<LocalDate, HCActivitySummary>

    suspend fun aggregateStepHourlyTotals(
        start: Instant,
        end: Instant,
    ): List<LocalQuantitySample>

    suspend fun aggregateCaloriesActiveHourlyTotals(
        start: Instant,
        end: Instant,
    ): List<LocalQuantitySample>

    suspend fun aggregateFloorsClimbedHourlyTotals(
        start: Instant,
        end: Instant,
    ): List<LocalQuantitySample>

    suspend fun aggregateDistanceHourlyTotals(
        start: Instant,
        end: Instant,
    ): List<LocalQuantitySample>
}


internal class HealthConnectRecordAggregator(
    private val context: Context,
    private val healthConnectClientProvider: HealthConnectClientProvider,
) : RecordAggregator {

    private val mutex = Mutex()
    private var cachedGrantedPermissions: Set<String>? = null

    private val healthConnectClient by lazy {
        healthConnectClientProvider.getHealthConnectClient(context)
    }

    suspend fun grantedPermissions(): Set<String> = mutex.withLock {
        val cached = cachedGrantedPermissions
        if (cached != null) {
            return@withLock cached
        }
        val grantedPermissions = healthConnectClient.permissionController.getGrantedPermissions()
        cachedGrantedPermissions = cached
        return grantedPermissions
    }

    suspend fun permittedMetrics(
        vararg pairs: Pair<KClass<out Record>, AggregateMetric<*>>
    ): Set<AggregateMetric<*>> {
        val permissions = grantedPermissions()
        return pairs.mapNotNullTo(mutableSetOf()) { (recordType, metric) ->
            if (HealthPermission.getReadPermission(recordType) in permissions)
                metric
            else
                null
        }
    }

    override suspend fun aggregateSleepSummary(
        startTime: Instant,
        endTime: Instant,
        dataOrigin: DataOrigin
    ): HCSleepSummary {
        val metricsToRequest = permittedMetrics(
            HeartRateRecord::class to HeartRateRecord.BPM_MAX,
            HeartRateRecord::class to HeartRateRecord.BPM_AVG,
            HeartRateRecord::class to HeartRateRecord.BPM_MIN,
        )

        // No permission for anything; fail gracefully by returning an empty summary.
        if (metricsToRequest.isEmpty()) {
            return HCSleepSummary()
        }

        val response = healthConnectClient.aggregate(
            AggregateRequest(
                metricsToRequest,
                // Inclusive-exclusive
                TimeRangeFilter.between(startTime, endTime),
                setOf(dataOrigin),
            )
        )

        return HCSleepSummary(
            heartRateMaximum = response[HeartRateRecord.BPM_MAX]?.toInt(),
            heartRateMinimum = response[HeartRateRecord.BPM_MIN]?.toInt(),
            heartRateMean = response[HeartRateRecord.BPM_AVG]?.toInt(),
            hrvMeanSdnn = null,
            respiratoryRateMean = null,
        )
    }

    override suspend fun aggregateWorkoutSummary(
        startTime: Instant,
        endTime: Instant,
        dataOrigin: DataOrigin
    ): HCWorkoutSummary {
        val (heartRate, response) = coroutineScope {
            val response = async {
                val metricsToRequest = permittedMetrics(
                    DistanceRecord::class to DistanceRecord.DISTANCE_TOTAL,
                    ActiveCaloriesBurnedRecord::class to ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                )

                // No permission for anything; fail gracefully by returning an empty summary.
                if (metricsToRequest.isEmpty()) {
                    return@async null
                }

                healthConnectClient.aggregate(
                    AggregateRequest(
                        metricsToRequest,
                        // Inclusive-exclusive
                        TimeRangeFilter.between(startTime, endTime),
                        setOf(dataOrigin),
                    )
                )
            }.await()

            val heartRate = async {
                if (HealthPermission.getReadPermission(HeartRateRecord::class) !in grantedPermissions()) {
                    return@async HCWorkoutSummary()
                }
                aggregateHeartRateZones(startTime, endTime, dataOrigin)
            }.await()

            heartRate to response
        }

        return if (response != null) {
            heartRate.copy(
                distanceMeter = response[DistanceRecord.DISTANCE_TOTAL]?.inMeters,
                caloriesBurned = response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
            )
        } else {
            heartRate
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    suspend fun aggregateHeartRateZones(
        startTime: Instant,
        endTime: Instant,
        dataOrigin: DataOrigin,
    ): HCWorkoutSummary {
        val timestamps = arrayListOf<Long>()
        val values = arrayListOf<Long>()
        var pageToken: String? = null

        do {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest<HeartRateRecord>(
                    TimeRangeFilter.between(startTime, endTime),
                    setOf(dataOrigin),
                    pageSize = 2500,
                    pageToken = pageToken,
                )
            )

            pageToken = if (response.pageToken.isNullOrBlank()) null else response.pageToken
            VitalLogger.getOrCreate().logI("pageToken: ${response.pageToken}")

            response.records.forEach { record ->
                record.samples.forEach { timestamps.add(it.time.toEpochMilli() / 1000) }
            }
            response.records.forEach { record ->
                record.samples.forEach { values.add(it.beatsPerMinute) }
            }

        } while (pageToken != null)

        if (timestamps.count() <= 1) {
            return HCWorkoutSummary()
        }

        val durations = timestamps.zipWithNext { a, b -> b - a }

        // Health Connect has no date of birth records. Assume 30 for now.
        val zoneMaxHr = 220.0 - 30

        val zone1Range = 0 ..< (zoneMaxHr * 0.5).toLong()
        val zone2Range = (zoneMaxHr * 0.5).toLong() ..< (zoneMaxHr * 0.6).toLong()
        val zone3Range = (zoneMaxHr * 0.6).toLong() ..< (zoneMaxHr * 0.7).toLong()
        val zone4Range = (zoneMaxHr * 0.7).toLong() ..< (zoneMaxHr * 0.8).toLong()
        val zone5Range = (zoneMaxHr * 0.8).toLong() ..< (zoneMaxHr * 0.9).toLong()
        val zone6Range = (zoneMaxHr * 0.9).toLong() ..< zoneMaxHr.toLong()

        val zones = arrayListOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        var minHr = Long.MAX_VALUE
        var maxHr = Long.MIN_VALUE
        var averageHr = 0.0

        for (i in 0 .. durations.lastIndex) {
            val value = values[i]
            minHr = min(minHr, value)
            maxHr = max(maxHr, value)
            averageHr += value

            when (value) {
                in zone1Range -> zones[0] = zones[0] + durations[i]
                in zone2Range -> zones[1] = zones[1] + durations[i]
                in zone3Range -> zones[2] = zones[2] + durations[i]
                in zone4Range -> zones[3] = zones[3] + durations[i]
                in zone5Range -> zones[4] = zones[4] + durations[i]
                in zone6Range -> zones[5] = zones[5] + durations[i]
                else -> {}
            }
        }

        averageHr /= durations.count()

        return HCWorkoutSummary(
            heartRateMaximum = maxHr.toInt(),
            heartRateMinimum = minHr.toInt(),
            heartRateMean = averageHr.toInt(),
            heartRateZone1 = zones[0].toInt(),
            heartRateZone2 = zones[1].toInt(),
            heartRateZone3 = zones[2].toInt(),
            heartRateZone4 = zones[3].toInt(),
            heartRateZone5 = zones[4].toInt(),
            heartRateZone6 = zones[5].toInt(),
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

    override suspend fun aggregateCaloriesActiveHourlyTotals(
        start: Instant,
        end: Instant
    ) = aggregateActivityHourlyTotals(
        start = start,
        end = end,
        record = ActiveCaloriesBurnedRecord::class,
        metric = ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
        extractValue = { floor(it.inKilocalories) },
        unit = "kcal",
    )

    override suspend fun aggregateDistanceHourlyTotals(
        start: Instant,
        end: Instant
    ) = aggregateActivityHourlyTotals(
        start = start,
        end = end,
        record = DistanceRecord::class,
        metric = DistanceRecord.DISTANCE_TOTAL,
        extractValue = { it.inMeters },
        unit = "m",
    )

    override suspend fun aggregateFloorsClimbedHourlyTotals(
        start: Instant,
        end: Instant
    ) = aggregateActivityHourlyTotals(
        start = start,
        end = end,
        record = FloorsClimbedRecord::class,
        metric = FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL,
        extractValue = { it },
        unit = "count",
    )

    override suspend fun aggregateStepHourlyTotals(
        start: Instant,
        end: Instant,
    ) = aggregateActivityHourlyTotals(
        start = start,
        end = end,
        record = StepsRecord::class,
        metric = StepsRecord.COUNT_TOTAL,
        extractValue = { it.toDouble() },
        unit = "count",
    )

    internal suspend inline fun <reified R: Record, M: Any> aggregateActivityHourlyTotals(
        start: Instant,
        end: Instant,
        record: KClass<R>,
        metric: AggregateMetric<M>,
        extractValue: (M) -> Double,
        unit: String
    ): List<LocalQuantitySample> {

        val metricsToRequest = permittedMetrics(record to metric)

        // No permission for anything; fail gracefully by returning an empty summary.
        if (metricsToRequest.isEmpty()) {
            return emptyList()
        }

        val results = healthConnectClient.aggregateGroupByDuration(
            AggregateGroupByDurationRequest(
                metricsToRequest,
                // Inclusive-exclusive
                timeRangeFilter = TimeRangeFilter.between(start, end),
                timeRangeSlicer = Duration.ofHours(1),
            )
        )

        val samples = mutableListOf<LocalQuantitySample>()
        for (response in results) {
            response.result[metric]?.let { result ->
                samples.add(
                    quantitySample(
                        value = extractValue(result),
                        unit = unit,
                        startDate = response.startTime,
                        endDate = response.endTime,
                        metadata = null,
                        sourceType = SourceType.MultipleSources,
                    )
                )
            }
        }

        return samples
    }
}
