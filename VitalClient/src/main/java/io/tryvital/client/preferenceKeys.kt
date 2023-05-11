package io.tryvital.client

import io.tryvital.client.services.data.ProviderSlug

object VitalClientPrefKeys {
    fun userHasConnectedTo(slug: ProviderSlug) = "connected_source.${slug}"
}