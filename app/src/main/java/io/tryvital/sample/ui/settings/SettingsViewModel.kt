package io.tryvital.sample.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.JsonClass
import io.tryvital.client.AuthenticateRequest
import io.tryvital.client.Environment
import io.tryvital.client.Region
import io.tryvital.client.VitalClient
import io.tryvital.client.services.data.CreateUserRequest
import io.tryvital.client.utils.VitalLogger
import io.tryvital.sample.AppSettings
import io.tryvital.sample.AppSettingsStore
import io.tryvital.vitalhealthconnect.VitalHealthConnectManager
import io.tryvital.vitalhealthcore.model.ConnectionPolicy
import io.tryvital.vitalsamsunghealth.VitalSamsungHealthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okio.ByteString.Companion.encodeUtf8
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

    val connectionPolicies = listOf(
        Pair(ConnectionPolicy.AutoConnect, "Auto Connect"),
        Pair(ConnectionPolicy.Explicit, "Explicit"),
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
        store.syncWithSDKStatus(context)
    }

    fun setEnvironment(environment: Environment, region: Region) {
        // viewModelState will be indirectly updated as an observer
        store.update { it.copy(environment = environment, region = region) }
    }

    fun setAuthMode(mode: SettingsAuthMode) {
        // viewModelState will be indirectly updated as an observer
        store.update { it.copy(authMode = mode) }
    }

    fun setConnectionPolicy(policy: ConnectionPolicy) {
        // viewModelState will be indirectly updated as an observer
        store.update { it.copy(connectionPolicy = policy) }
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
            try {
                VitalClient.debugForceTokenRefresh(context)
            } catch (e: Throwable) {
                VitalLogger.getOrCreate().logE("Demo: Force Token Refresh Failed", e)
                viewModelState.update { it.copy(currentError = e) }
            }
        }
    }

    fun configureSDK(context: Context) {
        val state = uiState.value

        // Emulate an external user ID by taking SHA1(userId)[0:10]
        // This should normally be a unique identifier from your own identity provider.
        // e.g., your own user primary key, or a stable ID from IdP such as Firebase Auth or Auth0.
        val shortHash = state.appSettings.userId.encodeUtf8().sha1().hex().substring(0, 10)
        val externalUserId = "externalUserId:${shortHash}"

        viewModelScope.launch {
            VitalClient.identifyExternalUser(context, externalUserId) {
                AuthenticateRequest.APIKey(
                    userId = state.appSettings.userId,
                    key = state.appSettings.apiKey,
                    environment = state.appSettings.environment,
                    region = state.appSettings.region
                )
            }

            VitalHealthConnectManager.getOrCreate(context).configureHealthConnectClient(
                logsEnabled = true,
                connectionPolicy = state.appSettings.connectionPolicy,
            )

            VitalSamsungHealthManager.getOrCreate(context).configureSamsungHealthClient(
                logsEnabled = true,
                connectionPolicy = state.appSettings.connectionPolicy,
            )

            store.syncWithSDKStatus(context)
        }
    }

    fun signInWithToken(context: Context) {
        val settings = uiState.value.appSettings
        val service = VitalClient.controlPlane(context, settings.environment, settings.region, settings.apiKey)

        viewModelScope.launch {
            try {

                // Sign-in with the SDK using the Sign In Token Scheme.

                // Emulate an external user ID by taking SHA1(userId)[0:10]
                // This should normally be a unique identifier from your own identity provider.
                // e.g., your own user primary key, or a stable ID from IdP such as Firebase Auth or Auth0.
                val shortHash = settings.userId.encodeUtf8().sha1().hex().substring(0, 10)
                val externalUserId = "externalUserId:${shortHash}"

                VitalClient.identifyExternalUser(context, externalUserId) {

                    // Emulate a backend-created Vital Sign-In Token.
                    val response = service.createSignInToken(settings.userId)
                    check(response.userId == settings.userId)

                    AuthenticateRequest.SignInToken(response.signInToken)
                }

                VitalHealthConnectManager.getOrCreate(context).configureHealthConnectClient(
                    logsEnabled = true,
                    connectionPolicy = settings.connectionPolicy,
                )

                store.syncWithSDKStatus(context)

            } catch (e: Throwable) {
                VitalLogger.getOrCreate().logE("Demo: Sign-in failed", e)
                viewModelState.update { it.copy(currentError = e) }
            }
        }
    }

    fun resetSDK(context: Context) {
        viewModelScope.launch {
            VitalClient.getOrCreate(context).signOut()
            store.syncWithSDKStatus(context)
        }
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

@JsonClass(generateAdapter = false)
enum class SettingsAuthMode {
    ApiKey, SignInTokenDemo;
}

data class SettingsState(
    val appSettings: AppSettings = AppSettings(),

    val currentError: Throwable? = null,
) {

    /**
     * The current SDK user ID.
     *
     * Does not have to match [AppSettings.userId] if new app settings have not yet applied to the
     * SDK.
     * */
    val sdkUserId: String?
        get() = appSettings.sdkUserId

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
