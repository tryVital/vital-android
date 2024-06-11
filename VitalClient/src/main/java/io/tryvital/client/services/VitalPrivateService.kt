package io.tryvital.client.services

import ManualProviderRequest
import ManualProviderResponse
import UserSDKSyncStateBody
import UserSDKSyncStateResponse
import io.tryvital.client.services.data.ManualProviderSlug
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

    companion object {
        fun create(retrofit: Retrofit): VitalPrivateService {
            return retrofit.create(VitalPrivateService::class.java)
        }
    }
}