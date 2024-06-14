package io.tryvital.client.services

import io.tryvital.client.VitalClient
import io.tryvital.client.services.data.BodyDataResponse
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.Instant
import java.util.*

@Suppress("unused")
interface BodyService {

    @GET("summary/body/{user_id}")
    suspend fun getBodyData(
        @Path("user_id") userId: String = VitalClient.checkUserId(),
        @Query("start_date") startDate: Instant,
        @Query("end_date") endDate: Instant?,
        @Query("provider") provider: String?,
    ): BodyDataResponse

    companion object {
        fun create(retrofit: Retrofit): BodyService {
            return retrofit.create(BodyService::class.java)
        }
    }
}