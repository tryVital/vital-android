package io.tryvital.client

import io.tryvital.client.services.ProfileService
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

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileServiceTest {
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
    fun `Get profile`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fakeProfileResponse)
        )
        val sut = ProfileService.create(retrofit)
        val profile = sut.getProfile(
            userId = userId,
            provider = null
        )
        assertEquals(
            "GET /summary/profile/$userId HTTP/1.1",
            server.takeRequest().requestLine
        )
        assertEquals(userId, profile.id)
        assertEquals(userId, profile.userId)
        assertEquals(180.0, profile.height)
        assertEquals(ProviderSlug.Oura, profile.source.provider)
        assertEquals(SourceType.App, profile.source.type)
    }
}

private lateinit var server: MockWebServer
private lateinit var retrofit: Retrofit

private const val apiKey = "API_KEY"
private const val userId = "user_id_1"


const val fakeProfileResponse = """
{
"user_id": "user_id_1",
"user_key": "user_key_1",
"id": "user_id_1",
"height": 180,
"source": {
    "provider": "oura",
    "type": "app"
}
}
"""
