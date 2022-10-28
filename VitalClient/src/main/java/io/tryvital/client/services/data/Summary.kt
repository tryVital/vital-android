package io.tryvital.client.services.data

import com.squareup.moshi.Json
import java.util.*

data class AddWorkoutRequest(
    @Json(name = "stage")
    val stage: String,
    @Json(name = "provider")
    val provider: String,
    @Json(name = "start_date")
    val startDate: Date?,
    @Json(name = "end_date")
    val endDate: Date?,
    @Json(name = "time_zone")
    val timeZone: String?, //time zone in second
    @Json(name = "data")
    val data: List<AddWorkoutRequestData>
)

data class AddWorkoutRequestData(
    @Json(name = "id")
    val id: String,
    @Json(name = "start_date")
    val startDate: Date,
    @Json(name = "end_date")
    val endDate: Date,
    @Json(name = "source_bundle")
    val sourceBundle: String?,
    @Json(name = "product_type")
    val deviceType: String?,
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
    val deviceType: String? = null,
    @Json(name = "type")
    val type: String? = null,
    @Json(name = "metadata")
    val metadata: String? = null,
)

sealed class SampleType(val unit: String) {
    object HeartRate : SampleType("bpm")
    object RespiratoryRate : SampleType("bpm")
}