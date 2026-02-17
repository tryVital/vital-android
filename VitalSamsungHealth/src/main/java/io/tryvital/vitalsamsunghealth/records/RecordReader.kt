package io.tryvital.vitalsamsunghealth.records

import android.content.Context
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.ActiveCaloriesBurnedRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.BasalMetabolicRateRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.BloodGlucoseRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.BloodPressureRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.BodyFatRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.BodyTemperatureRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.CervicalMucusRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.DistanceRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.ExerciseSessionRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.FloorsClimbedRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.HeartRateRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.HeartRateVariabilityRmssdRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.HeightRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.HydrationRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.IntermenstrualBleedingRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.MenstruationFlowRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.MenstruationPeriodRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.NutritionRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.OvulationTestRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.OxygenSaturationRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.RespiratoryRateRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.RestingHeartRateRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.SexualActivityRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.SleepSessionRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.StepsRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.Vo2MaxRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.WeightRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.metadata.Metadata
import io.tryvital.vitalsamsunghealth.healthconnect.client.units.BloodGlucose
import io.tryvital.vitalsamsunghealth.healthconnect.client.units.Energy
import io.tryvital.vitalsamsunghealth.healthconnect.client.units.Length
import io.tryvital.vitalsamsunghealth.healthconnect.client.units.Mass
import io.tryvital.vitalsamsunghealth.healthconnect.client.units.Percentage
import io.tryvital.vitalsamsunghealth.healthconnect.client.units.Power
import io.tryvital.vitalsamsunghealth.healthconnect.client.units.Pressure
import io.tryvital.vitalsamsunghealth.healthconnect.client.units.Temperature
import io.tryvital.vitalsamsunghealth.healthconnect.client.units.Volume
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalsamsunghealth.SamsungHealthClientProvider
import io.tryvital.vitalsamsunghealth.ext.returnEmptyIfException
import com.samsung.android.sdk.health.data.data.AggregatedData
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.data.entries.ExerciseSession
import com.samsung.android.sdk.health.data.data.entries.HeartRate
import com.samsung.android.sdk.health.data.data.entries.OxygenSaturation
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.InstantTimeFilter
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.LocalTimeGroup
import com.samsung.android.sdk.health.data.request.LocalTimeGroupUnit
import com.samsung.android.sdk.health.data.request.Ordering
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToInt
import kotlin.math.roundToLong

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

    suspend fun readNutritionRecords(
        start: Instant,
        end: Instant,
    ): List<NutritionRecord>

    suspend fun readRespiratoryRates(start: Instant, end: Instant): List<RespiratoryRateRecord>
    suspend fun readBodyTemperatures(start: Instant, end: Instant): List<BodyTemperatureRecord>

    suspend fun menstruationPeriod(start: Instant, end: Instant): List<MenstruationPeriodRecord>
    suspend fun menstruationFlow(start: Instant, end: Instant): List<MenstruationFlowRecord>
    suspend fun cervicalMucus(start: Instant, end: Instant): List<CervicalMucusRecord>
    suspend fun sexualActivity(start: Instant, end: Instant): List<SexualActivityRecord>
    suspend fun intermenstrualBleeding(start: Instant, end: Instant): List<IntermenstrualBleedingRecord>
    suspend fun ovulationTest(start: Instant, end: Instant): List<OvulationTestRecord>
}

internal class HealthConnectRecordReader(
    private val context: Context,
    private val samsungHealthClientProvider: SamsungHealthClientProvider,
) : RecordReader {

    private val healthDataStore by lazy {
        samsungHealthClientProvider.getHealthDataStore(context)
    }

    override suspend fun readExerciseSessions(startTime: Instant, endTime: Instant): List<ExerciseSessionRecord> {
        return readPoints(startTime, endTime) { DataTypes.EXERCISE.readDataRequestBuilder }
            .flatMap { point ->
                val sessions = point.getValue(DataType.ExerciseType.SESSIONS) ?: emptyList()
                val meta = metadataOf(point)
                sessions.map { session ->
                    ExerciseSessionRecord(
                        session.startTime,
                        ZoneOffset.UTC,
                        session.endTime,
                        ZoneOffset.UTC,
                        meta,
                        mapExerciseType(session.exerciseType),
                    )
                }
            }
    }

    override suspend fun readHeartRate(startTime: Instant, endTime: Instant): List<HeartRateRecord> {
        return readPoints(startTime, endTime) { DataTypes.HEART_RATE.readDataRequestBuilder }
            .mapNotNull { point ->
                val meta = metadataOf(point)
                val series = point.getValue(DataType.HeartRateType.SERIES_DATA) ?: emptyList()
                val samples = if (series.isNotEmpty()) {
                    series.map { HeartRateRecord.Sample(it.startTime, it.heartRate.roundToLong()) }
                } else {
                    val bpm = point.getValue(DataType.HeartRateType.HEART_RATE) ?: return@mapNotNull null
                    listOf(HeartRateRecord.Sample(point.startTime, bpm.roundToLong()))
                }

                val start = samples.minOfOrNull { it.time } ?: point.startTime
                val end = series.maxOfOrNull { it.endTime } ?: point.endTime ?: start
                HeartRateRecord(start, ZoneOffset.UTC, end, ZoneOffset.UTC, samples, meta)
            }
    }

    override suspend fun readRestingHeartRate(startTime: Instant, endTime: Instant): List<RestingHeartRateRecord> = emptyList()

    override suspend fun readHeartRateVariabilityRmssd(startTime: Instant, endTime: Instant): List<HeartRateVariabilityRmssdRecord> = emptyList()

    override suspend fun readHeights(startTime: Instant, endTime: Instant): List<HeightRecord> {
        return readPoints(startTime, endTime) { DataTypes.BODY_COMPOSITION.readDataRequestBuilder }
            .mapNotNull { point ->
                val heightCm = point.getValue(DataType.BodyCompositionType.HEIGHT) ?: return@mapNotNull null
                HeightRecord(
                    point.startTime,
                    point.zoneOffset ?: ZoneOffset.UTC,
                    Length.meters(heightCm / 100.0),
                    metadataOf(point)
                )
            }
    }

    override suspend fun readWeights(startTime: Instant, endTime: Instant): List<WeightRecord> {
        return readPoints(startTime, endTime) { DataTypes.BODY_COMPOSITION.readDataRequestBuilder }
            .mapNotNull { point ->
                val weightKg = point.getValue(DataType.BodyCompositionType.WEIGHT) ?: return@mapNotNull null
                WeightRecord(
                    point.startTime,
                    point.zoneOffset ?: ZoneOffset.UTC,
                    Mass.kilograms(weightKg.toDouble()),
                    metadataOf(point)
                )
            }
    }

    override suspend fun readBodyFat(startTime: Instant, endTime: Instant): List<BodyFatRecord> {
        return readPoints(startTime, endTime) { DataTypes.BODY_COMPOSITION.readDataRequestBuilder }
            .mapNotNull { point ->
                val bodyFat = point.getValue(DataType.BodyCompositionType.BODY_FAT) ?: return@mapNotNull null
                BodyFatRecord(
                    point.startTime,
                    point.zoneOffset ?: ZoneOffset.UTC,
                    Percentage(bodyFat.toDouble()),
                    metadataOf(point)
                )
            }
    }

    override suspend fun readSleepSession(startTime: Instant, endTime: Instant): List<SleepSessionRecord> {
        return readPoints(startTime, endTime) { DataTypes.SLEEP.readDataRequestBuilder }
            .flatMap { point ->
                val meta = metadataOf(point)
                val sessions = point.getValue(DataType.SleepType.SESSIONS) ?: emptyList()

                sessions.map { session ->
                    val stages = (session.stages ?: emptyList()).map { stage ->
                        SleepSessionRecord.Stage(
                            stage.startTime,
                            stage.endTime,
                            mapSleepStage(stage.stage)
                        )
                    }

                    SleepSessionRecord(
                        session.startTime,
                        ZoneOffset.UTC,
                        session.endTime,
                        ZoneOffset.UTC,
                        meta,
                        null,
                        null,
                        stages,
                    )
                }
            }
    }

    override suspend fun readOxygenSaturation(startTime: Instant, endTime: Instant): List<OxygenSaturationRecord> {
        return readPoints(startTime, endTime) { DataTypes.BLOOD_OXYGEN.readDataRequestBuilder }
            .flatMap { point ->
                val meta = metadataOf(point)
                val series = point.getValue(DataType.BloodOxygenType.SERIES_DATA) ?: emptyList()
                if (series.isNotEmpty()) {
                    series.map {
                        OxygenSaturationRecord(
                            it.startTime,
                            ZoneOffset.UTC,
                            Percentage((it.oxygenSaturation / 100.0)),
                            meta,
                        )
                    }
                } else {
                    val value = point.getValue(DataType.BloodOxygenType.OXYGEN_SATURATION)
                    if (value == null) emptyList() else listOf(
                        OxygenSaturationRecord(
                            point.startTime,
                            point.zoneOffset ?: ZoneOffset.UTC,
                            Percentage((value / 100.0).toDouble()),
                            meta,
                        )
                    )
                }
            }
    }

    override suspend fun readActiveEnergyBurned(startTime: Instant, endTime: Instant): List<ActiveCaloriesBurnedRecord> {
        return aggregateHourly(
            startTime,
            endTime,
            DataType.ActivitySummaryType.TOTAL_ACTIVE_CALORIES_BURNED
        ).mapNotNull { item ->
            val kcal = item.value ?: return@mapNotNull null
            ActiveCaloriesBurnedRecord(
                item.startTime,
                ZoneOffset.UTC,
                item.endTime,
                ZoneOffset.UTC,
                Energy.kilocalories(kcal.toDouble()),
                Metadata.manualEntry()
            )
        }
    }

    override suspend fun readBasalMetabolicRate(startTime: Instant, endTime: Instant): List<BasalMetabolicRateRecord> {
        return readPoints(startTime, endTime) { DataTypes.BODY_COMPOSITION.readDataRequestBuilder }
            .mapNotNull { point ->
                val kcalPerDay = point.getValue(DataType.BodyCompositionType.BASAL_METABOLIC_RATE) ?: return@mapNotNull null
                BasalMetabolicRateRecord(
                    point.startTime,
                    point.zoneOffset ?: ZoneOffset.UTC,
                    Power.kilocaloriesPerDay(kcalPerDay.toDouble()),
                    metadataOf(point)
                )
            }
    }

    override suspend fun readSteps(startTime: Instant, endTime: Instant): List<StepsRecord> {
        return aggregateHourlyLong(startTime, endTime, DataType.StepsType.TOTAL)
            .mapNotNull { item ->
                val count = item.value ?: return@mapNotNull null
                StepsRecord(
                    item.startTime,
                    ZoneOffset.UTC,
                    item.endTime,
                    ZoneOffset.UTC,
                    count,
                    Metadata.manualEntry()
                )
            }
    }

    override suspend fun readDistance(startTime: Instant, endTime: Instant): List<DistanceRecord> {
        return aggregateHourly(startTime, endTime, DataType.ActivitySummaryType.TOTAL_DISTANCE)
            .mapNotNull { item ->
                val meters = item.value ?: return@mapNotNull null
                DistanceRecord(
                    item.startTime,
                    ZoneOffset.UTC,
                    item.endTime,
                    ZoneOffset.UTC,
                    Length.meters(meters.toDouble()),
                    Metadata.manualEntry()
                )
            }
    }

    override suspend fun readFloorsClimbed(startTime: Instant, endTime: Instant): List<FloorsClimbedRecord> {
        return aggregateHourly(startTime, endTime, DataType.FloorsClimbedType.TOTAL)
            .mapNotNull { item ->
                val floors = item.value ?: return@mapNotNull null
                FloorsClimbedRecord(
                    item.startTime,
                    ZoneOffset.UTC,
                    item.endTime,
                    ZoneOffset.UTC,
                    floors.toDouble(),
                    Metadata.manualEntry()
                )
            }
    }

    override suspend fun readVo2Max(startTime: Instant, endTime: Instant): List<Vo2MaxRecord> {
        return readPoints(startTime, endTime) { DataTypes.EXERCISE.readDataRequestBuilder }
            .flatMap { point ->
                val meta = metadataOf(point)
                val sessions = point.getValue(DataType.ExerciseType.SESSIONS) ?: emptyList()
                sessions.mapNotNull { session ->
                    val vo2 = session.vo2Max ?: return@mapNotNull null
                    Vo2MaxRecord(
                        session.endTime,
                        ZoneOffset.UTC,
                        meta,
                        vo2.toDouble(),
                        Vo2MaxRecord.MEASUREMENT_METHOD_OTHER,
                    )
                }
            }
    }

    override suspend fun readBloodGlucose(startTime: Instant, endTime: Instant): List<BloodGlucoseRecord> {
        return readPoints(startTime, endTime) { DataTypes.BLOOD_GLUCOSE.readDataRequestBuilder }
            .flatMap { point ->
                val meta = metadataOf(point)
                val series = point.getValue(DataType.BloodGlucoseType.SERIES_DATA) ?: emptyList()
                if (series.isNotEmpty()) {
                    series.map {
                        BloodGlucoseRecord(
                            it.timestamp,
                            ZoneOffset.UTC,
                            meta,
                            BloodGlucose.milligramsPerDeciliter(it.glucose.toDouble()),
                            BloodGlucoseRecord.SPECIMEN_SOURCE_UNKNOWN,
                            0,
                            BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN,
                        )
                    }
                } else {
                    val value = point.getValue(DataType.BloodGlucoseType.GLUCOSE_LEVEL)
                    if (value == null) emptyList() else listOf(
                        BloodGlucoseRecord(
                            point.startTime,
                            point.zoneOffset ?: ZoneOffset.UTC,
                            meta,
                            BloodGlucose.milligramsPerDeciliter(value.toDouble()),
                            BloodGlucoseRecord.SPECIMEN_SOURCE_UNKNOWN,
                            0,
                            BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN,
                        )
                    )
                }
            }
    }

    override suspend fun readBloodPressure(startTime: Instant, endTime: Instant): List<BloodPressureRecord> {
        return readPoints(startTime, endTime) { DataTypes.BLOOD_PRESSURE.readDataRequestBuilder }
            .mapNotNull { point ->
                val systolic = point.getValue(DataType.BloodPressureType.SYSTOLIC) ?: return@mapNotNull null
                val diastolic = point.getValue(DataType.BloodPressureType.DIASTOLIC) ?: return@mapNotNull null

                BloodPressureRecord(
                    point.startTime,
                    point.zoneOffset ?: ZoneOffset.UTC,
                    metadataOf(point),
                    Pressure.millimetersOfMercury(systolic.toDouble()),
                    Pressure.millimetersOfMercury(diastolic.toDouble()),
                    BloodPressureRecord.BODY_POSITION_UNKNOWN,
                    BloodPressureRecord.MEASUREMENT_LOCATION_UNKNOWN,
                )
            }
    }

    override suspend fun readHydration(startTime: Instant, endTime: Instant): List<HydrationRecord> {
        return readPoints(startTime, endTime) { DataTypes.WATER_INTAKE.readDataRequestBuilder }
            .mapNotNull { point ->
                val amount = point.getValue(DataType.WaterIntakeType.AMOUNT) ?: return@mapNotNull null
                HydrationRecord(
                    point.startTime,
                    point.zoneOffset ?: ZoneOffset.UTC,
                    point.endTime ?: point.startTime,
                    point.zoneOffset ?: ZoneOffset.UTC,
                    Volume.milliliters(amount.toDouble()),
                    metadataOf(point)
                )
            }
    }

    override suspend fun readNutritionRecords(start: Instant, end: Instant): List<NutritionRecord> = emptyList()

    override suspend fun readRespiratoryRates(start: Instant, end: Instant): List<RespiratoryRateRecord> = emptyList()

    override suspend fun readBodyTemperatures(start: Instant, end: Instant): List<BodyTemperatureRecord> {
        return readPoints(start, end) { DataTypes.BODY_TEMPERATURE.readDataRequestBuilder }
            .mapNotNull { point ->
                val celsius = point.getValue(DataType.BodyTemperatureType.BODY_TEMPERATURE) ?: return@mapNotNull null
                BodyTemperatureRecord(
                    point.startTime,
                    point.zoneOffset ?: ZoneOffset.UTC,
                    metadataOf(point),
                    Temperature.celsius(celsius.toDouble()),
                    0,
                )
            }
    }

    override suspend fun menstruationPeriod(start: Instant, end: Instant): List<MenstruationPeriodRecord> = emptyList()
    override suspend fun menstruationFlow(start: Instant, end: Instant): List<MenstruationFlowRecord> = emptyList()
    override suspend fun cervicalMucus(start: Instant, end: Instant): List<CervicalMucusRecord> = emptyList()
    override suspend fun sexualActivity(start: Instant, end: Instant): List<SexualActivityRecord> = emptyList()
    override suspend fun intermenstrualBleeding(start: Instant, end: Instant): List<IntermenstrualBleedingRecord> = emptyList()
    override suspend fun ovulationTest(start: Instant, end: Instant): List<OvulationTestRecord> = emptyList()

    private suspend fun readPoints(
        startTime: Instant,
        endTime: Instant,
        builderFactory: () -> com.samsung.android.sdk.health.data.request.ReadDataRequest.DualTimeBuilder<HealthDataPoint>
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
        operation: com.samsung.android.sdk.health.data.data.AggregateOperation<Float, com.samsung.android.sdk.health.data.request.AggregateRequest.LocalTimeBuilder<Float>>,
    ): List<AggregatedData<Float>> {
        val zone = ZoneId.systemDefault()
        val request = operation.requestBuilder
            .setLocalTimeFilterWithGroup(
                LocalTimeFilter.of(startTime.atZone(zone).toLocalDateTime(), endTime.atZone(zone).toLocalDateTime()),
                LocalTimeGroup.of(LocalTimeGroupUnit.HOURLY, 1)
            )
            .setOrdering(Ordering.ASC)
            .build()

        return healthDataStore.aggregateData(request).dataList
    }

    private suspend fun aggregateHourlyLong(
        startTime: Instant,
        endTime: Instant,
        operation: com.samsung.android.sdk.health.data.data.AggregateOperation<Long, com.samsung.android.sdk.health.data.request.AggregateRequest.LocalTimeBuilder<Long>>,
    ): List<AggregatedData<Long>> {
        val zone = ZoneId.systemDefault()
        val request = operation.requestBuilder
            .setLocalTimeFilterWithGroup(
                LocalTimeFilter.of(startTime.atZone(zone).toLocalDateTime(), endTime.atZone(zone).toLocalDateTime()),
                LocalTimeGroup.of(LocalTimeGroupUnit.HOURLY, 1)
            )
            .setOrdering(Ordering.ASC)
            .build()

        return healthDataStore.aggregateData(request).dataList
    }

    private fun metadataOf(point: HealthDataPoint): Metadata {
        return Metadata.manualEntryWithId(point.uid)
    }

    private fun mapSleepStage(stage: DataType.SleepType.StageType): Int {
        return when (stage) {
            DataType.SleepType.StageType.AWAKE -> SleepSessionRecord.STAGE_TYPE_AWAKE
            DataType.SleepType.StageType.LIGHT -> SleepSessionRecord.STAGE_TYPE_LIGHT
            DataType.SleepType.StageType.DEEP -> SleepSessionRecord.STAGE_TYPE_DEEP
            DataType.SleepType.StageType.REM -> SleepSessionRecord.STAGE_TYPE_REM
            else -> SleepSessionRecord.STAGE_TYPE_UNKNOWN
        }
    }

    private fun mapExerciseType(type: DataType.ExerciseType.PredefinedExerciseType): Int {
        val key = type.name.lowercase()
        return ExerciseSessionRecord.EXERCISE_TYPE_STRING_TO_INT_MAP[key]
            ?: ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
    }
}
