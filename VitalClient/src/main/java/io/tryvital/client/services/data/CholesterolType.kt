package io.tryvital.client.services.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
enum class CholesterolType {
    @Json(name = "ldn")
    Ldn,
    @Json(name = "total")
    Total,
    @Json(name = "triglycerides")
    Triglycerides,
    @Json(name = "hdl")
    Hdl,
}