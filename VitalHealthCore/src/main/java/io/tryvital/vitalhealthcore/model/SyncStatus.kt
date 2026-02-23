package io.tryvital.vitalhealthcore.model

sealed class SyncStatus {
    data class ResourceSyncFailed(val resource: VitalResource) : SyncStatus() {
        override fun toString(): String = "SyncFailed: ${resource.name}"
    }
    data class ResourceNothingToSync(val resource: VitalResource) : SyncStatus() {
        override fun toString(): String = "NothingToSync: ${resource.name}"
    }
    data class ResourceSyncing(val resource: VitalResource) : SyncStatus() {
        override fun toString(): String = "Syncing: ${resource.name}"
    }
    data class ResourceSyncingComplete(val resource: VitalResource) : SyncStatus() {
        override fun toString(): String = "Completed: ${resource.name}"
    }
    object SyncingCompleted : SyncStatus() {
        override fun toString(): String = "Completed"
    }
    object Unknown : SyncStatus() {
        override fun toString(): String = "Unknown"
    }
}
