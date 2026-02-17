package io.tryvital.vitalsamsunghealth.records

import android.content.Context
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.ActiveCaloriesBurnedRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.DistanceRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.FloorsClimbedRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.StepsRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.metadata.DataOrigin
import io.tryvital.client.services.data.LocalQuantitySample
import io.tryvital.client.services.data.SourceType
import io.tryvital.vitalsamsunghealth.SamsungHealthClientProvider
import io.tryvital.vitalsamsunghealth.model.HCActivitySummary
import io.tryvital.vitalsamsunghealth.model.HCSleepSummary
import io.tryvital.vitalsamsunghealth.model.HCWorkoutSummary
import io.tryvital.vitalsamsunghealth.model.quantitySample
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.TimeZone
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

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
    context: Context,
    samsungHealthClientProvider: SamsungHealthClientProvider,
) : RecordAggregator {

    private val reader: RecordReader = HealthConnectRecordReader(context, samsungHealthClientProvider)

    override suspend fun aggregateSleepSummary(
        startTime: Instant,
        endTime: Instant,
        dataOrigin: DataOrigin
    ): HCSleepSummary {
        val samples = reader.readHeartRate(startTime, endTime)
            .flatMap { it.samples }
            .filter { it.time >= startTime && it.time <= endTime }

        if (samples.isEmpty()) {
            return HCSleepSummary()
        }

        val min = samples.minOf { it.beatsPerMinute }
        val max = samples.maxOf { it.beatsPerMinute }
        val avg = samples.map { it.beatsPerMinute }.average().toInt()

        return HCSleepSummary(
            heartRateMaximum = max.toInt(),
            heartRateMinimum = min.toInt(),
            heartRateMean = avg,
            hrvMeanSdnn = null,
            respiratoryRateMean = null,
        )
    }

    override suspend fun aggregateWorkoutSummary(
        startTime: Instant,
        endTime: Instant,
        dataOrigin: DataOrigin
    ): HCWorkoutSummary {
        val heartRateSummary = aggregateHeartRateZones(startTime, endTime)
        val distance = reader.readDistance(startTime, endTime).sumOf { it.distance.inMeters }
        val calories = reader.readActiveEnergyBurned(startTime, endTime).sumOf { it.energy.inKilocalories }

        return heartRateSummary.copy(
            distanceMeter = distance,
            caloriesBurned = calories,
        )
    }

    private suspend fun aggregateHeartRateZones(
        startTime: Instant,
        endTime: Instant,
    ): HCWorkoutSummary {
        val samples = reader.readHeartRate(startTime, endTime)
            .flatMap { it.samples }
            .sortedBy { it.time }

        if (samples.size <= 1) {
            return HCWorkoutSummary()
        }

        val timestamps = samples.map { it.time.epochSecond }
        val values = samples.map { it.beatsPerMinute }
        val durations = timestamps.zipWithNext { a, b -> b - a }

        val zoneMaxHr = 220.0 - 30
        val zone1Range = 0..< (zoneMaxHr * 0.5).toLong()
        val zone2Range = (zoneMaxHr * 0.5).toLong() ..< (zoneMaxHr * 0.6).toLong()
        val zone3Range = (zoneMaxHr * 0.6).toLong() ..< (zoneMaxHr * 0.7).toLong()
        val zone4Range = (zoneMaxHr * 0.7).toLong() ..< (zoneMaxHr * 0.8).toLong()
        val zone5Range = (zoneMaxHr * 0.8).toLong() ..< (zoneMaxHr * 0.9).toLong()
        val zone6Range = (zoneMaxHr * 0.9).toLong() ..< zoneMaxHr.toLong()

        val zones = arrayListOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        var minHr = Long.MAX_VALUE
        var maxHr = Long.MIN_VALUE
        var averageHr = 0.0

        for (i in durations.indices) {
            val value = values[i]
            minHr = min(minHr, value)
            maxHr = max(maxHr, value)
            averageHr += value

            when (value) {
                in zone1Range -> zones[0] += durations[i]
                in zone2Range -> zones[1] += durations[i]
                in zone3Range -> zones[2] += durations[i]
                in zone4Range -> zones[3] += durations[i]
                in zone5Range -> zones[4] += durations[i]
                in zone6Range -> zones[5] += durations[i]
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
        val zoneId = timeZone.toZoneId()
        val start = startDate.atStartOfDay(zoneId).toInstant()
        val end = endDate.plusDays(1).atStartOfDay(zoneId).toInstant()

        val steps = aggregateStepHourlyTotals(start, end)
        val active = aggregateCaloriesActiveHourlyTotals(start, end)
        val distance = aggregateDistanceHourlyTotals(start, end)
        val floors = aggregateFloorsClimbedHourlyTotals(start, end)

        val exerciseMinutesByDay = reader.readExerciseSessions(start, end)
            .groupBy { it.startTime.atZone(zoneId).toLocalDate() }
            .mapValues { (_, sessions) ->
                sessions.sumOf { ChronoUnit.MINUTES.between(it.startTime, it.endTime) }
            }

        val dates = generateSequence(startDate) { d -> if (d < endDate) d.plusDays(1) else null }.toList()

        return dates.associateWith { day ->
            HCActivitySummary(
                steps = steps.filter { it.startDate.atZone(zoneId).toLocalDate() == day }.sumOf { it.value }.toLong(),
                activeCaloriesBurned = floor(active.filter { it.startDate.atZone(zoneId).toLocalDate() == day }.sumOf { it.value }),
                basalCaloriesBurned = null,
                distance = floor(distance.filter { it.startDate.atZone(zoneId).toLocalDate() == day }.sumOf { it.value }),
                floorsClimbed = floor(floors.filter { it.startDate.atZone(zoneId).toLocalDate() == day }.sumOf { it.value }),
                totalExerciseDuration = exerciseMinutesByDay[day],
            )
        }
    }

    override suspend fun aggregateCaloriesActiveHourlyTotals(start: Instant, end: Instant): List<LocalQuantitySample> {
        return fromIntervals(
            records = reader.readActiveEnergyBurned(start, end),
            unit = "kcal",
            startAt = { it.startTime },
            endAt = { it.endTime },
            metadataOf = { it.metadata },
            valueOf = { it.energy.inKilocalories }
        )
    }

    override suspend fun aggregateDistanceHourlyTotals(start: Instant, end: Instant): List<LocalQuantitySample> {
        return fromIntervals(
            records = reader.readDistance(start, end),
            unit = "m",
            startAt = { it.startTime },
            endAt = { it.endTime },
            metadataOf = { it.metadata },
            valueOf = { it.distance.inMeters }
        )
    }

    override suspend fun aggregateFloorsClimbedHourlyTotals(start: Instant, end: Instant): List<LocalQuantitySample> {
        return fromIntervals(
            records = reader.readFloorsClimbed(start, end),
            unit = "count",
            startAt = { it.startTime },
            endAt = { it.endTime },
            metadataOf = { it.metadata },
            valueOf = { it.floors }
        )
    }

    override suspend fun aggregateStepHourlyTotals(start: Instant, end: Instant): List<LocalQuantitySample> {
        return fromIntervals(
            records = reader.readSteps(start, end),
            unit = "count",
            startAt = { it.startTime },
            endAt = { it.endTime },
            metadataOf = { it.metadata },
            valueOf = { it.count.toDouble() }
        )
    }

    private fun <T> fromIntervals(
        records: List<T>,
        unit: String,
        startAt: (T) -> Instant,
        endAt: (T) -> Instant,
        metadataOf: (T) -> io.tryvital.vitalsamsunghealth.healthconnect.client.records.metadata.Metadata,
        valueOf: (T) -> Double
    ): List<LocalQuantitySample> {
        return records.map {
            quantitySample(
                value = valueOf(it),
                unit = unit,
                startDate = startAt(it),
                endDate = endAt(it),
                metadata = metadataOf(it),
                sourceType = SourceType.MultipleSources,
            )
        }
    }
}
