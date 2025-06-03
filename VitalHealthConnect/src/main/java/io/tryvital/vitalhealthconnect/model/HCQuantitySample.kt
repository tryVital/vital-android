package io.tryvital.vitalhealthconnect.model

import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import io.tryvital.client.services.data.LocalQuantitySample
import io.tryvital.client.services.data.SourceType
import java.time.Instant

fun quantitySample(
    value: Double,
    unit: String,
    startDate: Instant,
    endDate: Instant,
    metadata: Metadata? = null,
    sourceType: SourceType? = null,
): LocalQuantitySample {
    val deviceMetadata = mutableMapOf<String, String>()

    metadata?.device?.let { device ->
        device.model?.let { deviceMetadata["_DMO"] = it }
        device.manufacturer?.let { deviceMetadata["_DMA"] = it }
        device.type.let { type ->
            deviceMetadata["_DTY"] = when (type) {
                Device.TYPE_UNKNOWN -> "UNKNOWN"
                Device.TYPE_WATCH -> "WATCH"
                Device.TYPE_PHONE -> "PHONE"
                Device.TYPE_SCALE -> "SCALE"
                Device.TYPE_RING -> "RING"
                Device.TYPE_HEAD_MOUNTED -> "HEAD_MOUNTED"
                Device.TYPE_FITNESS_BAND -> "FITNESS_BAND"
                Device.TYPE_CHEST_STRAP -> "CHEST_STRAP"
                Device.TYPE_SMART_DISPLAY -> "SMART_DISPLAY"
                else -> "UNKNOWN"
            }
        }
    }

    return LocalQuantitySample(
        id = metadata?.id,
        value = value,
        unit = unit,
        startDate = startDate,
        endDate = endDate,
        type = sourceType ?: metadata?.inferredSourceType,
        sourceBundle = metadata?.dataOrigin?.packageName,
        deviceModel = metadata?.device?.model,
        metadata = deviceMetadata
    )
}

internal val Metadata.inferredSourceType: SourceType? get() {
    if (recordingMethod == Metadata.RECORDING_METHOD_MANUAL_ENTRY) {
        return SourceType.App
    }

    if (device != null) {
        return device!!.sourceType
    }

    // Delegate to backend to infer source type from packageName and deviceModel.
    return null
}

internal val Device.sourceType get() = when (this.type) {
    Device.TYPE_CHEST_STRAP -> SourceType.ChestStrap
    Device.TYPE_WATCH -> SourceType.Watch
    Device.TYPE_PHONE -> SourceType.Phone
    Device.TYPE_RING -> SourceType.Ring
    Device.TYPE_SCALE -> SourceType.Scale
    else -> SourceType.Unknown
}
