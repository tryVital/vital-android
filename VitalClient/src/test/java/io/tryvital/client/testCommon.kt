package io.tryvital.client

import io.tryvital.client.dependencies.Dependencies
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit

fun MockWebServer.createTestRetrofit(): Retrofit {
    return Dependencies.createRetrofit(
        this.url("").toString(),
        Dependencies.createHttpClient(
            null,
            StaticConfiguration(
                authStrategy = VitalClientAuthStrategy.APIKey(
                    environment = Environment.Dev,
                    region = Region.US,
                    apiKey = "fake-api-key",
                )
            ),
            MockVitalJWTAuth()
        ),
        Dependencies.createMoshi()
    )
}
