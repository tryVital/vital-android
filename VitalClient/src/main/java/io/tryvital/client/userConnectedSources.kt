package io.tryvital.client

import io.tryvital.client.services.data.*
import retrofit2.HttpException

fun VitalClient.hasUserConnectedTo(provider: ProviderSlug): Boolean {
    return this.sharedPreferences.getBoolean(
        VitalClientPrefKeys.userHasConnectedTo(provider),
        false
    )
}

// TODO: VIT-2924 Move userId management to VitalClient
suspend fun VitalClient.createConnectedSource(provider: ManualProviderSlug, userId: String) {
    val slug = provider.toProviderSlug()
    if (hasUserConnectedTo(slug)) {
        // Local Hit: The client has witnessed a valid connected source for this provider before.
        return
    }

    // Local Miss: First try to query the user's current set of connected sources.
    val sources = userConnectedSources(userId = userId)
    if (sources.any { it.slug == slug }) {
        // Remote Hit: The client has connected to this provider.
        return
    }

    try {
        // Remote Miss: Try to create the manual connected source.
        linkService.manualProvider(
            provider = provider,
            request = ManualProviderRequest(userId = userId)
        )
    } catch (exception: HttpException) {
        // A 409 means that there's a already a connected source, so we can fail gracefully.
        if (exception.code() != 409) {
            // Rethrow any other exception.
            throw exception
        }
    }

    sharedPreferences.edit()
        .putBoolean(VitalClientPrefKeys.userHasConnectedTo(slug), true)
        .apply()
}

// TODO: VIT-2924 Move userId management to VitalClient
suspend fun VitalClient.userConnectedSources(userId: String): List<Source> {
    val response = userService.getProviders(userId = userId)

    val remote = response.providers.mapTo(mutableSetOf()) { it.slug }
    val keysToClear = (ProviderSlug.values().toSet() - remote)
        .map { VitalClientPrefKeys.userHasConnectedTo(it) }
        .filter { sharedPreferences.contains(it) }

    sharedPreferences.edit()
        .apply { keysToClear.forEach { key -> remove(key) } }
        .apply { remote.forEach { slug -> putBoolean(VitalClientPrefKeys.userHasConnectedTo(slug), true) } }
        .apply()

    return response.providers
}
