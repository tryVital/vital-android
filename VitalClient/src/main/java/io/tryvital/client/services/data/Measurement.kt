package io.tryvital.client.services.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.*

@JsonClass(generateAdapter = true)
data class ScalarSample(
    val timestamp: Date,
    val value: Double,
    val type: String?,
    val unit: String,
    @Json(name = "timezone_offset")
    val timezoneOffset: Int? = null,
)

@JsonClass(generateAdapter = true)
data class IntervalSample(
    val start: Date,
    val end: Date,
    val value: Double,
    val type: String?,
    val unit: String,
    @Json(name = "timezone_offset")
    val timezoneOffset: Int? = null,
)

@JsonClass(generateAdapter = true)
data class BloodPressureSample(
    val timestamp: Date,
    val systolic: Double?,
    val diastolic: Double?,
    val type: String?,
    val unit: String,
    @Json(name = "timezone_offset")
    val timezoneOffset: Int? = null,
)

@JsonClass(generateAdapter = true)
data class GroupedSamples<Sample>(
    val data: List<Sample>,
    val source: Source,
)

@JsonClass(generateAdapter = true)
data class GroupedSamplesResponse<Sample>(
    val groups: List<GroupedSamples<Sample>>,
    val next: String?,
)
