package io.tryvital.client.services.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.*

@JsonClass(generateAdapter = true)
data class WorkoutsResponse(
    val workouts: List<Workout>
)

@JsonClass(generateAdapter = true)
data class Workout(
    @Json(name = "user_id")
    val userId: String,
    @Json(name = "user_key")
    val userKey: String,
    val id: String,
    val title: String?,
    @Json(name = "timezone_offset")
    val timezoneOffset: Int?,
    @Json(name = "average_hr")
    val averageHr: Double?,
    @Json(name = "max_hr")
    val maxHr: Double?,
    val distance: Double?,
    @Json(name = "time_start")
    val timeStart: Date?,
    @Json(name = "time_end")
    val timeEnd: Date?,
    val calories: Double?,
    val sport: Sport?,
    @Json(name = "hr_zones")
    val hrZones: List<Any>?,
    @Json(name = "moving_time")
    val movingTime: Int?,
    @Json(name = "total_elevation_gain")
    val totalElevationGain: Double?,
    @Json(name = "elev_high")
    val elevHigh: Double?,
    @Json(name = "elev_low")
    val elevLow: Double?,
    @Json(name = "average_speed")
    val averageSpeed: Double?,
    @Json(name = "max_speed")
    val maxSpeed: Double?,
    @Json(name = "average_watts")
    val averageWatts: Double?,
    @Json(name = "device_watts")
    val deviceWatts: Double?,
    @Json(name = "max_watts")
    val maxWatts: Double?,
    @Json(name = "weighted_average_watts")
    val weightedAverageWatts: Double?,
    val map: MapData?,
    @Json(name = "provider_id")
    val providerId: String?,
    val source: Source,
)

@JsonClass(generateAdapter = true)
data class Sport(
    val id: Int,
    val name: String,
)

@JsonClass(generateAdapter = true)
data class MapData(
    val id: String,
    val polyline: String?,
    @Json(name = "summary_polyline")
    val summaryPolyline: String?,
)

@JsonClass(generateAdapter = true)
data class WorkoutStreamResponse(
    val time: List<Int> = listOf(),
    val cadence: List<Double> = listOf(),
    val altitude: List<Double> = listOf(),
    @Json(name = "velocity_smooth")
    val velocitySmooth: List<Double> = listOf(),
    val heartrate: List<Double> = listOf(),
    val lat: List<Double> = listOf(),
    val lng: List<Double> = listOf(),
    val distance: List<Double> = listOf(),
    val power: List<Double> = listOf(),
    val resistance: List<Double> = listOf(),
)
