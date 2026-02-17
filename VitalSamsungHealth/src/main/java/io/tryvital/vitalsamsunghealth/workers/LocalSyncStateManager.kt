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
import io.tryvital.vitalsamsunghealth.exceptions.ConnectionDestroyed
import io.tryvital.vitalsamsunghealth.exceptions.ConnectionPaused
import io.tryvital.vitalsamsunghealth.model.HealthConnectConnectionStatus
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
                ConnectionPolicy.AutoConnect -> HealthConnectConnectionStatus.AutoConnect
                ConnectionPolicy.Explicit -> when (syncStatus) {
                    UserSDKSyncStatus.Active -> HealthConnectConnectionStatus.Connected
                    UserSDKSyncStatus.Paused -> HealthConnectConnectionStatus.ConnectionPaused
                    UserSDKSyncStatus.Error, null -> HealthConnectConnectionStatus.Disconnected
                }
            }
        },
        connectionPausedFactory = { ConnectionPaused() },
        connectionDestroyedFactory = { ConnectionDestroyed() },
    )

    override fun getPersistedLocalSyncState(): LocalSyncState? = delegate.getPersistedLocalSyncState()

    val connectionStatus: StateFlow<HealthConnectConnectionStatus> get() = delegate.connectionStatus

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
