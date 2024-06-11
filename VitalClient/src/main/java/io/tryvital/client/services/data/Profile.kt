package io.tryvital.client.services.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Profile(
    @Json(name = "user_id")
    val userId: String,
    @Json(name = "user_key")
    val userKey: String?,
    val id: String,
    val height: Double?,
    val source: Source,
)
