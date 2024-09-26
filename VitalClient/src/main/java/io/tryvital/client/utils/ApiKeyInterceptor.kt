package io.tryvital.client.utils

import io.tryvital.client.ConfigurationReader
import io.tryvital.client.VitalClientAuthStrategy
import io.tryvital.client.VitalClientUnconfigured
import io.tryvital.client.jwt.AbstractVitalJWTAuth
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import okio.IOException

data class VitalRequestError(val wrapped: Throwable): IOException()

internal class ApiKeyInterceptor(
    private val configurationReader: ConfigurationReader,
    private val jwtAuth: AbstractVitalJWTAuth,
): Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val authStrategy = configurationReader.authStrategy ?: throw VitalRequestError(VitalClientUnconfigured())

        val request = when (authStrategy) {
            is VitalClientAuthStrategy.APIKey -> {
                chain.request().newBuilder().addHeader("x-vital-api-key", authStrategy.apiKey)
                    .build()
            }

            is VitalClientAuthStrategy.JWT -> {
                val token = try {
                    runBlocking { jwtAuth.withAccessToken { it } }
                } catch (e: Throwable) {
                    // Any new exception we introduced must be wrapped in VitalRequestError.
                    // Otherwise OkHttp will treat it as "unexpected" and crash the process.
                    // https://github.com/square/retrofit/issues/3505
                    throw VitalRequestError(e)
                }
                chain.request().newBuilder().addHeader("authorization", "Bearer $token").build()
            }
        }

        val response = chain.proceed(request)

        return if (response.code == 401 && authStrategy is VitalClientAuthStrategy.JWT) {
            val token = try {
                runBlocking {
                    jwtAuth.refreshToken()
                    jwtAuth.withAccessToken { it }
                }
            } catch (e: Throwable) {
                // Any new exception we introduced must be wrapped in VitalRequestError.
                // Otherwise OkHttp will treat it as "unexpected" and crash the process.
                // https://github.com/square/retrofit/issues/3505
                throw VitalRequestError(e)
            }
            val retryRequest = chain.request().newBuilder().addHeader("authorization", "Bearer $token").build()
            chain.proceed(retryRequest)
        } else {
            response
        }
    }
}