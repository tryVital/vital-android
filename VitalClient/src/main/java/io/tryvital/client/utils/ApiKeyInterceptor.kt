package io.tryvital.client.utils

import okhttp3.Interceptor
import okhttp3.Response

class ApiKeyInterceptor(val apiKey: String): Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder().addHeader("x-vital-api-key", apiKey).build()
        return chain.proceed(request)
    }
}