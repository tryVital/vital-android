package io.tryvital.sample.ui.healthconnect

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.permission.HealthPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.tryvital.client.VitalClient
import io.tryvital.client.healthconnect.HealthConnectAvailability
import io.tryvital.client.services.data.User
import io.tryvital.sample.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HealthConnectViewModel(
    private val vitalClient: VitalClient,
    userRepository: UserRepository
) : ViewModel() {

    private val viewModelState =
        MutableStateFlow(HealthConnectViewModelState(user = userRepository.selectedUser!!))
    val uiState = viewModelState.asStateFlow()

    fun init(context: Context) {
        viewModelScope.launch {
            viewModelState.update {
                it.copy(
                    available = vitalClient.healthConnectManager.isAvailable(context),
                    permissionsGranted = vitalClient.healthConnectManager.hasAllPermissions(context)
                )
            }
        }
    }

    fun checkAvailability(context: Context) {
        viewModelState.update {
            it.copy(available = vitalClient.healthConnectManager.isAvailable(context))
        }
    }

    fun checkPermissions(context: Context) {
        viewModelScope.launch {
            viewModelState.update {
                it.copy(
                    permissionsGranted = vitalClient.healthConnectManager.hasAllPermissions(context)
                )
            }
        }
    }

    fun getPermissions(): Set<HealthPermission> {
        return vitalClient.healthConnectManager.permissions
    }

    fun createRequestPermissionResultContract(): ActivityResultContract<Set<HealthPermission>, Set<HealthPermission>> {
        return vitalClient.healthConnectManager.createRequestPermissionResultContract()
    }

    companion object {
        fun provideFactory(
            client: VitalClient,
            userRepository: UserRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HealthConnectViewModel(client, userRepository) as T
            }
        }
    }
}

data class HealthConnectViewModelState(
    val available: HealthConnectAvailability? = null,
    val permissionsGranted: Boolean? = null,
    val user: User
)