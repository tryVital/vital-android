package io.tryvital.sample.ui.healthconnect

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.tryvital.client.VitalClient
import io.tryvital.client.VitalClientPrefKeys
import io.tryvital.client.services.data.User
import io.tryvital.sample.AppSettingsStore
import io.tryvital.sample.UserRepository
import io.tryvital.vitalhealthconnect.VitalHealthConnectManager
import io.tryvital.vitalhealthconnect.model.HealthConnectAvailability
import io.tryvital.vitalhealthconnect.model.VitalResource
import io.tryvital.vitalhealthconnect.model.WritableVitalResource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.system.exitProcess

class HealthConnectViewModel(
    private val context: Context,
    private val settingsStore: AppSettingsStore,
    private val userRepository: UserRepository
) : ViewModel() {
    private val vitalHealthConnectManager = VitalHealthConnectManager.getOrCreate(context)

    private val isCurrentSDKUser
        get() = userRepository.selectedUser!!.userId == VitalClient.currentUserId

    private val viewModelState =
        MutableStateFlow(HealthConnectViewModelState(
            user = userRepository.selectedUser!!,
            isCurrentSDKUser = isCurrentSDKUser,
        ))
    val uiState = viewModelState.asStateFlow()

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

    fun checkAvailability(context: Context) {
        viewModelState.update { it.copy(isCurrentSDKUser = this.isCurrentSDKUser) }

        viewModelState.update {
            it.copy(available = VitalHealthConnectManager.isAvailable(context))
        }
    }

    fun openHealthConnect(context: Context) = VitalHealthConnectManager.openHealthConnect(context)

    fun checkPermissions() {
        viewModelScope.launch {
            val state = viewModelState.value
            if (state.available != HealthConnectAvailability.Installed)
                return@launch

            val allResources = VitalResource.values().toSet()
            val permissionsGranted = allResources.filter(vitalHealthConnectManager::hasAskedForPermission).toSet()
            val permissionsMissing = allResources - permissionsGranted

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
            context: Context,
            settingsStore: AppSettingsStore,
            userRepository: UserRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HealthConnectViewModel(context.applicationContext, settingsStore, userRepository) as T
            }
        }
    }
}

data class HealthConnectViewModelState(
    val available: HealthConnectAvailability? = null,
    val permissionsGranted: List<VitalResource> = listOf(),
    val permissionsMissing: List<VitalResource> = listOf(),
    val user: User,
    val isCurrentSDKUser: Boolean,
    val syncStatus: String = "",
)