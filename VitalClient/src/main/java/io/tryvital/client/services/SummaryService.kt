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
        @Body addWorkoutRequest: AddWorkoutRequest
    ): ActivitiesResponse

    companion object {
        fun create(retrofit: Retrofit): SummaryService {
            return retrofit.create(SummaryService::class.java)
        }
    }
}