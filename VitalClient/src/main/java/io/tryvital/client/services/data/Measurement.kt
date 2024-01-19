package io.tryvital.client.services.data

import com.squareup.moshi.JsonClass
import java.util.*

@JsonClass(generateAdapter = true)
data class Measurement(
    val id: Int,
    val timestamp: Date,
    val value: Double?,
    val type: String?,
    val unit: String?
)