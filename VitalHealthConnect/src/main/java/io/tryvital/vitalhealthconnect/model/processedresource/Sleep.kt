package io.tryvital.vitalhealthconnect.model.processedresource

import io.tryvital.client.services.data.SleepPayload
import java.util.*

data class Sleep(
    val id: String,
    val startDate: Date,
    val endDate: Date,
    val sourceBundle: String?,
    val deviceModel: String?,
    val heartRate: List<QuantitySample>,
    val restingHeartRate: List<QuantitySample>,
    val heartRateVariability: List<QuantitySample>,
    val oxygenSaturation: List<QuantitySample>,
    val respiratoryRate: List<QuantitySample>,
) {
    fun toSleepPayload(): SleepPayload {
        return SleepPayload(
            id = id,
            startDate = startDate,
            endDate = endDate,
            sourceBundle = sourceBundle,
            deviceModel = deviceModel,
            heartRate = heartRate.map { it.toQuantitySamplePayload() },
            restingHeartRate = restingHeartRate.map { it.toQuantitySamplePayload() },
            heartRateVariability = heartRateVariability.map { it.toQuantitySamplePayload() },
            oxygenSaturation = oxygenSaturation.map { it.toQuantitySamplePayload() },
            respiratoryRate = respiratoryRate.map { it.toQuantitySamplePayload() },
        )
    }
}