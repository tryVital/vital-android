package io.tryvital.client.services

import io.tryvital.client.services.data.*
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.*
import java.time.Instant
import java.util.*

enum class ScalarTimeseriesResource(val rawValue: String) {
    Glucose("glucose"),
    HeartRate("heartrate"),
    BloodOxygen("blood_oxygen");
}

@Suppress("unused")
class TimeSeriesService private constructor(private val timeSeries: TimeSeries) {

    suspend fun getGlucose(
        userId: String,
        resource: ScalarTimeseriesResource,
        startDate: Instant,
        endDate: Instant? = null,
        provider: String? = null,
        nextCursor: String? = null,
    ): GroupedSamplesResponse<ScalarSample> {
        return timeSeries.scalarSampleTimeseriesRequest(
            userId = userId, resource = resource.rawValue, startDate = startDate,
            endDate = endDate, provider = provider, nextCursor = nextCursor,
        )
    }

    suspend fun getBloodPressure(
        userId: String,
        startDate: Instant,
        endDate: Instant? = null,
        provider: String? = null,
        nextCursor: String? = null,
    ): GroupedSamplesResponse<BloodPressureSample> {
        return timeSeries.bloodPressureTimeseriesRequest(
            userId = userId, startDate = startDate,
            endDate = endDate, provider = provider, nextCursor = nextCursor,
        )
    }

    suspend fun sendBloodPressure(
        userId: String,
        timeseriesPayload: TimeseriesPayload<List<BloodPressureSamplePayload>>
    ) {
        timeSeries.bloodPressureTimeseriesPost(
            userId = userId,
            resource = "blood_pressure",
            payload = timeseriesPayload
        )
    }

    suspend fun sendQuantitySamples(
        resource: IngestibleTimeseriesResource,
        userId: String,
        timeseriesPayload: TimeseriesPayload<List<QuantitySamplePayload>>
    ) {
        timeSeries.timeseriesPost(
            userId = userId,
            resource = resource.toString(),
            payload = timeseriesPayload
        )
    }

    companion object {
        fun create(retrofit: Retrofit): TimeSeriesService {
            return TimeSeriesService(retrofit.create(TimeSeries::class.java))
        }
    }
}

private interface TimeSeries {
    @GET("timeseries/{user_id}/{resource}")
    suspend fun scalarSampleTimeseriesRequest(
        @Path("user_id") userId: String,
        @Path("resource", encoded = true) resource: String,
        @Query("start_date") startDate: Instant,
        @Query("end_date") endDate: Instant? = null,
        @Query("provider") provider: String? = null,
        @Query("next_cursor") nextCursor: String? = null,
    ): GroupedSamplesResponse<ScalarSample>

    @GET("timeseries/{user_id}/blood_pressure")
    suspend fun bloodPressureTimeseriesRequest(
        @Path("user_id") userId: String,
        @Query("start_date") startDate: Instant,
        @Query("end_date") endDate: Instant? = null,
        @Query("provider") provider: String? = null,
        @Query("next_cursor") nextCursor: String? = null,
    ): GroupedSamplesResponse<BloodPressureSample>

    @POST("timeseries/{user_id}/{resource}")
    suspend fun timeseriesPost(
        @Path("user_id") userId: String,
        @Path("resource", encoded = true) resource: String,
        @Body payload: TimeseriesPayload<List<QuantitySamplePayload>>
    ): Response<Unit>

    @POST("timeseries/{user_id}/{resource}")
    suspend fun bloodPressureTimeseriesPost(
        @Path("user_id") userId: String,
        @Path("resource", encoded = true) resource: String,
        @Body payload: TimeseriesPayload<List<BloodPressureSamplePayload>>
    ): Response<Unit>
}

