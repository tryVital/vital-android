package io.tryvital.client.services.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.tryvital.client.utils.getJsonName
import java.time.Instant
import java.util.*

@JsonClass(generateAdapter = true)
data class TimeseriesPayload<T> (
    @Json(name = "stage")
    val stage: DataStage,
    @Json(name = "provider")
    val provider: ManualProviderSlug,
    @Json(name = "start_date")
    val startDate: Instant?,
    @Json(name = "end_date")
    val endDate: Instant?,
    @Json(name = "time_zone")
    val timeZoneId: String?,
    @Json(name = "data")
    val data: T
)

@JsonClass(generateAdapter = false)
enum class IngestibleTimeseriesResource {
    @Json(name = "vo2_max") Vo2Max,
    @Json(name = "distance") Distance,
    @Json(name = "floors_climbed") FloorsClimbed,
    @Json(name = "calories_basal") CaloriesBasal,
    @Json(name = "calories_active") CaloriesActive,
    @Json(name = "steps") Steps,
    @Json(name = "glucose") BloodGlucose,
    @Json(name = "water") Water,
    @Json(name = "heartrate") HeartRate,
    @Json(name = "heartrate_variability") HeartRateVariability;

    // Use the Json name also when converting to string.
    // This is intended for Retrofit request parameter serialization.
    override fun toString() = getJsonName(this)
}
