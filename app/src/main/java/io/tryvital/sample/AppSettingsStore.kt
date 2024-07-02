package io.tryvital.sample

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import io.tryvital.client.Environment
import io.tryvital.client.Region
import io.tryvital.client.VitalClient
import io.tryvital.sample.ui.settings.SettingsAuthMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.updateAndGet

class AppSettingsStore(
    private val sharedPreferences: SharedPreferences
) {
    private val _state = MutableStateFlow(AppSettings())
    val uiState: StateFlow<AppSettings> get() = _state

    init {
        _state.value = get()
    }

    fun get(): AppSettings {
        val value = sharedPreferences.getString("demo-settings", null) ?: return AppSettings()
        return moshi.adapter(AppSettings::class.java).fromJson(value)!!
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val newValue = _state.updateAndGet(transform)
        sharedPreferences.edit()
            .putString("demo-settings", moshi.adapter(AppSettings::class.java).toJson(newValue))
            .apply()
    }

    fun syncWithSDKStatus(context: Context) {
        // Ensure that the VitalClient has been initialized.
        VitalClient.getOrCreate(context)

        val status = VitalClient.status

        val isConfigured = VitalClient.Status.Configured in status
        update { it.copy(isSDKConfigured = isConfigured) }
    }

    companion object {
        private var shared: AppSettingsStore? = null

        fun getOrCreate(context: Context): AppSettingsStore = synchronized(AppSettingsStore) {
            if (shared != null)
                return shared!!

            shared = AppSettingsStore(
                sharedPreferences = context.getSharedPreferences(
                    "vital-demo-settings",
                    MODE_PRIVATE
                )
            )
            return shared!!
        }
    }
}

@JsonClass(generateAdapter = true)
data class AppSettings(
    val authMode: SettingsAuthMode = SettingsAuthMode.ApiKey,
    val apiKey: String = "",
    val environment: Environment = Environment.Sandbox,
    val region: Region = Region.US,
    val userId: String = "",
    val isSDKConfigured: Boolean = false,
)

private val moshi by lazy {
    Moshi.Builder()
        .build()
}
