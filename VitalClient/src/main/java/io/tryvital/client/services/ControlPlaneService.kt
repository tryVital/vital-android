package io.tryvital.client.services

import io.tryvital.client.services.data.*
import retrofit2.Retrofit
import retrofit2.http.*

@Suppress("unused")
interface ControlPlaneService {
    @GET("user/")
    suspend fun getAll(): GetAllUsersResponse

    @POST("user")
    suspend fun createUser(@Body request: CreateUserRequest): CreateUserResponse

    @POST("user/{user_id}/sign_in_token")
    suspend fun createSignInToken(@Path("user_id") userId: String): CreateSignInTokenResponse

    @DELETE("user/{user_id}")
    suspend fun deleteUser(@Path("user_id") userId: String): DeleteUserResponse

    @GET("user/resolve/{client_user_id}")
    suspend fun resolveUser(@Path("client_user_id") clientUserId: String): User

    companion object {
        fun create(retrofit: Retrofit): ControlPlaneService {
            return retrofit.create(ControlPlaneService::class.java)
        }
    }
}