import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.tryvital.client.services.data.DataStage
import java.time.Instant
import java.util.Date


@JsonClass(generateAdapter = true)
data class UserSDKSyncStateBody(
    val tzinfo: String,
    @Json(name = "request_start_date")
    val requestStartDate: Instant?,
    @Json(name = "request_end_date")
    val requestEndDate: Instant?,
)

@JsonClass(generateAdapter = false)
enum class UserSDKSyncStatus {
    @Json(name = "paused") Paused,
    @Json(name = "error") Error,
    @Json(name = "active") Active;
}

@JsonClass(generateAdapter = true)
data class UserSDKSyncStateResponse(
    val status: UserSDKSyncStatus,
    @Json(name = "request_start_date")
    val requestStartDate: Instant?,
    @Json(name = "request_end_date")
    val requestEndDate: Instant?,
    @Json(name = "per_device_activity_ts")
    val perDeviceActivityTS: Boolean = false,
    @Json(name = "expires_in")
    val expiresIn: Long = 14400,
) {
    override fun toString(): String = buildString {
        append("(status=${status.name}, perDeviceActivityTs=$perDeviceActivityTS, expiresIn=$expiresIn")
        if (requestStartDate != null) {
            append(", start=")
            append(requestStartDate.toString())
        }
        if (requestEndDate != null) {
            append(", end=")
            append(requestEndDate.toString())
        }
        append(")")
    }
}

@JsonClass(generateAdapter = true)
data class ManualProviderRequest(
    @Json(name = "user_id")
    val userId: String,
    @Json(name = "provider_id")
    val providerId: String? = null,
)

@JsonClass(generateAdapter = true)
data class ManualProviderResponse(
    @Json(name = "success")
    val success: Boolean,
)
