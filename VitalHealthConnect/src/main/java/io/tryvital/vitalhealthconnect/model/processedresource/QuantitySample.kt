package io.tryvital.vitalhealthconnect.model.processedresource

import io.tryvital.client.services.data.LocalQuantitySample
import java.time.Instant

data class QuantitySample(
    val id: String? = null,
    val value: Double,
    val unit: String,
    val startDate: Instant,
    val endDate: Instant,
    val sourceBundle: String? = null,
    val deviceModel: String? = null,
    val type: String? = null,
    val metadata: String? = null,
) {
    fun toQuantitySamplePayload() = LocalQuantitySample(
        id = id,
        value = value,
        unit = unit,
        startDate = startDate,
        endDate = endDate,
        sourceBundle = sourceBundle,
        deviceModel = deviceModel,
        type = type,
        metadata = metadata,
    )
}