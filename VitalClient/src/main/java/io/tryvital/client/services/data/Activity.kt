package io.tryvital.client.services.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.LocalDate
import java.util.*

@JsonClass(generateAdapter = true)
data class ActivitiesResponse(
    val activity: List<Activity>
)

@JsonClass(generateAdapter = true)
data class Activity(
    @Json(name = "user_id")
    val userId: String?,
    @Json(name = "user_key")
    val userKey: String?,
    val id: String,
    val date: Date,
    @Json(name = "calories_total")
    val caloriesTotal: Double?,
    @Json(name = "calories_active")
    val caloriesActive: Double?,
    val steps: Double?,
    @Json(name = "daily_movement")
    val dailyMovement: Double?,
    val low: Double?,
    val medium: Double?,
    val high: Double?,
    val source: Source,
)

@JsonClass(generateAdapter = true)
data class ActivityDaySummary(
    @Json(name = "calendar_date")
    val date: LocalDate,
    @Json(name = "active_energy_burned_sum")
    val activeEnergyBurnedSum: Double?,
    @Json(name = "basal_energy_burned_sum")
    val basalEnergyBurnedSum: Double?,
    @Json(name = "steps_sum")
    val stepsSum: Long?,
    @Json(name = "distance_walking_running_sum")
    val distanceWalkingRunningSum: Double?,
    @Json(name = "floors_climbed_sum")
    val floorsClimbedSum: Long?,
    @Json(name = "exercise_time")
    val exerciseTime: Double?,
)
