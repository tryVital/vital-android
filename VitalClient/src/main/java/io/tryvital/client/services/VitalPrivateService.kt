package io.tryvital.client.services

import ManualProviderRequest
import ManualProviderResponse
import UserSDKSyncStateBody
import UserSDKSyncStateResponse
import io.tryvital.client.services.data.LocalActivity
import io.tryvital.client.services.data.LocalBloodPressureSample
import io.tryvital.client.services.data.LocalBody
import io.tryvital.client.services.data.LocalMenstrualCycle
import io.tryvital.client.services.data.ManualProviderSlug
import io.tryvital.client.services.data.LocalProfile
import io.tryvital.client.services.data.LocalQuantitySample
import io.tryvital.client.services.data.LocalSleep
import io.tryvital.client.services.data.LocalWorkout
import io.tryvital.client.services.data.SummaryPayload
import io.tryvital.client.services.data.TimeseriesPayload
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
        @Body body: SummaryPayload<List<LocalWorkout>>
    ): Response<Unit>

    @POST("summary/menstrual_cycle/{user_id}")
    suspend fun addMenstrualCycles(
        @Path("user_id") userId: String,
        @Body body: SummaryPayload<List<LocalMenstrualCycle>>
    ): Response<Unit>

    @POST("summary/activity/{user_id}")
    suspend fun addActivities(
        @Path("user_id") userId: String,
        @Body body: SummaryPayload<List<LocalActivity>>
    ): Response<Unit>

    @POST("summary/profile/{user_id}")
    suspend fun addProfile(
        @Path("user_id") userId: String,
        @Body body: SummaryPayload<LocalProfile>
    ): Response<Unit>

    @POST("summary/body/{user_id}")
    suspend fun addBody(
        @Path("user_id") userId: String,
        @Body body: SummaryPayload<LocalBody>
    ): Response<Unit>

    @POST("summary/sleep/{user_id}")
    suspend fun addSleeps(
        @Path("user_id") userId: String,
        @Body body: SummaryPayload<List<LocalSleep>>
    ): Response<Unit>

    @POST("timeseries/{user_id}/{resource}")
    suspend fun timeseriesPost(
        @Path("user_id") userId: String,
        @Path("resource", encoded = true) resource: String,
        @Body payload: TimeseriesPayload<List<LocalQuantitySample>>
    ): Response<Unit>

    @POST("timeseries/{user_id}/blood_pressure")
    suspend fun bloodPressureTimeseriesPost(
        @Path("user_id") userId: String,
        @Body payload: TimeseriesPayload<List<LocalBloodPressureSample>>
    ): Response<Unit>

    companion object {
        fun create(retrofit: Retrofit): VitalPrivateService {
            return retrofit.create(VitalPrivateService::class.java)
        }
    }
}