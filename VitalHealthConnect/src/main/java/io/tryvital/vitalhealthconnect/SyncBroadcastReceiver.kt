package io.tryvital.vitalhealthconnect

import android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_BOOT_COMPLETED
import android.os.Looper
import io.tryvital.client.utils.VitalLogger

const val ACTION_SYNC_DATA = "io.tryvital.vitalhealthconnect.action.SYNC_DATA"
private val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"

@ExperimentalVitalApi
class SyncBroadcastReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED, ACTION_BOOT_COMPLETED, ACTION_QUICKBOOT_POWERON -> {
                exactAlarmPermissionStateChanged(context, intent)
            }
            ACTION_SYNC_DATA -> {
                exactAlarmFired(context, intent)
            }
            else -> {}
        }
    }

    private fun exactAlarmPermissionStateChanged(context: Context, intent: Intent) {
        VitalLogger.getOrCreate().info { "BgSync: exactAlarmPermissionStateChanged because ${intent.action}" }

        val manager = VitalHealthConnectManager.getOrCreate(context)
        if (manager.isBackgroundSyncEnabled) {
            manager.scheduleNextExactAlarm(force = true)
        }
    }

    private fun exactAlarmFired(context: Context, intent: Intent) {
        VitalLogger.getOrCreate().info { "BgSync: exactAlarmFired" }

        val manager = VitalHealthConnectManager.getOrCreate(context)
        manager.scheduleNextExactAlarm(force = true)

        if (!context.isConnectedToInternet) {
            return VitalLogger.getOrCreate().info { "BgSync: skipped launch - no internet" }
        }

        if (manager.pauseSynchronization) {
            return VitalLogger.getOrCreate().info { "BgSync: skipped launch - sync paused" }
        }

        manager.launchSyncWorkerFromBackground {
            check(Looper.getMainLooper().isCurrentThread)
            context.startForegroundService(intent)
        }
    }
}
