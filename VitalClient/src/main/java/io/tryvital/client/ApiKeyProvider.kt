package io.tryvital.client

import java.util.concurrent.atomic.AtomicReference

sealed interface ApiKeyProvider {
    fun get(): String

    @JvmInline
    value class VitalClientConfiguration(private val reference: AtomicReference<Configuration?>): ApiKeyProvider {
        override fun get(): String = reference.getOrThrow().apiKey
    }

    @JvmInline
    value class Constant(private val value: String): ApiKeyProvider {
        override fun get(): String = value
    }
}