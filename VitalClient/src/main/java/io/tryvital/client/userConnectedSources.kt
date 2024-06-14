package io.tryvital.client

import ManualProviderRequest
import io.tryvital.client.services.VitalPrivateApi
import io.tryvital.client.services.data.*
import retrofit2.HttpException

fun VitalClient.hasUserConnectedTo(provider: ProviderSlug): Boolean {
    resetCachedUserConnectedSourceRecordIfNeeded()

    return this.sharedPreferences.getBoolean(
        VitalClientPrefKeys.userHasConnectedTo(provider),
        false
    )
}

@VitalPrivateApi
suspend fun VitalClient.createConnectedSourceIfNotExist(provider: ManualProviderSlug) {
    val userId = VitalClient.checkUserId()
    val slug = provider.toProviderSlug()
    if (hasUserConnectedTo(slug)) {
        // Local Hit: The client has witnessed a valid connected source for this provider before.
        return
    }

    // Local Miss: First try to query the user's current set of connected sources.
    val sources = userConnections()
    if (sources.any { it.slug == slug }) {
        // Remote Hit: The client has connected to this provider.
        return
    }

    fun recordConnectedSourceExistence() {
        sharedPreferences.edit()
            .putBoolean(VitalClientPrefKeys.userHasConnectedTo(slug), true)
            .apply()
    }

    try {
        // Remote Miss: Try to create the manual connected source.
        vitalPrivateService.manualProvider(
            provider = provider,
            request = ManualProviderRequest(userId = userId)
        )
        recordConnectedSourceExistence()

    } catch (exception: HttpException) {
        if (exception.code() == 409) {
            // A 409 means that there is already a connected source for the specified provider.
            recordConnectedSourceExistence()
        } else {
            // Rethrow the exception.
            throw exception
        }
    }
}

suspend fun VitalClient.userConnections(): List<UserConnection> {
    val userId = VitalClient.checkUserId()
    resetCachedUserConnectedSourceRecordIfNeeded()

    val response = userService.getUserConnections(userId = userId)

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

/**
 * Sometimes SharedPreferences can survive app deletion / OS reinstall due to automatic backup.
 * This method is used to detect such cases — where the user ID no longer matches — and proactively
 * clear the SharedPreferences.
 */
private fun VitalClient.resetCachedUserConnectedSourceRecordIfNeeded() {
    val userId = VitalClient.currentUserId ?: return
    val perfUserId = this.sharedPreferences.getString(VitalClientPrefKeys.connectedSourcePerfUserId, null)

    if (userId != perfUserId) {
        sharedPreferences.edit()
            .apply {
                // Delete all existing records, since the user ID has changed
                ProviderSlug.values()
                    .map { VitalClientPrefKeys.userHasConnectedTo(it) }
                    .forEach { key -> remove(key) }
            }
            .apply {
                // Write the new user ID
                putString(VitalClientPrefKeys.connectedSourcePerfUserId, userId)
            }
            .apply()
    }
}
