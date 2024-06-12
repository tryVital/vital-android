package io.tryvital.vitalhealthconnect.model

import androidx.health.connect.client.records.metadata.Metadata
import io.tryvital.vitalhealthconnect.model.processedresource.QuantitySample
import java.time.Instant
import java.util.Date

data class HCQuantitySample(
    val value: Double,
    val unit: String,
    val startDate: Instant,
    val endDate: Instant,
    val type: String? = null,
    val metadata: Metadata,
) {

    fun toQuantitySample(fallbackDeviceModel: String): QuantitySample {
        return QuantitySample(
            id = metadata.id,
            value = value,
            unit = unit,
            startDate = startDate,
            endDate = endDate,
            type = "automatic",
            sourceBundle = metadata.dataOrigin.packageName,
            deviceModel = metadata.device?.model ?: fallbackDeviceModel,
        )
    }
}