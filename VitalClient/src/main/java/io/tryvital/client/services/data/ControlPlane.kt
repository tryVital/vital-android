package io.tryvital.client.services.data

import com.squareup.moshi.Json

data class CreateSignInTokenResponse(
    @Json(name = "user_id")
    val userId: String,
    @Json(name = "sign_in_token")
    val signInToken: String,
)
