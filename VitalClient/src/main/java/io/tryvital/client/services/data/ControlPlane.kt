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
