package io.tryvital.client

sealed class AuthenticateRequest {
    data class APIKey(val key: String, val userId: String, val environment: Environment, val region: Region): AuthenticateRequest()
    data class SignInToken(val rawToken: String): AuthenticateRequest()
}
