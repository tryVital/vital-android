package io.tryvital.vitalhealthconnect

import android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_BOOT_COMPLETED
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalhealthconnect.model.HealthConnectAvailability
import io.tryvital.vitalhealthconnect.workers.SyncOnExactAlarmService

const val ACTION_SYNC_DATA = "io.tryvital.vitalhealthconnect.action.SYNC_DATA"
private val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"

class SyncBroadcastReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val action = intent.action) {
            ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED, ACTION_BOOT_COMPLETED, ACTION_QUICKBOOT_POWERON -> {
                exactAlarmPermissionStateChanged(context, action)
            }
            ACTION_SYNC_DATA -> {
                exactAlarmFired(context)
            }
            else -> {}
        }
    }

    private fun exactAlarmPermissionStateChanged(context: Context, action: String) {
        VitalLogger.getOrCreate().info { "BgSync: exactAlarmPermissionStateChanged because $action" }

        val manager = VitalHealthConnectManager.getOrCreate(context)

        // Just in case we are on an earlier Android OS version, in which one can uninstall
        // Health Connect.
        if (VitalHealthConnectManager.isAvailable(manager.context) != HealthConnectAvailability.Installed) {
            return VitalLogger.getOrCreate().info { "BgSync: HealthConnect gone" }
        }

        if (!manager.isBackgroundSyncEnabled) {
            VitalLogger.getOrCreate().info { "BgSync: scheduling skipped; backgroundSync is disabled" }
            return
        }

        manager.scheduleNextExactAlarm(force = true)
    }

    private fun exactAlarmFired(context: Context) {
        VitalLogger.getOrCreate().info { "BgSync: exactAlarmFired" }

        val manager = VitalHealthConnectManager.getOrCreate(context)

        // Just in case we are on an earlier Android OS version, in which one can uninstall
        // Health Connect.
        if (VitalHealthConnectManager.isAvailable(manager.context) != HealthConnectAvailability.Installed) {
            return VitalLogger.getOrCreate().info { "BgSync: HealthConnect gone" }
        }

        if (!manager.isBackgroundSyncEnabled) {
            return VitalLogger.getOrCreate().info { "BgSync: skipped launch - backgroundSync is disabled" }
        }

        manager.scheduleNextExactAlarm(force = true)

        // By platform contract, we MUST call Context.startForegroundService.
        // But we cannot enqueue WorkManager foreground work through Context.startForegroundService.
        //
        // [SyncOnExactAlarmService] bridges this abstraction gap. It starts as a FGS, then
        // asks WorkManager to start [ResourceSyncStarter] without an FGS. It then finally waits for
        // the enqueued work to complete, before stopping itself.
        context.startForegroundService(
            Intent(context, SyncOnExactAlarmService::class.java)
        )
    }
}
