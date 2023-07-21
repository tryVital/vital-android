package io.tryvital.vitalhealthconnect.records

import android.content.Context
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SleepStageRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.tryvital.vitalhealthconnect.HealthConnectClientProvider
import io.tryvital.vitalhealthconnect.ext.returnEmptyIfException
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

    suspend fun readHeartRateVariabilityRmssd(
        startTime: Instant,
        endTime: Instant
    ): List<HeartRateVariabilityRmssdRecord>

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

    suspend fun readSleepStages(
        startTime: Instant,
        endTime: Instant
    ): List<SleepStageRecord>

    suspend fun readOxygenSaturation(
        startTime: Instant,
        endTime: Instant
    ): List<OxygenSaturationRecord>

    suspend fun readActiveEnergyBurned(
        startTime: Instant,
        endTime: Instant
    ): List<ActiveCaloriesBurnedRecord>

    suspend fun readBasalMetabolicRate(
        startTime: Instant,
        endTime: Instant
    ): List<BasalMetabolicRateRecord>

    suspend fun readSteps(
        startTime: Instant,
        endTime: Instant
    ): List<StepsRecord>

    suspend fun readDistance(
        startTime: Instant,
        endTime: Instant
    ): List<DistanceRecord>

    suspend fun readFloorsClimbed(
        startTime: Instant,
        endTime: Instant
    ): List<FloorsClimbedRecord>

    suspend fun readVo2Max(
        startTime: Instant,
        endTime: Instant
    ): List<Vo2MaxRecord>

    suspend fun readBloodGlucose(
        startTime: Instant,
        endTime: Instant
    ): List<BloodGlucoseRecord>

    suspend fun readBloodPressure(
        startTime: Instant,
        endTime: Instant
    ): List<BloodPressureRecord>

    suspend fun readHydration(
        startTime: Instant,
        endTime: Instant
    ): List<HydrationRecord>
}

internal class HealthConnectRecordReader(
    private val context: Context,
    private val healthConnectClientProvider: HealthConnectClientProvider,
) : RecordReader {

    private val healthConnectClient by lazy {
        healthConnectClientProvider.getHealthConnectClient(context)
    }

    override suspend fun readExerciseSessions(
        startTime: Instant, endTime: Instant
    ): List<ExerciseSessionRecord> = readRecords(startTime, endTime)

    override suspend fun readHeartRate(
        startTime: Instant, endTime: Instant
    ): List<HeartRateRecord> = readRecords(startTime, endTime)

    override suspend fun readRestingHeartRate(
        startTime: Instant, endTime: Instant
    ): List<RestingHeartRateRecord> = readRecords(startTime, endTime)

    override suspend fun readHeartRateVariabilityRmssd(
        startTime: Instant, endTime: Instant
    ): List<HeartRateVariabilityRmssdRecord> = readRecords(startTime, endTime)

    override suspend fun readRespiratoryRate(
        startTime: Instant, endTime: Instant
    ): List<RespiratoryRateRecord> = readRecords(startTime, endTime)

    override suspend fun readHeights(
        startTime: Instant, endTime: Instant
    ): List<HeightRecord> = readRecords(startTime, endTime)

    override suspend fun readWeights(
        startTime: Instant, endTime: Instant
    ): List<WeightRecord> = readRecords(startTime, endTime)

    override suspend fun readBodyFat(
        startTime: Instant, endTime: Instant
    ): List<BodyFatRecord> = readRecords(startTime, endTime)

    override suspend fun readSleepSession(
        startTime: Instant, endTime: Instant
    ): List<SleepSessionRecord> = readRecords(startTime, endTime)

    override suspend fun readSleepStages(
        startTime: Instant,
        endTime: Instant
    ): List<SleepStageRecord> = readRecords(startTime, endTime)

    override suspend fun readOxygenSaturation(
        startTime: Instant, endTime: Instant
    ): List<OxygenSaturationRecord> = readRecords(startTime, endTime)

    override suspend fun readActiveEnergyBurned(
        startTime: Instant, endTime: Instant
    ): List<ActiveCaloriesBurnedRecord> = readRecords(startTime, endTime)

    override suspend fun readBasalMetabolicRate(
        startTime: Instant, endTime: Instant
    ): List<BasalMetabolicRateRecord> = readRecords(startTime, endTime)

    override suspend fun readSteps(
        startTime: Instant, endTime: Instant
    ): List<StepsRecord> = readRecords(startTime, endTime)

    override suspend fun readDistance(
        startTime: Instant, endTime: Instant
    ): List<DistanceRecord> = readRecords(startTime, endTime)

    override suspend fun readFloorsClimbed(
        startTime: Instant,
        endTime: Instant
    ): List<FloorsClimbedRecord> = readRecords(startTime, endTime)

    override suspend fun readVo2Max(
        startTime: Instant, endTime: Instant
    ): List<Vo2MaxRecord> = readRecords(startTime, endTime)

    override suspend fun readBloodGlucose(
        startTime: Instant,
        endTime: Instant
    ): List<BloodGlucoseRecord> = readRecords(startTime, endTime)

    override suspend fun readBloodPressure(
        startTime: Instant,
        endTime: Instant
    ): List<BloodPressureRecord> = readRecords(startTime, endTime)

    override suspend fun readHydration(
        startTime: Instant,
        endTime: Instant
    ): List<HydrationRecord> = readRecords(startTime, endTime)

    private suspend inline fun <reified T : Record> readRecords(
        startTime: Instant, endTime: Instant
    ): List<T> {
        return returnEmptyIfException {
            healthConnectClient.readRecords(
                ReadRecordsRequest(
                    T::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            ).records
        }
    }
}