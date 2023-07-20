package io.tryvital.client.utils

import com.squareup.moshi.Json

internal fun getJsonName(value: Enum<*>) = value.javaClass
    .getField(value.name).getAnnotation(Json::class.java)?.name ?: value.name
