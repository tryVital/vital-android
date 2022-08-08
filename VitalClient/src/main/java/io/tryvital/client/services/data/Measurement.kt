package io.tryvital.client.services.data

import java.util.*

data class Measurement(
    val id: Int,
    val timestamp: Date,
    val value: Double?,
    val type: String?,
    val unit: String?
)