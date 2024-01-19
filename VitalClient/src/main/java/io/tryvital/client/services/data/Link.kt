package io.tryvital.client.services.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.tryvital.client.Region

@JsonClass(generateAdapter = true)
data class CreateLinkRequest(
    @Json(name = "user_id")
    val userId: String,
    val provider: String,
    @Json(name = "redirect_url")
    val redirectUrl: String,
)

@JsonClass(generateAdapter = true)
data class PasswordProviderRequest(
    val username: String,
    val password: String,
    @Json(name = "redirect_url")
    val redirectUrl: String,
)

@JsonClass(generateAdapter = true)
data class EmailProviderRequest(
    val email: String,
    val region: Region,
)

@JsonClass(generateAdapter = true)
data class ManualProviderRequest(
    @Json(name = "user_id")
    val userId: String,
    @Json(name = "provider_id")
    val providerId: String? = null,
)

@JsonClass(generateAdapter = true)
data class CreateLinkResponse(
    @Json(name = "link_token")
    val linkToken: String?,
)

@JsonClass(generateAdapter = true)
data class OauthLinkResponse(
    val name: String?,
    val slug: String?,
    val description: String?,
    val logo: String?,
    val group: String?,
    @Json(name = "oauth_url")
    val oauthUrl: String?,
    @Json(name = "auth_type")
    val authType: String?,
    @Json(name = "is_active")
    val isActive: Boolean,
    val id: Int,
)

@JsonClass(generateAdapter = true)
data class EmailProviderResponse(
    val success: Boolean,
    @Json(name = "redirect_url")
    val redirectUrl: String?,
)

@JsonClass(generateAdapter = true)
data class ManualProviderResponse(
    @Json(name = "success")
    val success: Boolean,
)