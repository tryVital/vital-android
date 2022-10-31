package io.tryvital.client.services

import io.tryvital.client.services.data.*
import retrofit2.Retrofit
import retrofit2.http.*
import java.util.*

@Suppress("unused")
interface SummaryService {

    @POST("summary/workouts/{user_id}")
    suspend fun addWorkout(
        @Path("user_id") userId: String,
        @Body body: SummaryTimeframe<List<WorkoutPayload>>
    )

    @POST("summary/profile/{user_id}")
    suspend fun addProfile(
        @Path("user_id") userId: String,
        @Body body: SummaryTimeframe<ProfilePayload>
    )

    @POST("summary/body/{user_id}")
    suspend fun addBody(
        @Path("user_id") userId: String,
        @Body body: SummaryTimeframe<BodyPayload>
    )

    @POST("summary/sleep/{user_id}")
    suspend fun addSleep(
        @Path("user_id") userId: String,
        @Body body: SummaryTimeframe<List<SleepPayload>>
    )

    companion object {
        fun create(retrofit: Retrofit): SummaryService {
            return retrofit.create(SummaryService::class.java)
        }
    }
}