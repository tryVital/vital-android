package io.tryvital.client.services

import io.tryvital.client.VitalClient
import io.tryvital.client.services.data.Profile
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

@Suppress("unused")
interface ProfileService {

    @GET("summary/profile/{user_id}")
    suspend fun getProfile(
        @Path("user_id") userId: String = VitalClient.checkUserId(),
        @Query("provider") provider: String?,
    ): Profile

    @GET("summary/profile/{user_id}/raw")
    suspend fun getProfileRaw(
        @Path("user_id") userId: String = VitalClient.checkUserId(),
        @Query("provider") provider: String?,
    ): Any

    companion object {
        fun create(retrofit: Retrofit): ProfileService {
            return retrofit.create(ProfileService::class.java)
        }
    }
}