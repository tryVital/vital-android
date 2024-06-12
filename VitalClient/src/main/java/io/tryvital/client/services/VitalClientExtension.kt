package io.tryvital.client.services

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import io.tryvital.client.VitalClient
import io.tryvital.client.services.data.CreateLinkRequest
import io.tryvital.client.services.data.OAuthProviderSlug
import io.tryvital.client.services.data.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

suspend fun VitalClient.linkOAuthProvider(
    context: Context,
    userId: String,
    provider: OAuthProviderSlug,
    callback: String,
    customizeTabs: (CustomTabsIntent.Builder) -> CustomTabsIntent.Builder = { it }
) {
    val token = linkService
        .createLink(CreateLinkRequest(userId, provider.toString(), callback))

    val oauth = linkService.linkOauthProvider(
        provider = provider.toString(),
        linkToken = token.linkToken,
    )

    withContext(Dispatchers.Main) {
        val builder = CustomTabsIntent.Builder().let(customizeTabs)
        val customTabsIntent = builder.build()
        customTabsIntent.launchUrl(context, Uri.parse(oauth.oauthUrl!!))
    }
}
