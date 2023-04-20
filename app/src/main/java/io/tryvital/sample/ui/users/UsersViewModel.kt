package io.tryvital.sample.ui.users

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.tryvital.client.VitalClient
import io.tryvital.client.services.data.CreateUserRequest
import io.tryvital.client.services.data.User
import io.tryvital.client.services.linkUserWithOauthProvider
import io.tryvital.sample.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UsersViewModel(
    private val vitalClient: VitalClient,
    private val userRepository: UserRepository
) : ViewModel() {
    private val viewModelState = MutableStateFlow(UsersViewModelState())
    val uiState = viewModelState.asStateFlow()

    fun update() {
        viewModelScope.launch {
            try {
                viewModelState.update { it.copy(loading = true) }
                val response = vitalClient.userService.getAll()
                viewModelState.update { it.copy(loading = false, users = response.users) }
            } catch (e: Exception) {
                viewModelState.update { it.copy(loading = false, users = null) }
            }
        }
    }

    fun addUser(name: String) {
        viewModelScope.launch {
            try {
                vitalClient.userService.createUser(CreateUserRequest(name))
                update()
            } catch (e: Exception) {
                setError(e)
            }
        }
    }

    fun removeUser(user: User) {
        viewModelScope.launch {
            vitalClient.userService.deleteUser(user.userId ?: "")
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
            vitalClient.linkUserWithOauthProvider(
                context,
                user,
                "strava",
                "vitalexample://callback"
            )
        }
    }

    fun setError(error: Throwable?) {
        viewModelState.update { it.copy(currentError = error) }
    }

    companion object {
        fun provideFactory(
            client: VitalClient,
            userRepository: UserRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return UsersViewModel(client, userRepository) as T
            }
        }
    }
}

data class UsersViewModelState(
    val loading: Boolean = false,
    val users: List<User>? = listOf(),
    val selectedUser: User? = null,
    var currentError: Throwable? = null,
)