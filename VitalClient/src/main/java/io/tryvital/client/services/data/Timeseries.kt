package io.tryvital.client.services.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.tryvital.client.utils.getJsonName
import java.util.*

@JsonClass(generateAdapter = true)
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

@JsonClass(generateAdapter = false)
enum class IngestibleTimeseriesResource {
    @Json(name = "glucose") BloodGlucose,
    @Json(name = "water") Water,
    @Json(name = "heartrate") HeartRate,
    @Json(name = "heartrate_variability") HeartRateVariability;

    // Use the Json name also when converting to string.
    // This is intended for Retrofit request parameter serialization.
    override fun toString() = getJsonName(this)
}
