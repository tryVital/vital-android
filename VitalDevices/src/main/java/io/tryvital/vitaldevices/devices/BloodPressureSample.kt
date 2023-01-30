package io.tryvital.vitaldevices.devices

import io.tryvital.client.services.data.QuantitySamplePayload

data class BloodPressureSample(
    val systolic: QuantitySamplePayload,
    val diastolic: QuantitySamplePayload,
    val pulse: QuantitySamplePayload,
)
