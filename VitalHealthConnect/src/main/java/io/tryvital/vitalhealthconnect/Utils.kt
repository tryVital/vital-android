package io.tryvital.vitalhealthconnect

import android.content.Context
import io.tryvital.vitalhealthconnect.model.VitalResource
import io.tryvital.vitalhealthconnect.model.WritableVitalResource

object UnSecurePrefKeys {
    internal const val loggerEnabledKey = "loggerEnabled"
    internal const val syncOnAppStartKey = "syncOnAppStartKey"
    internal const val numberOfDaysToBackFillKey = "numberOfDaysToBackFill"
    internal const val pauseSyncKey = "pauseSync"
    internal const val useExactAlarmKey = "useExactAlarm"
    internal const val nextAlarmAtKey = "nextAlarmAt"
    internal const val lastAutoSyncedAtKey = "lastAutoSyncedAt"
    internal const val lastSeenWorkIdKey = "lastSeenWorkId"
    internal const val typesMonitoredByChangesTokenKey = "typesMonitoredByChangesToken"
    internal fun readResourceGrant(resource: VitalResource) = "resource.read.$resource"
    internal fun writeResourceGrant(resource: WritableVitalResource) = "resource.write.$resource"
}

internal suspend fun getGrantedPermissions(context: Context) =
    HealthConnectClientProvider().getHealthConnectClient(context)
        .permissionController.getGrantedPermissions()
