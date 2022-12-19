package io.tryvital.vitalhealthconnect

import android.content.Context
import androidx.health.connect.client.request.ChangesTokenRequest

internal suspend fun saveNewChangeToken(context: Context) {
    val sharedPreferences = context.getSharedPreferences(
        prefsFileName,
        Context.MODE_PRIVATE
    )

    val grantedPermissions = getGrantedPermissions(context)

    if (grantedPermissions.isEmpty()) {
        sharedPreferences.edit().remove(changeTokenKey).apply()
    } else {
        sharedPreferences.edit().putString(
            changeTokenKey,
            HealthConnectClientProvider().getHealthConnectClient(context)
                .getChangesToken(ChangesTokenRequest(vitalRecordTypes(grantedPermissions)))
        ).apply()
    }
}

internal suspend fun getGrantedPermissions(context: Context) =
    HealthConnectClientProvider().getHealthConnectClient(context)
        .permissionController.getGrantedPermissions(VitalHealthConnectManager.vitalRequiredPermissions)
