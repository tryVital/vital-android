package io.tryvital.client

import java.util.concurrent.atomic.AtomicReference

internal fun AtomicReference<Configuration?>.getOrThrow(): Configuration
    = get() ?: throw VitalClientNotConfigured()

object SecurePrefKeys{
    internal const val regionKey = "region"
    internal const val environmentKey = "environment"
    internal const val apiKeyKey = "apiKey"
    const val userIdKey = "userId"
}

object UnSecurePrefKeys {
    const val loggerEnabledKey = "loggerEnabled"
}