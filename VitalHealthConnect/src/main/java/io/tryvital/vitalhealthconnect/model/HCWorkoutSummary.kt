package io.tryvital.vitalhealthconnect.model

internal data class HCWorkoutSummary(
    val heartRateMaximum: Int? = null,
    val heartRateMinimum: Int? = null,
    val heartRateMean: Int? = null,
    val distanceMeter: Double? = null,
    val caloriesBurned: Double? = null,
    val heartRateZone1: Int? = null,
    val heartRateZone2: Int? = null,
    val heartRateZone3: Int? = null,
    val heartRateZone4: Int? = null,
    val heartRateZone5: Int? = null,
    val heartRateZone6: Int? = null,
)
