package io.tryvital.client.services.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreateSignInTokenResponse(
    @Json(name = "user_id")
    val userId: String,
    @Json(name = "sign_in_token")
    val signInToken: String,
)

@JsonClass(generateAdapter = true)
data class CreateUserRequest(
    @Json(name = "client_user_id")
    val clientUserId: String,
    @Json(name = "fallback_time_zone")
    val fallbackTimeZone: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateUserResponse(
    @Json(name = "user_id")
    val userId: String,
    @Json(name = "user_key")
    val userKey: String?,
    @Json(name = "client_user_id")
    val clientUserId: String
)

@JsonClass(generateAdapter = true)
data class DeleteUserResponse(
    val success: Boolean,
    val error: String?
)

@JsonClass(generateAdapter = true)
data class GetAllUsersResponse(
    @Json(name = "users")
    val users: List<User>?
)