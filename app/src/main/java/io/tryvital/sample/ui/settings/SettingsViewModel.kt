package io.tryvital.sample.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.tryvital.client.Environment
import io.tryvital.client.Region
import io.tryvital.client.VitalClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class SettingsViewModel: ViewModel() {
    private val viewModelState = MutableStateFlow(SettingsState())
    val uiState = viewModelState.asStateFlow()

    init {
        updateSDKStatus()
    }

    val authModes = listOf(
        Pair(SettingsAuthMode.ApiKey, "API Key"),
        Pair(SettingsAuthMode.SignInTokenDemo, "Sign-in Token Demo"),
    )

    val environments = listOf(
        Pair(Pair(Environment.Sandbox, Region.US), "sandbox - us"),
        Pair(Pair(Environment.Sandbox, Region.EU), "sandbox - eu"),
        Pair(Pair(Environment.Production, Region.US), "production - us"),
        Pair(Pair(Environment.Production, Region.EU), "production - eu"),
        Pair(Pair(Environment.Dev, Region.US), "dev - us"),
        Pair(Pair(Environment.Dev, Region.EU), "dev - eu"),
    )

    fun setEnvironment(environment: Environment, region: Region) {
        viewModelState.update { it.copy(environment = environment, region = region) }
    }

    fun setAuthMode(mode: SettingsAuthMode) {
        viewModelState.update { it.copy(authMode = mode) }
    }

    fun setApiKey(value: String) {
        viewModelState.update { it.copy(apiKey = value) }
    }

    fun setUserId(value: String) {
        viewModelState.update { it.copy(userId = value) }
    }

    fun generateUserID(context: Context) {
        // TODO: Generate User ID
    }

    fun configureSDK(context: Context) {
        val state = uiState.value
        VitalClient.configure(context, state.region, state.environment, state.apiKey)
    }

    fun signInWithToken(context: Context) {
        val state = uiState.value
        viewModelScope.launch {
            // TODO: Create sign-in token
            // TODO: VitalClient.signIn
        }
    }

    fun resetSDK(context: Context) {
        VitalClient.getOrCreate(context).cleanUp()
        updateSDKStatus()
    }

    fun updateSDKStatus() {
        val status = VitalClient.status

        viewModelState.update {
            it.copy(
                sdkUserId = if (VitalClient.Status.SignedIn in status) VitalClient.currentUserId ?: "error" else "null",
                sdkIsConfigured = VitalClient.Status.Configured in status,
            )
        }
    }

    companion object {
        fun provideFactory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel() as T
            }
        }
    }
}

enum class SettingsAuthMode {
    ApiKey, SignInTokenDemo;
}

data class SettingsState(
    val authMode: SettingsAuthMode = SettingsAuthMode.ApiKey,
    val userId: String = "",
    val apiKey: String = "",
    val environment: Environment = Environment.Sandbox,
    val region: Region = Region.US,

    val sdkUserId: String = "",
    val sdkIsConfigured: Boolean = false,
) {
    val isApiKeyValid: Boolean
        get() = apiKey != ""

    val isUserIdValid: Boolean
        get() = runCatching { UUID.fromString(userId) }.isSuccess

    val canConfigureSDK: Boolean
        get() = !sdkIsConfigured && isApiKeyValid && isUserIdValid

    val canGenerateUserId: Boolean
        get() = !sdkIsConfigured && isApiKeyValid

    val canResetSDK: Boolean
        get() = sdkIsConfigured
}