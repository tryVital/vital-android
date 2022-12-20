package io.tryvital.vitalhealthconnect

import android.content.Context
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

internal const val prefsFileName: String = "vital_health_connect_prefs"
internal const val encryptedPrefsFileName: String = "safe_vital_health_connect_prefs"

object SecurePrefKeys{
    internal const val regionKey = "region"
    internal const val environmentKey = "environment"
    internal const val apiKeyKey = "apiKey"
}

object UnSecurePrefKeys{
    internal const val loggerEnabledKey = "loggerEnabled"
    internal const val changeTokenKey = "changeToken"
    internal const val syncOnAppStartKey = "syncOnAppStartKey"
    internal const val numberOfDaysToBackFillKey = "numberOfDaysToBackFill"
}

internal suspend fun saveNewChangeToken(context: Context) {
    val sharedPreferences = context.getSharedPreferences(
        prefsFileName,
        Context.MODE_PRIVATE
    )

    val grantedPermissions = getGrantedPermissions(context)

    if (grantedPermissions.isEmpty()) {
        sharedPreferences.edit().remove(UnSecurePrefKeys.changeTokenKey).apply()
    } else {
        sharedPreferences.edit().putString(
            UnSecurePrefKeys.changeTokenKey,
            HealthConnectClientProvider().getHealthConnectClient(context)
                .getChangesToken(ChangesTokenRequest(vitalRecordTypes(grantedPermissions)))
        ).apply()
    }
}

internal fun createEncryptedSharedPreferences(context: Context) = EncryptedSharedPreferences.create(
    encryptedPrefsFileName,
    MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
    context,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)

internal suspend fun getGrantedPermissions(context: Context) =
    HealthConnectClientProvider().getHealthConnectClient(context)
        .permissionController.getGrantedPermissions(VitalHealthConnectManager.vitalRequiredPermissions)
