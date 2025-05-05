package io.tryvital.client

import io.tryvital.client.services.data.ProviderSlug

object VitalClientPrefKeys {
    const val firstInstallTimeKey = "appFirstInstallTime"
    const val externalUserId = "externalUserId"
    fun userHasConnectedTo(slug: ProviderSlug) = "connected_source.${slug}"
}
