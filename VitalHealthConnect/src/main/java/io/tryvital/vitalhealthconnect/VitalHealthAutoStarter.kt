package io.tryvital.vitalhealthconnect

import android.content.Context
import android.content.SharedPreferences
import androidx.startup.Initializer
import io.tryvital.client.Configuration
import io.tryvital.client.Environment
import io.tryvital.client.Region
import io.tryvital.client.VitalClient
import io.tryvital.client.utils.VitalLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Suppress("unused")
class VitalHealthInitializer : Initializer<VitalHealthAutoStarter> {
    override fun create(context: Context): VitalHealthAutoStarter {
        return VitalHealthAutoStarter(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}

class VitalHealthAutoStarter(private val context: Context) {
    private val vitalLogger = VitalLogger.getOrCreate()

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        prefsFileName, Context.MODE_PRIVATE
    )

    private val encryptedSharedPreferences: SharedPreferences by lazy {
        try {
            createEncryptedSharedPreferences(context)
        } catch (e: Exception) {
            vitalLogger.logE(
                "Failed to decrypt shared preferences, creating new encrypted shared preferences", e
            )
            context.deleteSharedPreferences(encryptedPrefsFileName)
            return@lazy createEncryptedSharedPreferences(context)
        }
    }

    init {
        vitalLogger.enabled = sharedPreferences.getBoolean(UnSecurePrefKeys.loggerEnabledKey, false)
        startSync()
    }

    private fun startSync() {
        try {
            if (sharedPreferences.contains(UnSecurePrefKeys.syncOnAppStartKey)) {
                val region = encryptedSharedPreferences.getString(SecurePrefKeys.regionKey, null)
                val environment =
                    encryptedSharedPreferences.getString(SecurePrefKeys.environmentKey, null)
                val apiKey = encryptedSharedPreferences.getString(SecurePrefKeys.apiKeyKey, null)
                val userIdKey = encryptedSharedPreferences.getString(SecurePrefKeys.userIdKey, null)

                if (region == null) {
                    vitalLogger.logI("No region is saved, will not start sync on startup")
                    return
                }
                if (environment == null) {
                    vitalLogger.logI("No environment is saved, will not start sync on startup")
                    return
                }
                if (apiKey == null) {
                    vitalLogger.logI("No api key is saved, will not start sync on startup")
                    return
                }

                if (userIdKey == null) {
                    vitalLogger.logI("No user id is saved, will not start sync on startup")
                    return
                }

                CoroutineScope(Job() + Dispatchers.IO).launch {
                    val client = VitalClient.getOrCreate(context)
                    client.configure(Configuration(
                        Region.valueOf(region),
                        Environment.valueOf(environment),
                        apiKey
                    ))

                    VitalHealthConnectManager.create(context).syncData()
                }
            } else {
                vitalLogger.logI("Sync on app start is not enabled, will not start sync on startup")
            }
        } catch (e: Exception) {
            vitalLogger.logE("Failed to start sync on startup", e)
        }
    }
}