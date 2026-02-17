package io.tryvital.vitalsamsunghealth

import android.content.Context
import io.tryvital.vitalsamsunghealth.model.VitalResource
import io.tryvital.vitalsamsunghealth.model.WritableVitalResource

object UnSecurePrefKeys {
    internal const val loggerEnabledKey = "loggerEnabled"
    internal const val syncOnAppStartKey = "syncOnAppStartKey"
    internal const val numberOfDaysToBackFillKey = "numberOfDaysToBackFill"
    internal const val pauseSyncKey = "pauseSync"
    internal const val useExactAlarmKey = "useExactAlarm"
    internal const val nextAlarmAtKey = "nextAlarmAt"
    internal const val lastAutoSyncedAtKey = "lastAutoSyncedAt"
    internal const val lastSeenWorkIdKey = "lastSeenWorkId"
    internal const val autoSyncThrottleKey = "autoSyncThrottle"
    internal const val backgroundSyncMinIntervalKey = "backgroundSyncMinInterval"
    internal const val localSyncStateKey = "localSyncState"
    internal const val connectionPolicyKey = "connectionPolicyKey"

    internal const val currentAskRequest = "currentAskRequest"

    internal fun syncStateKey(resource: VitalResource) = "sync-state.${resource.name}"
    internal fun monitoringTypesKey(resource: VitalResource) = "monitoringTypes.${resource.name}"

    internal fun requestCount(permission: String) = "requestCount.$permission"
    internal fun readResourceGrant(resource: VitalResource) = "resource.read.$resource"
    internal fun writeResourceGrant(resource: WritableVitalResource) = "resource.write.$resource"
}

internal suspend fun getGrantedPermissions(context: Context) =
    SamsungHealthClientProvider().getSamsungHealthClient(context)
        .permissionController.getGrantedPermissions()
