package io.tryvital.vitalsamsunghealth

import android.content.Context
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import io.tryvital.vitalhealthcore.model.VitalResource
import io.tryvital.vitalhealthcore.model.WritableVitalResource
import io.tryvital.vitalsamsunghealth.model.recordTypeDependencies

object UnSecurePrefKeys {
    internal const val loggerEnabledKey = "loggerEnabled.samsungHealth"
    internal const val syncOnAppStartKey = "syncOnAppStartKey.samsungHealth"
    internal const val numberOfDaysToBackFillKey = "numberOfDaysToBackFill.samsungHealth"
    internal const val pauseSyncKey = "pauseSync.samsungHealth"
    internal const val useExactAlarmKey = "useExactAlarm.samsungHealth"
    internal const val nextAlarmAtKey = "nextAlarmAt.samsungHealth"
    internal const val lastAutoSyncedAtKey = "lastAutoSyncedAt.samsungHealth"
    internal const val lastSeenWorkIdKey = "lastSeenWorkId.samsungHealth"
    internal const val autoSyncThrottleKey = "autoSyncThrottle.samsungHealth"
    internal const val backgroundSyncMinIntervalKey = "backgroundSyncMinInterval.samsungHealth"
    internal const val localSyncStateKey = "localSyncState.samsungHealth"
    internal const val connectionPolicyKey = "connectionPolicyKey.samsungHealth"

    internal const val currentAskRequest = "currentAskRequest.samsungHealth"

    internal fun syncStateKey(resource: VitalResource) = "sync-state.samsungHealth.${resource.name}"
    internal fun monitoringTypesKey(resource: VitalResource) = "monitoringTypes.samsungHealth.${resource.name}"

    internal fun requestCount(permission: String) = "requestCount.samsungHealth.$permission"
    internal fun readResourceGrant(resource: VitalResource) = "resource.read.samsungHealth.$resource"
    internal fun writeResourceGrant(resource: WritableVitalResource) = "resource.write.samsungHealth.$resource"
}

internal suspend fun getGrantedPermissions(context: Context) =
    SamsungHealthClientProvider().getHealthDataStore(context).let { store ->
        val candidates = mutableSetOf<Permission>()

        for (resource in VitalResource.values()) {
            for (recordType in resource.recordTypeDependencies().allRecordTypes) {
                permissionForRecordType(recordType, AccessType.READ)?.let(candidates::add)
            }
        }
        for (resource in WritableVitalResource.values()) {
            candidates += Permission.of(writableResourceToSamsungDataType(resource), AccessType.WRITE)
        }

        store.getGrantedPermissions(candidates)
            .mapTo(mutableSetOf()) { permissionKey(it.dataType, it.accessType) }
    }
