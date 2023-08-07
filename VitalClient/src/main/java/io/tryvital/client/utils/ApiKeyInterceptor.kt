package io.tryvital.client.utils

import io.tryvital.client.ConfigurationReader
import io.tryvital.client.VitalClientUnconfigured
import okhttp3.Interceptor
import okhttp3.Response

internal class ApiKeyInterceptor(private val configurationReader: ConfigurationReader): Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val apiKey = configurationReader.apiKey ?: throw VitalClientUnconfigured()
        val request = chain.request().newBuilder().addHeader("x-vital-api-key", apiKey).build()
        return chain.proceed(request)
    }
}