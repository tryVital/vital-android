package io.tryvital.vitalhealthconnect.model

sealed class SyncStatus {
    data class ResourceSyncFailed(val resource: VitalResource) : SyncStatus()
    data class ResourceNothingToSync(val resource: VitalResource) : SyncStatus()
    data class ResourceSyncing(val resource: VitalResource) : SyncStatus()
    data class ResourceSyncingComplete(val resource: VitalResource) : SyncStatus()
    object SyncingCompleted : SyncStatus()
    object Unknown : SyncStatus()
}

