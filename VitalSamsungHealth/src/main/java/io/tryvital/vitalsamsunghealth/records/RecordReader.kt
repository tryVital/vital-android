package io.tryvital.vitalsamsunghealth.records

import android.content.Context
import com.samsung.android.sdk.health.data.data.AggregateOperation
import com.samsung.android.sdk.health.data.data.AggregatedData
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.InstantTimeFilter
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.LocalTimeGroup
import com.samsung.android.sdk.health.data.request.LocalTimeGroupUnit
import com.samsung.android.sdk.health.data.request.Ordering
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalsamsunghealth.SamsungHealthClientProvider
import io.tryvital.vitalsamsunghealth.ext.returnEmptyIfException
import io.tryvital.vitalsamsunghealth.permissionKey
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId

internal interface RecordReader {
    suspend fun readExerciseSessions(startTime: Instant, endTime: Instant): List<HealthDataPoint>
    suspend fun readHeartRate(startTime: Instant, endTime: Instant): List<HealthDataPoint>
    suspend fun readHeights(startTime: Instant, endTime: Instant): List<HealthDataPoint>
    suspend fun readBodyCompositions(startTime: Instant, endTime: Instant): List<HealthDataPoint>
    suspend fun readSleepSession(startTime: Instant, endTime: Instant): List<HealthDataPoint>
    suspend fun readSleepSkinTemperature(sleepIds: List<String>): Map<String, List<HealthDataPoint>>
    suspend fun readOxygenSaturation(startTime: Instant, endTime: Instant): List<HealthDataPoint>
    suspend fun readActiveEnergyBurned(startTime: Instant, endTime: Instant): List<AggregatedData<Float>>
    suspend fun readBasalMetabolicRate(startTime: Instant, endTime: Instant): List<HealthDataPoint>
    suspend fun readSteps(startTime: Instant, endTime: Instant): List<AggregatedData<Long>>
    suspend fun readDistance(startTime: Instant, endTime: Instant): List<AggregatedData<Float>>
    suspend fun readFloorsClimbed(startTime: Instant, endTime: Instant): List<AggregatedData<Float>>
    suspend fun readVo2Max(startTime: Instant, endTime: Instant): List<HealthDataPoint>
    suspend fun readBloodGlucose(startTime: Instant, endTime: Instant): List<HealthDataPoint>
    suspend fun readBloodPressure(startTime: Instant, endTime: Instant): List<HealthDataPoint>
    suspend fun readHydration(startTime: Instant, endTime: Instant): List<HealthDataPoint>
    suspend fun readNutritionRecords(start: Instant, end: Instant): List<HealthDataPoint>
    suspend fun readBodyTemperatures(start: Instant, end: Instant): List<HealthDataPoint>
}

internal class HealthConnectRecordReader(
    private val context: Context,
    private val samsungHealthClientProvider: SamsungHealthClientProvider,
    private val grantedPermissions: () -> Set<String>,
) : RecordReader {

    private val healthDataStore by lazy {
        samsungHealthClientProvider.getHealthDataStore(context)
    }

    override suspend fun readExerciseSessions(startTime: Instant, endTime: Instant): List<HealthDataPoint> {
        return readPoints(startTime, endTime, DataTypes.EXERCISE) { DataTypes.EXERCISE.readDataRequestBuilder }
    }

    override suspend fun readHeartRate(startTime: Instant, endTime: Instant): List<HealthDataPoint> {
        return readPoints(startTime, endTime, DataTypes.HEART_RATE) { DataTypes.HEART_RATE.readDataRequestBuilder }
    }

    override suspend fun readHeights(startTime: Instant, endTime: Instant): List<HealthDataPoint> {
        return readPoints(startTime, endTime, DataTypes.BODY_COMPOSITION) { DataTypes.BODY_COMPOSITION.readDataRequestBuilder }
            .filter { it.getValue(DataType.BodyCompositionType.HEIGHT) != null }
    }

    override suspend fun readBodyCompositions(startTime: Instant, endTime: Instant): List<HealthDataPoint> {
        return readPoints(startTime, endTime, DataTypes.BODY_COMPOSITION) { DataTypes.BODY_COMPOSITION.readDataRequestBuilder }
    }

    override suspend fun readSleepSession(startTime: Instant, endTime: Instant): List<HealthDataPoint> {
        return readPoints(startTime, endTime, DataTypes.SLEEP) { DataTypes.SLEEP.readDataRequestBuilder }
    }

    override suspend fun readSleepSkinTemperature(sleepIds: List<String>): Map<String, List<HealthDataPoint>> {
        if (sleepIds.isEmpty()) {
            return emptyMap()
        }

        if (!hasReadPermission(DataTypes.SKIN_TEMPERATURE)) {
            VitalLogger.getOrCreate().info { "Skipping sleep skin temperature: read permission is not granted" }
            return emptyMap()
        }

        return try {
            val idFilter = com.samsung.android.sdk.health.data.request.IdFilter.builder().run {
                for (sleepId in sleepIds) {
                    addDataUid(sleepId)
                }
                build()
            }
            val associatedData = mutableListOf<com.samsung.android.sdk.health.data.data.AssociatedDataPoints>()
            var pageToken: String? = null

            do {
                val builder = DataTypes.SLEEP.associatedReadRequestBuilder
                    .addAssociatedDataType(DataType.SleepType.Associates.SKIN_TEMPERATURE)
                    .setPageSize(1000)

                if (pageToken == null) {
                    builder.setIdFilter(idFilter)
                } else {
                    builder.setPageToken(pageToken)
                }

                val response = healthDataStore.readAssociatedData(builder.build())
                associatedData += response.dataList
                val nextToken = response.pageToken
                if (nextToken.isNullOrBlank() || nextToken == pageToken) {
                    pageToken = null
                } else {
                    pageToken = nextToken
                }
            } while (pageToken != null)

            associatedData
                .groupBy(
                    keySelector = { it.uid },
                    valueTransform = { it.getDataPointOf(DataTypes.SKIN_TEMPERATURE) ?: emptyList() },
                )
                .mapValues { it.value.flatten() }
        } catch (exc: CancellationException) {
            throw exc
        } catch (exc: Throwable) {
            // Skin temperature is supplementary sleep enrichment. A revoked permission or an
            // unavailable associated-data API must not prevent the sleep session from syncing.
            VitalLogger.getOrCreate().info { "Skipping sleep skin temperature: $exc" }
            emptyMap()
        }
    }

    override suspend fun readOxygenSaturation(startTime: Instant, endTime: Instant): List<HealthDataPoint> {
        return readPoints(startTime, endTime, DataTypes.BLOOD_OXYGEN) { DataTypes.BLOOD_OXYGEN.readDataRequestBuilder }
    }

    override suspend fun readActiveEnergyBurned(startTime: Instant, endTime: Instant): List<AggregatedData<Float>> {
        return aggregateHourly(startTime, endTime, DataTypes.ACTIVITY_SUMMARY, DataType.ActivitySummaryType.TOTAL_ACTIVE_CALORIES_BURNED)
    }

    override suspend fun readBasalMetabolicRate(startTime: Instant, endTime: Instant): List<HealthDataPoint> {
        return readPoints(startTime, endTime, DataTypes.BODY_COMPOSITION) { DataTypes.BODY_COMPOSITION.readDataRequestBuilder }
            .filter { it.getValue(DataType.BodyCompositionType.BASAL_METABOLIC_RATE) != null }
    }

    override suspend fun readSteps(startTime: Instant, endTime: Instant): List<AggregatedData<Long>> {
        return aggregateHourlyLong(startTime, endTime, DataTypes.STEPS, DataType.StepsType.TOTAL)
    }

    override suspend fun readDistance(startTime: Instant, endTime: Instant): List<AggregatedData<Float>> {
        return aggregateHourly(startTime, endTime, DataTypes.ACTIVITY_SUMMARY, DataType.ActivitySummaryType.TOTAL_DISTANCE)
    }

    override suspend fun readFloorsClimbed(startTime: Instant, endTime: Instant): List<AggregatedData<Float>> {
        return aggregateHourly(startTime, endTime, DataTypes.FLOORS_CLIMBED, DataType.FloorsClimbedType.TOTAL)
    }

    override suspend fun readVo2Max(startTime: Instant, endTime: Instant): List<HealthDataPoint> {
        return readPoints(startTime, endTime, DataTypes.EXERCISE) { DataTypes.EXERCISE.readDataRequestBuilder }
    }

    override suspend fun readBloodGlucose(startTime: Instant, endTime: Instant): List<HealthDataPoint> {
        return readPoints(startTime, endTime, DataTypes.BLOOD_GLUCOSE) { DataTypes.BLOOD_GLUCOSE.readDataRequestBuilder }
    }

    override suspend fun readBloodPressure(startTime: Instant, endTime: Instant): List<HealthDataPoint> {
        return readPoints(startTime, endTime, DataTypes.BLOOD_PRESSURE) { DataTypes.BLOOD_PRESSURE.readDataRequestBuilder }
    }

    override suspend fun readHydration(startTime: Instant, endTime: Instant): List<HealthDataPoint> {
        return readPoints(startTime, endTime, DataTypes.WATER_INTAKE) { DataTypes.WATER_INTAKE.readDataRequestBuilder }
    }

    override suspend fun readNutritionRecords(start: Instant, end: Instant): List<HealthDataPoint> {
        return readPoints(start, end, DataTypes.NUTRITION) { DataTypes.NUTRITION.readDataRequestBuilder }
    }

    override suspend fun readBodyTemperatures(start: Instant, end: Instant): List<HealthDataPoint> {
        return readPoints(start, end, DataTypes.BODY_TEMPERATURE) { DataTypes.BODY_TEMPERATURE.readDataRequestBuilder }
            .filter { it.getValue(DataType.BodyTemperatureType.BODY_TEMPERATURE) != null }
    }

    private suspend fun readPoints(
        startTime: Instant,
        endTime: Instant,
        dataType: DataType,
        builderFactory: () -> com.samsung.android.sdk.health.data.request.ReadDataRequest.DualTimeBuilder<HealthDataPoint>,
    ): List<HealthDataPoint> {
        if (!hasReadPermission(dataType)) {
            return emptyList()
        }

        return returnEmptyIfException {
            val points = mutableListOf<HealthDataPoint>()
            var pageToken: String? = null

            do {
                val builder = builderFactory()
                    .setInstantTimeFilter(InstantTimeFilter.of(startTime, endTime))
                    .setOrdering(Ordering.ASC)

                if (pageToken != null) {
                    builder.setPageToken(pageToken)
                }

                val response = healthDataStore.readData(builder.build())
                points += response.dataList
                pageToken = response.pageToken
                VitalLogger.getOrCreate().info { "readPoints page=${response.dataList.size} token=${response.pageToken}" }
            } while (!pageToken.isNullOrBlank())

            points
        }
    }

    private suspend fun aggregateHourly(
        startTime: Instant,
        endTime: Instant,
        dataType: DataType,
        operation: AggregateOperation<Float, com.samsung.android.sdk.health.data.request.AggregateRequest.LocalTimeBuilder<Float>>,
    ): List<AggregatedData<Float>> {
        if (!hasReadPermission(dataType)) {
            return emptyList()
        }

        return returnEmptyIfException {
            val zone = ZoneId.systemDefault()
            val request = operation.requestBuilder
                .setLocalTimeFilterWithGroup(
                    LocalTimeFilter.of(startTime.atZone(zone).toLocalDateTime(), endTime.atZone(zone).toLocalDateTime()),
                    LocalTimeGroup.of(LocalTimeGroupUnit.HOURLY, 1),
                )
                .setOrdering(Ordering.ASC)
                .build()

            healthDataStore.aggregateData(request).dataList
        }
    }

    private suspend fun aggregateHourlyLong(
        startTime: Instant,
        endTime: Instant,
        dataType: DataType,
        operation: AggregateOperation<Long, com.samsung.android.sdk.health.data.request.AggregateRequest.LocalTimeBuilder<Long>>,
    ): List<AggregatedData<Long>> {
        if (!hasReadPermission(dataType)) {
            return emptyList()
        }

        return returnEmptyIfException {
            val zone = ZoneId.systemDefault()
            val request = operation.requestBuilder
                .setLocalTimeFilterWithGroup(
                    LocalTimeFilter.of(startTime.atZone(zone).toLocalDateTime(), endTime.atZone(zone).toLocalDateTime()),
                    LocalTimeGroup.of(LocalTimeGroupUnit.HOURLY, 1),
                )
                .setOrdering(Ordering.ASC)
                .build()

            healthDataStore.aggregateData(request).dataList
        }
    }

    private fun hasReadPermission(dataType: DataType): Boolean =
        permissionKey(dataType, AccessType.READ) in grantedPermissions()
}
