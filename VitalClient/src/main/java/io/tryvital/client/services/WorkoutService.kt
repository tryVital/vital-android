package io.tryvital.client.services

import io.tryvital.client.services.data.CholesterolType
import io.tryvital.client.services.data.Measurement
import io.tryvital.client.services.data.WorkoutStreamResponse
import io.tryvital.client.services.data.WorkoutsResponse
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.*

interface WorkoutService {
    @GET("summary/workouts/{user_id}")
    suspend fun getWorkouts(
        @Path("user_id") userId: String,
        @Query("start_date") startDate: Date,
        @Query("end_date") endDate: Date?,
        @Query("provider") provider: String?,
    ): Response<WorkoutsResponse>

    @GET("summary/workouts/{user_id}/raw")
    suspend fun getWorkoutsRaw(
        @Path("user_id") userId: String,
        @Query("start_date") startDate: Date,
        @Query("end_date") endDate: Date?,
        @Query("provider") provider: String?,
    ): Response<Any>

    @GET("timeseries/workouts/{workout_id}/stream")
    suspend fun getWorkoutStream(
        @Path("workout_id") workoutId: String,
    ): Response<WorkoutStreamResponse>

    companion object {
        fun create(retrofit: Retrofit): WorkoutService {
            return retrofit.create(WorkoutService::class.java)
        }
    }
}