package io.tryvital.vitalhealthconnect.model

data class HCActivitySummary(
    /**
     * Unit: Scalar count
     */
    val steps: Long?,
    /**
     * Unit: kcal
     */
    val activeCaloriesBurned: Double?,
    /**
     * Unit: kcal
     */
    val basalCaloriesBurned: Double?,
    /**
     * Unit: meters
     */
    val distance: Double?,
    /**
     * Unit: Scalar count
     */
    val floorsClimbed: Double?,
    /**
     * Unit: minutes
     */
    val totalExerciseDuration: Long?,
)
