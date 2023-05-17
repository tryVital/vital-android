package io.tryvital.vitalhealthconnect

import android.content.Context
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import io.tryvital.client.VITAL_PERFS_FILE_NAME
import io.tryvital.vitalhealthconnect.model.VitalResource
import io.tryvital.vitalhealthconnect.model.WritableVitalResource

object UnSecurePrefKeys {
    internal const val loggerEnabledKey = "loggerEnabled"
    internal const val changeTokenKey = "changeToken"
    internal const val syncOnAppStartKey = "syncOnAppStartKey"
    internal const val numberOfDaysToBackFillKey = "numberOfDaysToBackFill"
    internal fun readResourceGrant(resource: VitalResource) = "resource.read.$resource"
    internal fun writeResourceGrant(resource: WritableVitalResource) = "resource.write.$resource"
}

internal suspend fun saveNewChangeToken(context: Context) {
    val sharedPreferences = context.getSharedPreferences(
        VITAL_PERFS_FILE_NAME,
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

internal suspend fun getGrantedPermissions(context: Context) =
    HealthConnectClientProvider().getHealthConnectClient(context)
        .permissionController.getGrantedPermissions()
