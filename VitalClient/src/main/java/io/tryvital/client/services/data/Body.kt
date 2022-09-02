package io.tryvital.client.services.data

import com.squareup.moshi.Json
import java.util.*

data class BodyDataResponse(
    val body: List<BodyData>
)

class BodyData(
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
