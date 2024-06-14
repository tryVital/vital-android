package io.tryvital.vitalhealthconnect.model.processedresource

import io.tryvital.client.services.data.LocalQuantitySample
import io.tryvital.client.services.data.LocalSleep
import java.time.Instant

data class Sleep(
    val id: String,
    val startDate: Instant,
    val endDate: Instant,
    val sourceBundle: String?,
    val deviceModel: String?,
    val heartRate: List<LocalQuantitySample>,
    val restingHeartRate: List<LocalQuantitySample>,
    val heartRateVariability: List<LocalQuantitySample>,
    val oxygenSaturation: List<LocalQuantitySample>,
    val respiratoryRate: List<LocalQuantitySample>,
    val stages: SleepStages,
) {
    fun toSleepPayload(): LocalSleep {
        return LocalSleep(
            id = id,
            startDate = startDate,
            endDate = endDate,
            sourceBundle = sourceBundle,
            deviceModel = deviceModel,
            heartRate = heartRate,
            restingHeartRate = restingHeartRate,
            heartRateVariability = heartRateVariability,
            oxygenSaturation = oxygenSaturation,
            respiratoryRate = respiratoryRate,
        )
    }
}

data class SleepStages(
    val awakeSleepSamples: List<LocalQuantitySample>,
    val deepSleepSamples: List<LocalQuantitySample>,
    val lightSleepSamples: List<LocalQuantitySample>,
    val remSleepSamples: List<LocalQuantitySample>,
    val outOfBedSleepSamples: List<LocalQuantitySample>,
    val unknownSleepSamples: List<LocalQuantitySample>,
)

enum class SleepStage(val id: Int) {
    Deep(1),
    Light(2),
    Rem(3),
    Awake(4),
    OutOfBed(5),
    Unknown(-1),
}