package io.tryvital.client.services

import io.tryvital.client.services.data.*
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.*
import java.util.*

interface ActivityService {
    @GET("user/")
    suspend fun getAll(): Response<List<User>>

    @GET("summary/activity/{user_id}")
    suspend fun getActivity(
        @Path("user_id") userId: String,
        @Query("start_date") startDate: Date,
        @Query("end_date") endDate: Date?,
        @Query("provider") provider: String?,
    ): Response<ActivitiesResponse>

    @GET("summary/activity/{user_id}/raw")
    suspend fun getActivityRaw(
        @Path("user_id") userId: String,
        @Query("start_date") startDate: Date,
        @Query("end_date") endDate: Date?,
        @Query("provider") provider: String?,
    ): Response<Any>

    companion object {
        fun create(retrofit: Retrofit): ActivityService {
            return retrofit.create(ActivityService::class.java)
        }
    }
}