package io.tryvital.vitalsamsunghealth

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import io.tryvital.client.createConnectedSourceIfNotExist
import io.tryvital.client.services.VitalPrivateApi
import io.tryvital.client.services.data.ManualProviderSlug
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalsamsunghealth.model.ConnectionPolicy
import io.tryvital.vitalsamsunghealth.model.HealthConnectAvailability
import io.tryvital.vitalsamsunghealth.model.PermissionOutcome
import io.tryvital.vitalsamsunghealth.model.RemappedVitalResource
import io.tryvital.vitalsamsunghealth.model.VitalResource
import io.tryvital.vitalsamsunghealth.model.WritableVitalResource
import io.tryvital.vitalsamsunghealth.model.recordTypeDependencies
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class VitalPermissionRequestContract(
    private val readResources: Set<VitalResource>,
    private val writeResources: Set<WritableVitalResource>,
    private val manager: VitalSamsungHealthManager,
    private val taskScope: CoroutineScope,
): ActivityResultContract<Unit, Deferred<PermissionOutcome>>() {

    override fun getSynchronousResult(
        context: Context,
        input: Unit
    ): SynchronousResult<Deferred<PermissionOutcome>> {
        if (VitalSamsungHealthManager.isAvailable(context) != HealthConnectAvailability.Installed) {
            return SynchronousResult(CompletableDeferred(PermissionOutcome.HealthConnectUnavailable))
        }

        val activity = context as? Activity
            ?: return SynchronousResult(
                CompletableDeferred(PermissionOutcome.UnknownError(IllegalStateException("Permission flow requires an Activity context.")))
            )

        val permissions = permissionsToRequest()

        // Keep per-permission ask counters for parity with previous behavior.
        val prefs = manager.sharedPreferences
        prefs.edit().apply {
            for (permission in permissions) {
                val key = UnSecurePrefKeys.requestCount(permissionKey(permission.dataType, permission.accessType))
                putLong(key, prefs.getLong(key, 0) + 1)
            }
            apply()
        }

        return SynchronousResult(
            processGrantedPermissionsAsync(
                requested = permissions,
                activity = activity,
            )
        )
    }

    override fun createIntent(context: Context, input: Unit): Intent = Intent()

    @Suppress("DeferredIsResult")
    override fun parseResult(resultCode: Int, intent: Intent?): Deferred<PermissionOutcome> {
        // This contract is handled synchronously via getSynchronousResult().
        return CompletableDeferred(PermissionOutcome.Cancelled)
    }

    @OptIn(VitalPrivateApi::class)
    private fun processGrantedPermissionsAsync(
        requested: Set<Permission>,
        activity: Activity,
    ): Deferred<PermissionOutcome> {
        return taskScope.async {
            val healthDataStore = manager.samsungHealthClientProvider.getHealthDataStore(activity)
            val granted = healthDataStore.requestPermissions(requested, activity)
            val grantedKeys = granted.mapTo(mutableSetOf()) { permissionKey(it.dataType, it.accessType) }

            val readGrants = readResources
                .filter { resource ->
                    resource.recordTypeDependencies().isResourceActive { recordType ->
                        permissionForRecordType(recordType, AccessType.READ)?.let {
                            permissionKey(it.dataType, it.accessType) in grantedKeys
                        } ?: false
                    }
                }
                .toSet()

            val writeGrants = writeResources
                .filter { grantedKeys.containsAll(manager.permissionsRequiredToWriteResources(setOf(it))) }
                .toSet()

            manager.sharedPreferences.edit().run {
                readGrants.forEach { putBoolean(UnSecurePrefKeys.readResourceGrant(it), true) }
                writeGrants.forEach { putBoolean(UnSecurePrefKeys.writeResourceGrant(it), true) }
                apply()
            }

            VitalLogger.getOrCreate().logI("[processGrantedPermissions] Saved read grants: ${readGrants.joinToString(", ")}; write grants = ${writeGrants.joinToString(", ")}")

            if (manager.localSyncStateManager.connectionPolicy == ConnectionPolicy.AutoConnect) {
                try {
                    manager.vitalClient
                        .createConnectedSourceIfNotExist(ManualProviderSlug.HealthConnect)
                } catch (e: Throwable) {
                    VitalLogger.getOrCreate().logE("[Ask] proactive CS creation failed", e)
                }
            }

            val (allGrants, discoveredNewGrants) = manager.checkAndUpdatePermissions()
            val allNewGrants = readGrants + discoveredNewGrants

            if (allNewGrants.isNotEmpty()) {
                manager.syncProgressStore.recordAsk(allNewGrants.map { RemappedVitalResource(it) }.toSet())
                taskScope.launch {
                    manager.syncData(allNewGrants)
                }
            }

            if (allGrants.isEmpty() || allNewGrants.isEmpty()) {
                val notPromptThreshold = requested.all { permission ->
                    val key = UnSecurePrefKeys.requestCount(permissionKey(permission.dataType, permission.accessType))
                    manager.sharedPreferences.getLong(key, 0) >= 3
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

    private fun permissionsToRequest(): Set<Permission> {
        val permissions = mutableSetOf<Permission>()

        for (resource in readResources) {
            for (recordType in resource.recordTypeDependencies().allRecordTypes) {
                permissionForRecordType(recordType, AccessType.READ)?.let(permissions::add)
            }
        }

        for (resource in writeResources) {
            permissions += Permission.of(writableResourceToSamsungDataType(resource), AccessType.WRITE)
        }

        return permissions
    }
}
