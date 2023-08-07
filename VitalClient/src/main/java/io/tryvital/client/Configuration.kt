package io.tryvital.client

import android.content.SharedPreferences

const val VITAL_ENCRYPTED_USER_ID_KEY: String = "userId"
const val VITAL_ENCRYPTED_REGION_KEY = "region"
const val VITAL_ENCRYPTED_ENVIRONMENT_KEY = "environment"
const val VITAL_ENCRYPTED_API_KEY_KEY = "apiKey"

internal interface ConfigurationReader {
    val userId: String?
    val region: Region?
    val environment: Environment?
    val apiKey: String?
}

@JvmInline
internal value class SharedPreferencesConfigurationReader(
    private val sharedPreferences: SharedPreferences
): ConfigurationReader {
    override val userId: String? get() = sharedPreferences.getString(VITAL_ENCRYPTED_USER_ID_KEY, null)
    override val region: Region?
        get() = sharedPreferences.getString(VITAL_ENCRYPTED_REGION_KEY, null)
            ?.let { Region.valueOf(it) }
    override val environment: Environment?
        get() = sharedPreferences.getString(
            VITAL_ENCRYPTED_ENVIRONMENT_KEY,
            null
        )?.let { Environment.valueOf(it) }
    override val apiKey: String? get() = sharedPreferences.getString(VITAL_ENCRYPTED_API_KEY_KEY, null)
}

internal data class StaticConfiguration(
    override val region: Region? = null,
    override val environment: Environment? = null,
    override val apiKey: String? = null,
    override val userId: String? = null,
): ConfigurationReader

class VitalClientUnconfigured: Throwable("VitalClient has not been configured.")
