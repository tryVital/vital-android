package io.tryvital.client

import io.tryvital.client.services.VitalsService
import io.tryvital.client.services.data.CholesterolType
import io.tryvital.client.services.data.GroupedSamples
import io.tryvital.client.services.data.GroupedSamplesResponse
import io.tryvital.client.services.data.ProviderSlug
import io.tryvital.client.services.data.ScalarSample
import io.tryvital.client.services.data.Source
import io.tryvital.client.services.data.SourceType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class VitalsServiceTest {
    @Before
    fun setUp() {
        server = MockWebServer()
        retrofit = server.createTestRetrofit()
    }

    @After
    fun cleanUp() {
        server.shutdown()
    }

    @Test
    fun `Get cholesterol`() = runTest {

        val sut = VitalsService.create(retrofit)
        for (type in CholesterolType.values()) {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(fakeScalarSampleResponse)
            )
            val response = sut.getCholesterol(
                cholesterolType = type,
                userId = userId,
                startDate = Instant.parse("2022-07-01T00:00:00Z")!!,
                endDate = Instant.parse("2022-07-21T00:00:00Z"),
                provider = null
            )
            assertEquals(
                "GET /timeseries/$userId/cholesterol/${type.name}?start_date=2022-07-01T00%3A00%3A00Z&end_date=2022-07-21T00%3A00%3A00Z HTTP/1.1",
                server.takeRequest().requestLine
            )
            checkMeasurements(response)
        }
    }

    @Test
    fun `Get glucose`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(fakeScalarSampleResponse)
        )
        val sut = VitalsService.create(retrofit)
        val response = sut.getGlucose(
            userId = userId,
            startDate = Instant.parse("2022-07-01T00:00:00Z")!!,
            endDate = Instant.parse("2022-07-21T00:00:00Z"),
            provider = null
        )
        assertEquals(
            "GET /timeseries/$userId/glucose?start_date=2022-07-01T00%3A00%3A00Z&end_date=2022-07-21T00%3A00%3A00Z HTTP/1.1",
            server.takeRequest().requestLine
        )
        checkMeasurements(response)
    }

    private fun checkMeasurements(measurements: GroupedSamplesResponse<ScalarSample>) {
        assertEquals(
            listOf(
                GroupedSamples(
                    data = listOf(
                        ScalarSample(
                            timestamp = Instant.parse("2022-01-01T03:16:31+00:00"),
                            value = 5.7,
                            type = null,
                            unit = "count",
                        ),
                        ScalarSample(
                            timestamp = Instant.parse("2022-01-02T03:16:32+00:00"),
                            value = 10.2,
                            type = null,
                            unit = "count",
                        )
                    ),
                    source = Source(
                        type = SourceType.Watch,
                        provider = ProviderSlug.Fitbit,
                    )
                )
            ),
            measurements.groups
        )
    }

}

private lateinit var server: MockWebServer
private lateinit var retrofit: Retrofit

private const val userId = "user_id_1"


private const val fakeScalarSampleResponse = """
{
    "groups": [
        {
            "data": [
                {
                    "timestamp": "2022-01-01T03:16:31+00:00",
                    "value": 5.7,
                    "type": null,
                    "unit": "count"
                },
                {
                    "timestamp": "2022-01-02T03:16:32+00:00",
                    "value": 10.2,
                    "type": null,
                    "unit": "count"
                }
            ],
            "source": {
                "provider": "fitbit",
                "type": "watch"
            }
        }
    ],
    "next": null
}
"""