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
    suspend fun passwordProvider(
        @Path("provider") provider: String,
        @Body request: PasswordProviderRequest,
        @Header("LinkToken") linkToken: String,
    ): EmailProviderResponse


    @POST("link/provider/email/{provider}")
    suspend fun emailProvider(
        @Path("provider") provider: String,
        @Body request: EmailProviderRequest,
        @Header("x-vital-link-token") linkToken: String,
    ): EmailProviderResponse

    @GET("link/provider/oauth/{provider}")
    suspend fun oauthProvider(
        @Path("provider") provider: String,
        @Header("LinkToken") linkToken: String,
    ): OauthLinkResponse

    @POST("link/provider/manual/{provider}")
    suspend fun manualProvider(
        @Path("provider") provider: String,
        @Header("LinkToken") linkToken: String,
        @Body request: ManualProviderRequest,
    ): ManualProviderResponse

    companion object {
        fun create(retrofit: Retrofit): LinkService {
            return retrofit.create(LinkService::class.java)
        }
    }
}
