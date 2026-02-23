package io.tryvital.sample.ui.healthconnect

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.tryvital.client.VitalClient
import io.tryvital.vitalhealthcore.model.PermissionStatus
import io.tryvital.vitalhealthcore.model.VitalResource
import io.tryvital.vitalhealthcore.model.WritableVitalResource
import io.tryvital.vitalsamsunghealth.ExperimentalVitalApi
import io.tryvital.vitalsamsunghealth.VitalSamsungHealthManager
import io.tryvital.vitalsamsunghealth.VitalSamsungHealthManager.Companion.openHealthConnectIntent
import io.tryvital.vitalsamsunghealth.disableBackgroundSync
import io.tryvital.vitalsamsunghealth.enableBackgroundSyncContract
import io.tryvital.vitalsamsunghealth.isBackgroundSyncEnabled
import io.tryvital.vitalsamsunghealth.model.HealthConnectAvailability
import io.tryvital.vitalsamsunghealth.model.HealthConnectConnectionStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

class SamsungHealthViewModel(context: Context) : ViewModel() {
    private val manager = VitalSamsungHealthManager.getOrCreate(context)

    private val viewModelState = MutableStateFlow(SamsungHealthViewModelState())

    private var currentConnectionAction: Job? = null

    var pauseSync: Boolean
        get() = manager.pauseSynchronization
        set(newValue) {
            manager.pauseSynchronization = newValue
        }

    val isBackgroundSyncEnabled: Boolean
        get() = manager.isBackgroundSyncEnabled

    val uiState = viewModelState.asStateFlow()

    val userId: String get() = VitalClient.currentUserId ?: "<null>"

    fun init(context: Context) {
        viewModelScope.launch {
            manager.status.collect {
                viewModelState.update { state ->
                    state.copy(syncStatus = state.syncStatus.plus("\n$it"))
                }
            }
        }
        viewModelScope.launch {
            manager.connectionStatus.collect {
                viewModelState.update { state ->
                    state.copy(connectionStatus = it)
                }
            }
        }

        checkAvailability(context)
        checkPermissions()
    }

    fun createPermissionRequestContract() = manager.createPermissionRequestContract(
        readResources = VitalResource.values().toSet(),
        writeResources = WritableVitalResource.values().toSet(),
    )

    @OptIn(ExperimentalVitalApi::class)
    fun enableBackgroundSyncContract() = manager.enableBackgroundSyncContract()

    @OptIn(ExperimentalVitalApi::class)
    fun disableBackgroundSync() = manager.disableBackgroundSync()

    fun checkAvailability(context: Context) {
        viewModelState.update {
            it.copy(available = VitalSamsungHealthManager.isAvailable(context))
        }
    }

    fun openSamsungHealth(context: Context) {
        openHealthConnectIntent(context)?.let { context.startActivity(it) }
    }

    fun checkPermissions() {
        viewModelScope.launch {
            val state = viewModelState.value
            if (state.available != HealthConnectAvailability.Installed) {
                return@launch
            }

            val allResources = VitalResource.values().filter(::isSupportedBySamsungDataApi)
            val permissionStatusMap = manager.permissionStatus(allResources)

            val permissionsGranted = permissionStatusMap
                .filter { it.value == PermissionStatus.Asked }
                .keys
                .toSet()
            val permissionsMissing = allResources.toSet() - permissionsGranted

            viewModelState.update {
                it.copy(
                    permissionsGranted = permissionsGranted.sortedBy(VitalResource::name),
                    permissionsMissing = permissionsMissing.sortedBy(VitalResource::name),
                )
            }
        }
    }

    fun toggleConnection() {
        check(viewModelState.value.connectionStatus != HealthConnectConnectionStatus.AutoConnect)

        if (currentConnectionAction != null) {
            return
        }

        currentConnectionAction = viewModelScope.launch {
            viewModelState.update { it.copy(isPerformingConnectionAction = true) }

            val status = viewModelState.value.connectionStatus

            try {
                if (status == HealthConnectConnectionStatus.Disconnected) {
                    manager.connect()
                } else {
                    manager.disconnect()
                }
            } finally {
                viewModelState.update { it.copy(isPerformingConnectionAction = false) }
                currentConnectionAction = null
            }
        }
    }

    fun sync() {
        viewModelScope.launch {
            manager.syncData()
        }
    }

    fun addWater() {
        viewModelScope.launch {
            manager.writeRecord(
                WritableVitalResource.Water,
                Instant.now(),
                Instant.now(),
                100.0,
            )
        }
    }

    fun addGlucose() {
        viewModelScope.launch {
            manager.writeRecord(
                WritableVitalResource.Glucose,
                Instant.now(),
                Instant.now(),
                15.0,
            )
        }
    }

    fun readResource(resource: VitalResource) {
        viewModelScope.launch {
            val result = manager.read(
                resource,
                Instant.now().plus(-10, ChronoUnit.DAYS),
                Instant.now(),
            )

            Log.e("vital samsung resource", "read: $result")
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SamsungHealthViewModel(context.applicationContext) as T
            }
        }
    }
}

private fun isSupportedBySamsungDataApi(resource: VitalResource): Boolean = when (resource) {
    VitalResource.HeartRateVariability -> false
    VitalResource.MenstrualCycle -> false
    VitalResource.RespiratoryRate -> false
    VitalResource.Meal -> false
    else -> true
}

data class SamsungHealthViewModelState(
    val available: HealthConnectAvailability? = null,
    val permissionsGranted: List<VitalResource> = listOf(),
    val permissionsMissing: List<VitalResource> = listOf(),
    val syncStatus: String = "",
    val connectionStatus: HealthConnectConnectionStatus = HealthConnectConnectionStatus.AutoConnect,
    val isPerformingConnectionAction: Boolean = false,
)
