package io.tryvital.sample.ui.healthconnect

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.tryvital.client.VitalClient
import io.tryvital.vitalhealthconnect.ExperimentalVitalApi
import io.tryvital.vitalhealthconnect.VitalHealthConnectManager
import io.tryvital.vitalhealthconnect.VitalHealthConnectManager.Companion.openHealthConnectIntent
import io.tryvital.vitalhealthconnect.disableBackgroundSync
import io.tryvital.vitalhealthconnect.enableBackgroundSyncContract
import io.tryvital.vitalhealthconnect.isBackgroundSyncEnabled
import io.tryvital.vitalhealthconnect.model.HealthConnectAvailability
import io.tryvital.vitalhealthconnect.model.PermissionStatus
import io.tryvital.vitalhealthconnect.model.VitalResource
import io.tryvital.vitalhealthconnect.model.WritableVitalResource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

class HealthConnectViewModel(context: Context) : ViewModel() {
    private val vitalHealthConnectManager = VitalHealthConnectManager.getOrCreate(context)

    private val viewModelState = MutableStateFlow(HealthConnectViewModelState())

    var pauseSync: Boolean
        get() = vitalHealthConnectManager.pauseSynchronization
        set(newValue) { vitalHealthConnectManager.pauseSynchronization = newValue }

    @OptIn(ExperimentalVitalApi::class)
    val isBackgroundSyncEnabled: Boolean
        get() = vitalHealthConnectManager.isBackgroundSyncEnabled

    val uiState = viewModelState.asStateFlow()

    val userId: String get() = VitalClient.currentUserId ?: "<null>"

    fun init(context: Context) {
        viewModelScope.launch {
            vitalHealthConnectManager.status.collect {
                viewModelState.update { state ->
                    state.copy(syncStatus = state.syncStatus.plus("\n${it}"))
                }
            }
        }

        checkAvailability(context)
        checkPermissions()
    }

    fun createPermissionRequestContract() = vitalHealthConnectManager.createPermissionRequestContract(
        readResources = VitalResource.values().toSet(),
        writeResources = WritableVitalResource.values().toSet(),
    )

    @OptIn(ExperimentalVitalApi::class)
    fun enableBackgroundSyncContract()
        = vitalHealthConnectManager.enableBackgroundSyncContract()

    @OptIn(ExperimentalVitalApi::class)
    fun disableBackgroundSync()
        = vitalHealthConnectManager.disableBackgroundSync()

    fun checkAvailability(context: Context) {
        viewModelState.update {
            it.copy(available = VitalHealthConnectManager.isAvailable(context))
        }
    }

    fun openHealthConnect(context: Context) {
        openHealthConnectIntent(context)?.let { context.startActivity(it) }
    }

    fun checkPermissions() {
        viewModelScope.launch {
            val state = viewModelState.value
            if (state.available != HealthConnectAvailability.Installed)
                return@launch

            val allResources = VitalResource.values().toList()
            val permissionStatusMap = vitalHealthConnectManager.permissionStatus(allResources)

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

    fun sync() {
        viewModelScope.launch {
            vitalHealthConnectManager.syncData()
        }
    }

    fun addWater() {
        viewModelScope.launch {
            vitalHealthConnectManager.writeRecord(
                WritableVitalResource.Water,
                Instant.now(),
                Instant.now(),
                100.0
            )
        }
    }

    fun addGlucose() {
        viewModelScope.launch {
            vitalHealthConnectManager.writeRecord(
                WritableVitalResource.Glucose,
                Instant.now(),
                Instant.now(),
                15.0
            )
        }

    }

    fun readResource(resource: VitalResource) {
        viewModelScope.launch {
            val result = vitalHealthConnectManager.read(
                resource,
                Instant.now().plus(-10, ChronoUnit.DAYS),
                Instant.now()
            )

            Log.e("vital resource", "read: $result")
        }
    }

    companion object {
        fun provideFactory(
            context: Context
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HealthConnectViewModel(context.applicationContext) as T
            }
        }
    }
}

data class HealthConnectViewModelState(
    val available: HealthConnectAvailability? = null,
    val permissionsGranted: List<VitalResource> = listOf(),
    val permissionsMissing: List<VitalResource> = listOf(),
    val syncStatus: String = "",
)
