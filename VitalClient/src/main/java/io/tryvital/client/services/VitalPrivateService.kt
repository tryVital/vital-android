package io.tryvital.client.services

import ManualProviderRequest
import ManualProviderResponse
import UserSDKSyncStateBody
import UserSDKSyncStateResponse
import io.tryvital.client.services.data.ActivityPayload
import io.tryvital.client.services.data.BodyPayload
import io.tryvital.client.services.data.ManualProviderSlug
import io.tryvital.client.services.data.ProfilePayload
import io.tryvital.client.services.data.SleepPayload
import io.tryvital.client.services.data.SummaryPayload
import io.tryvital.client.services.data.WorkoutPayload
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.*

@Suppress("unused")
@VitalPrivateApi
interface VitalPrivateService {
    @VitalPrivateApi
    @POST("user/{user_id}/sdk_sync_state/health_connect")
    suspend fun healthConnectSdkSyncState(
        @Path("user_id") userId: String,
        @Body body: UserSDKSyncStateBody
    ): UserSDKSyncStateResponse

    @VitalPrivateApi
    @POST("link/provider/manual/{provider}")
    suspend fun manualProvider(
        @Path("provider") provider: ManualProviderSlug,
        @Body request: ManualProviderRequest,
    ): ManualProviderResponse

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
        fun create(retrofit: Retrofit): VitalPrivateService {
            return retrofit.create(VitalPrivateService::class.java)
        }
    }
}