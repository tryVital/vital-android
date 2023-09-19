package io.tryvital.client.services

import io.tryvital.client.services.data.*
import retrofit2.Retrofit
import retrofit2.http.*

@Suppress("unused")
interface UserService {
    @Deprecated(message = "Moved to ControlPlaneService, which can be instantiated with VitalClient.Companion.controlPlane(...).")
    @GET("user/")
    suspend fun getAll(): GetAllUsersResponse

    @GET("user/{user_id}")
    suspend fun getUser(@Path("user_id") userId: String): User

    @Deprecated(message = "Moved to ControlPlaneService, which can be instantiated with VitalClient.Companion.controlPlane(...).")
    @POST("user")
    suspend fun createUser(@Body request: CreateUserRequest): CreateUserResponse

    @Deprecated(message = "Moved to ControlPlaneService, which can be instantiated with VitalClient.Companion.controlPlane(...).")
    @DELETE("user/{user_id}")
    suspend fun deleteUser(@Path("user_id") userId: String): DeleteUserResponse

    @Deprecated(message = "Moved to ControlPlaneService, which can be instantiated with VitalClient.Companion.controlPlane(...).")
    @GET("user/resolve/{client_user_id}")
    suspend fun resolveUser(@Path("client_user_id") clientUserId: String): User

    @GET("user/providers/{user_id}")
    suspend fun getProviders(@Path("user_id") userId: String): ProvidersResponse

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