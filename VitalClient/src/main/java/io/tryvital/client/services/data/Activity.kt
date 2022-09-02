package io.tryvital.client.services.data

import com.squareup.moshi.Json
import java.util.*

data class ActivitiesResponse(
    val activity: List<Activity>
)

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
