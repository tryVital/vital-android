package io.tryvital.vitalhealthconnect

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.PermissionController
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalhealthconnect.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

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

        val grantedPermissions = contract.getSynchronousResult(context, permissionsToRequest())?.value

        return if (grantedPermissions != null) {
            processGrantedPermissionsAsync(grantedPermissions).let(::SynchronousResult)
        } else {
            null
        }
    }

    override fun createIntent(context: Context, input: Unit): Intent
        = contract.createIntent(context, permissionsToRequest())

    @Suppress("DeferredIsResult")
    override fun parseResult(resultCode: Int, intent: Intent?): Deferred<PermissionOutcome> {
        val grantedPermissions = contract.parseResult(resultCode, intent)

        if (intent == null) {
            return CompletableDeferred(
                PermissionOutcome.Failure(cause = IllegalStateException("Missing intent in parseResult."))
            )
        }

        return processGrantedPermissionsAsync(grantedPermissions)
    }

    private fun processGrantedPermissionsAsync(permissions: Set<String>): Deferred<PermissionOutcome> {
        val readGrants = readResources
            .filter { permissions.containsAll(manager.permissionsRequiredToSyncResources(setOf(it))) }
            .toSet()

        val writeGrants = writeResources
            .filter { permissions.containsAll(manager.permissionsRequiredToWriteResources(setOf(it))) }
            .toSet()

        manager.sharedPreferences.edit().run {
            readGrants.forEach { putBoolean(UnSecurePrefKeys.readResourceGrant(it), true) }
            writeGrants.forEach { putBoolean(UnSecurePrefKeys.writeResourceGrant(it), true) }
            apply()
        }

        VitalLogger.getOrCreate().logI("[processGrantedPermissions] Saved read grants: ${readGrants.joinToString(", ")}; write grants = ${writeGrants.joinToString(", ")}")

        return taskScope.async {
            // The activity result reports only permissions granted in this UI interaction.
            // Since we have VitalResources that are an aggregate of multiple record types, we need
            // to recompute based on the full set of permissions.
            manager.checkAndUpdatePermissions()
            PermissionOutcome.Success
        }
    }

    private fun permissionsToRequest(): Set<String> {
        return manager.permissionsRequiredToWriteResources(this.writeResources) +
                manager.permissionsRequiredToSyncResources(this.readResources)
    }
}