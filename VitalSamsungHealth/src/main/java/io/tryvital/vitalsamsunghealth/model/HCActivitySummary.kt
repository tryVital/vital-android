package io.tryvital.vitalsamsunghealth.model

import io.tryvital.client.services.data.ActivityDaySummary
import io.tryvital.client.services.data.LocalQuantitySample
import java.time.LocalDate

internal data class HCActivitySummary(
    /**
     * Unit: Scalar count
     */
    val steps: Long? = null,
    /**
     * Unit: kcal
     */
    val activeCaloriesBurned: Double? = null,
    /**
     * Unit: kcal
     */
    val basalCaloriesBurned: Double? = null,
    /**
     * Unit: meters
     */
    val distance: Double? = null,
    /**
     * Unit: Scalar count
     */
    val floorsClimbed: Double? = null,
    /**
     * Unit: minutes
     */
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

internal data class HCActivityHourlyTotals(
    val activeCalories: Map<LocalDate, List<LocalQuantitySample>> = emptyMap(),
    val steps: Map<LocalDate, List<LocalQuantitySample>> = emptyMap(),
    val distance: Map<LocalDate, List<LocalQuantitySample>> = emptyMap(),
    val floorsClimbed: Map<LocalDate, List<LocalQuantitySample>> = emptyMap(),
)
