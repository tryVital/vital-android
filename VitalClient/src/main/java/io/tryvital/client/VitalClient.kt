package io.tryvital.client

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import io.tryvital.client.dependencies.Dependencies
import io.tryvital.client.services.*
import java.util.concurrent.atomic.AtomicReference

const val VITAL_PERFS_FILE_NAME: String = "vital_health_connect_prefs"
const val VITAL_ENCRYPTED_PERFS_FILE_NAME: String = "safe_vital_health_connect_prefs"

@Suppress("unused")
class VitalClient internal constructor(context: Context) {
    val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(
            VITAL_PERFS_FILE_NAME, Context.MODE_PRIVATE
        )
    }

    val encryptedSharedPreferences: SharedPreferences by lazy {
        try {
            createEncryptedSharedPreferences(context)
        } catch (e: Exception) {
            vitalLogger.logE(
                "Failed to decrypt shared preferences, creating new encrypted shared preferences", e
            )
            context.deleteSharedPreferences(VITAL_ENCRYPTED_PERFS_FILE_NAME)
            return@lazy createEncryptedSharedPreferences(context)
        }
    }

    private val configurationReader = SharedPreferencesConfigurationReader(encryptedSharedPreferences)

    private val dependencies: Dependencies by lazy {
        Dependencies(context, configurationReader)
    }

    val activityService by lazy {
        ActivityService.create(dependencies.retrofit)
    }

    val bodyService by lazy {
        BodyService.create(dependencies.retrofit)
    }

    val linkService by lazy {
        LinkService.create(dependencies.retrofit)
    }

    val profileService by lazy {
        ProfileService.create(dependencies.retrofit)
    }

    val sleepService by lazy {
        SleepService.create(dependencies.retrofit)
    }

    val userService by lazy {
        UserService.create(dependencies.retrofit)
    }

    val summaryService by lazy {
        SummaryService.create(dependencies.retrofit)
    }

    val vitalsService by lazy {
        VitalsService.create(dependencies.retrofit)
    }

    val vitalLogger by lazy {
        dependencies.vitalLogger
    }

    val isConfigured: Boolean
        get() = configurationReader.region != null &&
                configurationReader.environment != null &&
                configurationReader.apiKey != null

    val currentUserId: String?
        get() = encryptedSharedPreferences.getString(VITAL_ENCRYPTED_USER_ID_KEY, null)

    fun cleanUp() {
        sharedPreferences.edit().clear().apply()
        encryptedSharedPreferences.edit().clear().apply()
    }

    // TODO: Shift config injection from initializer, and support config change.
    fun configure(region: Region, environment: Environment, apiKey: String) {
        encryptedSharedPreferences.edit()
            .putString(VITAL_ENCRYPTED_API_KEY_KEY, apiKey)
            .putString(VITAL_ENCRYPTED_REGION_KEY, region.name)
            .putString(VITAL_ENCRYPTED_ENVIRONMENT_KEY, environment.name)
            .apply()
    }

    @SuppressLint("ApplySharedPref")
    fun setUserId(userId: String) {
        encryptedSharedPreferences.edit().apply {
            putString(VITAL_ENCRYPTED_USER_ID_KEY, userId)
            apply()
        }
    }

    fun checkUserId(): String {
        return currentUserId ?: throw IllegalStateException(
            "You need to call setUserId before you can read the health data"
        )
    }

    fun hasUserId(): Boolean {
        return encryptedSharedPreferences.getString(VITAL_ENCRYPTED_USER_ID_KEY, null) != null
    }

    companion object {
        private var sharedInstance: VitalClient? = null

        fun getOrCreate(context: Context): VitalClient = synchronized(VitalClient) {
            var instance = sharedInstance
            if (instance == null) {
                instance = VitalClient(context)
                sharedInstance = instance
            }
            instance = VitalClient(context)
            return instance
        }
    }
}

fun createEncryptedSharedPreferences(context: Context) = EncryptedSharedPreferences.create(
    VITAL_ENCRYPTED_PERFS_FILE_NAME,
    MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
    context,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
