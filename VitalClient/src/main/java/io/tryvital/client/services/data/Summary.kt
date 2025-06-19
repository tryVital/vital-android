package io.tryvital.client.services.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.tryvital.client.services.VitalPrivateApi
import java.time.Instant

// @VitalPrivateApi
@JsonClass(generateAdapter = true)
data class SummaryPayload<T>(
    @Json(name = "stage")
    val stage: DataStage,
    @Json(name = "provider")
    val provider: ManualProviderSlug,
    @Json(name = "start_date")
    val startDate: Instant?,
    @Json(name = "end_date")
    val endDate: Instant?,
    @Json(name = "time_zone")
    val timeZoneId: String?,
    @Json(name = "data")
    val data: T
)

// @VitalPrivateApi
@JsonClass(generateAdapter = true)
data class LocalWorkout(
    @Json(name = "id")
    val id: String,
    @Json(name = "start_date")
    val startDate: Instant,
    @Json(name = "end_date")
    val endDate: Instant,
    @Json(name = "source_bundle")
    val sourceBundle: String?,
    @Json(name = "product_type")
    val deviceModel: String?,
    @Json(name = "source_type")
    val sourceType: SourceType?,
    @Json(name = "sport")
    val sport: String,
    @Json(name = "calories")
    val calories: Double,
    @Json(name = "distance")
    val distance: Double,
    @Json(name = "heart_rate_maximum")
    val heartRateMaximum: Int? = null,
    @Json(name = "heart_rate_minimum")
    val heartRateMinimum: Int? = null,
    @Json(name = "heart_rate_mean")
    val heartRateMean: Int? = null,
    @Json(name = "heart_rate_zone_1")
    val heartRateZone1: Int? = null,
    @Json(name = "heart_rate_zone_2")
    val heartRateZone2: Int? = null,
    @Json(name = "heart_rate_zone_3")
    val heartRateZone3: Int? = null,
    @Json(name = "heart_rate_zone_4")
    val heartRateZone4: Int? = null,
    @Json(name = "heart_rate_zone_5")
    val heartRateZone5: Int? = null,
    @Json(name = "heart_rate_zone_6")
    val heartRateZone6: Int? = null,
)

// @VitalPrivateApi
@JsonClass(generateAdapter = true)
data class LocalActivity(
    @Json(name = "day_summary")
    val daySummary: ActivityDaySummary? = null,
    @Json(name = "active_energy_burned")
    val activeEnergyBurned: List<LocalQuantitySample> = emptyList(),
    @Json(name = "basal_energy_burned")
    val basalEnergyBurned: List<LocalQuantitySample> = emptyList(),
    @Json(name = "steps")
    val steps: List<LocalQuantitySample> = emptyList(),
    @Json(name = "distance_walking_running")
    val distanceWalkingRunning: List<LocalQuantitySample> = emptyList(),
    @Json(name = "vo2_max")
    val vo2Max: List<LocalQuantitySample> = emptyList(),
    @Json(name = "floors_climbed")
    val floorsClimbed: List<LocalQuantitySample> = emptyList(),
)

// @VitalPrivateApi
@JsonClass(generateAdapter = true)
data class LocalProfile(
    @Json(name = "biological_sex")
    val biologicalSex: String?,
    @Json(name = "date_of_birth")
    val dateOfBirth: Instant?,
    @Json(name = "height")
    val heightInCm: Int?,
)

// @VitalPrivateApi
@JsonClass(generateAdapter = true)
data class LocalBody(
    @Json(name = "body_mass")
    val bodyMass: List<LocalQuantitySample>,
    @Json(name = "body_fat_percentage")
    val bodyFatPercentage: List<LocalQuantitySample>,
)

// @VitalPrivateApi
@JsonClass(generateAdapter = true)
data class LocalSleep(
    @Json(name = "id")
    val id: String,
    @Json(name = "start_date")
    val startDate: Instant,
    @Json(name = "end_date")
    val endDate: Instant,
    @Json(name = "source_bundle")
    val sourceBundle: String?,
    @Json(name = "product_type")
    val deviceModel: String?,
    @Json(name = "source_type")
    val sourceType: SourceType?,
    @Json(name = "heart_rate_maximum")
    val heartRateMaximum: Int? = null,
    @Json(name = "heart_rate_minimum")
    val heartRateMinimum: Int? = null,
    @Json(name = "heart_rate_mean")
    val heartRateMean: Int? = null,
    @Json(name = "hrv_mean_sdnn")
    val hrvMeanSdnn: Double? = null,
    @Json(name = "respiratory_rate_mean")
    val respiratoryRateMean: Double? = null,
    @Json(name = "sleep_stages")
    val sleepStages: Stages,
) {

    @JsonClass(generateAdapter = true)
    data class Stages(
        val awakeSleepSamples: List<LocalQuantitySample>,
        val deepSleepSamples: List<LocalQuantitySample>,
        val lightSleepSamples: List<LocalQuantitySample>,
        val remSleepSamples: List<LocalQuantitySample>,
        val outOfBedSleepSamples: List<LocalQuantitySample>,
        val unknownSleepSamples: List<LocalQuantitySample>,
    )

    enum class Stage(val id: Int) {
        Deep(1),
        Light(2),
        Rem(3),
        Awake(4),
        OutOfBed(5),
        Unknown(-1),
    }
}

// @VitalPrivateApi
@JsonClass(generateAdapter = true)
data class LocalMenstrualCycle(
    @Json(name = "source_bundle")
    val sourceBundle: String,

    val cycle: MenstrualCycle,
)

@JsonClass(generateAdapter = true)
data class ManualMealCreation(
    @Json(name = "health_connect")
    val healthConnect: HealthConnectRecordCollection? = null
)

@JsonClass(generateAdapter = true)
data class LocalQuantitySample(
    @Json(name = "id")
    val id: String? = null,
    @Json(name = "value")
    val value: Double,
    @Json(name = "unit")
    val unit: String,
    @Json(name = "start_date")
    val startDate: Instant,
    @Json(name = "end_date")
    val endDate: Instant,
    @Json(name = "source_bundle")
    val sourceBundle: String? = null,
    @Json(name = "product_type")
    val deviceModel: String? = null,
    @Json(name = "type")
    val type: SourceType? = null,
    @Json(name = "metadata")
    val metadata: Map<String, String> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class LocalBloodPressureSample(
    @Json(name = "systolic")
    val systolic: LocalQuantitySample,
    @Json(name = "diastolic")
    val diastolic: LocalQuantitySample,
    @Json(name = "pulse")
    val pulse: LocalQuantitySample?,
)


sealed class SampleType(val unit: String) {
    object RespiratoryRate : SampleType("bpm")
    object Weight : SampleType("kg")
    object BodyFat : SampleType("percent")
    object HeartRate : SampleType("bpm")
    object HeartRateVariabilityRmssd : SampleType("rmssd")
    object OxygenSaturation : SampleType("%")
    object ActiveCaloriesBurned : SampleType("kJ")
    object Steps : SampleType("")
    object Distance : SampleType("m")
    object FloorsClimbed : SampleType("")
    object Vo2Max : SampleType("mL/kg/min")
    object BasalMetabolicRate : SampleType("kJ")
    object GlucoseConcentrationMillimolePerLitre : SampleType("mmol/L")
    object GlucoseConcentrationMilligramPerDecilitre : SampleType("mg/dL")
    object BloodPressureSystolic : SampleType("mmHg")
    object BloodPressureDiastolic : SampleType("mmHg")
    object Water : SampleType("ml")
    object Temperature : SampleType("°C")
}