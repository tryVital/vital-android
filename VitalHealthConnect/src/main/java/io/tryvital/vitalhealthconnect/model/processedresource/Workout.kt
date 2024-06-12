package io.tryvital.vitalhealthconnect.model.processedresource

import io.tryvital.client.services.data.WorkoutPayload
import java.time.Instant
import java.util.Date

data class Workout(
    val id: String,
    val startDate: Instant,
    val endDate: Instant,
    val sourceBundle: String?,
    val deviceModel: String?,
    val sport: String,
    val caloriesInKiloJules: Double?,
    val distanceInMeter: Double?,
    val heartRate: List<QuantitySample>,
    val respiratoryRate: List<QuantitySample>

) {
    fun toWorkoutPayload(): WorkoutPayload {
        return WorkoutPayload(
            id = id,
            startDate = startDate,
            endDate = endDate,
            sourceBundle = sourceBundle,
            deviceModel = deviceModel,
            sport = sport,
            // TODO: ManualWorkoutCreation should have had these two nullable.
            caloriesInKiloJules = caloriesInKiloJules ?: 0.0,
            distanceInMeter = distanceInMeter ?: 0.0,
            heartRate = heartRate.map { it.toQuantitySamplePayload() },
            respiratoryRate = respiratoryRate.map { it.toQuantitySamplePayload() },
        )
    }
}