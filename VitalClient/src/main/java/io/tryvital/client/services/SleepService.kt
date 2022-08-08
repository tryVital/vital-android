package io.tryvital.client.services

import io.tryvital.client.services.data.SleepResponse
import io.tryvital.client.services.data.SleepStreamResponse
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.*

interface SleepService {

    @GET("summary/sleep/{user_id}")
    suspend fun getSleepData(
        @Path("user_id") userId: String,
        @Query("start_date") startDate: Date,
        @Query("end_date") endDate: Date?,
        @Query("provider") provider: String?,
    ): Response<SleepResponse>


    @GET("summary/sleep/{user_id}/stream")
    suspend fun getSleepStreamSeries(
        @Path("user_id") userId: String,
        @Query("start_date") startDate: Date,
        @Query("end_date") endDate: Date?,
        @Query("provider") provider: String?,
    ): Response<SleepResponse>

    @GET("summary/sleep/{user_id}/raw")
    suspend fun getSleepDataRaw(
        @Path("user_id") userId: String,
        @Query("start_date") startDate: Date,
        @Query("end_date") endDate: Date?,
        @Query("provider") provider: String?,
    ): Response<Any>

    @GET("timeseries/sleep/{sleep_id}/stream")
    suspend fun getSleepStream(@Path("sleep_id") sleepId: String): Response<SleepStreamResponse>

    companion object {
        fun create(retrofit: Retrofit): SleepService {
            return retrofit.create(SleepService::class.java)
        }
    }
}