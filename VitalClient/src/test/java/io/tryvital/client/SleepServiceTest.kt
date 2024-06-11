package io.tryvital.client

import io.tryvital.client.services.SleepService
import io.tryvital.client.services.data.ProviderSlug
import io.tryvital.client.services.data.SleepData
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

@OptIn(ExperimentalCoroutinesApi::class)
class SleepServiceTest {
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

        assertEquals(1, response.sleep.size)
        val sleep = response.sleep[0]
        checkFirstSleep(sleep)
    }

    private fun checkFirstSleep(sleep: SleepData) {
        assertEquals(id, sleep.id)
        assertEquals(userId, sleep.userId)
        assertEquals(21480, sleep.duration)
        assertEquals(80.0, sleep.efficiency)
        assertEquals(17.12, sleep.respiratoryRate)
        assertEquals(ProviderSlug.Oura, sleep.source.provider)
        assertEquals(SourceType.Ring, sleep.source.type)
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
    "calendar_date": "2022-07-16",
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
        "provider": "oura",
        "type": "ring"
    }
}
]
}"""
