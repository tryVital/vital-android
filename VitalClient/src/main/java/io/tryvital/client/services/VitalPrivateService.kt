package io.tryvital.client.services

import UserSDKSyncStateBody
import UserSDKSyncStateResponse
import io.tryvital.client.services.data.WorkoutStreamResponse
import io.tryvital.client.services.data.WorkoutsResponse
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.*

@Suppress("unused")
@VitalPrivateApi
interface VitalPrivateService {
    @VitalPrivateApi
    @POST("user/{user_id}/sdk_sync_state/health_connect")
    suspend fun healthConnectSyncState(
        @Path("user_id") userId: String,
        @Body body: UserSDKSyncStateBody
    ): UserSDKSyncStateResponse

    companion object {
        fun create(retrofit: Retrofit): VitalPrivateService {
            return retrofit.create(VitalPrivateService::class.java)
        }
    }
}