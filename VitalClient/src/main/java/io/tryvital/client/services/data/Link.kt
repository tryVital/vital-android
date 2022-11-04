package io.tryvital.client.services.data

import com.squareup.moshi.Json
import io.tryvital.client.Region

data class CreateLinkRequest(
    @Json(name = "user_id")
    val userId: String,
    val provider: String,
    @Json(name = "redirect_url")
    val redirectUrl: String,
)


data class PasswordProviderRequest(
    val username: String,
    val password: String,
    @Json(name = "redirect_url")
    val redirectUrl: String,
)

data class EmailProviderRequest(
    val email: String,
    val region: Region,
)

data class ManualProviderRequest(
    @Json(name = "user_id")
    val userId: String,
    @Json(name = "provider_id")
    val providerId: String,
)

data class CreateLinkResponse(
    @Json(name = "link_token")
    val linkToken: String?,
)

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

data class EmailProviderResponse(
    val success: Boolean,
    @Json(name = "redirect_url")
    val redirectUrl: String?,
)

data class ManualProviderResponse(
    @Json(name = "success")
    val success: Boolean,
)