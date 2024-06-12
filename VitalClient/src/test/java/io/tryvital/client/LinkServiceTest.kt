package io.tryvital.client

import io.tryvital.client.dependencies.Dependencies
import io.tryvital.client.services.LinkService
import io.tryvital.client.services.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

@OptIn(ExperimentalCoroutinesApi::class)
class LinkServiceTest {

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
    fun `Create link token`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fakeCreateLinkResponse)
        )

        val sut = LinkService.create(retrofit)
        val response = sut.createLink(
            CreateLinkRequest(
                userId = userId,
                provider = "strava",
                redirectUrl = "callback://vital"
            )
        )

        assertEquals("POST /link/token HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(linkToken, response.linkToken)
    }

    @Test
    fun `Link oauth provider`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fakeLinkOauthProviderResponse)
        )

        val sut = LinkService.create(retrofit)
        val oauthResponse = sut.linkOauthProvider(
            provider = "strava",
            linkToken = linkToken
        )

        assertEquals("GET /link/provider/oauth/strava HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("https://www.strava.com/oauth/", oauthResponse.oauthUrl)
        assertTrue(oauthResponse.isActive)
        assertEquals("oauth", oauthResponse.authType)
        assertEquals(5, oauthResponse.id)
    }

    @Test
    fun `Link email provider`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fakeEmailLinkResponse)
        )

        val sut = LinkService.create(retrofit)
        val response = sut.linkEmailProvider(
            provider = "strava",
            linkToken = linkToken,
            request = LinkEmailProviderInput(
                email = "test@test.com",
                region = "us",
            )
        )

        assertEquals("POST /link/provider/email/strava HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(response.state, LinkResponse.State.Success)
        assertEquals("callback://vital", response.redirectUrl)
    }

    @Test
    fun `Link password provider`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fakeEmailLinkResponse)
        )

        val sut = LinkService.create(retrofit)
        val response = sut.linkPasswordProvider(
            provider = "strava",
            linkToken = linkToken,
            request = LinkPasswordProviderInput(
                username = "username",
                password = "password",
            )
        )

        assertEquals(
            "POST /link/provider/password/strava HTTP/1.1",
            server.takeRequest().requestLine
        )
        assertEquals(response.state, LinkResponse.State.Success)
        assertEquals("callback://vital", response.redirectUrl)
    }

    private lateinit var server: MockWebServer
    private lateinit var retrofit: Retrofit

    private val apiKey = "API_KEY"
    private val userId = "user_id_1"
    private val linkToken = "linkTokenSample"

    private val fakeCreateLinkResponse = """{
    "link_token": "linkTokenSample"
}"""

    private val fakeLinkOauthProviderResponse = """{
"name": "Strava",
"slug": "strava",
"description": "Activity Social Network",
"logo": "https://storage.googleapis.com/vital-assets/strava.png",
"group": null,
"oauth_url": "https://www.strava.com/oauth/",
"auth_type": "oauth",
"is_active": true,
"id": 5
}"""

    private val fakeEmailLinkResponse = """{"state":"success","redirect_url":"callback://vital"}"""

}