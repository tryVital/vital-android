package io.tryvital.client

import android.content.SharedPreferences
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory

const val VITAL_ENCRYPTED_USER_ID_KEY: String = "userId"
const val VITAL_ENCRYPTED_REGION_KEY = "region"
const val VITAL_ENCRYPTED_ENVIRONMENT_KEY = "environment"
const val VITAL_ENCRYPTED_API_KEY_KEY = "apiKey"
const val VITAL_ENCRYPTED_AUTH_STRATEGY_KEY = "auth_strategy"

internal interface ConfigurationReader {
    val userId: String?
    val authStrategy: VitalClientAuthStrategy?
}

@JsonClass(generateAdapter = false)
internal sealed class VitalClientAuthStrategy {
    abstract val environment: Environment
    abstract val region: Region

    @JsonClass(generateAdapter = true)
    data class JWT(override val environment: Environment, override val region: Region): VitalClientAuthStrategy()
    @JsonClass(generateAdapter = true)
    data class APIKey(val apiKey: String, override val environment: Environment, override val region: Region): VitalClientAuthStrategy()
}

@JvmInline
internal value class SharedPreferencesConfigurationReader(
    private val sharedPreferences: SharedPreferences
): ConfigurationReader {
    override val userId: String? get() = sharedPreferences.getString(VITAL_ENCRYPTED_USER_ID_KEY, null)
    override val authStrategy: VitalClientAuthStrategy?
        get() {
            val rawAuthStrategy =
                sharedPreferences.getString(VITAL_ENCRYPTED_AUTH_STRATEGY_KEY, null)
            if (rawAuthStrategy != null) {
                return configurationMoshi.adapter(VitalClientAuthStrategy::class.java)
                    .fromJson(rawAuthStrategy)!!
            }

            // Backward compatibility
            val region = sharedPreferences.getString(VITAL_ENCRYPTED_REGION_KEY, null)
                ?.let { Region.valueOf(it) }
            val environment = sharedPreferences.getString(VITAL_ENCRYPTED_ENVIRONMENT_KEY, null)
                ?.let { Environment.valueOf(it) }
            val apiKey: String? = sharedPreferences.getString(VITAL_ENCRYPTED_API_KEY_KEY, null)

            if (region != null && environment != null && apiKey != null) {
                return VitalClientAuthStrategy.APIKey(apiKey, environment, region)
            }

            return null
        }
}

internal data class StaticConfiguration(
    override val authStrategy: VitalClientAuthStrategy?,
    override val userId: String? = null,
): ConfigurationReader

class VitalClientUnconfigured: Throwable("VitalClient has not been configured.")


internal val configurationMoshi by lazy {
    Moshi.Builder()
        .add(
            PolymorphicJsonAdapterFactory
                .of(VitalClientAuthStrategy::class.java,"type")
                .withSubtype(VitalClientAuthStrategy.JWT::class.java, "jwt")
                .withSubtype(VitalClientAuthStrategy.APIKey::class.java, "apiKey")
        )
        .build()
}
