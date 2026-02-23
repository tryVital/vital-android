package io.tryvital.vitalsamsunghealth.model

internal data class HCSleepSummary(
   val heartRateMaximum: Int? = null,
   val heartRateMinimum: Int? = null,
   val heartRateMean: Int? = null,
   val hrvMeanSdnn: Double? = null,
   val respiratoryRateMean: Double? = null,
)
