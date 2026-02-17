package io.tryvital.vitalsamsunghealth.workers

import android.content.SharedPreferences
import io.tryvital.client.VitalClient
import io.tryvital.client.services.data.ManualProviderSlug
import io.tryvital.client.services.data.UserSDKSyncStatus
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalhealthcore.model.ConnectionPolicy
import io.tryvital.vitalhealthcore.workers.BaseLocalSyncStateManager
import io.tryvital.vitalhealthcore.workers.LocalSyncState
import io.tryvital.vitalhealthcore.workers.LocalSyncStateProvider
import io.tryvital.vitalsamsunghealth.UnSecurePrefKeys
import io.tryvital.vitalhealthcore.exceptions.ConnectionDestroyed
import io.tryvital.vitalhealthcore.exceptions.ConnectionPaused
import io.tryvital.vitalhealthcore.model.ConnectionStatus
import kotlinx.coroutines.flow.StateFlow

internal class LocalSyncStateManager(
    vitalClient: VitalClient,
    vitalLogger: VitalLogger,
    preferences: SharedPreferences,
) : LocalSyncStateProvider {

    private val delegate = BaseLocalSyncStateManager(
        vitalClient = vitalClient,
        vitalLogger = vitalLogger,
        preferences = preferences,
        providerSlug = ManualProviderSlug.SamsungHealth,
        localSyncStateKey = UnSecurePrefKeys.localSyncStateKey,
        connectionPolicyKey = UnSecurePrefKeys.connectionPolicyKey,
        numberOfDaysToBackfillKey = UnSecurePrefKeys.numberOfDaysToBackFillKey,
        statusMapper = { connectionPolicy, syncStatus ->
            when (connectionPolicy) {
                ConnectionPolicy.AutoConnect -> ConnectionStatus.AutoConnect
                ConnectionPolicy.Explicit -> when (syncStatus) {
                    UserSDKSyncStatus.Active -> ConnectionStatus.Connected
                    UserSDKSyncStatus.Paused -> ConnectionStatus.ConnectionPaused
                    UserSDKSyncStatus.Error, null -> ConnectionStatus.Disconnected
                }
            }
        },
        connectionPausedFactory = { ConnectionPaused() },
        connectionDestroyedFactory = { ConnectionDestroyed() },
    )

    override fun getPersistedLocalSyncState(): LocalSyncState? = delegate.getPersistedLocalSyncState()

    val connectionStatus: StateFlow<ConnectionStatus> get() = delegate.connectionStatus

    val connectionPolicy: ConnectionPolicy get() = delegate.connectionPolicy

    fun setConnectionPolicy(policy: ConnectionPolicy) {
        delegate.setConnectionPolicy(policy)
    }

    fun setPersistedLocalSyncState(newValue: LocalSyncState?) {
        delegate.setPersistedLocalSyncState(newValue)
    }

    suspend fun getLocalSyncState(
        forceRemoteCheck: Boolean = false,
        onRevalidation: (() -> Unit)? = null,
    ): LocalSyncState = delegate.getLocalSyncState(forceRemoteCheck, onRevalidation)
}
