package io.tryvital.vitalhealthconnect.workers

import android.content.SharedPreferences
import io.tryvital.client.VitalClient
import io.tryvital.client.createConnectedSourceIfNotExist
import io.tryvital.client.services.VitalPrivateApi
import io.tryvital.client.services.data.ManualProviderSlug
import io.tryvital.client.services.data.UserSDKSyncStateBody
import io.tryvital.client.services.data.UserSDKSyncStatus
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalhealthcore.workers.LocalSyncStateProvider
import io.tryvital.vitalhealthconnect.exceptions.ConnectionPaused
import io.tryvital.vitalhealthconnect.UnSecurePrefKeys
import io.tryvital.vitalhealthconnect.exceptions.ConnectionDestroyed
import io.tryvital.vitalhealthcore.model.ConnectionPolicy
import io.tryvital.vitalhealthcore.workers.LocalSyncState
import io.tryvital.vitalhealthconnect.model.HealthConnectConnectionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.TimeZone

internal class LocalSyncStateManager(
    private val vitalClient: VitalClient,
    private val vitalLogger: VitalLogger,
    private val preferences: SharedPreferences,
) : LocalSyncStateProvider {

    override fun getPersistedLocalSyncState(): LocalSyncState?
        = preferences.getJson(UnSecurePrefKeys.localSyncStateKey)

    @Suppress("unused")
    val connectionStatus: StateFlow<HealthConnectConnectionStatus> get() = _connectionStatus

    internal val connectionPolicy: ConnectionPolicy
        get() = preferences.getJson(UnSecurePrefKeys.connectionPolicyKey)
            ?: ConnectionPolicy.AutoConnect

    internal fun setConnectionPolicy(policy: ConnectionPolicy) {
        preferences.edit()
            .putJson(UnSecurePrefKeys.connectionPolicyKey, policy)
            .apply()
        connectionStatusDidChange()
    }

    internal fun setPersistedLocalSyncState(newValue: LocalSyncState?) {
        preferences.edit()
            .putJson(UnSecurePrefKeys.localSyncStateKey, newValue)
            .apply()
        connectionStatusDidChange()
    }

    private val _computedConnectionStatus: HealthConnectConnectionStatus
        get() = when (connectionPolicy) {
        ConnectionPolicy.AutoConnect -> HealthConnectConnectionStatus.AutoConnect
        ConnectionPolicy.Explicit -> {
            when (getPersistedLocalSyncState()?.status) {
                UserSDKSyncStatus.Active -> HealthConnectConnectionStatus.Connected
                UserSDKSyncStatus.Paused -> HealthConnectConnectionStatus.ConnectionPaused
                UserSDKSyncStatus.Error, null -> HealthConnectConnectionStatus.Disconnected
            }
        }
    }

    private val _connectionStatus: MutableStateFlow<HealthConnectConnectionStatus> =
        MutableStateFlow(_computedConnectionStatus)


    private fun connectionStatusDidChange() {
        _connectionStatus.value = _computedConnectionStatus
    }

    @OptIn(VitalPrivateApi::class)
    suspend fun getLocalSyncState(forceRemoteCheck: Boolean = false, onRevalidation: (() -> Unit)? = null): LocalSyncState {
        // If we have a LocalSyncState with valid TTL, return it.
        val state = getPersistedLocalSyncState()
        if (state != null && !forceRemoteCheck && state.expiresAt > Instant.now()) {
            checkLocalSyncState(state)
            return state
        }

        return localSyncStateMutex.withLock {
            // Double check if a LocalSyncState could have already been computed by the previous
            // lock holder.
            val previousState = getPersistedLocalSyncState()
            if (previousState != null && !forceRemoteCheck && previousState.expiresAt > Instant.now()) {
                checkLocalSyncState(previousState)
                return@withLock previousState
            }

            vitalLogger.info { "LocalSyncState: revalidating" }
            onRevalidation?.invoke()

            when (connectionPolicy) {
                ConnectionPolicy.AutoConnect -> {
                    /// Make sure the user has a connected source set up
                    vitalClient.createConnectedSourceIfNotExist(ManualProviderSlug.HealthConnect)
                }
                ConnectionPolicy.Explicit -> {
                    // No-op; If the connection has been destroyed via the Junction API,
                    // healthConnectSdkSyncState will report status=error.
                }
            }

            val now = Instant.now()

            // Health Connect limits historical query to first connection date mins 30 days.
            val numberOfDaysToBackfill = minOf(preferences.getInt(UnSecurePrefKeys.numberOfDaysToBackFillKey, 30).toLong(), 30)
            val proposedStart = now.minus(numberOfDaysToBackfill, ChronoUnit.DAYS)
            val backendState = vitalClient.vitalPrivateService.healthConnectSdkSyncState(
                VitalClient.checkUserId(),
                UserSDKSyncStateBody(
                    tzinfo = TimeZone.getDefault().id,
                    requestStartDate = proposedStart,
                    requestEndDate = now,
                )
            )

            val newState = LocalSyncState(
                status = backendState.status,

                // Historical start date is generally fixed once generated the first time, until signOut() reset.
                //
                // The only exception is if an ingestion start was set, in which case the most up-to-date
                // ingestion start date takes precedence.
                historicalStageAnchor = backendState.requestStartDate ?: previousState?.historicalStageAnchor ?: now,
                defaultDaysToBackfill = previousState?.defaultDaysToBackfill ?: numberOfDaysToBackfill,

                // The query upper bound (end date for historical & daily) is normally open-ended.
                // In other words, `ingestionEnd` is typically nil.
                //
                // The only exception is if an ingestion end was set, in which case the most up-to-date
                // ingestion end date dictates the query upper bound.
                ingestionEnd = backendState.requestEndDate,

                perDeviceActivityTS = backendState.perDeviceActivityTS,

                // When we should revalidate the LocalSyncState again.
                expiresAt = now.plusSeconds(backendState.expiresIn),
            )

            setPersistedLocalSyncState(newState)

            vitalLogger.info { "LocalSyncState: updated; $newState" }

            checkLocalSyncState(newState)

            return@withLock newState
        }
    }

    private fun checkLocalSyncState(state: LocalSyncState) {
        when (state.status) {
            UserSDKSyncStatus.Paused -> {
                vitalLogger.info { "LocalSyncState: connection is paused" }
                throw ConnectionPaused()
            }
            UserSDKSyncStatus.Error -> {
                vitalLogger.info { "LocalSyncState: connection is destroyed" }
                throw ConnectionDestroyed()
            }
            UserSDKSyncStatus.Active, null -> {
                // No-op
            }
        }
    }

    companion object {
        internal val localSyncStateMutex = Mutex(false)
    }
}
