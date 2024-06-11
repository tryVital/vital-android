package io.tryvital.client

import io.tryvital.client.dependencies.Dependencies
import io.tryvital.client.services.ActivityService
import io.tryvital.client.services.data.ProviderSlug
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
class ActivityServiceTest {
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
    fun `Get activity`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fakeActivityResponse)
        )
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        val sut = ActivityService.create(retrofit)
        val response = sut.getActivity(
            userId = userId,
            startDate = dateFormat.parse("2022-07-01")!!,
            endDate = dateFormat.parse("2022-07-21"),
            provider = null
        )
        assertEquals(
            "GET /summary/activity/$userId?start_date=2022-07-01&end_date=2022-07-21 HTTP/1.1",
            server.takeRequest().requestLine
        )

        assertEquals(1, response.activity.size)
        val activity = response.activity[0]
        assertEquals("id_1", activity.id)
        assertEquals(userId, activity.userId)
        assertEquals(0.0, activity.steps)
        assertEquals(1565.0, activity.caloriesTotal)
        assertEquals(1565.0, activity.caloriesActive)
        assertEquals(ProviderSlug.Fitbit, activity.source.provider)
        assertEquals(SourceType.Watch, activity.source.type)
    }

}

private lateinit var server: MockWebServer
private lateinit var retrofit: Retrofit

private const val apiKey = "API_KEY"
private const val userId = "user_id_1"


const val fakeActivityResponse = """{
"activity": [
{
    "user_id": "user_id_1",
    "user_key": "user_key_1",
    "id": "id_1",
    "calendar_date": "2022-07-22",
    "calories_total": 1565.0,
    "calories_active": 1565.0,
    "steps": 0,
    "daily_movement": 0.0,
    "low": 0.0,
    "medium": 0.0,
    "high": 0.0,
    "source": {
        "provider": "fitbit",
        "type": "watch"
    }
}
]
}"""