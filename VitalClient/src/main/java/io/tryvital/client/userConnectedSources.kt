package io.tryvital.client

import io.tryvital.client.services.data.ManualProviderRequest
import io.tryvital.client.services.data.ManualProviderSlug
import retrofit2.HttpException

// TODO: VIT-2924 Move userId management to VitalClient
suspend fun VitalClient.createConnectedSource(provider: ManualProviderSlug, userId: String) {
    try {
        linkService.manualProvider(
            provider = provider,
            request = ManualProviderRequest(userId = userId)
        )
    } catch (exception: HttpException) {
        if (exception.code() == 409) {
            // A 409 means that there's a already a connected source, so we can fail gracefully.
            return
        } else {
            // Rethrow exception.
            throw exception
        }
    }
}
