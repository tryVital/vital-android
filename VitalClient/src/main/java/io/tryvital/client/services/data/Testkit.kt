package io.tryvital.client.services.data

import com.squareup.moshi.Json
import java.util.*

data class CreateOrderRequest(
    @Json(name = "testkit_id")
    val testkitId: String,
    @Json(name = "patient_address")
    val patientAddress: PatientAddress,
    @Json(name = "patient_details")
    val patientDetails: PatientDetails,
    @Json(name = "user_id")
    val userId: String,
)

data class OrderResponse(
    val status: String?,
    val message: String?,
    val order: OrderData?,
)

data class OrdersResponse(
    val orders: List<OrderData>,
)

data class TestkitsResponse(
    val testkits: List<Testkit>,
)

data class OrderData(
    @Json(name = "user_id")
    val userId: String?,
    @Json(name = "user_key")
    val userKey: String?,
    val id: String?,
    @Json(name = "team_id")
    val teamId: String?,
    @Json(name = "created_on")
    val createdOn: Date?,
    @Json(name = "updated_on")
    val updatedOn: Date?,
    val status: String?,
    @Json(name = "testkit_id")
    val testkitId: String?,
    val testkit: Testkit?,
    @Json(name = "inbound_tracking_number")
    val inboundTrackingNumber: String?,
    @Json(name = "outbound_tracking_number")
    val outboundTrackingNumber: String?,
    @Json(name = "inbound_tracking_url")
    val inboundTrackingUrl: String?,
    @Json(name = "outbound_tracking_url")
    val outboundTrackingUrl: String?,
    @Json(name = "outbound_courier")
    val outboundCourier: String?,
    @Json(name = "inbound_courier")
    val inboundCourier: String?,
    @Json(name = "patient_address")
    val patientAddress: PatientAddress?,
    @Json(name = "patient_details")
    val patientDetails: PatientDetails?,
    @Json(name = "sample_id")
    val sampleId: String?,
)

data class Testkit(
    val id: String,
    val name: String,
    val description: String,
    val markers: List<TestkitMarker>,
    @Json(name = "turnaround_time_lower")
    val turnaroundTimeLower: Int?,
    @Json(name = "turnaround_time_upper")
    val turnaroundTimeUpper: Int?,
    val price: Double?,
)

data class TestkitMarker(
    val name: String,
    val slug: String,
    val description: String?,
)

data class PatientAddress(
    @Json(name = "receiver_name")
    val receiverName: String?,
    val street: String?,
    @Json(name = "street_number")
    val streetNumber: String?,
    val city: String?,
    val state: String?,
    val zip: String?,
    val country: String?,
    @Json(name = "phone_number")
    val phoneNumber: String?,
)

data class PatientDetails(
    val dob: Date?,
    val gender: String?,
    val email: String?,
)