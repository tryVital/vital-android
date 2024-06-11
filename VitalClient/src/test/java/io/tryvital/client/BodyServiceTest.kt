package io.tryvital.client

import io.tryvital.client.dependencies.Dependencies
import io.tryvital.client.services.BodyService
import io.tryvital.client.services.data.ProviderSlug
import io.tryvital.client.services.data.SourceType
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
class BodyServiceTest {
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
    fun `Get body data`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fakeBodyResponse)
        )
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        val sut = BodyService.create(retrofit)
        val response = sut.getBodyData(
            userId = userId,
            startDate = dateFormat.parse("2022-07-01")!!,
            endDate = dateFormat.parse("2022-07-21"),
            provider = null
        )
        assertEquals(
            "GET /summary/body/$userId?start_date=2022-07-01&end_date=2022-07-21 HTTP/1.1",
            server.takeRequest().requestLine
        )

        assertEquals(response.body.size, 2)
        val bodyData = response.body[0]
        assertEquals("id_1", bodyData.id)
        assertEquals(userId, bodyData.userId)
        assertEquals(0.0, bodyData.fat)
        assertEquals(80.0, bodyData.weight)
        assertEquals(SourceType.Scale, bodyData.source.type)
        assertEquals(ProviderSlug.Fitbit, bodyData.source.provider)

        val bodyData2 = response.body[1]
        assertEquals("id_2", bodyData2.id)
        assertEquals("user_id_2", bodyData2.userId)
        assertNull(bodyData2.fat)
        assertNull(bodyData2.weight)
        assertEquals(ProviderSlug.HealthConnect, bodyData2.source.provider)
        assertEquals(SourceType.App, bodyData2.source.type)

    }

}

private lateinit var server: MockWebServer
private lateinit var retrofit: Retrofit

private const val apiKey = "API_KEY"
private const val userId = "user_id_1"


const val fakeBodyResponse = """{
"body": [
        {
            "user_id": "user_id_1",
            "id": "id_1",
            "calendar_date": "2022-07-19",
            "weight": 80.0,
            "fat": 0.0,
            "source": {
                "provider": "fitbit",
                "type": "scale"
            }
        },
        {
            "user_id": "user_id_2",
            "id": "id_2",
            "calendar_date": "2022-07-18",
            "weight": null,
            "fat": null,
            "source": {
                "provider": "health_connect",
                "type": "app"
            }
        }
    ]
}"""