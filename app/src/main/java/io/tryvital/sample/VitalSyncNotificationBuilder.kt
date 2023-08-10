package io.tryvital.sample

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import androidx.core.app.NotificationCompat
import io.tryvital.vitalhealthconnect.SyncNotificationBuilder
import io.tryvital.vitalhealthconnect.model.VitalResource

object VitalSyncNotificationBuilder: SyncNotificationBuilder {
    override fun build(context: Context, resources: Set<VitalResource>): Notification {
        return NotificationCompat.Builder(context, createChannel(context))
            .setContentTitle("Vital Sync")
            .setTicker("Vital Sync")
            .setContentText("Syncing ${resources.map { it.toString() }.joinToString(", ")}")
            .setOngoing(true)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .build()
    }

    fun createChannel(context: Context): String {
        val importance = NotificationManager.IMPORTANCE_MIN
        val mChannel = NotificationChannel("VitalSyncNotificationBuilder", "Vital Sync", importance)
        mChannel.description = "Notifies when Vital is syncing your data"
        val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(mChannel)
        return mChannel.id
    }
}
