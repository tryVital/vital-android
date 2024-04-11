import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.tryvital.client.services.data.DataStage
import java.util.Date


@JsonClass(generateAdapter = true)
data class UserSDKSyncStateBody(
    val stage: DataStage,
    val tzinfo: String,
    @Json(name = "request_start_date")
    val requestStartDate: Date?,
    @Json(name = "request_end_date")
    val requestEndDate: Date?,
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
    val requestStartDate: Date?,
    @Json(name = "request_end_date")
    val requestEndDate: Date?,
) {
    override fun toString(): String = buildString {
        append("(status=${status.name})")
        if (requestStartDate != null) {
            append(", start=")
            append(requestStartDate.toInstant().toString())
        }
        if (requestEndDate != null) {
            append(", end=")
            append(requestEndDate.toInstant().toString())
        }
        append(")")
    }
}
