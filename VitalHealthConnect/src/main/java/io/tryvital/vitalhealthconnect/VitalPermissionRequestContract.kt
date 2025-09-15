package io.tryvital.vitalhealthconnect

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import io.tryvital.client.VitalClient
import io.tryvital.client.createConnectedSourceIfNotExist
import io.tryvital.client.services.VitalPrivateApi
import io.tryvital.client.services.data.ManualProviderSlug
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalhealthconnect.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class VitalPermissionRequestContract(
    private val readResources: Set<VitalResource>,
    private val writeResources: Set<WritableVitalResource>,
    private val manager: VitalHealthConnectManager,
    private val taskScope: CoroutineScope,
): ActivityResultContract<Unit, Deferred<PermissionOutcome>>() {
    private val contract = PermissionController.createRequestPermissionResultContract()

    override fun getSynchronousResult(
        context: Context,
        input: Unit
    ): SynchronousResult<Deferred<PermissionOutcome>>? {
        if (VitalHealthConnectManager.isAvailable(context) != HealthConnectAvailability.Installed) {
            return SynchronousResult(CompletableDeferred(PermissionOutcome.HealthConnectUnavailable))
        }

        val permissions = permissionsToRequest()
        val grantedPermissions = contract.getSynchronousResult(context, permissions)?.value

        return if (grantedPermissions != null) {
            processGrantedPermissionsAsync(requested = permissions, granted = grantedPermissions)
                .let(::SynchronousResult)
        } else {
            null
        }
    }

    override fun createIntent(context: Context, input: Unit): Intent {
        val permissions = permissionsToRequest()

        // Health Connect keeps a counter per individual permission
        // 14+: count both Cancel and Allow-but-Unselected
        // 13: count only when user presses Cancel
        val prefs = manager.sharedPreferences
        prefs.edit().apply {
            for (permission in permissions) {
                val key = UnSecurePrefKeys.requestCount(permission)
                putLong(key, prefs.getLong(key, 0) + 1)
            }
            putStringSet(UnSecurePrefKeys.currentAskRequest, permissions)
            apply()
        }

        return contract.createIntent(context, permissions)
    }

    @Suppress("DeferredIsResult")
    override fun parseResult(resultCode: Int, intent: Intent?): Deferred<PermissionOutcome> {
        val grantedPermissions = contract.parseResult(resultCode, intent)

        if (intent == null) {
            val outcome = when (resultCode) {
                0 -> PermissionOutcome.Cancelled
                else -> PermissionOutcome.UnknownError(IllegalStateException("Missing intent in parseResult."))
            }
            return CompletableDeferred(outcome)
        }

        val currentAskRequest = manager.sharedPreferences.getStringSet(
            UnSecurePrefKeys.currentAskRequest,
            null
        ) ?: emptySet()

        return processGrantedPermissionsAsync(requested = currentAskRequest, granted = grantedPermissions)
    }

    @OptIn(VitalPrivateApi::class)
    private fun processGrantedPermissionsAsync(requested: Set<String>, granted: Set<String>): Deferred<PermissionOutcome> {
        val readGrants = readResources
            .filter { resource ->
                resource.recordTypeDependencies().isResourceActive {
                    HealthPermission.getReadPermission(it) in granted
                }
            }
            .toSet()

        val writeGrants = writeResources
            .filter { granted.containsAll(manager.permissionsRequiredToWriteResources(setOf(it))) }
            .toSet()

        manager.sharedPreferences.edit().run {
            readGrants.forEach { putBoolean(UnSecurePrefKeys.readResourceGrant(it), true) }
            writeGrants.forEach { putBoolean(UnSecurePrefKeys.writeResourceGrant(it), true) }
            apply()
        }

        VitalLogger.getOrCreate().logI("[processGrantedPermissions] Saved read grants: ${readGrants.joinToString(", ")}; write grants = ${writeGrants.joinToString(", ")}")

        return taskScope.async {

            if (manager.localSyncStateManager.connectionPolicy == ConnectionPolicy.AutoConnect) {
                // User has gone through the Ask flow successfully.
                // Irrespective of the permission state, we proactively create the CS here (if not already
                // exists).
                try {
                    manager.vitalClient
                        .createConnectedSourceIfNotExist(ManualProviderSlug.HealthConnect)

                } catch (e: Throwable) {
                    VitalLogger.getOrCreate().logE("[Ask] proactive CS creation failed", e)
                }
            }

            // The activity result reports only permissions granted in this UI interaction.
            // Since we have VitalResources that are an aggregate of multiple record types, we need
            // to recompute based on the full set of permissions.
            val (allGrants, discoveredNewGrants) = manager.checkAndUpdatePermissions()

            val allNewGrants = readGrants + discoveredNewGrants

            if (allNewGrants.isNotEmpty()) {
                manager.syncProgressStore.recordAsk(allNewGrants.map { it.remapped() }.toSet())

                // Asynchronously start syncing the newly granted read resources
                taskScope.launch {
                    manager.syncData(allNewGrants)
                }
            }

            return@async if (allGrants.isEmpty() || allNewGrants.isEmpty()) {
                // https://issuetracker.google.com/issues/233239418#comment2
                val notPromptThreshold = requested.all { permission ->
                    // We increment upfront, so if this is the 3rd attempt, the value
                    // would be 3 (post increment).
                    manager.sharedPreferences.getLong(
                        UnSecurePrefKeys.requestCount(permission),
                        0
                    ) >= 3
                }

                if (notPromptThreshold) {
                    PermissionOutcome.NotPrompted
                } else {
                    PermissionOutcome.Success
                }
            } else {
                PermissionOutcome.Success
            }
        }
    }

    private fun permissionsToRequest(): Set<String> {
        val appContext = manager.context.applicationContext
        val hostAppPackageInfo = appContext.packageManager.getPackageInfo(
            appContext.packageName,
            PackageManager.GET_PERMISSIONS,
        )

        val permissionsDeclaredByHostApp = (hostAppPackageInfo.requestedPermissions ?: emptyArray()).toSet()

        // Only request permissions that the host app has declared in their AndroidManifest.xml
        return permissionsDeclaredByHostApp.intersect(
            manager.permissionsRequiredToWriteResources(this.writeResources) +
                manager.readPermissionsToRequestForResources(this.readResources)
        )
    }
}