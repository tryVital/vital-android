package io.tryvital.client

import io.tryvital.client.dependencies.Dependencies
import io.tryvital.client.services.VitalsService
import io.tryvital.client.services.data.CholesterolType
import io.tryvital.client.services.data.Measurement
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import java.text.SimpleDateFormat

@OptIn(ExperimentalCoroutinesApi::class)
class VitalsServiceTest {
    @Before
    fun setUp() {
        server = MockWebServer()

        retrofit = Dependencies.createRetrofit(
            server.url("").toString(),
            Dependencies.createHttpClient(apiKeyProvider = ApiKeyProvider.Constant("")),
            Dependencies.createMoshi()
        )
    }

    @After
    fun cleanUp() {
        server.shutdown()
    }

    @Test
    fun `Get cholesterol`() = runTest {

        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        val sut = VitalsService.create(retrofit)
        for (type in CholesterolType.values()) {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(fakeVitalsResponse)
            )
            val response = sut.getCholesterol(
                cholesterolType = type,
                userId = userId,
                startDate = dateFormat.parse("2022-07-01")!!,
                endDate = dateFormat.parse("2022-07-21"),
                provider = null
            )
            assertEquals(
                "GET /timeseries/$userId/cholesterol/${type.name}?start_date=2022-07-01&end_date=2022-07-21 HTTP/1.1",
                server.takeRequest().requestLine
            )
            checkMeasurements(response)
        }
    }

    @Test
    fun `Get glucose`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(fakeVitalsResponse)
        )
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        val sut = VitalsService.create(retrofit)
        val response = sut.getGlucose(
            userId = userId,
            startDate = dateFormat.parse("2022-07-01")!!,
            endDate = dateFormat.parse("2022-07-21"),
            provider = null
        )
        assertEquals(
            "GET /timeseries/$userId/glucose?start_date=2022-07-01&end_date=2022-07-21 HTTP/1.1",
            server.takeRequest().requestLine
        )
        checkMeasurements(response)
    }

    private fun checkMeasurements(measurements: List<Measurement>) {
        assertEquals(3, measurements.size)
        assertEquals(1, measurements[0].id)
        assertEquals(5.7, measurements[0].value)
        assertEquals("automatic", measurements[0].type)
        assertEquals("mmol/L", measurements[0].unit)
        assertEquals(2, measurements[1].id)
        assertNull(measurements[1].value)
        assertNull(measurements[1].type)
        assertNull(measurements[1].unit)
        assertEquals(3, measurements[2].id)
        assertNull(measurements[2].value)
        assertNull(measurements[2].type)
        assertNull(measurements[2].unit)
    }

}

private lateinit var server: MockWebServer
private lateinit var retrofit: Retrofit

private const val apiKey = "API_KEY"
private const val userId = "user_id_1"


private const val fakeVitalsResponse = """[
    {
        "id": 1,
        "timestamp": "2022-01-01T03:16:31+00:00",
        "value": 5.7,
        "type": "automatic",
        "unit": "mmol/L"
    },
    {
        "id": 2,
        "timestamp": "2022-01-02T03:16:31+00:00",
        "value": null,
        "type": null,
        "unit": null
    },
    {
      "id": 3,
      "timestamp": "2022-01-03T03:16:31+00:00"  
    }
]"""