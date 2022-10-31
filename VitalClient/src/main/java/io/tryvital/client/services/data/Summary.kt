package io.tryvital.client.services.data

import com.squareup.moshi.Json
import java.util.*

data class SummaryTimeframe<T>(
    @Json(name = "stage")
    val stage: String,
    @Json(name = "provider")
    val provider: String,
    @Json(name = "start_date")
    val startDate: Date?,
    @Json(name = "end_date")
    val endDate: Date?,
    @Json(name = "time_zone")
    val timeZoneInSecond: String?,
    @Json(name = "data")
    val data: T
)

data class WorkoutPayload(
    @Json(name = "id")
    val id: String,
    @Json(name = "start_date")
    val startDate: Date,
    @Json(name = "end_date")
    val endDate: Date,
    @Json(name = "source_bundle")
    val sourceBundle: String?,
    @Json(name = "product_type")
    val deviceModel: String?,
    @Json(name = "sport")
    val sport: String,
    @Json(name = "calories")
    val caloriesInKiloJules: Long,
    @Json(name = "distance")
    val distanceInMeter: Long,
    @Json(name = "heart_rate")
    val heartRate: List<QuantitySample>,
    @Json(name = "respiratory_rate")
    val respiratoryRate: List<QuantitySample>
)

data class ProfilePayload(
    @Json(name = "biological_sex")
    val biologicalSex: String,
    @Json(name = "date_of_birth")
    val dateOfBirth: Date,
    @Json(name = "height")
    val heightInCm: Int,
)

data class BodyPayload(
    @Json(name = "body_mass")
    val bodyMass: List<QuantitySample>,
    @Json(name = "body_fat_percentage")
    val bodyFatPercentage: List<QuantitySample>,
)

data class SleepPayload(
    @Json(name = "id")
    val id: String,
    @Json(name = "start_date")
    val startDate: Date,
    @Json(name = "end_date")
    val endDate: Date,
    @Json(name = "source_bundle")
    val sourceBundle: String?,
    @Json(name = "product_type")
    val deviceModel: String?,
    @Json(name = "heart_rate")
    val heartRate: List<QuantitySample>,
    @Json(name = "resting_heart_rate")
    val restingHeartRate: List<QuantitySample>,
    @Json(name = "heart_rate_variability")
    val heartRateVariability: List<QuantitySample>,
    @Json(name = "oxygen_saturation")
    val oxygenSaturation: List<QuantitySample>,
    @Json(name = "respiratory_rate")
    val respiratoryRate: List<QuantitySample>,

    )

data class QuantitySample(
    @Json(name = "id")
    val id: String,
    @Json(name = "value")
    val value: String,
    @Json(name = "unit")
    val unit: String,
    @Json(name = "start_date")
    val startDate: Date,
    @Json(name = "end_date")
    val endDate: Date,
    @Json(name = "source_bundle")
    val sourceBundle: String? = null,
    @Json(name = "product_type")
    val deviceModel: String? = null,
    @Json(name = "type")
    val type: String? = null,
    @Json(name = "metadata")
    val metadata: String? = null,
)

sealed class SampleType(val unit: String) {
    object HeartRate : SampleType("bpm")
    object RespiratoryRate : SampleType("bpm")
    object Weight : SampleType("kg")
    object BodyFat : SampleType("percent")
    object HeartRateVariabilitySdnn : SampleType("rmssd")
    object OxygenSaturation : SampleType("percent")
}