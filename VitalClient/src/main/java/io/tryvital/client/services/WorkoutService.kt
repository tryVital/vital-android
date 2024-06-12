package io.tryvital.client.services

import io.tryvital.client.services.data.WorkoutStreamResponse
import io.tryvital.client.services.data.WorkoutsResponse
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.Instant
import java.util.*

@Suppress("unused")
interface WorkoutService {
    @GET("summary/workouts/{user_id}")
    suspend fun getWorkouts(
        @Path("user_id") userId: String,
        @Query("start_date") startDate: Instant,
        @Query("end_date") endDate: Instant?,
        @Query("provider") provider: String?,
    ): WorkoutsResponse

    @GET("summary/workouts/{user_id}/raw")
    suspend fun getWorkoutsRaw(
        @Path("user_id") userId: String,
        @Query("start_date") startDate: Instant,
        @Query("end_date") endDate: Instant?,
        @Query("provider") provider: String?,
    ): Any

    @GET("timeseries/workouts/{workout_id}/stream")
    suspend fun getWorkoutStream(
        @Path("workout_id") workoutId: String,
    ): WorkoutStreamResponse

    companion object {
        fun create(retrofit: Retrofit): WorkoutService {
            return retrofit.create(WorkoutService::class.java)
        }
    }
}