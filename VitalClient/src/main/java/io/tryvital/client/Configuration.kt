package io.tryvital.client

data class Configuration(
    val region: Region,
    val environment: Environment = Environment.Sandbox,
    val apiKey: String,
)
