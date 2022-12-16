package io.tryvital.sample.ui.healthconnect

import android.content.Context
import androidx.health.connect.client.permission.HealthPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.tryvital.client.services.data.User
import io.tryvital.sample.UserRepository
import io.tryvital.vitalhealthconnect.VitalHealthConnectManager
import io.tryvital.vitalhealthconnect.model.HealthConnectAvailability
import io.tryvital.vitalhealthconnect.model.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
                it.copy(
                    available = vitalHealthConnectManager.isAvailable(context),
                    permissionsGranted = vitalHealthConnectManager.hasAllPermissions(context)
                )
            }
        }

        viewModelScope.launch {
            vitalHealthConnectManager.status.collect {
                viewModelState.update { state ->
                    state.copy(syncStatus = it)
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
                    permissionsGranted = vitalHealthConnectManager.hasAllPermissions(context)
                )
            }
        }
    }

    fun getPermissions(): Set<HealthPermission> {
        return vitalHealthConnectManager.requiredPermissions
    }

    fun linkProvider() {
        viewModelScope.launch {
            vitalHealthConnectManager.linkUserHealthConnectProvider("vitalexample://callback")
        }
    }

    fun readAndUploadHealthData() {
        viewModelScope.launch {
            vitalHealthConnectManager.apply {
                setUserId(userRepository.selectedUser!!.userId!!)
                configureHealthConnectClient(true)
            }
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
    val syncStatus: SyncStatus = SyncStatus.Unknown,
)