package io.tryvital.client.services.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.*

@JsonClass(generateAdapter = true)
data class SleepResponse(
    val sleep: List<SleepData>
)

@JsonClass(generateAdapter = true)
data class SleepData(
    @Json(name = "user_id")
    val userId: String?,
    @Json(name = "user_key")
    val userKey: String?,
    val id: String,
    val date: Date,
    @Json(name = "bedtime_start")
    val bedtimeStart: Date?,
    @Json(name = "bedtime_stop")
    val bedtimeStop: Date?,
    @Json(name = "timezone_offset")
    val timezoneOffset: Int?,
    val duration: Int?,
    val total: Int?,
    val awake: Int?,
    val light: Int?,
    val rem: Int?,
    val deep: Int?,
    val score: Int?,
    @Json(name = "hr_lowest")
    val hrLowest: Int?,
    @Json(name = "hr_average")
    val hrAverage: Int?,
    val efficiency: Double?,
    val latency: Int?,
    @Json(name = "temperature_delta")
    val temperatureDelta: Double?,
    @Json(name = "average_hrv")
    val averageHrv: Double?,
    @Json(name = "respiratory_rate")
    val respiratoryRate: Double?,
    val source: Source,
    @Json(name = "sleep_stream")
    val sleepStream: SleepStreamResponse?,
)

@JsonClass(generateAdapter = true)
data class SleepStreamResponse(
    val hrv: List<Measurement> = emptyList(),
    val heartrate: List<Measurement> = emptyList(),
    val hypnogram: List<Measurement> = emptyList(),
    @Json(name = "respiratory_rate")
    val respiratoryRate: List<Measurement> = emptyList()
)
