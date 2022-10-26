package io.tryvital.client.healthconnect

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission

const val MIN_SUPPORTED_SDK = Build.VERSION_CODES.O_MR1

class HealthConnectManager private constructor(
    private val healthConnectClient: HealthConnectClient
) {

    fun isAvailable(context: Context): HealthConnectAvailability {
        return when {
            HealthConnectClient.isAvailable(context) -> HealthConnectAvailability.Installed
            Build.VERSION.SDK_INT >= MIN_SUPPORTED_SDK -> HealthConnectAvailability.NotInstalled
            else -> HealthConnectAvailability.NotSupported
        }
    }

    suspend fun hasAllPermissions(permissions: Set<HealthPermission>): Boolean {
        return permissions == healthConnectClient.permissionController
            .getGrantedPermissions(permissions)
    }

    companion object {
        fun create(healthConnectClient: HealthConnectClient): HealthConnectManager =
            HealthConnectManager(healthConnectClient)
    }
}

