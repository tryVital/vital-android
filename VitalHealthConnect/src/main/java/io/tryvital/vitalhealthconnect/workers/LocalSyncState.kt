package io.tryvital.vitalhealthconnect.workers

import com.squareup.moshi.JsonClass
import io.tryvital.client.services.data.UserSDKSyncStatus
import io.tryvital.vitalhealthconnect.model.RemappedVitalResource
import java.time.Instant
import java.time.temporal.ChronoUnit

@JsonClass(generateAdapter = true)
data class LocalSyncState(
    val status: UserSDKSyncStatus? = null,
    val historicalStageAnchor: Instant,
    val defaultDaysToBackfill: Long,
    val ingestionEnd: Instant?,
    val perDeviceActivityTS: Boolean,
    val expiresAt: Instant,
    val reportingInterval: Long? = null,
) {

    fun historicalStartDate(@Suppress("UNUSED_PARAMETER") resource: RemappedVitalResource): Instant {
        return historicalStageAnchor.minus(defaultDaysToBackfill, ChronoUnit.DAYS)
    }
}
