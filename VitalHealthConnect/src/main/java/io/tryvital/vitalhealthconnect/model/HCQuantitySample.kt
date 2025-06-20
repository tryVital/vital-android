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
    return LocalQuantitySample(
        id = metadata?.id,
        value = value,
        unit = unit,
        startDate = startDate,
        endDate = endDate,
        type = sourceType ?: metadata?.inferredSourceType,
        sourceBundle = metadata?.dataOrigin?.packageName,
        deviceModel = null,
        metadata = metadata?.device?.toMetadataMap() ?: emptyMap()
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
    Device.TYPE_FITNESS_BAND -> SourceType.Watch
    else -> SourceType.Unknown
}
