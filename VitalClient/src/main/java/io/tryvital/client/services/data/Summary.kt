package io.tryvital.client.services.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant
import java.util.*

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

@JsonClass(generateAdapter = true)
data class WorkoutPayload(
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
    @Json(name = "sport")
    val sport: String,
    @Json(name = "calories")
    val caloriesInKiloJules: Double,
    @Json(name = "distance")
    val distanceInMeter: Double,
    @Json(name = "heart_rate")
    val heartRate: List<QuantitySamplePayload>,
    @Json(name = "respiratory_rate")
    val respiratoryRate: List<QuantitySamplePayload>
)

@JsonClass(generateAdapter = true)
data class ActivityPayload(
    @Json(name = "day_summary")
    val daySummary: ActivityDaySummary?,
    @Json(name = "active_energy_burned")
    val activeEnergyBurned: List<QuantitySamplePayload>,
    @Json(name = "basal_energy_burned")
    val basalEnergyBurned: List<QuantitySamplePayload>,
    @Json(name = "steps")
    val steps: List<QuantitySamplePayload>,
    @Json(name = "distance_walking_running")
    val distanceWalkingRunning: List<QuantitySamplePayload>,
    @Json(name = "vo2_max")
    val vo2Max: List<QuantitySamplePayload>,
    @Json(name = "floors_climbed")
    val floorsClimbed: List<QuantitySamplePayload>,
)

@JsonClass(generateAdapter = true)
data class ProfilePayload(
    @Json(name = "biological_sex")
    val biologicalSex: String?,
    @Json(name = "date_of_birth")
    val dateOfBirth: Instant?,
    @Json(name = "height")
    val heightInCm: Int?,
)

@JsonClass(generateAdapter = true)
data class BodyPayload(
    @Json(name = "body_mass")
    val bodyMass: List<QuantitySamplePayload>,
    @Json(name = "body_fat_percentage")
    val bodyFatPercentage: List<QuantitySamplePayload>,
)

@JsonClass(generateAdapter = true)
data class SleepPayload(
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
    @Json(name = "heart_rate")
    val heartRate: List<QuantitySamplePayload>,
    @Json(name = "resting_heart_rate")
    val restingHeartRate: List<QuantitySamplePayload>,
    @Json(name = "heart_rate_variability")
    val heartRateVariability: List<QuantitySamplePayload>,
    @Json(name = "oxygen_saturation")
    val oxygenSaturation: List<QuantitySamplePayload>,
    @Json(name = "respiratory_rate")
    val respiratoryRate: List<QuantitySamplePayload>,
)

@JsonClass(generateAdapter = true)
data class QuantitySamplePayload(
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
    val type: String? = null,
    @Json(name = "metadata")
    val metadata: String? = null,
)

@JsonClass(generateAdapter = true)
data class BloodPressureSamplePayload(
    @Json(name = "systolic")
    val systolic: QuantitySamplePayload,
    @Json(name = "diastolic")
    val diastolic: QuantitySamplePayload,
    @Json(name = "pulse")
    val pulse: QuantitySamplePayload?,
)


sealed class SampleType(val unit: String) {
    object RespiratoryRate : SampleType("bpm")
    object Weight : SampleType("kg")
    object BodyFat : SampleType("percent")
    object HeartRate : SampleType("bpm")
    object HeartRateVariabilityRmssd : SampleType("rmssd")
    object OxygenSaturation : SampleType("percent")
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
}