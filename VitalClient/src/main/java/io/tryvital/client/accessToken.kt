package io.tryvital.client

import android.content.Context
import io.tryvital.client.jwt.VitalJWTAuth
import java.lang.IllegalStateException

suspend fun VitalClient.Companion.getAccessToken(context: Context): String {
    val statuses = this.status
    if (VitalClient.Status.Configured !in statuses) {
        throw IllegalStateException("Cannot get access token: SDK is not configured")
    }
    if (VitalClient.Status.UseSignInToken !in statuses) {
        throw IllegalStateException("Cannot get access token: User is not signed-in with Vital Sign-In Token")
    }
    return VitalJWTAuth.getInstance(context).withAccessToken { it }
}

suspend fun VitalClient.Companion.refreshToken(context: Context): Unit {
    val statuses = this.status
    if (VitalClient.Status.Configured !in statuses) {
        throw IllegalStateException("Cannot refresh token: SDK is not configured")
    }
    if (VitalClient.Status.UseSignInToken !in statuses) {
        throw IllegalStateException("Cannot refresh token: User is not signed-in with Vital Sign-In Token")
    }
    VitalJWTAuth.getInstance(context).refreshToken()
}
