package io.tryvital.client.services

import io.tryvital.client.services.data.*
import retrofit2.Retrofit
import retrofit2.http.*
import java.util.*

@Suppress("unused")
class VitalsService private constructor(private val timeSeries: TimeSeries) {

    suspend fun getGlucose(
        userId: String,
        startDate: Date,
        endDate: Date? = null,
        provider: String? = null,
    ): List<Measurement> {
        return timeSeries.timeseriesRequest(
            userId = userId, resource = "glucose", startDate = startDate,
            endDate = endDate, provider = provider
        )
    }

    suspend fun getCholesterol(
        cholesterolType: CholesterolType,
        userId: String,
        startDate: Date,
        endDate: Date? = null,
        provider: String? = null,
    ): List<Measurement> {
        return timeSeries.timeseriesRequest(
            userId = userId,
            resource = "cholesterol/${cholesterolType.name}",
            startDate = startDate,
            endDate = endDate,
            provider = provider,
        )
    }

    suspend fun getIge(
        userId: String,
        startDate: Date,
        endDate: Date? = null,
        provider: String? = null,
    ): List<Measurement> {
        return timeSeries.timeseriesRequest(
            userId = userId,
            resource = "ige",
            startDate = startDate,
            endDate = endDate,
            provider = provider,
        )
    }

    suspend fun getIgg(
        userId: String,
        startDate: Date,
        endDate: Date? = null,
        provider: String? = null
    ): List<Measurement> {
        return timeSeries.timeseriesRequest(
            userId = userId,
            resource = "igg",
            startDate = startDate,
            endDate = endDate,
            provider = provider,
        )
    }

    suspend fun getHeartrate(
        userId: String,
        startDate: Date,
        endDate: Date? = null,
        provider: String? = null,
    ): List<Measurement> {
        return timeSeries.timeseriesRequest(
            userId = userId,
            resource = "heartrate",
            startDate = startDate,
            endDate = endDate,
            provider = provider
        )
    }

    suspend fun sendBloodPressure(
        userId: String,
        timeseriesPayload: TimeseriesPayload<List<BloodPressureSamplePayload>>
    ) {
        return timeSeries.bloodPressureTimeseriesPost(
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
        return timeSeries.timeseriesPost(
            userId = userId,
            resource = resource.toString(),
            payload = timeseriesPayload
        )
    }

    companion object {
        fun create(retrofit: Retrofit): VitalsService {
            return VitalsService(retrofit.create(TimeSeries::class.java))
        }
    }
}

private interface TimeSeries {
    @GET("timeseries/{user_id}/{resource}")
    suspend fun timeseriesRequest(
        @Path("user_id") userId: String,
        @Path("resource", encoded = true) resource: String,
        @Query("start_date") startDate: Date,
        @Query("end_date") endDate: Date? = null,
        @Query("provider") provider: String? = null,
    ): List<Measurement>

    @POST("timeseries/{user_id}/{resource}")
    suspend fun timeseriesPost(
        @Path("user_id") userId: String,
        @Path("resource", encoded = true) resource: String,
        @Body payload: TimeseriesPayload<List<QuantitySamplePayload>>
    )

    @POST("timeseries/{user_id}/{resource}")
    suspend fun bloodPressureTimeseriesPost(
        @Path("user_id") userId: String,
        @Path("resource", encoded = true) resource: String,
        @Body payload: TimeseriesPayload<List<BloodPressureSamplePayload>>
    )
}

