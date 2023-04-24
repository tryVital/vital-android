package io.tryvital.vitalhealthconnect.model

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
)
