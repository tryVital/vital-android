package io.tryvital.client.services

import io.tryvital.client.services.data.SleepResponse
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.Instant
import java.util.*

@Suppress("unused")
interface SleepService {

    @GET("summary/sleep/{user_id}")
    suspend fun getSleepData(
        @Path("user_id") userId: String,
        @Query("start_date") startDate: Instant,
        @Query("end_date") endDate: Instant?,
        @Query("provider") provider: String?,
    ): SleepResponse


    @GET("summary/sleep/{user_id}/stream")
    suspend fun getSleepStreamSeries(
        @Path("user_id") userId: String,
        @Query("start_date") startDate: Instant,
        @Query("end_date") endDate: Instant?,
        @Query("provider") provider: String?,
    ): SleepResponse

    @GET("summary/sleep/{user_id}/raw")
    suspend fun getSleepDataRaw(
        @Path("user_id") userId: String,
        @Query("start_date") startDate: Instant,
        @Query("end_date") endDate: Instant?,
        @Query("provider") provider: String?,
    ): Any

    companion object {
        fun create(retrofit: Retrofit): SleepService {
            return retrofit.create(SleepService::class.java)
        }
    }
}