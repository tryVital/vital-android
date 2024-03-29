package io.tryvital.vitalhealthconnect.model.processedresource

import io.tryvital.client.services.data.QuantitySamplePayload
import java.util.Date

data class QuantitySample(
    val id: String? = null,
    val value: Double,
    val unit: String,
    val startDate: Date,
    val endDate: Date,
    val sourceBundle: String? = null,
    val deviceModel: String? = null,
    val type: String? = null,
    val metadata: String? = null,
) {
    fun toQuantitySamplePayload() = QuantitySamplePayload(
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