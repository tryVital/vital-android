package io.tryvital.client.services

import io.tryvital.client.VitalClient
import io.tryvital.client.services.data.*
import retrofit2.Retrofit
import retrofit2.http.*
import java.time.Instant
import java.util.*

@Suppress("unused")
interface ActivityService {
    @GET("user/")
    suspend fun getAll(): List<User>

    @GET("summary/activity/{user_id}")
    suspend fun getActivity(
        @Path("user_id") userId: String = VitalClient.checkUserId(),
        @Query("start_date") startDate: Instant,
        @Query("end_date") endDate: Instant?,
        @Query("provider") provider: String?,
    ): ActivitiesResponse

    @GET("summary/activity/{user_id}/raw")
    suspend fun getActivityRaw(
        @Path("user_id") userId: String = VitalClient.checkUserId(),
        @Query("start_date") startDate: Instant,
        @Query("end_date") endDate: Instant?,
        @Query("provider") provider: String?,
    ): Any

    companion object {
        fun create(retrofit: Retrofit): ActivityService {
            return retrofit.create(ActivityService::class.java)
        }
    }
}