package io.tryvital.client.services

import UserSDKSyncStateBody
import UserSDKSyncStateResponse
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

    companion object {
        fun create(retrofit: Retrofit): VitalPrivateService {
            return retrofit.create(VitalPrivateService::class.java)
        }
    }
}