package io.tryvital.client.services.data

import com.squareup.moshi.Json

enum class DataStage {
    @Json(name = "historical")
    Historical,
    @Json(name = "daily")
    Daily
}
