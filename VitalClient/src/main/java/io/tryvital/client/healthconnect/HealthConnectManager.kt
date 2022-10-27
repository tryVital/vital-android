package io.tryvital.client.healthconnect

import android.content.Context
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import io.tryvital.client.dependencies.HealthConnectClientProvider

const val MIN_SUPPORTED_SDK = Build.VERSION_CODES.P


//TODO this leaks the health dependency to the client because of the permissions,
// rework it so the sdk can request the permissions internally
class HealthConnectManager private constructor(
    private val healthConnectClientProvider: HealthConnectClientProvider
) {

    val permissions =
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
        return permissions == healthConnectClientProvider.getHealthConnectClient(context)
            .permissionController.getGrantedPermissions(permissions)
    }

    fun createRequestPermissionResultContract(): ActivityResultContract<Set<HealthPermission>, Set<HealthPermission>> {
        return PermissionController.createRequestPermissionResultContract()
    }

    companion object {
        fun create(healthConnectClientProvider: HealthConnectClientProvider): HealthConnectManager =
            HealthConnectManager(healthConnectClientProvider)
    }
}

