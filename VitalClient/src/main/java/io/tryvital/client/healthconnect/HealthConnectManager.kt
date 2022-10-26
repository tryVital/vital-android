package io.tryvital.client.healthconnect

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import io.tryvital.client.dependencies.HealthConnectClientProvider

const val MIN_SUPPORTED_SDK = Build.VERSION_CODES.P

class HealthConnectManager private constructor(
    private val healthConnectClientProvider: HealthConnectClientProvider
) {

    fun isAvailable(context: Context): HealthConnectAvailability {
        val sdkInt = Build.VERSION.SDK_INT
        Log.e("asd",sdkInt.toString())
        return when {
            sdkInt < MIN_SUPPORTED_SDK -> HealthConnectAvailability.NotSupportedSDK
            HealthConnectClient.isAvailable(context) -> HealthConnectAvailability.Installed
            else -> HealthConnectAvailability.NotInstalled
        }
    }

    @Suppress("unused")
    suspend fun hasAllPermissions(context: Context, permissions: Set<HealthPermission>): Boolean {
        return permissions == healthConnectClientProvider.getHealthConnectClient(context)
            .permissionController.getGrantedPermissions(permissions)
    }

    companion object {
        fun create(healthConnectClientProvider: HealthConnectClientProvider): HealthConnectManager =
            HealthConnectManager(healthConnectClientProvider)
    }
}

