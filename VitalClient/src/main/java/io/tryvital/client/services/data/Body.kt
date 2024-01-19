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
    val userId: String?,
    @Json(name = "user_key")
    val userKey: String?,
    val id: String,
    val date: Date,
    val weight: Double?,
    val fat: Double?,
    val source: Source,
)
