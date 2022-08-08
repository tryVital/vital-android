package io.tryvital.client.services

import io.tryvital.client.services.data.BodyData
import io.tryvital.client.services.data.BodyDataResponse
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.*

interface BodyService {

    @GET("/summary/body/{user_id}")
    suspend fun getBodyData(
        @Path("user_id") userId: String,
        @Query("start_date") startDate: Date,
        @Query("end_date") endDate: Date?,
        @Query("provider") provider: String?,
    ): Response<BodyDataResponse>

    companion object {
        fun create(retrofit: Retrofit): BodyService {
            return retrofit.create(BodyService::class.java)
        }
    }
}