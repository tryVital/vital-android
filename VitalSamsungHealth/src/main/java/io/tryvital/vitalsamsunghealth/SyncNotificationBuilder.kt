package io.tryvital.vitalsamsunghealth

import androidx.core.app.NotificationCompat
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import com.squareup.moshi.JsonClass
import io.tryvital.client.VITAL_PERFS_FILE_NAME
import io.tryvital.vitalhealthcore.model.VitalResource
import io.tryvital.vitalsamsunghealth.workers.getJson
import io.tryvital.vitalsamsunghealth.workers.putJson

private const val SYNC_NOTIFICATION_CONTENT_KEY = "sync_notif_content"

interface SyncNotificationBuilder {
    fun build(context: Context, resources: Set<VitalResource>): Notification
}

@JsonClass(generateAdapter = true)
data class DefaultSyncNotificationContent(
    val notificationTitle: String,
    val notificationContent: String,
    val channelName: String,
    val channelDescription: String,
)

class DefaultSyncNotificationBuilder(
    private val sharedPreferences: SharedPreferences
): SyncNotificationBuilder {
    override fun build(context: Context, resources: Set<VitalResource>): Notification {
        val content = getContent(context)

        return NotificationCompat.Builder(context, createChannel(context, content))
            .setContentTitle(content.notificationTitle)
            .setTicker(content.notificationTitle)
            .setContentText(content.notificationContent)
            .setOngoing(true)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .build()
    }

    /**
     * Override the default content at application runtime.
     *
     * Native Android SDK customers should override by supplying their own [SyncNotificationBuilder].
     * This API is intended for Flutter and React Native customers.
     */
    fun setContentOverride(content: DefaultSyncNotificationContent) {
        sharedPreferences.edit()
            .putJson(SYNC_NOTIFICATION_CONTENT_KEY, content)
            .apply()
    }

    private fun createChannel(context: Context, content: DefaultSyncNotificationContent): String {
        // We cannot use IMPORTANT_MIN for FGS, or else Android will coerce it to IMPORTANT_HIGH
        val importance = NotificationManager.IMPORTANCE_LOW
        val mChannel = NotificationChannel("VitalHealthConnectSync", content.channelName, importance)
        mChannel.description = content.channelDescription
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(mChannel)
        return mChannel.id
    }

   private fun getContent(context: Context): DefaultSyncNotificationContent {
        val customized = sharedPreferences.getJson<DefaultSyncNotificationContent>(SYNC_NOTIFICATION_CONTENT_KEY)
        return customized ?: context.applicationName.let { appName ->
            DefaultSyncNotificationContent(
                notificationTitle = "Health Data Sync",
                notificationContent = "$appName is synchronizing with Health Connect...",
                channelName = "Health Data Sync",
                channelDescription = "Notifies when $appName is synchronizing with Health Connect.",
            )
        }
    }

    companion object {
        private var shared: DefaultSyncNotificationBuilder? = null

        fun getOrCreate(context: Context): DefaultSyncNotificationBuilder = synchronized(this) {
            if (shared == null) {
                shared = DefaultSyncNotificationBuilder(
                    context.applicationContext.getSharedPreferences(
                        VITAL_PERFS_FILE_NAME, MODE_PRIVATE
                    )
                )
            }
            return shared as DefaultSyncNotificationBuilder
        }
    }
}

private val Context.applicationName: String
    get() = applicationInfo.loadLabel(packageManager).toString()

