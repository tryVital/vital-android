package io.tryvital.sample.ui.healthconnect

import android.content.Context
import android.util.Log
import androidx.health.connect.client.permission.HealthPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.tryvital.client.services.data.User
import io.tryvital.sample.UserRepository
import io.tryvital.vitalhealthconnect.VitalHealthConnectManager
import io.tryvital.vitalhealthconnect.VitalHealthConnectManager.Companion.vitalRequiredPermissions
import io.tryvital.vitalhealthconnect.model.HealthConnectAvailability
import io.tryvital.vitalhealthconnect.model.HealthResource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

class HealthConnectViewModel(
    private val vitalHealthConnectManager: VitalHealthConnectManager,
    private val userRepository: UserRepository
) : ViewModel() {

    private val viewModelState =
        MutableStateFlow(HealthConnectViewModelState(user = userRepository.selectedUser!!))
    val uiState = viewModelState.asStateFlow()

    fun init(context: Context) {
        viewModelScope.launch {
            viewModelState.update {
                val available = vitalHealthConnectManager.isAvailable(context)

                val permissionsGranted =
                    if (available == HealthConnectAvailability.Installed) vitalHealthConnectManager.getGrantedPermissions(
                        context
                    )
                        .containsAll(vitalRequiredPermissions) else false

                it.copy(
                    available = available,
                    permissionsGranted = permissionsGranted
                )
            }
        }

        viewModelScope.launch {
            vitalHealthConnectManager.status.collect {
                viewModelState.update { state ->
                    state.copy(syncStatus = state.syncStatus.plus("\n${it}"))
                }
            }
        }
    }

    fun checkAvailability(context: Context) {
        viewModelState.update {
            it.copy(available = vitalHealthConnectManager.isAvailable(context))
        }
    }

    fun checkPermissions(context: Context) {
        viewModelScope.launch {
            viewModelState.update {
                it.copy(
                    permissionsGranted = vitalHealthConnectManager.getGrantedPermissions(context) == vitalRequiredPermissions
                )
            }
        }
    }

    fun getPermissions(): Set<HealthPermission> {
        return vitalRequiredPermissions
    }

    fun linkProvider() {
        viewModelScope.launch {
            vitalHealthConnectManager.linkUserHealthConnectProvider("vitalexample://callback")
        }
    }

    fun sync() {
        viewModelScope.launch {
            vitalHealthConnectManager.apply {
                setUserId(userRepository.selectedUser!!.userId!!)
                configureHealthConnectClient(true)
            }
        }
    }

    fun addWater() {
        viewModelScope.launch {
            vitalHealthConnectManager.setUserId(userRepository.selectedUser!!.userId!!)
            vitalHealthConnectManager.addHealthResource(
                HealthResource.Water,
                Instant.now(),
                Instant.now(),
                100.0
            )
        }
    }

    fun addGlucose() {
        viewModelScope.launch {
            vitalHealthConnectManager.setUserId(userRepository.selectedUser!!.userId!!)
            vitalHealthConnectManager.addHealthResource(
                HealthResource.Glucose,
                Instant.now(),
                Instant.now(),
                15.0
            )
        }

    }

    fun readResource(resource: HealthResource) {
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
            vitalHealthConnectManager: VitalHealthConnectManager,
            userRepository: UserRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HealthConnectViewModel(vitalHealthConnectManager, userRepository) as T
            }
        }
    }
}

data class HealthConnectViewModelState(
    val available: HealthConnectAvailability? = null,
    val permissionsGranted: Boolean? = null,
    val user: User,
    val syncStatus: String = "",
)