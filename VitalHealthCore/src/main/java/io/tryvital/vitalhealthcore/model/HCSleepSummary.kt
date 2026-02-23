package io.tryvital.vitalhealthcore.model

data class HCSleepSummary(
    val heartRateMaximum: Int? = null,
    val heartRateMinimum: Int? = null,
    val heartRateMean: Int? = null,
    val hrvMeanSdnn: Double? = null,
    val respiratoryRateMean: Double? = null,
)
