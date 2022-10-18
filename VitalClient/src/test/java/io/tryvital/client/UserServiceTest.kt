package io.tryvital.client

import io.tryvital.client.dependencies.Dependencies
import io.tryvital.client.services.UserService
import io.tryvital.client.services.data.CreateUserRequest
import io.tryvital.client.services.data.User
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
class UserServiceTest {

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
    fun `Get all users`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fakeUsersResponse)
        )

        val sut = UserService.create(retrofit)
        val users = sut.getAll().users

        assertEquals("GET /user/ HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(2, users?.size)
        val user = users!![0]
        validateFirstUser(user)

        assertNull(users[1].connectedSources)
    }

    @Test
    fun `Get user`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fakeUserResponse)
        )

        val sut = UserService.create(retrofit)
        val response = sut.getUser(userId)

        assertEquals("GET /user/$userId HTTP/1.1", server.takeRequest().requestLine)

        validateFirstUser(response)
    }

    @Test
    fun `Resolve user`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fakeUserResponse)
        )

        val sut = UserService.create(retrofit)
        val response = sut.resolveUser(userName)
        assertEquals("GET /user/key/User%20Name HTTP/1.1", server.takeRequest().requestLine)

        validateFirstUser(response)
    }

    @Test
    fun `Get providers`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fakeProvidersResponse)
        )

        val sut = UserService.create(retrofit)
        val response = sut.getProviders(userId)
        assertEquals("GET /user/providers/$userId HTTP/1.1", server.takeRequest().requestLine)

        val provider = response.providers[0]
        assertEquals(provider.name, "Fitbit")
        assertEquals(provider.slug, "fitbit")
    }

    @Test
    fun `Get refresh user`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fakeRefreshResponse)
        )

        val sut = UserService.create(retrofit)
        val response = sut.refreshUser(userId)
        assertEquals("POST /user/refresh/$userId HTTP/1.1", server.takeRequest().requestLine)

        assertEquals(response.refreshedSources.size, 5)
        assertEquals(response.failedSources.size, 3)
    }

    @Test
    fun `Create user`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fakeCreateUserResponse)
        )

        val sut = UserService.create(retrofit)
        val user = sut.createUser(CreateUserRequest(userName))
        assertEquals("POST /user/key HTTP/1.1", server.takeRequest().requestLine)

        assertEquals(user.userId, userId)
        assertEquals(user.userKey, userKey)
        assertEquals(user.clientUserId, clientUserId)
    }

    @Test
    fun `Delete user`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fakeDeleteUserResponse)
        )

        val sut = UserService.create(retrofit)
        val response = sut.deleteUser(userId)
        assertEquals("DELETE /user/$userId HTTP/1.1", server.takeRequest().requestLine)

        assertEquals(response.success, true)
    }

    @Test
    fun `Deregister provider`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fakeDeregisterProviderResponse)
        )

        val sut = UserService.create(retrofit)
        val response = sut.deregisterProvider(userId, "strava")
        assertEquals("DELETE /user/$userId/strava HTTP/1.1", server.takeRequest().requestLine)

        assertEquals(response.success, true)
    }

    private fun validateFirstUser(user: User) {
        assertEquals(userId, user.userId)
        assertEquals(userKey, user.userKey)
        assertEquals(clientUserId, user.clientUserId)
        assertEquals(teamId, user.teamId)
        assertEquals(3, user.connectedSources?.size)
        assertEquals("Fitbit", user.connectedSources?.get(0)?.source?.name)
        assertEquals("fitbit", user.connectedSources?.get(0)?.source?.slug)
    }

    private lateinit var server: MockWebServer
    private lateinit var retrofit: Retrofit
    private val apiKey = "apiKey"
    private val userId = "user_id_1"
    private val userKey = "user_key_1"
    private val teamId = "team_id_1"
    private val clientUserId = "Test 1"
    private val userName = "User Name"
    private val fakeUsersResponse = """
{
  "users": [
    {
      "user_id": "user_id_1",
      "user_key": "user_key_1",
      "team_id": "team_id_1",
      "client_user_id": "Test 1",
      "created_on": "2021-04-02T16:03:11.847830+00:00",
      "connected_sources": [
        {
          "source": {
            "name": "Fitbit",
            "slug": "fitbit",
            "logo": "https://storage.googleapis.com/vital-assets/fitbit.png"
          },
          "created_on": "2022-06-15T13:44:34.770879+00:00"
        },
        {
          "source": {
            "name": null,
            "slug": null,
            "logo": null
          },
          "created_on": "2022-03-01T12:25:15.558385+00:00"
        },
        {
          "source": null,
          "created_on": null
        }
      ]
    },
    {
      "user_id": "user_id_2",
      "user_key": "user_key_2",
      "team_id": "team_id_2",
      "client_user_id": "Test 2",
      "created_on": "2021-12-01T22:43:32.570793+00:00",
      "connected_sources": null
    }
  ]
}"""

    private val fakeUserResponse = """
{
    "user_id": "user_id_1",
    "user_key": "user_key_1",
    "team_id": "team_id_1",
    "client_user_id": "Test 1",
    "created_on": "2021-04-02T16:03:11.847830+00:00",
    "connected_sources": [
    {
        "source": {
        "name": "Fitbit",
        "slug": "fitbit",
        "logo": "https://storage.googleapis.com/vital-assets/fitbit.png"
    },
        "created_on": "2022-06-15T13:44:34.770879+00:00"
    },
    {
        "source": {
        "name": null,
        "slug": null,
        "logo": null
    },
        "created_on": "2022-03-01T12:25:15.558385+00:00"
    },
    {
        "source": null,
        "created_on": null
    }
    ]
}
"""

    private val fakeRefreshResponse = """
{
    "success": true,
    "user_id": "user_id_1",
    "refreshed_sources": [
    "Freestyle Libre/vitals/glucose",
    "Garmin/body",
    "Garmin/activity",
    "Garmin/workouts",
    "Garmin/sleep"
    ],
    "failed_sources": [
    "Fitbit/heartrate",
    "Fitbit/body",
    "Fitbit/activity"
    ]
}
"""

    private val fakeProvidersResponse = """{
        "providers": [
            {
                "name": "Fitbit",
                "slug": "fitbit",
                "logo": "https://storage.googleapis.com/vital-assets/fitbit.png"
            },
            {
            }
        ]
    }"""

    private val fakeCreateUserResponse =
        """{"user_id":"user_id_1","user_key":"user_key_1","client_user_id":"Test 1"}"""
    private val fakeDeleteUserResponse = """{"success":true}"""
    private val fakeDeregisterProviderResponse = """{"success":true}"""
}