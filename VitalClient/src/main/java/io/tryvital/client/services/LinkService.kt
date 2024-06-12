package io.tryvital.client.services

import io.tryvital.client.services.data.*
import retrofit2.Retrofit
import retrofit2.http.*

@Suppress("unused")
interface LinkService {
    @POST("link/token")
    suspend fun createLink(
        @Body request: CreateLinkRequest,
    ): CreateLinkResponse

    @POST("link/provider/password/{provider}")
    suspend fun linkPasswordProvider(
        @Path("provider") provider: String,
        @Body request: LinkPasswordProviderInput,
        @Header("x-vital-link-token") linkToken: String,
    ): LinkResponse

    @POST("link/provider/password/{provider}/complete_mfa")
    suspend fun completePasswordProviderMFA(
        @Path("provider") provider: String,
        @Body request: CompletePasswordProviderMFAInput,
        @Header("x-vital-link-token") linkToken: String,
    ): LinkResponse

    @POST("link/provider/email/{provider}")
    suspend fun linkEmailProvider(
        @Path("provider") provider: String,
        @Body request: LinkEmailProviderInput,
        @Header("x-vital-link-token") linkToken: String,
    ): LinkResponse

    @GET("link/provider/oauth/{provider}")
    suspend fun linkOauthProvider(
        @Path("provider") provider: String,
        @Header("x-vital-link-token") linkToken: String,
    ): OauthLinkResponse

    companion object {
        fun create(retrofit: Retrofit): LinkService {
            return retrofit.create(LinkService::class.java)
        }
    }
}
