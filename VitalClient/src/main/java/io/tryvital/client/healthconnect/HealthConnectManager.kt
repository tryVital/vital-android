package io.tryvital.client.healthconnect

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import io.tryvital.client.dependencies.HealthConnectClientProvider

const val MIN_SUPPORTED_SDK = Build.VERSION_CODES.P

class HealthConnectManager private constructor(
    private val healthConnectClientProvider: HealthConnectClientProvider
) {

    val requiredPermissions =
        setOf(
            HealthPermission.createReadPermission(StepsRecord::class),
        )

    fun isAvailable(context: Context): HealthConnectAvailability {
        return when {
            Build.VERSION.SDK_INT < MIN_SUPPORTED_SDK -> HealthConnectAvailability.NotSupportedSDK
            HealthConnectClient.isAvailable(context) -> HealthConnectAvailability.Installed
            else -> HealthConnectAvailability.NotInstalled
        }
    }

    suspend fun hasAllPermissions(context: Context): Boolean {
        return requiredPermissions == healthConnectClientProvider.getHealthConnectClient(context)
            .permissionController.getGrantedPermissions(requiredPermissions)
    }

    companion object {
        fun create(healthConnectClientProvider: HealthConnectClientProvider): HealthConnectManager =
            HealthConnectManager(healthConnectClientProvider)
    }
}

