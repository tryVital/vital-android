package io.tryvital.client

import android.content.Context
import io.tryvital.client.services.VitalPrivateApi
import io.tryvital.client.services.data.*
import retrofit2.HttpException

fun VitalClient.hasUserConnectedTo(provider: ProviderSlug): Boolean {
    return this.sharedPreferences.getBoolean(
        VitalClientPrefKeys.userHasConnectedTo(provider),
        false
    )
}

@VitalPrivateApi
suspend fun VitalClient.createConnectedSourceIfNotExist(provider: ManualProviderSlug) {
    val userId = VitalClient.checkUserId()
    val slug = provider.toProviderSlug()

    fun recordConnectedSourceExistence() {
        sharedPreferences.edit()
            .putBoolean(VitalClientPrefKeys.userHasConnectedTo(slug), true)
            .apply()
    }

    try {
        // Try to create the manual connected source.
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
 * This method is used to detect such cases — where the first install time no longer matches — and
 * proactively clear the SharedPreferences.
 */
internal fun VitalClient.resetSharedPreferencesOnReinstallation(context: Context) {
    val storedTime = this.sharedPreferences.getLong(VitalClientPrefKeys.firstInstallTimeKey, -1L)
    val appPackage = context.packageManager.getPackageInfo(
        context.applicationInfo.packageName, 0
    )
    val observedTime = appPackage.firstInstallTime

    if (storedTime != observedTime) {
        val editor = sharedPreferences.edit()

        if (storedTime != -1L) {
            editor.clear()
        }

        editor.putLong(VitalClientPrefKeys.firstInstallTimeKey, observedTime)
        editor.apply()
    }
}
