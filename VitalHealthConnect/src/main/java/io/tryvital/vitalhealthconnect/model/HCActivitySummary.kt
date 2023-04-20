package io.tryvital.vitalhealthconnect.model

import io.tryvital.client.services.data.ActivityDaySummary
import java.time.LocalDate

data class HCActivitySummary(
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
        low = null,
        medium = null,
        high = totalExerciseDuration?.toDouble(),
    )
}
