package io.tryvital.client.services.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.adapters.EnumJsonAdapter
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
data class LinkPasswordProviderInput(
    val username: String,
    val password: String,
    val region: String? = null,
)

@JsonClass(generateAdapter = true)
data class CompletePasswordProviderMFAInput(
    @Json(name = "mfa_code")
    val mfaCode: String,
)

@JsonClass(generateAdapter = true)
data class LinkEmailProviderInput(
    val email: String,
    val region: String? = null,
)

@JsonClass(generateAdapter = true)
data class CreateLinkResponse(
    @Json(name = "link_token")
    val linkToken: String,
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
data class LinkResponse(
    val state: State,
    @Json(name = "redirect_url")
    val redirectUrl: String? = null,
    @Json(name = "error_type")
    val errorType: String? = null,
    val error: String? = null,
    @Json(name = "provider_mfa")
    val providerMFA: ProviderMFA? = null,
) {

    @JsonClass(generateAdapter = false)
    enum class State {
        @Json(name = "success")
        Success,
        @Json(name = "error")
        Error,
        @Json(name = "pending_provider_mfa")
        PendingProviderMFA;

        companion object {
            val jsonAdapter: EnumJsonAdapter<State>
                get() = EnumJsonAdapter.create(State::class.java)
        }
    }

    @JsonClass(generateAdapter = true)
    data class ProviderMFA(
        val method: Method,
        val hint: String,
    ) {

        @JsonClass(generateAdapter = false)
        enum class Method {
            @Json(name = "sms")
            SMS,
            @Json(name = "email")
            Email;

            companion object {
                val jsonAdapter: EnumJsonAdapter<Method>
                    get() = EnumJsonAdapter.create(Method::class.java)
            }
        }
    }
}
