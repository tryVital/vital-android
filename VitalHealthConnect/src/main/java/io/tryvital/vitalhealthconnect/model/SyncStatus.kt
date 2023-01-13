package io.tryvital.vitalhealthconnect.model

sealed class SyncStatus {
    data class ResourceSyncFailed(val resource: HealthResource) : SyncStatus()
    data class ResourceNothingToSync(val resource: HealthResource) : SyncStatus()
    data class ResourceSyncing(val resource: HealthResource) : SyncStatus()
    data class ResourceSyncingComplete(val resource: HealthResource) : SyncStatus()
    object SyncingCompleted : SyncStatus()
    object Unknown : SyncStatus()
}

