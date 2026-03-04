package io.tryvital.vitalsamsunghealth.records

import android.content.Context
import com.samsung.android.sdk.health.data.data.AggregateOperation
import com.samsung.android.sdk.health.data.data.AggregatedData
import com.samsung.android.sdk.health.data.data.HealthDataPoint
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
import java.time.Instant
import java.time.ZoneId

internal interface RecordReader {
    suspend fun readExerciseSessions(startTime: Instant, endTime: Instant): List<HealthDataPoint>
    suspend fun readHeartRate(startTime: Instant, endTime: Instant): List<HealthDataPoint>
    suspend fun readRestingHeartRate(startTime: Instant, endTime: Instant): List<HealthDataPoint>
    suspend fun readHeartRateVariabilityRmssd(startTime: Instant, endTime: Instant): List<HealthDataPoint>
    suspend fun readHeights(startTime: Instant, endTime: Instant): List<HealthDataPoint>
    suspend fun readWeights(startTime: Instant, endTime: Instant): List<HealthDataPoint>
    suspend fun readBodyFat(startTime: Instant, endTime: Instant): List<HealthDataPoint>
    suspend fun readSleepSession(startTime: Instant, endTime: Instant): List<HealthDataPoint>
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
) : RecordReader {

    private val healthDataStore by lazy {
        samsungHealthClientProvider.getHealthDataStore(context)
    }

    override suspend fun readExerciseSessions(startTime: Instant, endTime: Instant): List<HealthDataPoint> {
        return readPoints(startTime, endTime) { DataTypes.EXERCISE.readDataRequestBuilder }
    }

    override suspend fun readHeartRate(startTime: Instant, endTime: Instant): List<HealthDataPoint> {
        return readPoints(startTime, endTime) { DataTypes.HEART_RATE.readDataRequestBuilder }
    }

    override suspend fun readRestingHeartRate(startTime: Instant, endTime: Instant): List<HealthDataPoint> = emptyList()

    override suspend fun readHeartRateVariabilityRmssd(startTime: Instant, endTime: Instant): List<HealthDataPoint> = emptyList()

    override suspend fun readHeights(startTime: Instant, endTime: Instant): List<HealthDataPoint> {
        return readPoints(startTime, endTime) { DataTypes.BODY_COMPOSITION.readDataRequestBuilder }
            .filter { it.getValue(DataType.BodyCompositionType.HEIGHT) != null }
    }

    override suspend fun readWeights(startTime: Instant, endTime: Instant): List<HealthDataPoint> {
        return readPoints(startTime, endTime) { DataTypes.BODY_COMPOSITION.readDataRequestBuilder }
            .filter { it.getValue(DataType.BodyCompositionType.WEIGHT) != null }
    }

    override suspend fun readBodyFat(startTime: Instant, endTime: Instant): List<HealthDataPoint> {
        return readPoints(startTime, endTime) { DataTypes.BODY_COMPOSITION.readDataRequestBuilder }
            .filter { it.getValue(DataType.BodyCompositionType.BODY_FAT) != null }
    }

    override suspend fun readSleepSession(startTime: Instant, endTime: Instant): List<HealthDataPoint> {
        return readPoints(startTime, endTime) { DataTypes.SLEEP.readDataRequestBuilder }
    }

    override suspend fun readOxygenSaturation(startTime: Instant, endTime: Instant): List<HealthDataPoint> {
        return readPoints(startTime, endTime) { DataTypes.BLOOD_OXYGEN.readDataRequestBuilder }
    }

    override suspend fun readActiveEnergyBurned(startTime: Instant, endTime: Instant): List<AggregatedData<Float>> {
        return aggregateHourly(startTime, endTime, DataType.ActivitySummaryType.TOTAL_ACTIVE_CALORIES_BURNED)
    }

    override suspend fun readBasalMetabolicRate(startTime: Instant, endTime: Instant): List<HealthDataPoint> {
        return readPoints(startTime, endTime) { DataTypes.BODY_COMPOSITION.readDataRequestBuilder }
            .filter { it.getValue(DataType.BodyCompositionType.BASAL_METABOLIC_RATE) != null }
    }

    override suspend fun readSteps(startTime: Instant, endTime: Instant): List<AggregatedData<Long>> {
        return aggregateHourlyLong(startTime, endTime, DataType.StepsType.TOTAL)
    }

    override suspend fun readDistance(startTime: Instant, endTime: Instant): List<AggregatedData<Float>> {
        return aggregateHourly(startTime, endTime, DataType.ActivitySummaryType.TOTAL_DISTANCE)
    }

    override suspend fun readFloorsClimbed(startTime: Instant, endTime: Instant): List<AggregatedData<Float>> {
        return aggregateHourly(startTime, endTime, DataType.FloorsClimbedType.TOTAL)
    }

    override suspend fun readVo2Max(startTime: Instant, endTime: Instant): List<HealthDataPoint> {
        return readPoints(startTime, endTime) { DataTypes.EXERCISE.readDataRequestBuilder }
    }

    override suspend fun readBloodGlucose(startTime: Instant, endTime: Instant): List<HealthDataPoint> {
        return readPoints(startTime, endTime) { DataTypes.BLOOD_GLUCOSE.readDataRequestBuilder }
    }

    override suspend fun readBloodPressure(startTime: Instant, endTime: Instant): List<HealthDataPoint> {
        return readPoints(startTime, endTime) { DataTypes.BLOOD_PRESSURE.readDataRequestBuilder }
    }

    override suspend fun readHydration(startTime: Instant, endTime: Instant): List<HealthDataPoint> {
        return readPoints(startTime, endTime) { DataTypes.WATER_INTAKE.readDataRequestBuilder }
    }

    override suspend fun readNutritionRecords(start: Instant, end: Instant): List<HealthDataPoint> {
        return readPoints(start, end) { DataTypes.NUTRITION.readDataRequestBuilder }
    }

    override suspend fun readBodyTemperatures(start: Instant, end: Instant): List<HealthDataPoint> {
        return readPoints(start, end) { DataTypes.BODY_TEMPERATURE.readDataRequestBuilder }
            .filter { it.getValue(DataType.BodyTemperatureType.BODY_TEMPERATURE) != null }
    }

    private suspend fun readPoints(
        startTime: Instant,
        endTime: Instant,
        builderFactory: () -> com.samsung.android.sdk.health.data.request.ReadDataRequest.DualTimeBuilder<HealthDataPoint>,
    ): List<HealthDataPoint> {
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
        operation: AggregateOperation<Float, com.samsung.android.sdk.health.data.request.AggregateRequest.LocalTimeBuilder<Float>>,
    ): List<AggregatedData<Float>> {
        val zone = ZoneId.systemDefault()
        val request = operation.requestBuilder
            .setLocalTimeFilterWithGroup(
                LocalTimeFilter.of(startTime.atZone(zone).toLocalDateTime(), endTime.atZone(zone).toLocalDateTime()),
                LocalTimeGroup.of(LocalTimeGroupUnit.HOURLY, 1),
            )
            .setOrdering(Ordering.ASC)
            .build()

        return healthDataStore.aggregateData(request).dataList
    }

    private suspend fun aggregateHourlyLong(
        startTime: Instant,
        endTime: Instant,
        operation: AggregateOperation<Long, com.samsung.android.sdk.health.data.request.AggregateRequest.LocalTimeBuilder<Long>>,
    ): List<AggregatedData<Long>> {
        val zone = ZoneId.systemDefault()
        val request = operation.requestBuilder
            .setLocalTimeFilterWithGroup(
                LocalTimeFilter.of(startTime.atZone(zone).toLocalDateTime(), endTime.atZone(zone).toLocalDateTime()),
                LocalTimeGroup.of(LocalTimeGroupUnit.HOURLY, 1),
            )
            .setOrdering(Ordering.ASC)
            .build()

        return healthDataStore.aggregateData(request).dataList
    }
}
