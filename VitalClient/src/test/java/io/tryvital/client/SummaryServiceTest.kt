package io.tryvital.client

import io.tryvital.client.dependencies.Dependencies
import io.tryvital.client.services.SummaryService
import io.tryvital.client.services.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import java.time.Instant
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("BlockingMethodInNonBlockingContext")
class SummaryServiceTest {
    @Before
    fun setUp() {
        server = MockWebServer()

        retrofit = Dependencies.createRetrofit(
            server.url("").toString(),
            Dependencies.createHttpClient(apiKey = apiKey),
            Dependencies.createMoshi()
        )
    }

    @After
    fun cleanUp() {
        server.shutdown()
    }

    @Test
    fun `Send workout data`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
        )

        val summaryService = SummaryService.create(retrofit)
        val response = summaryService.addWorkout(
            userId = userId,
            body = SummaryTimeframe(
                stage = "dev",
                provider = "manual",
                startDate = null,
                endDate = null,
                timeZone = null,
                data = listOf(
                    RawWorkout(
                        id = "test raw supersize",
                        startDate = Date.from(Instant.parse("2007-01-01T00:00:00.00Z")),
                        endDate = Date.from(Instant.parse("2007-01-05T00:00:00.00Z")),
                        sourceBundle = "fit",
                        deviceType = "not Iphone",
                        sport = "walking",
                        caloriesInKiloJules = 101,
                        distanceInMeter = 301,
                        heartRate = emptyList(),
                        respiratoryRate = emptyList()
                    )
                )
            )
        )
        assertEquals("POST /summary/workouts/user_id_1 HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(Unit, response)
    }

    @Test
    fun `Send profile data`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("")
        )
        val summaryService = SummaryService.create(retrofit)
        val response = summaryService.addProfile(
            userId = userId,
            body = SummaryTimeframe(
                stage = "dev",
                provider = "manual",
                startDate = null,
                endDate = null,
                timeZone = null,
                data = RawProfile(
                    biologicalSex = "not_set",
                    dateOfBirth = Date(0),
                    heightInCm = 188,
                )
            )
        )

        assertEquals("POST /summary/profile/user_id_1 HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(Unit, response)
    }

}

private lateinit var server: MockWebServer
private lateinit var retrofit: Retrofit

private const val apiKey = "API_KEY"
private const val userId = "user_id_1"


