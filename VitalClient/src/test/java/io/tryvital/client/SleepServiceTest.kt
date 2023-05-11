package io.tryvital.client

import io.tryvital.client.dependencies.Dependencies
import io.tryvital.client.services.SleepService
import io.tryvital.client.services.data.ProviderSlug
import io.tryvital.client.services.data.SleepData
import io.tryvital.client.services.data.SleepStreamResponse
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

@OptIn(ExperimentalCoroutinesApi::class)
class SleepServiceTest {
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
    fun `Get sleep data`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fakeSleepDataResponse)
        )
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        val sut = SleepService.create(retrofit)
        val response = sut.getSleepData(
            userId = userId,
            startDate = dateFormat.parse("2022-07-01")!!,
            endDate = dateFormat.parse("2022-07-21"),
            provider = null
        )
        assertEquals(
            "GET /summary/sleep/$userId?start_date=2022-07-01&end_date=2022-07-21 HTTP/1.1",
            server.takeRequest().requestLine
        )

        assertEquals(2, response.sleep.size)
        val sleep = response.sleep[0]
        checkFirstSleep(sleep)

        assertEquals("Health Connect", response.sleep[1].source.name)
        assertEquals(ProviderSlug.HealthConnect, response.sleep[1].source.slug)
    }

    @Test
    fun `Get sleep stream series`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fakeSleepStreamSeriesResponse)
        )
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        val sut = SleepService.create(retrofit)
        val response = sut.getSleepStreamSeries(
            userId = userId,
            startDate = dateFormat.parse("2022-07-01")!!,
            endDate = dateFormat.parse("2022-07-21"),
            provider = null
        )
        assertEquals(
            "GET /summary/sleep/$userId/stream?start_date=2022-07-01&end_date=2022-07-21 HTTP/1.1",
            server.takeRequest().requestLine
        )

        assertEquals(2, response.sleep.size)
        val sleep = response.sleep[0]
        checkFirstSleep(sleep)
        checkFirstSleepStream(sleep.sleepStream!!)

        assertEquals("Health Connect", response.sleep[1].source.name)
        assertEquals(ProviderSlug.HealthConnect, response.sleep[1].source.slug)
    }

    @Test
    fun `Get sleep stream`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fakeSleepStreamResponse)
        )
        val sut = SleepService.create(retrofit)
        val response = sut.getSleepStream(
            sleepId = streamId
        )
        assertEquals(
            "GET /timeseries/sleep/$streamId/stream HTTP/1.1",
            server.takeRequest().requestLine
        )

        checkFirstSleepStream(response)
    }

    private fun checkFirstSleep(sleep: SleepData) {
        assertEquals(id, sleep.id)
        assertEquals(userId, sleep.userId)
        assertEquals(21480, sleep.duration)
        assertEquals(80.0, sleep.efficiency)
        assertEquals(17.12, sleep.respiratoryRate)
        assertEquals("Oura", sleep.source.name)
        assertEquals(ProviderSlug.Oura, sleep.source.slug)
    }

    private fun checkFirstSleepStream(sleepStream: SleepStreamResponse) {
        assertEquals(2, sleepStream.hrv.size)
        assertEquals(1, sleepStream.heartrate.size)
        assertEquals(1, sleepStream.hypnogram.size)
        assertEquals(1, sleepStream.respiratoryRate.size)
    }

}

private lateinit var server: MockWebServer
private lateinit var retrofit: Retrofit

private const val apiKey = "API_KEY"
private const val userId = "user_id_1"
const val id = "id_1"
const val streamId = "stream_id_1"

const val fakeSleepDataResponse = """{
"sleep": [
{
    "user_id": "user_id_1",
    "user_key": "user_id_1",
    "id": "id_1",
    "date": "2022-07-15T00:00:00+00:00",
    "bedtime_start": "2022-07-16T01:25:42+00:00",
    "bedtime_stop": "2022-07-16T07:23:42+00:00",
    "timezone_offset": 3600,
    "duration": 21480,
    "total": 17280,
    "awake": 4200,
    "light": 5970,
    "rem": 3420,
    "deep": 7890,
    "score": 58,
    "hr_lowest": 49,
    "hr_average": 58,
    "efficiency": 80.0,
    "latency": 1200,
    "temperature_delta": 0.28,
    "average_hrv": 42.0,
    "respiratory_rate": 17.12,
    "source": {
    "name": "Oura",
    "slug": "oura",
    "logo": "https://storage.googleapis.com/vital-assets/oura.png"
},
    "sleep_stream": null
},
{
    "user_id": "user_id_1",
    "user_key": "user_id_1",
    "id": "id_2",
    "date": "2022-07-14T00:00:00+00:00",
    "bedtime_start": null,
    "bedtime_stop": null,
    "timezone_offset": null,
    "duration": null,
    "total": null,
    "awake": null,
    "light": null,
    "rem": null,
    "deep": null,
    "score": null,
    "hr_lowest": null,
    "hr_average": null,
    "efficiency": null,
    "latency": null,
    "temperature_delta": null,
    "average_hrv": null,
    "respiratory_rate": null,
    "source": {
    "name": "Health Connect",
    "slug": "health_connect",
    "logo": null
},
    "sleep_stream": null
}
]
}"""

const val fakeSleepStreamSeriesResponse = """{
"sleep": [
{
    "user_id": "user_id_1",
    "user_key": "user_id_1",
    "id": "id_1",
    "date": "2022-07-15T00:00:00+00:00",
    "bedtime_start": "2022-07-16T01:25:42+00:00",
    "bedtime_stop": "2022-07-16T07:23:42+00:00",
    "timezone_offset": 3600,
    "duration": 21480,
    "total": 17280,
    "awake": 4200,
    "light": 5970,
    "rem": 3420,
    "deep": 7890,
    "score": 58,
    "hr_lowest": 49,
    "hr_average": 58,
    "efficiency": 80.0,
    "latency": 1200,
    "temperature_delta": 0.28,
    "average_hrv": 42.0,
    "respiratory_rate": 17.12,
    "source": {
    "name": "Oura",
    "slug": "oura",
    "logo": "https://storage.googleapis.com/vital-assets/oura.png"
},
    "sleep_stream": {
    "hrv": [
    {
        "id": 1,
        "timestamp": "2022-07-16T00:25:42+00:00",
        "value": 0.0,
        "type": "automatic",
        "unit": "rmssd"
    },
    {
        "id": 2,
        "timestamp": "2022-07-16T00:30:42+00:00",
        "value": null,
        "type": null,
        "unit": null
    }
    ],
    "heartrate": [
    {
        "id": 3,
        "timestamp": "2022-07-14T00:01:00+00:00",
        "value": 79.0,
        "type": "automatic",
        "unit": "bpm"
    }
    ],
    "hypnogram": [
    {
        "id": 4,
        "timestamp": "2022-07-16T00:25:42+00:00",
        "value": 4.0,
        "type": "automatic",
        "unit": "vital_hypnogram"
    }
    ],
    "respiratory_rate": [
    {
        "id": 5,
        "timestamp": "2022-07-16T00:25:42+00:00",
        "value": 4.0,
        "type": "automatic",
        "unit": "rate"
    }
    ]
}
},
{
    "user_id": "user_id_1",
    "user_key": "user_id_1",
    "id": "id_2",
    "date": "2022-07-14T00:00:00+00:00",
    "bedtime_start": null,
    "bedtime_stop": null,
    "timezone_offset": null,
    "duration": null,
    "total": null,
    "awake": null,
    "light": null,
    "rem": null,
    "deep": null,
    "score": null,
    "hr_lowest": null,
    "hr_average": null,
    "efficiency": null,
    "latency": null,
    "temperature_delta": null,
    "average_hrv": null,
    "respiratory_rate": null,
    "source": {
    "name": "Health Connect",
    "slug": "health_connect",
    "logo": null
},
    "sleep_stream": {}
}
]
}"""
const val fakeSleepStreamResponse = """{
"hrv": [
{
    "id": 1,
    "timestamp": "2022-07-16T00:25:42+00:00",
    "value": 0.0,
    "type": "automatic",
    "unit": "rmssd"
},
{
    "id": 2,
    "timestamp": "2022-07-16T00:30:42+00:00",
    "value": null,
    "type": null,
    "unit": null
}
],
"heartrate": [
{
    "id": 3,
    "timestamp": "2022-07-14T00:01:00+00:00",
    "value": 79.0,
    "type": "automatic",
    "unit": "bpm"
}
],
"hypnogram": [
{
    "id": 4,
    "timestamp": "2022-07-16T00:25:42+00:00",
    "value": 4.0,
    "type": "automatic",
    "unit": "vital_hypnogram"
}
],
"respiratory_rate": [
{
    "id": 5,
    "timestamp": "2022-07-16T00:25:42+00:00",
    "value": 4.0,
    "type": "automatic",
    "unit": "rate"
}
]
}"""