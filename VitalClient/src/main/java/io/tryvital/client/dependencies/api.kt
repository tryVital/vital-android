package io.tryvital.client.dependencies

import io.tryvital.client.Environment
import io.tryvital.client.Region

internal fun apiBaseUrl(region: Region, environment: Environment) = when (region) {
    Region.EU -> when (environment) {
        Environment.Production -> "https://api.eu.junction.com"
        Environment.Dev -> "https://api.dev.eu.junction.com"
        Environment.Sandbox -> "https://api.sandbox.eu.junction.com"
    }
    Region.US -> when (environment) {
        Environment.Production -> "https://api.us.junction.com"
        Environment.Dev -> "https://api.dev.us.junction.com"
        Environment.Sandbox -> "https://api.sandbox.us.junction.com"
    }
}

internal fun apiAuthBaseUrl(region: Region, environment: Environment) = when (region) {
    Region.EU -> when (environment) {
        Environment.Production -> "https://auth.eu.junction.com"
        Environment.Dev -> "https://auth.dev.eu.junction.com"
        Environment.Sandbox -> "https://auth.sandbox.eu.junction.com"
    }
    Region.US -> when (environment) {
        Environment.Production -> "https://auth.us.junction.com"
        Environment.Dev -> "https://auth.dev.us.junction.com"
        Environment.Sandbox -> "https://auth.sandbox.us.junction.com"
    }
}


internal fun tokenEndpoint(region: Region, environment: Environment): String
    = "${apiAuthBaseUrl(region, environment)}/v1/token"
