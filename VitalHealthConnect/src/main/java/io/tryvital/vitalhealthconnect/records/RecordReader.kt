package io.tryvital.vitalhealthconnect.records

import android.content.Context
import android.os.RemoteException
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.CervicalMucusRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.IntermenstrualBleedingRecord
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.OvulationTestRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SexualActivityRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalhealthconnect.HealthConnectClientProvider
import io.tryvital.vitalhealthconnect.ext.returnEmptyIfException
import kotlinx.coroutines.delay
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

    suspend fun menstruationPeriod(start: Instant, end: Instant): List<MenstruationPeriodRecord>
    suspend fun menstruationFlow(start: Instant, end: Instant): List<MenstruationFlowRecord>
    suspend fun cervicalMucus(start: Instant, end: Instant): List<CervicalMucusRecord>
    suspend fun sexualActivity(start: Instant, end: Instant): List<SexualActivityRecord>
    suspend fun intermenstrualBleeding(start: Instant, end: Instant): List<IntermenstrualBleedingRecord>
    suspend fun ovulationTest(start: Instant, end: Instant): List<OvulationTestRecord>
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


    override suspend fun menstruationPeriod(start: Instant, end: Instant): List<MenstruationPeriodRecord>
        = readRecords(start, end)
    override suspend fun menstruationFlow(start: Instant, end: Instant): List<MenstruationFlowRecord>
        = readRecords(start, end)
    override suspend fun cervicalMucus(start: Instant, end: Instant): List<CervicalMucusRecord>
        = readRecords(start, end)
    override suspend fun sexualActivity(start: Instant, end: Instant): List<SexualActivityRecord>
        = readRecords(start, end)
    override suspend fun intermenstrualBleeding(start: Instant, end: Instant): List<IntermenstrualBleedingRecord>
        = readRecords(start, end)
    override suspend fun ovulationTest(start: Instant, end: Instant): List<OvulationTestRecord>
        = readRecords(start, end)

    private suspend inline fun <reified T : Record> readRecords(
        startTime: Instant, endTime: Instant
    ): List<T> {
        return returnEmptyIfException {
            val records = mutableListOf<T>()
            var pageToken: String? = null

            do {
                val result = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        T::class,
                        timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                        pageToken = pageToken,
                    )
                )

                records.addAll(result.records)

                // pageToken can be empty string (undocumented) or null.
                pageToken = if (result.pageToken.isNullOrBlank()) null else result.pageToken
                VitalLogger.getOrCreate().info { "${T::class.simpleName}: received ${result.records.size} pageToken = ${result.pageToken}" }

            } while (pageToken != null)

            return@returnEmptyIfException records
        }
    }
}