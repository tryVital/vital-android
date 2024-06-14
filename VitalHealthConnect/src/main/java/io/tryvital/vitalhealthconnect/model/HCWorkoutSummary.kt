package io.tryvital.vitalhealthconnect.model

internal data class HCWorkoutSummary(
    /**
     * Unit: bpm
     */
    val maxHeartRate: Long? = null,
    /**
     * Unit: bpm
     */
    val averageHeartRate: Long? = null,
    /**
     * Unit: meters
     */
    val distance: Double? = null,
    /**
     * Unit: kcal
     */
    val caloriesBurned: Double? = null,
    /**
     * Unit: meters
     */
    val elevationGained: Double? = null,
    /**
     * Unit: meters/sec
     */
    val maxSpeed: Double? = null,
    /**
     * Unit: meters/sec
     */
    val averageSpeed: Double? = null,
    /**
     * Unit: watts
     */
    val maxWatts: Double? = null,
    /**
     * Unit: watts
     */
    val averageWatts: Double? = null,
)
