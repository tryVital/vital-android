package io.tryvital.vitalhealthcore.workers

import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import io.tryvital.client.VitalClient
import io.tryvital.client.createConnectedSourceIfNotExist
import io.tryvital.client.services.VitalPrivateApi
import io.tryvital.client.services.data.ManualProviderSlug
import io.tryvital.client.services.data.UserSDKSyncStateBody
import io.tryvital.client.services.data.UserSDKSyncStatus
import io.tryvital.client.utils.InstantJsonAdapter
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalhealthcore.model.ConnectionPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.TimeZone

class BaseLocalSyncStateManager<ConnectionStatus>(
    private val vitalClient: VitalClient,
    private val vitalLogger: VitalLogger,
    private val preferences: SharedPreferences,
    private val providerSlug: ManualProviderSlug,
    private val localSyncStateKey: String,
    private val connectionPolicyKey: String,
    private val numberOfDaysToBackfillKey: String,
    private val statusMapper: (ConnectionPolicy, UserSDKSyncStatus?) -> ConnectionStatus,
    private val connectionPausedFactory: () -> Throwable,
    private val connectionDestroyedFactory: () -> Throwable,
) : LocalSyncStateProvider {

    override fun getPersistedLocalSyncState(): LocalSyncState? = preferences.getJson(localSyncStateKey)

    val connectionPolicy: ConnectionPolicy
        get() = preferences.getJson(connectionPolicyKey) ?: ConnectionPolicy.AutoConnect

    fun setConnectionPolicy(policy: ConnectionPolicy) {
        preferences.edit()
            .putJson(connectionPolicyKey, policy)
            .apply()
        connectionStatusDidChange()
    }

    fun setPersistedLocalSyncState(newValue: LocalSyncState?) {
        preferences.edit()
            .putJson(localSyncStateKey, newValue)
            .apply()
        connectionStatusDidChange()
    }

    val connectionStatus: StateFlow<ConnectionStatus> get() = _connectionStatus

    private val _computedConnectionStatus: ConnectionStatus
        get() = statusMapper(connectionPolicy, getPersistedLocalSyncState()?.status)

    private val _connectionStatus: MutableStateFlow<ConnectionStatus> = MutableStateFlow(_computedConnectionStatus)

    private fun connectionStatusDidChange() {
        _connectionStatus.value = _computedConnectionStatus
    }

    @OptIn(VitalPrivateApi::class)
    suspend fun getLocalSyncState(
        forceRemoteCheck: Boolean = false,
        onRevalidation: (() -> Unit)? = null,
    ): LocalSyncState {
        val state = getPersistedLocalSyncState()
        if (state != null && !forceRemoteCheck && state.expiresAt > Instant.now()) {
            checkLocalSyncState(state)
            return state
        }

        return localSyncStateMutex.withLock {
            val previousState = getPersistedLocalSyncState()
            if (previousState != null && !forceRemoteCheck && previousState.expiresAt > Instant.now()) {
                checkLocalSyncState(previousState)
                return@withLock previousState
            }

            vitalLogger.info { "LocalSyncState: revalidating" }
            onRevalidation?.invoke()

            when (connectionPolicy) {
                ConnectionPolicy.AutoConnect -> {
                    vitalClient.createConnectedSourceIfNotExist(providerSlug)
                }

                ConnectionPolicy.Explicit -> {
                    // No-op; If the connection has been destroyed via the Junction API,
                    // healthConnectSdkSyncState will report status=error.
                }
            }

            val now = Instant.now()

            val numberOfDaysToBackfill =
                minOf(preferences.getInt(numberOfDaysToBackfillKey, 30).toLong(), 30)
            val proposedStart = now.minus(numberOfDaysToBackfill, ChronoUnit.DAYS)
            val backendState = vitalClient.vitalPrivateService.sdkSyncState(
                VitalClient.checkUserId(),
                providerSlug,
                UserSDKSyncStateBody(
                    tzinfo = TimeZone.getDefault().id,
                    requestStartDate = proposedStart,
                    requestEndDate = now,
                ),
            )

            val newState = LocalSyncState(
                status = backendState.status,
                historicalStageAnchor = backendState.requestStartDate ?: previousState?.historicalStageAnchor ?: now,
                defaultDaysToBackfill = previousState?.defaultDaysToBackfill ?: numberOfDaysToBackfill,
                ingestionEnd = backendState.requestEndDate,
                perDeviceActivityTS = backendState.perDeviceActivityTS,
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
                throw connectionPausedFactory()
            }

            UserSDKSyncStatus.Error -> {
                vitalLogger.info { "LocalSyncState: connection is destroyed" }
                throw connectionDestroyedFactory()
            }

            UserSDKSyncStatus.Active, null -> {
                // No-op
            }
        }
    }

    companion object {
        internal val localSyncStateMutex = Mutex(false)

        private val moshi: Moshi by lazy {
            Moshi.Builder()
                .add(Instant::class.java, InstantJsonAdapter)
                .build()
        }

        @OptIn(ExperimentalStdlibApi::class)
        private inline fun <reified T : Any> SharedPreferences.getJson(key: String): T? =
            getJson(key, default = null)

        @OptIn(ExperimentalStdlibApi::class)
        private inline fun <reified T> SharedPreferences.getJson(key: String, default: T): T {
            val jsonString = getString(key, null) ?: return default
            val adapter = moshi.adapter<T>()
            return adapter.fromJson(jsonString)
                ?: throw IllegalStateException("Failed to decode JSON string")
        }

        @OptIn(ExperimentalStdlibApi::class)
        private inline fun <reified T : Any> SharedPreferences.Editor.putJson(
            key: String,
            value: T?,
        ): SharedPreferences.Editor {
            val adapter = moshi.adapter<T>()
            return putString(key, value?.let(adapter::toJson))
        }
    }
}
