package io.tryvital.client.services

import io.tryvital.client.services.data.*
import retrofit2.Retrofit
import retrofit2.http.*

interface UserService {
    @GET("user/")
    suspend fun getAll(): List<User>

    @GET("user/{user_id}")
    suspend fun getUser(@Path("user_id") userId: String): User

    @POST("user/key")
    suspend fun createUser(@Body request: CreateUserRequest): CreateUserResponse

    @DELETE("user/{user_id}")
    suspend fun deleteUser(@Path("user_id") userId: String): DeleteUserResponse

    @GET("user/key/{client_user_id}")
    suspend fun resolveUser(@Path("client_user_id") clientUserId: String): User

    @GET("user/providers/{user_id}")
    suspend fun getProviders(@Path("user_id") userId: String): ProvidersResponse

    @DELETE("user/{user_id}/{provider}")
    suspend fun deregisterProvider(
        @Path("user_id") userId: String,
        @Path("provider") provider: String,
    ): DeregisterProviderResponse

    @POST("user/refresh/{user_id}")
    suspend fun refreshUser(@Path("user_id") userId: String): RefreshResponse

    companion object {
        fun create(retrofit: Retrofit): UserService {
            return retrofit.create(UserService::class.java)
        }
    }
}