package io.tryvital.vitalhealthconnect

import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import io.tryvital.client.services.data.QuantitySample
import java.util.*

data class HCQuantitySample(
    val value: String,
    val unit: String,
    val startDate: Date,
    val endDate: Date,
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
            type = "automatic", //metadata?.device?.type?.toQuantitySampleDeviceModel()
            sourceBundle = metadata.dataOrigin.packageName,
            deviceModel = metadata.device?.model ?: fallbackDeviceModel,
        )
    }
}

private fun Int?.toQuantitySampleDeviceModel(): String? {
    return when (this) {
        Device.TYPE_WATCH -> "watch"
        Device.TYPE_PHONE -> "phone"
        Device.TYPE_SCALE -> "scale"
        Device.TYPE_RING -> "ring"
        Device.TYPE_HEAD_MOUNTED -> "headMounted"
        Device.TYPE_FITNESS_BAND -> "fitnessBand"
        Device.TYPE_CHEST_STRAP -> "chestStrap"
        Device.TYPE_SMART_DISPLAY -> "smartDisplay"
        Device.TYPE_UNKNOWN -> null
        else -> null
    }
}
