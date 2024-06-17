package io.tryvital.vitalhealthconnect.model.processedresource

import io.tryvital.client.services.data.LocalBloodPressureSample

data class BloodPressureSample(
    val systolic: QuantitySample,
    val diastolic: QuantitySample,
    val pulse: QuantitySample?,
) {
    fun toBloodPressurePayload() = LocalBloodPressureSample(
        systolic = systolic.toQuantitySamplePayload(),
        diastolic = diastolic.toQuantitySamplePayload(),
        pulse = pulse?.toQuantitySamplePayload(),
    )
}