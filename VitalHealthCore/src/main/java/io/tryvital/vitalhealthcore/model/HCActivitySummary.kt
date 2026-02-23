package io.tryvital.vitalhealthcore.model

import io.tryvital.client.services.data.ActivityDaySummary
import io.tryvital.client.services.data.LocalQuantitySample
import java.time.LocalDate

data class HCActivitySummary(
    val steps: Long? = null,
    val activeCaloriesBurned: Double? = null,
    val basalCaloriesBurned: Double? = null,
    val distance: Double? = null,
    val floorsClimbed: Double? = null,
    val totalExerciseDuration: Long? = null,
) {
    fun toDatedPayload(date: LocalDate) = ActivityDaySummary(
        date = date,
        stepsSum = steps,
        activeEnergyBurnedSum = activeCaloriesBurned,
        basalEnergyBurnedSum = basalCaloriesBurned,
        distanceWalkingRunningSum = distance,
        floorsClimbedSum = floorsClimbed?.toLong(),
        exerciseTime = totalExerciseDuration?.toDouble(),
    )
}

data class HCActivityHourlyTotals(
    val activeCalories: Map<LocalDate, List<LocalQuantitySample>> = emptyMap(),
    val steps: Map<LocalDate, List<LocalQuantitySample>> = emptyMap(),
    val distance: Map<LocalDate, List<LocalQuantitySample>> = emptyMap(),
    val floorsClimbed: Map<LocalDate, List<LocalQuantitySample>> = emptyMap(),
)
