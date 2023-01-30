package io.tryvital.vitalhealthconnect.model.processedresource

import io.tryvital.client.services.data.WorkoutPayload
import java.util.*

data class Workout(
    val id: String,
    val startDate: Date,
    val endDate: Date,
    val sourceBundle: String?,
    val deviceModel: String?,
    val sport: String,
    val caloriesInKiloJules: Long,
    val distanceInMeter: Long,
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
            caloriesInKiloJules = caloriesInKiloJules,
            distanceInMeter = distanceInMeter,
            heartRate = heartRate.map { it.toQuantitySamplePayload() },
            respiratoryRate = respiratoryRate.map { it.toQuantitySamplePayload() },
        )
    }
}