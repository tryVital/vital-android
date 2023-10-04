package io.tryvital.client

import io.tryvital.client.services.data.ProviderSlug

object VitalClientPrefKeys {
    const val connectedSourcePerfUserId = "connected_source._user_id"
    fun userHasConnectedTo(slug: ProviderSlug) = "connected_source.${slug}"
}
