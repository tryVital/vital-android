package io.tryvital.vitalhealthconnect.model

data class HCWorkoutSummary(
    /**
     * Unit: bpm
     */
    val maxHeartRate: Long?,
    /**
     * Unit: bpm
     */
    val averageHeartRate: Long?,
    /**
     * Unit: meters
     */
    val distance: Double?,
    /**
     * Unit: kcal
     */
    val caloriesBurned: Double?,
    /**
     * Unit: meters
     */
    val elevationGained: Double?,
    /**
     * Unit: meters/sec
     */
    val maxSpeed: Double?,
    /**
     * Unit: meters/sec
     */
    val averageSpeed: Double?,
    /**
     * Unit: watts
     */
    val maxWatts: Double?,
    /**
     * Unit: watts
     */
    val averageWatts: Double?,
)