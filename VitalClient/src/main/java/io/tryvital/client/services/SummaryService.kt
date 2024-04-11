package io.tryvital.client.services

import io.tryvital.client.services.data.*
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.*
import java.util.*

@Suppress("unused")
interface SummaryService {

    @POST("summary/workouts/{user_id}")
    suspend fun addWorkouts(
        @Path("user_id") userId: String,
        @Body body: SummaryPayload<List<WorkoutPayload>>
    ): Response<Unit>

    @POST("summary/activity/{user_id}")
    suspend fun addActivities(
        @Path("user_id") userId: String,
        @Body body: SummaryPayload<List<ActivityPayload>>
    ): Response<Unit>

    @POST("summary/profile/{user_id}")
    suspend fun addProfile(
        @Path("user_id") userId: String,
        @Body body: SummaryPayload<ProfilePayload>
    ): Response<Unit>

    @POST("summary/body/{user_id}")
    suspend fun addBody(
        @Path("user_id") userId: String,
        @Body body: SummaryPayload<BodyPayload>
    ): Response<Unit>

    @POST("summary/sleep/{user_id}")
    suspend fun addSleeps(
        @Path("user_id") userId: String,
        @Body body: SummaryPayload<List<SleepPayload>>
    ): Response<Unit>

    companion object {
        fun create(retrofit: Retrofit): SummaryService {
            return retrofit.create(SummaryService::class.java)
        }
    }
}