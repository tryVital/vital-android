package io.tryvital.client.services.data

import com.squareup.moshi.Json
import java.util.*

data class TimeseriesPayload<T> (
    @Json(name = "stage")
    val stage: DataStage,
    @Json(name = "provider")
    val provider: ManualProviderSlug,
    @Json(name = "start_date")
    val startDate: Date?,
    @Json(name = "end_date")
    val endDate: Date?,
    @Json(name = "time_zone")
    val timeZoneId: String?,
    @Json(name = "data")
    val data: T
)