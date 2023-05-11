package io.tryvital.client

import io.tryvital.client.dependencies.Dependencies
import io.tryvital.client.services.WorkoutService
import io.tryvital.client.services.data.ProviderSlug
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
class WorkoutServiceTest {
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
    fun `Get workouts`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(fakeWorkoutsResponse)
        )
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        val sut = WorkoutService.create(retrofit)
        val response = sut.getWorkouts(
            userId = userId,
            startDate = dateFormat.parse("2022-07-01")!!,
            endDate = dateFormat.parse("2022-07-21"),
            provider = null
        )
        assertEquals(
            "GET /summary/workouts/$userId?start_date=2022-07-01&end_date=2022-07-21 HTTP/1.1",
            server.takeRequest().requestLine
        )
        val workout1 = response.workouts[0]
        assertEquals("id_1", workout1.id)
        assertEquals(-28800, workout1.timezoneOffset)
        assertEquals(2.0, workout1.distance)
        assertEquals(5.81152, workout1.averageSpeed)
        assertEquals(43, workout1.sport!!.id)
        assertEquals("Cycling", workout1.sport!!.name)
        assertEquals("Peloton", workout1.source.name)
        assertEquals(ProviderSlug.Peloton, workout1.source.slug)

        val workout2 = response.workouts[1]
        assertEquals("Health Connect", workout2.source.name)
        assertEquals(ProviderSlug.HealthConnect, workout2.source.slug)
    }

    @Test
    fun `Get workout stream`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(fakeStreamResponse)
        )
        val sut = WorkoutService.create(retrofit)
        val stream = sut.getWorkoutStream(workoutId)
        assertEquals(
            "GET /timeseries/workouts/$workoutId/stream HTTP/1.1", server.takeRequest().requestLine
        )
        assertEquals(12.123456, stream.lat[0])
        assertEquals(12.234567, stream.lat[1])
        assertEquals(47.123456, stream.lng[0])
        assertEquals(47.234567, stream.lng[1])
        assertEquals(1643054836, stream.time[0])
        assertEquals(1643054886, stream.time[1])
        assertEquals(207.0, stream.power[0])
        assertEquals(206.0, stream.power[1])

    }

    @Test
    fun `Get workout stream nulls`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(fakeStreamResponseNulls)
        )
        SimpleDateFormat("yyyy-MM-dd")
        val sut = WorkoutService.create(retrofit)
        val stream = sut.getWorkoutStream(workoutId)
        assertEquals(
            "GET /timeseries/workouts/$workoutId/stream HTTP/1.1", server.takeRequest().requestLine
        )
        assertEquals(0, stream.altitude.size)
        assertEquals(0, stream.cadence.size)
        assertEquals(0, stream.distance.size)
        assertEquals(0, stream.heartrate.size)
        assertEquals(0, stream.lat.size)
        assertEquals(0, stream.lng.size)
        assertEquals(0, stream.power.size)
        assertEquals(0, stream.resistance.size)
        assertEquals(0, stream.time.size)
        assertEquals(0, stream.velocitySmooth.size)
    }

    private fun assertEquals(expected: Double, actual: Double) {
        assertEquals(expected, actual, 0.01)
    }

}

private lateinit var server: MockWebServer
private lateinit var retrofit: Retrofit

private const val apiKey = "API_KEY"
private const val userId = "user_id_1"
private const val workoutId = "workout_id_1"


const val fakeWorkoutsResponse = """
{
    "workouts": [
    {
        "user_id": "user_id_1",
        "user_key": "user_key_1",
        "id": "id_1",
        "title": "Cycling Workout",
        "timezone_offset": -28800,
        "average_hr": null,
        "max_hr": null,
        "distance": 2.0,
        "time_start": "2022-01-24T20:07:14+00:00",
        "time_end": "2022-01-24T20:17:14+00:00",
        "calories": 98.0,
        "sport": {
        "id": 43,
        "name": "Cycling"
    },
        "hr_zones": null,
        "moving_time": 600,
        "total_elevation_gain": null,
        "elev_high": 120.0,
        "elev_low": 100,
        "average_speed": 5.81152,
        "max_speed": 21.9,
        "average_watts": 114.0,
        "device_watts": 180,
        "max_watts": 212.0,
        "weighted_average_watts": null,
        "map": null,
        "provider_id": "b16d5dee1f83431787c2d3df8fbd50a9",
        "source": {
        "name": "Peloton",
        "slug": "peloton",
        "logo": "https://storage.googleapis.com/vital-assets/peloton.png"
    }
    },
    {
        "user_id": "user_id_1",
        "user_key": "user_key_1",
        "id": "id_2",
        "title": null,
        "timezone_offset": null,
        "average_hr": null,
        "max_hr": null,
        "distance": null,
        "time_start": "2022-01-06T16:02:46+00:00",
        "time_end": "2022-01-06T16:22:46+00:00",
        "calories": null,
        "sport": null,
        "hr_zones": null,
        "moving_time": null,
        "total_elevation_gain": null,
        "elev_high": null,
        "elev_low": null,
        "average_speed": null,
        "max_speed": null,
        "average_watts": null,
        "device_watts": null,
        "max_watts": null,
        "weighted_average_watts": null,
        "map": null,
        "provider_id": null,
        "source": {
        "name": "Health Connect",
        "slug": "health_connect",
        "logo": ""
        }
    }
    ]
}
"""

const val fakeStreamResponse = """{
    "cadence": [
        71.0,
        74.0
    ],
    "time": [
        1643054836,
        1643054886
    ],
    "altitude": [],
    "velocity_smooth": [
        21.7,
        21.6
    ],
    "heartrate": [],
    "lat": [
        12.123456,
        12.234567
    ],
    "lng": [
        47.123456,
        47.234567
    ],
    "distance": [],
    "power": [
        207.0,
        206.0
    ],
    "resistance": [
        56.0,
        56.0
    ]
}"""

const val fakeStreamResponseNulls = """{
}"""