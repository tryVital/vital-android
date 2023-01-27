package io.tryvital.vitalhealthconnect.model.processedresource

import io.tryvital.client.services.data.BloodPressureSamplePayload

data class BloodPressureSample(
    val systolic: QuantitySample,
    val diastolic: QuantitySample,
    val pulse: QuantitySample?,
) {
    fun toBloodPressurePayload() = BloodPressureSamplePayload(
        systolic = systolic.toQuantitySamplePayload(),
        diastolic = diastolic.toQuantitySamplePayload(),
        pulse = pulse?.toQuantitySamplePayload(),
    )
}