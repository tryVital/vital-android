package io.tryvital.vitaldevices.devices

import io.tryvital.client.services.data.QuantitySample

data class BloodPressureSample(
    val systolic: QuantitySample,
    val diastolic: QuantitySample,
    val pulse: QuantitySample,
)
