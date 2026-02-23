package io.tryvital.vitalsamsunghealth.model

import io.tryvital.vitalsamsunghealth.healthconnect.client.records.metadata.Device

internal fun Device.toMetadataMap(): Map<String, String> {
    val metadata = mutableMapOf<String, String>()
    model?.let { metadata["_DMO"] = it }
    manufacturer?.let { metadata["_DMA"] = it }
    metadata["_DTY"] = sourceType.rawValue
    return metadata
}
