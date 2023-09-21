package io.tryvital.sample.ui.users

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.tryvital.client.VitalClient
import io.tryvital.client.services.data.CreateUserRequest
import io.tryvital.client.services.data.OAuthProviderSlug
import io.tryvital.client.services.data.User
import io.tryvital.client.services.linkOAuthProvider
import io.tryvital.client.services.linkUserWithOauthProvider
import io.tryvital.client.utils.VitalLogger
import io.tryvital.sample.AppSettings
import io.tryvital.sample.AppSettingsStore
import io.tryvital.sample.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UsersViewModel(
    context: Context,
    private val settingsStore: AppSettingsStore,
    private val userRepository: UserRepository
) : ViewModel() {
    private val viewModelState = MutableStateFlow(UsersViewModelState())
    val uiState = viewModelState.asStateFlow()

    private val controlPlane = settingsStore.uiState
        .distinctUntilChangedBy { Triple(it.apiKey, it.environment, it.region) }
        .map {
            if (it.isSDKConfigured)
                VitalClient.controlPlane(context, it.environment, it.region, it.apiKey)
            else
                null
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    init {
        controlPlane
            .onEach { update() }
            .launchIn(viewModelScope)

        settingsStore.uiState
            .map { it.userId }
            .distinctUntilChanged()
            .onEach { sdkUserId -> viewModelState.update { it.copy(sdkUserId = sdkUserId) } }
            .launchIn(viewModelScope)
    }

    fun update() {
        val service = controlPlane.value
            ?: return viewModelState.update { it.copy(isSDKConfigured = false) }

        viewModelState.update { it.copy(isSDKConfigured = true, loading = true) }

        viewModelScope.launch {
            try {
                val response = service.getAll()
                viewModelState.update { it.copy(loading = false, users = response.users) }
            } catch (e: Exception) {
                viewModelState.update { it.copy(loading = false, users = null) }
            }
        }
    }

    fun addUser(name: String) {
        val service = controlPlane.value ?: return
        viewModelScope.launch {
            try {
                service.createUser(CreateUserRequest(name))
                update()
            } catch (e: Exception) {
                setError(e)
            }
        }
    }

    fun removeUser(user: User) {
        val service = controlPlane.value ?: return
        viewModelScope.launch {
            service.deleteUser(user.userId)
            update()
        }
    }

    fun selectUser(newSelectedUser: User) {
        viewModelScope.launch {
            viewModelState.update {
                val selectedUser = if (newSelectedUser == it.selectedUser) null else newSelectedUser

                userRepository.selectedUser = selectedUser
                it.copy(selectedUser = selectedUser)
            }
        }
    }

    fun linkUserWithProvider(context: Context, user: User) {
        viewModelScope.launch {
            if (user.userId != viewModelState.value.sdkUserId) {
                return@launch
            }

            try {
                VitalClient.getOrCreate(context).linkOAuthProvider(
                    context,
                    user.userId,
                    OAuthProviderSlug.Fitbit,
                    "vitalexample://callback"
                )
            } catch (e: Throwable) {
                setError(e)
            }
        }
    }

    fun setError(error: Throwable?) {
        if (error != null) {
            VitalLogger.getOrCreate().logE("UsersScreen error", error)
        }
        viewModelState.update { it.copy(currentError = error) }
    }

    companion object {
        fun provideFactory(
            context: Context,
            settingsStore: AppSettingsStore,
            userRepository: UserRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return UsersViewModel(context.applicationContext, settingsStore, userRepository) as T
            }
        }
    }
}

data class UsersViewModelState(
    val loading: Boolean = false,
    val sdkUserId: String? = null,
    val users: List<User>? = listOf(),
    val selectedUser: User? = null,
    var currentError: Throwable? = null,
    val isSDKConfigured: Boolean = false,
)