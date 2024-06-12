package io.tryvital.client.services

import io.tryvital.client.services.data.*
import retrofit2.Retrofit
import retrofit2.http.*

@Suppress("unused")
interface UserService {
    @GET("user/{user_id}")
    suspend fun getUser(@Path("user_id") userId: String): User

    @GET("user/providers/{user_id}")
    suspend fun getUserConnections(@Path("user_id") userId: String): UserConnectionsResponse

    @DELETE("user/{user_id}/{provider}")
    suspend fun deregisterProvider(
        @Path("user_id") userId: String,
        @Path("provider") provider: ProviderSlug,
    ): DeregisterProviderResponse

    @POST("user/refresh/{user_id}")
    suspend fun refreshUser(@Path("user_id") userId: String): RefreshResponse

    companion object {
        fun create(retrofit: Retrofit): UserService {
            return retrofit.create(UserService::class.java)
        }
    }
}