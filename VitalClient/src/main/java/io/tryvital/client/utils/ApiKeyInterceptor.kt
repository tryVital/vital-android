package io.tryvital.client.utils

import io.tryvital.client.ConfigurationReader
import io.tryvital.client.VitalClientAuthStrategy
import io.tryvital.client.VitalClientUnconfigured
import io.tryvital.client.jwt.AbstractVitalJWTAuth
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

internal class ApiKeyInterceptor(
    private val configurationReader: ConfigurationReader,
    private val jwtAuth: AbstractVitalJWTAuth,
): Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val authStrategy = configurationReader.authStrategy ?: throw VitalClientUnconfigured()

        val request = when (authStrategy) {
            is VitalClientAuthStrategy.APIKey -> {
                chain.request().newBuilder().addHeader("x-vital-api-key", authStrategy.apiKey)
                    .build()
            }

            is VitalClientAuthStrategy.JWT -> {
                val token = runBlocking { jwtAuth.withAccessToken { it } }
                chain.request().newBuilder().addHeader("authorization", "Bearer $token").build()
            }
        }

        val response = chain.proceed(request)

        return if (response.code == 401 && authStrategy is VitalClientAuthStrategy.JWT) {
            val token = runBlocking {
                jwtAuth.refreshToken()
                jwtAuth.withAccessToken { it }
            }
            val retryRequest = chain.request().newBuilder().addHeader("authorization", "Bearer $token").build()
            chain.proceed(retryRequest)
        } else {
            response
        }
    }
}