package io.tryvital.vitalhealthconnect.workers

import com.squareup.moshi.JsonClass
import io.tryvital.vitalhealthconnect.model.VitalResource
import java.time.Instant
import java.time.temporal.ChronoUnit

@JsonClass(generateAdapter = true)
data class LocalSyncState(
    val historicalStageAnchor: Instant,
    val defaultDaysToBackfill: Long,
    val ingestionEnd: Instant?,
    val perDeviceActivityTS: Boolean,
    val expiresAt: Instant,
) {

    fun historicalStartDate(@Suppress("UNUSED_PARAMETER") resource: VitalResource): Instant {
        return historicalStageAnchor.minus(defaultDaysToBackfill, ChronoUnit.DAYS)
    }
}
