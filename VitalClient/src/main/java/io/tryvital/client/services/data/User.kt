package io.tryvital.client.services.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.adapters.EnumJsonAdapter
import java.time.Instant
import java.util.*

@JsonClass(generateAdapter = true)
data class User(
    @Json(name = "user_id")
    val userId: String,
    @Json(name = "team_id")
    val teamId: String,
    @Json(name = "client_user_id")
    val clientUserId: String,
    @Json(name = "created_on")
    val createdOn: Instant,
)

@JsonClass(generateAdapter = true)
data class Provider(
    val name: String,
    val slug: ProviderSlug,
    val logo: String,
)

@JsonClass(generateAdapter = true)
data class UserConnection(
    val name: String,
    val slug: ProviderSlug,
    val logo: String,
    val status: UserConnectionStatus,
    @Json(name = "resource_availability")
    val resourceAvailability: Map<VitalAPIResource, ResourceAvailability>,
)

@JsonClass(generateAdapter = false)
enum class UserConnectionStatus {
    @Json(name = "connected")
    Connected,
    @Json(name = "error")
    Error,
    @Json(name = "paused")
    Paused,
    @Json(name = "unrecognized")
    Unrecognized;

    companion object {
        val jsonAdapter: EnumJsonAdapter<UserConnectionStatus>
            get() = EnumJsonAdapter.create(UserConnectionStatus::class.java).withUnknownFallback(Unrecognized)
    }
}

@JsonClass(generateAdapter = true)
data class ResourceAvailability(
    val status: Status,
    val scopeRequirements: ScopeRequirementsGrants? = null,
) {

    @JsonClass(generateAdapter = false)
    enum class Status {
        @Json(name = "available")
        Available,
        @Json(name = "unavailable")
        Unavailable;

        companion object {
            val jsonAdapter: EnumJsonAdapter<Status>
                get() = EnumJsonAdapter.create(Status::class.java)
        }
    }
}

@JsonClass(generateAdapter = true)
data class ScopeRequirementsGrants(
    @Json(name = "user_granted")
    val userGranted: ScopeRequirements,
    @Json(name = "user_denied")
    val userDenied: ScopeRequirements,
)

@JsonClass(generateAdapter = true)
data class ScopeRequirements(
    val required: List<String>,
    val optional: List<String>,
)

@JsonClass(generateAdapter = true)
data class RefreshResponse(
    val success: Boolean,
    @Json(name = "user_id")
    val userId: String?,
    val error: String?,
    @Json(name = "refreshed_sources")
    val refreshedSources: List<String> = emptyList(),
    @Json(name = "failed_sources")
    val failedSources: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class UserConnectionsResponse(
    val providers: List<UserConnection> = emptyList()
)

@JsonClass(generateAdapter = true)
data class DeregisterProviderResponse(
    val success: Boolean
)
