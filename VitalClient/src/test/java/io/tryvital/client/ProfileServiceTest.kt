package io.tryvital.client

import io.tryvital.client.dependencies.Dependencies
import io.tryvital.client.services.ProfileService
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

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("BlockingMethodInNonBlockingContext")
class ProfileServiceTest {
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
        assertEquals("Oura", profile.source?.name)
        assertEquals("oura", profile.source?.slug)
    }

    @Test
    fun `Get profile nulls`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fakeProfileResponseNulls)
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
        assertNull(profile.height)
        assertNull(profile.source)
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
    "name": "Oura",
    "slug": "oura",
    "logo": "https://storage.googleapis.com/vital-assets/oura.png"
}
}
"""

const val fakeProfileResponseNulls = """{
"user_id": "user_id_1",
"user_key": null,
"id": "user_id_1",
"height": null,
"source": null
}"""