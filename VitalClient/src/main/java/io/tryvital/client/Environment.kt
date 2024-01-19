package io.tryvital.client

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
enum class Environment {
    Dev, Sandbox, Production
}