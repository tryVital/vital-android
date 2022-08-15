package io.tryvital.sample.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.tryvital.client.VitalClient
import io.tryvital.client.services.data.CreateUserRequest
import io.tryvital.client.services.data.User
import io.tryvital.client.services.linkProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UsersViewModel(private val vitalClient: VitalClient) : ViewModel() {

    private val viewModelState = MutableStateFlow(UsersViewModelState())
    val uiState = viewModelState.asStateFlow()


    fun update(){
        viewModelScope.launch {
            viewModelState.update { it.copy(loading = true) }
            val response = vitalClient.userService.getAll()
            viewModelState.update { it.copy(loading = false, users = response) }
        }
    }

    fun addUser(name: String) {
        viewModelScope.launch {
            vitalClient.userService.createUser(CreateUserRequest(name))
            update()
        }
    }

    fun removeUser(user: User) {
        viewModelScope.launch {
            vitalClient.userService.deleteUser(user.userId ?: "")
            update()
        }
    }

    fun linkProvider(context: Context, user: User) {
        viewModelScope.launch {
            vitalClient.linkProvider(context, user, "strava", "vitalexample://callback")
        }
    }

    companion object {
        fun provideFactory(
            client: VitalClient,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return UsersViewModel(client) as T
            }
        }
    }
}

data class UsersViewModelState(
    val loading: Boolean = false,
    val users: List<User>? = listOf()
)