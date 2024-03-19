package io.tryvital.vitalhealthconnect.workers

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalhealthconnect.VitalHealthConnectManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SyncOnExactAlarmService: Service() {
    private val mainScope = MainScope()

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        VitalLogger.getOrCreate().info { "BgSync: started SyncOnExactAlarmService" }

        val manager = VitalHealthConnectManager.getOrCreate(applicationContext)
        val resources = manager.resourcesWithReadPermission()

        // This logic should be identical to that in ResourceSyncStarter
        // when input.startForeground is set to true.
        val syncNotificationBuilder = manager.syncNotificationBuilder
        val notification = syncNotificationBuilder.build(applicationContext, resources)

        ServiceCompat.startForeground(
            this,
            VITAL_SYNC_NOTIFICATION_ID,
            notification,
            foregroundServiceType()
        )

        val launched = manager.launchAutoSyncWorker(startForeground = false) {
            VitalLogger.getOrCreate().info { "BgSync: sync triggered by SyncOnExactAlarmService" }
        }

        if (launched) {
            // Use `invokeOnCompletion` so that `done()` is called unconditionally,
            // no matter whether the job is cancelled, completed or failed.
            mainScope.launch {
                manager.observeSyncWorkerCompleted()
                this@SyncOnExactAlarmService.done()
            }
        } else {
            this.done()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }

    private fun done() {
        this.stopForeground(STOP_FOREGROUND_REMOVE)
        this.stopSelf()
        VitalLogger.getOrCreate().info { "BgSync: stopped SyncOnExactAlarmService" }
    }
}
