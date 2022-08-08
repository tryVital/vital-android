package io.tryvital.client

import io.tryvital.client.dependencies.Dependencies
import io.tryvital.client.services.TestkitService
import io.tryvital.client.services.data.CreateOrderRequest
import io.tryvital.client.services.data.OrderData
import io.tryvital.client.services.data.PatientAddress
import io.tryvital.client.services.data.PatientDetails
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
import java.util.*


@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("BlockingMethodInNonBlockingContext")
class TestkitServiceTest {
    @Before
    fun setUp() {
        server = MockWebServer()

        retrofit = Dependencies.createRetrofit(
            server.url("").toString(),
            Dependencies.createHttpClient(),
            Dependencies.createMoshi()
        )
    }

    @After
    fun cleanUp() {
        server.shutdown()
    }

    @Test
    fun `Get all orders`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fakeOrdersResponse)
        )
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        val sut = TestkitService.create(retrofit)
        val response = sut.getAllOrders(
            startDate = dateFormat.parse("2022-07-01")!!,
            endDate = dateFormat.parse("2022-07-21"),
            status = null
        )
        assertEquals(
            "GET /testkit/orders?start_date=2022-07-01&end_date=2022-07-21&page=1&size=50 HTTP/1.1",
            server.takeRequest().requestLine
        )
        assertEquals(1, response.body()!!.orders.size)
        val order = response.body()!!.orders[0]
        checkOrder(order)
    }

    @Test
    fun `Get all testkits`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fakeTestKitsResponse)
        )
        val sut = TestkitService.create(retrofit)
        val response = sut.getAllTestkits()
        assertEquals(
            "GET /testkit/ HTTP/1.1",
            server.takeRequest().requestLine
        )
        assertEquals(2, response.body()?.testkits?.size)
        val testkit1 = response.body()!!.testkits[0]
        assertEquals("71d54fff-70e1-4f74-937e-5a185b925d0d", testkit1.id)
        assertEquals(2, testkit1.markers.size)
        assertEquals("ALLERGEN-Blomia tropicalis", testkit1.markers[0].name)
        assertEquals("ag:blomia_tropicalis", testkit1.markers[0].slug)
        assertNull(testkit1.markers[0].description)
    }

    @Test
    fun `Create order`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fakeCreateResponse)
        )
        val sut = TestkitService.create(retrofit)
        val response = sut.createOrder(
            CreateOrderRequest(
                testkitId = "71d54fff-70e1-4f74-937e-5a185b925d0d",
                userId = userId,
                patientAddress = PatientAddress(
                    receiverName = "Receiver Name",
                    street = "340 street",
                    streetNumber = "340",
                    city = "City",
                    state = "State",
                    zip = "12345",
                    country = "US",
                    phoneNumber = "+123"
                ),
                patientDetails = PatientDetails(
                    dob = Date(1993, 8, 18),
                    gender = "male",
                    email = null
                ),
            ),
            skipAddressValidation = true
        )
        assertEquals(
            "POST /testkit/orders HTTP/1.1",
            server.takeRequest().requestLine
        )
        assertEquals(response.body()?.status, "success")
        assertEquals(response.body()?.message, "order created")
        val order = response.body()!!.order!!
        checkOrder(order)
    }

    @Test
    fun `Cancel order`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fakeCancelResponse)
        )
        val sut = TestkitService.create(retrofit)
        val response = sut.cancelOrder(orderId = "id_1")
        assertEquals(
            "POST /testkit/orders/id_1/cancel HTTP/1.1",
            server.takeRequest().requestLine
        )
        assertEquals(response.body()?.status, "success")
        assertEquals(response.body()?.message, "order cancelled")
        val order = response.body()!!.order!!
        checkOrder(order, status = "cancelled")
    }

    fun checkOrder(order: OrderData, status: String = "ordered") {
        assertEquals(order.id, "id_1")
        assertEquals(order.userId, userId)
        assertEquals(order.testkit!!.id, "71d54fff-70e1-4f74-937e-5a185b925d0d")
        assertEquals(order.patientAddress!!.receiverName, "Receiver Name")
        assertEquals(order.patientDetails!!.gender, "male")
        assertEquals(order.status, status)
    }
}

private lateinit var server: MockWebServer
private lateinit var retrofit: Retrofit

private val apiKey = "API_KEY"
private val userId = "user_id_1"

const val fakeTestKitsResponse = """{
"testkits": [
{
    "id": "71d54fff-70e1-4f74-937e-5a185b925d0d",
    "name": "Respiratory Allergen",
    "description": "Respiratory Allergens ",
    "markers": [
    {
        "name": "ALLERGEN-Blomia tropicalis",
        "slug": "ag:blomia_tropicalis",
        "description": null
    },
    {
        "name": "ALLERGEN-D. pteronyssinus",
        "slug": "ag:d._pteronyssinus",
        "description": null
    }
    ],
    "turnaround_time_lower": 4,
    "turnaround_time_upper": 14,
    "price": 250.0
},
{
    "id": "8ac4e01b-95a2-4bcf-a6f3-4cccacb74821",
    "name": "Wyndly Respiratory Allergen",
    "description": "Respiratory Allergens",
    "markers": [
    {
        "name": "ALLERGEN-Blomia tropicalis",
        "slug": "ag:blomia_tropicalis",
        "description": null
    }
    ],
    "turnaround_time_lower": 4,
    "turnaround_time_upper": 14,
    "price": 270.0
}
]
}"""

const val fakeOrdersResponse = """{
"orders": [
{
    "user_id": "user_id_1",
    "user_key": "user_key_1",
    "id": "id_1",
    "team_id": "team_id_1",
    "created_on": "2022-07-05T15:14:41.547806+00:00",
    "updated_on": "2022-07-05T15:14:41.547806+00:00",
    "status": "ordered",
    "testkit_id": "testkit_id_1",
    "testkit": {
    "id": "71d54fff-70e1-4f74-937e-5a185b925d0d",
    "name": "Respiratory Allergen",
    "description": "Respiratory Allergens ",
    "markers": [],
    "turnaround_time_lower": 4,
    "turnaround_time_upper": 14,
    "price": 250.0
},
    "inbound_tracking_number": null,
    "outbound_tracking_number": null,
    "inbound_tracking_url": null,
    "outbound_tracking_url": null,
    "outbound_courier": null,
    "inbound_courier": null,
    "patient_address": {
    "receiver_name": "Receiver Name",
    "street": "340 street",
    "street_number": "340",
    "city": "City",
    "state": "State",
    "zip": "12345",
    "country": "US",
    "phone_number": "+123"
},
    "patient_details": {
    "dob": "1993-08-18T00:00:00+00:00",
    "gender": "male",
    "email": null
},
    "sample_id": null
}
],
"total": 1,
"page": 1,
"size": 50
}"""

const val fakeCreateResponse = """{
"order": {
    "user_id": "user_id_1",
    "user_key": "user_key_1",
    "id": "id_1",
    "team_id": "team_id_1",
    "created_on": "2022-07-05T15:14:41.547806+00:00",
    "updated_on": "2022-07-05T15:14:41.547806+00:00",
    "status": "ordered",
    "testkit_id": "testkit_id_1",
    "testkit": {
        "id": "71d54fff-70e1-4f74-937e-5a185b925d0d",
        "name": "Respiratory Allergen",
        "description": "Respiratory Allergens ",
        "markers": [],
        "turnaround_time_lower": 4,
        "turnaround_time_upper": 14,
        "price": 250.0
    },
    "inbound_tracking_number": null,
    "outbound_tracking_number": null,
    "inbound_tracking_url": null,
    "outbound_tracking_url": null,
    "outbound_courier": null,
    "inbound_courier": null,
    "patient_address": {
        "receiver_name": "Receiver Name",
        "street": "340 street",
        "street_number": "340",
        "city": "City",
        "state": "State",
        "zip": "12345",
        "country": "US",
        "phone_number": "+123"
    },
    "patient_details": {
        "dob": "1993-08-18T00:00:00+00:00",
        "gender": "male",
        "email": null
    },
    "sample_id": null
},
"status": "success",
"message": "order created"
}"""

const val fakeCancelResponse = """{
"order": {
    "user_id": "user_id_1",
    "user_key": "user_key_1",
    "id": "id_1",
    "team_id": "team_id_1",
    "created_on": "2022-07-05T15:14:41.547806+00:00",
    "updated_on": "2022-07-05T15:14:41.547806+00:00",
    "status": "cancelled",
    "testkit_id": "testkit_id_1",
    "testkit": {
        "id": "71d54fff-70e1-4f74-937e-5a185b925d0d",
        "name": "Respiratory Allergen",
        "description": "Respiratory Allergens ",
        "markers": [],
        "turnaround_time_lower": 4,
        "turnaround_time_upper": 14,
        "price": 250.0
    },
    "inbound_tracking_number": null,
    "outbound_tracking_number": null,
    "inbound_tracking_url": null,
    "outbound_tracking_url": null,
    "outbound_courier": null,
    "inbound_courier": null,
    "patient_address": {
        "receiver_name": "Receiver Name",
        "street": "340 street",
        "street_number": "340",
        "city": "City",
        "state": "State",
        "zip": "12345",
        "country": "US",
        "phone_number": "+123"
    },
    "patient_details": {
        "dob": "1993-08-18T00:00:00+00:00",
        "gender": "male",
        "email": null
    },
    "sample_id": null
},
"status": "success",
"message": "order cancelled"
}"""

const val fakeOrderData = """{
"user_id": "user_id_1",
"user_key": "user_key_1",
"id": "id_1",
"team_id": "team_id_1",
"created_on": "2022-07-05T15:14:41.547806+00:00",
"updated_on": "2022-07-05T15:14:41.547806+00:00",
"status": "ordered",
"testkit_id": "testkit_id_1",
"testkit": {
    "id": "71d54fff-70e1-4f74-937e-5a185b925d0d",
    "name": "Respiratory Allergen",
    "description": "Respiratory Allergens ",
    "markers": [],
    "turnaround_time_lower": 4,
    "turnaround_time_upper": 14,
    "price": 250.0
},
"inbound_tracking_number": null,
"outbound_tracking_number": null,
"inbound_tracking_url": null,
"outbound_tracking_url": null,
"outbound_courier": null,
"inbound_courier": null,
"patient_address": {
    "receiver_name": "Receiver Name",
    "street": "340 street",
    "street_number": "340",
    "city": "City",
    "state": "State",
    "zip": "12345",
    "country": "US",
    "phone_number": "+123"
},
"patient_details": {
    "dob": "1993-08-18T00:00:00+00:00",
    "gender": "male",
    "email": null
},
"sample_id": null
}"""

