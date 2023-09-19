package io.tryvital.sample.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.tryvital.client.Environment
import io.tryvital.client.Region
import io.tryvital.client.VitalClient
import io.tryvital.client.services.data.CreateUserRequest
import io.tryvital.client.utils.VitalLogger
import io.tryvital.sample.AppSettings
import io.tryvital.sample.AppSettingsStore
import io.tryvital.vitalhealthconnect.VitalHealthConnectManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

class SettingsViewModel(private val store: AppSettingsStore): ViewModel() {
    private val viewModelState = MutableStateFlow(SettingsState())
    val uiState = viewModelState.asStateFlow()

    init {
        store.uiState
            .onEach { appSettings ->
                viewModelState.update { it.copy(appSettings = appSettings) }
            }
            .launchIn(viewModelScope)
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

    fun didLaunch(context: Context) {
        updateSDKStatus(context)
    }

    fun setEnvironment(environment: Environment, region: Region) {
        // viewModelState will be indirectly updated as an observer
        store.update { it.copy(environment = environment, region = region) }
    }

    fun setAuthMode(mode: SettingsAuthMode) {
        // viewModelState will be indirectly updated as an observer
        store.update { it.copy(authMode = mode) }
    }

    fun setApiKey(value: String) {
        // viewModelState will be indirectly updated as an observer
        store.update { it.copy(apiKey = value) }
    }

    fun setUserId(value: String) {
        // viewModelState will be indirectly updated as an observer
        store.update { it.copy(userId = value) }
    }

    fun clearError() {
        viewModelState.update { it.copy(currentError = null) }
    }

    fun generateUserID(context: Context) {
        val settings = uiState.value.appSettings
        val service = VitalClient.controlPlane(context, settings.environment, settings.region, settings.apiKey)
        viewModelScope.launch {
            try {
                val request = CreateUserRequest("android-demo-${Instant.now()}")
                val response = service.createUser(request)
                setUserId(response.userId)
            } catch (e: Throwable) {
                VitalLogger.getOrCreate().logE("Demo: Generate User ID failed", e)
                viewModelState.update { it.copy(currentError = e) }
            }
        }
    }

    fun forceTokenRefresh(context: Context) {
        viewModelScope.launch {
            VitalClient.debugForceTokenRefresh(context)
        }
    }

    fun configureSDK(context: Context) {
        val state = uiState.value
        VitalClient.configure(context, state.appSettings.region, state.appSettings.environment, state.appSettings.apiKey)
        VitalClient.setUserId(context, state.appSettings.userId)

        VitalHealthConnectManager.getOrCreate(context).configureHealthConnectClient()

        updateSDKStatus(context)
    }

    fun signInWithToken(context: Context) {
        val settings = uiState.value.appSettings
        val service = VitalClient.controlPlane(context, settings.environment, settings.region, settings.apiKey)

        viewModelScope.launch {
            try {
                // Emulate a backend-created Vital Sign-In Token.
                val response = service.createSignInToken(settings.userId)
                check(response.userId == settings.userId)

                // Sign-in with the SDK using the created token.
                VitalClient.signIn(context, response.signInToken)

                VitalHealthConnectManager.getOrCreate(context).configureHealthConnectClient()

                updateSDKStatus(context)

            } catch (e: Throwable) {
                VitalLogger.getOrCreate().logE("Demo: Sign-in failed", e)
                viewModelState.update { it.copy(currentError = e) }
            }
        }
    }

    fun resetSDK(context: Context) {
        VitalClient.getOrCreate(context).cleanUp()
        updateSDKStatus(context)
    }

    fun updateSDKStatus(context: Context) {
        // Ensure that the VitalClient has been initialized.
        VitalClient.getOrCreate(context)

        val status = VitalClient.status

        viewModelState.update {
            it.copy(
                sdkUserId = if (VitalClient.Status.SignedIn in status) VitalClient.currentUserId ?: "error" else "null",
            )
        }

        val isConfigured = VitalClient.Status.Configured in status
        store.update { it.copy(isSDKConfigured = isConfigured) }
    }

    companion object {
        fun provideFactory(
            store: AppSettingsStore
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(store) as T
            }
        }
    }
}

enum class SettingsAuthMode {
    ApiKey, SignInTokenDemo;
}

data class SettingsState(
    val appSettings: AppSettings = AppSettings(),

    /**
     * The current SDK user ID.
     *
     * Does not have to match [AppSettings.userId] if new app settings have not yet applied to the
     * SDK.
     * */
    val sdkUserId: String = "",

    val currentError: Throwable? = null,
) {
    val isApiKeyValid: Boolean
        get() = appSettings.apiKey != ""

    val isUserIdValid: Boolean
        get() = runCatching { UUID.fromString(appSettings.userId) }.isSuccess

    val canConfigureSDK: Boolean
        get() = !appSettings.isSDKConfigured && isApiKeyValid && isUserIdValid

    val canGenerateUserId: Boolean
        get() = !appSettings.isSDKConfigured && isApiKeyValid && !isUserIdValid

    val canResetSDK: Boolean
        get() = appSettings.isSDKConfigured

    val canForceTokenRefresh: Boolean
        get() = appSettings.authMode == SettingsAuthMode.SignInTokenDemo && sdkUserId != ""
}
