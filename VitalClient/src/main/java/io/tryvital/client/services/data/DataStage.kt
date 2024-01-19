package io.tryvital.client.services.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
enum class DataStage {
    @Json(name = "historical")
    Historical,
    @Json(name = "daily")
    Daily
}
