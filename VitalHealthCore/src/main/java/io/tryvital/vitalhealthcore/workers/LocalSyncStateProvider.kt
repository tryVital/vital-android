package io.tryvital.vitalhealthcore.workers

interface LocalSyncStateProvider {
    fun getPersistedLocalSyncState(): LocalSyncState?
}
