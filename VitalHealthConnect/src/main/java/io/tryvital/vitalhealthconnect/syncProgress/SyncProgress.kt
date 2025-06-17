package io.tryvital.vitalhealthconnect.syncProgress

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.tryvital.vitalhealthconnect.model.BackfillType
import io.tryvital.vitalhealthconnect.model.VitalResource
import java.util.Date


@JsonClass(generateAdapter = true)
internal data class SyncProgress(
    @Json(name = "backfill_types")
    val backfillTypes: MutableMap<BackfillType, Resource> = mutableMapOf()
) {

    @JsonClass(generateAdapter = false)
    enum class SystemEventType(val raw: Int) {
        healthConnectCalloutBackground(0),
        healthConnectCalloutForeground(1),
        backgroundProcessingTask(2),
        healthConnectCalloutAppLaunching(3),
        healthConnectCalloutAppTerminating(4);
    }

    @JsonClass(generateAdapter = true)
    data class Event<T>(
        val timestamp: Date,
        val type: T,
        var count: Int = 1,
        @Json(name = "error_details")
        val errorDetails: String? = null
    ) {
        val id: Date get() = timestamp
    }

    @JsonClass(generateAdapter = false)
    enum class SyncStatus(val raw: Int) {
        deprioritized(0),
        started(1),
        readChunk(2),
        uploadedChunk(3),
        cancelled(4),
        completed(5),
        noData(6),
        error(7),
        revalidatingSyncState(8),
        timedOut(9),

        /** Expected/transient error (e.g. iOS data‑protection lock). */
        expectedError(10);

        val isInProgress: Boolean
            get() = when (this) {
                deprioritized, started, readChunk, uploadedChunk, revalidatingSyncState -> true
                else -> false
            }
    }

    @JsonClass(generateAdapter = true)
    data class Sync(
        val start: Date,
        var end: Date? = null,
        val tags: MutableSet<SyncContextTag> = mutableSetOf(),
        val statuses: MutableList<Event<SyncStatus>> = mutableListOf(),
        @Json(name = "data_count")
        var dataCount: Int = 0
    ) {
        val id: Date get() = start
        val lastStatus: SyncStatus get() = statuses.last().type

        fun append(status: SyncStatus, timestamp: Date = Date(), errorDetails: String? = null) {
            statuses += Event(timestamp, status, errorDetails = errorDetails)
        }

        fun pruneDeprioritizedStatus(afterFirst: Int) {
            val indices = statuses
                .asSequence()
                .drop(afterFirst)
                .withIndex()
                .filter { it.value.type == SyncStatus.deprioritized }
                .map { it.index + afterFirst }
                .toSet()
            statuses.removeAll(statuses.filterIndexed { i, _ -> i in indices })
        }
    }

    @JsonClass(generateAdapter = true)
    data class SyncID(
        val resource: VitalResource,
        val start: Date = Date(),
        val tags: MutableSet<SyncContextTag> = mutableSetOf()
    )

    @JsonClass(generateAdapter = true)
    data class Resource(
        val syncs: MutableList<Sync> = mutableListOf(),
        @Json(name = "system_events")
        val systemEvents: MutableList<Event<SystemEventType>> = mutableListOf(),
        @Json(name = "data_count")
        var dataCount: Int = 0,
        @Json(name = "first_asked")
        var firstAsked: Date? = null
    ) {
        val latestSync: Sync? get() = syncs.lastOrNull()
        inline fun with(action: Resource.() -> Unit) = action()
    }

    @JsonClass(generateAdapter = false)
    enum class SyncContextTag(val raw: Int) {
        foreground(0),
        background(1),
        healthKit(2),
        processingTask(3),
        historicalStage(4),
        barUnavailable(5),
        lowPowerMode(6),
        maintenanceTask(7),
        manual(8),
        appLaunching(9),
        appTerminating(10),
    }
}

