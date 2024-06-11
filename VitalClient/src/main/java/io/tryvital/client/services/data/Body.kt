package io.tryvital.client.services.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.*

@JsonClass(generateAdapter = true)
data class BodyDataResponse(
    val body: List<BodyData>
)

@JsonClass(generateAdapter = true)
data class BodyData(
    @Json(name = "user_id")
    val userId: String,
    val id: String,
    @Json(name = "calendar_date")
    val calendarDate: String,
    val weight: Double?,
    val fat: Double?,
    val source: Source,
)
