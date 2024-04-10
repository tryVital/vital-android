package io.tryvital.vitalhealthconnect

import android.content.Context
import io.tryvital.vitalhealthconnect.model.VitalResource
import io.tryvital.vitalhealthconnect.model.WritableVitalResource
import io.tryvital.vitalhealthconnect.workers.syncStateKey

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

    internal const val backendSyncStateLastQueriedKey = "backendSyncStateLastQueried"
    internal const val backendSyncStateKey = "backendSyncState"

    internal fun syncStateKey(resource: VitalResource) = "sync-state.${resource.name}"
    internal fun monitoringTypesKey(resource: VitalResource) = "monitoringTypes.${resource.name}"

    internal fun readResourceGrant(resource: VitalResource) = "resource.read.$resource"
    internal fun writeResourceGrant(resource: WritableVitalResource) = "resource.write.$resource"
}

internal suspend fun getGrantedPermissions(context: Context) =
    HealthConnectClientProvider().getHealthConnectClient(context)
        .permissionController.getGrantedPermissions()
