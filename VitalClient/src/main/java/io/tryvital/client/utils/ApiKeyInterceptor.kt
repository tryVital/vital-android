package io.tryvital.client.utils

import io.tryvital.client.ApiKeyProvider
import okhttp3.Interceptor
import okhttp3.Response

class ApiKeyInterceptor(private val apiKeyProvider: ApiKeyProvider): Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val apiKey = apiKeyProvider.get()
        val request = chain.request().newBuilder().addHeader("x-vital-api-key", apiKey).build()
        return chain.proceed(request)
    }
}