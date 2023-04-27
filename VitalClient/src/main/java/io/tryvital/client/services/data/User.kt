package io.tryvital.client.services.data

import com.squareup.moshi.Json
import java.util.*

data class User(
    @Json(name = "user_id")
    val userId: String,
    @Json(name = "user_key")
    val userKey: String?,
    @Json(name = "team_id")
    val teamId: String,
    @Json(name = "client_user_id")
    val clientUserId: String,
    @Json(name = "created_on")
    val createdOn: Date,
    @Json(name = "connected_sources")
    val connectedSources: List<ConnectedSource>
)

data class ConnectedSource(
    val source: Source,
    @Json(name = "created_on")
    val createdOn: Date
)

data class Source(
    val name: String,
    val slug: ProviderSlug,
    val logo: String?
)

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

data class ProvidersResponse(
    val providers: List<Source> = emptyList()
)

data class CreateUserRequest(
    @Json(name = "client_user_id")
    val clientUserId: String,
    @Json(name = "fallback_time_zone")
    val fallbackTimeZone: String? = null
)

data class CreateUserResponse(
    @Json(name = "user_id")
    val userId: String,
    @Json(name = "user_key")
    val userKey: String?,
    @Json(name = "client_user_id")
    val clientUserId: String
)

data class DeleteUserResponse(
    val success: Boolean,
    val error: String?
)

data class DeregisterProviderResponse(
    val success: Boolean
)

data class GetAllUsersResponse(
    @Json(name = "users")
    val users: List<User>?
)