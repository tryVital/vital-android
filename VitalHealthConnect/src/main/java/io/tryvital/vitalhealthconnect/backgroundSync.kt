@file:Suppress("unused")
@file:OptIn(ExperimentalVitalApi::class)

package io.tryvital.vitalhealthconnect

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.AlarmManagerCompat
import io.tryvital.client.utils.VitalLogger
import java.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private val MIN_SYNC_INTERVAL = 1.hours
private val AUTO_SYNC_THROTTLE = 10.minutes

@ExperimentalVitalApi
val VitalHealthConnectManager.isBackgroundSyncEnabled: Boolean
    get() = sharedPreferences.getBoolean(UnSecurePrefKeys.useExactAlarmKey, false)

@ExperimentalVitalApi
val VitalHealthConnectManager.backgroundSyncScheduledAt: Instant?
    get() = sharedPreferences.getLong(UnSecurePrefKeys.nextAlarmAtKey, -1)
        .takeIf { it >= System.currentTimeMillis() }
        ?.let { Instant.ofEpochMilli(it) }

internal val VitalHealthConnectManager.lastAutoSyncedAt: Long
    get() = sharedPreferences.getLong(UnSecurePrefKeys.lastAutoSyncedAtKey, 0)

internal val VitalHealthConnectManager.shouldSkipAutoSync: Boolean
    get() = lastAutoSyncedAt
        .let { System.currentTimeMillis() < it + AUTO_SYNC_THROTTLE.inWholeMilliseconds }

internal fun VitalHealthConnectManager.markAutoSyncSuccess() {
    sharedPreferences.edit()
        .putLong(UnSecurePrefKeys.lastAutoSyncedAtKey, System.currentTimeMillis())
        .apply()
}

/**
 * An [ActivityResultContract] to enable automatic data sync. This includes requesting permissions
 * from the end user whenever necessary.
 *
 * Vital SDK achieves automatic data sync through Android [AlarmManager] exact alarms.
 *
 * Refer to the [Vital Health Connect guide for full context and setup instructions](https://docs.tryvital.io/wearables/guides/android_health_connect).
 *
 * ## Gist on Exact Alarms
 *
 * "Exact Alarm" here refers to the Android Exact Alarm mechanism. The Vital SDK would propose
 * to the Android OS to fire the next data sync with a T+60min wall clock target. The Android OS
 * may fulfill the request exactly as proposed, e.g., when the user happens to be actively using
 * the device. However, it may also choose to defer it arbitrarily, under the influence of
 * power-saving policies like [Doze mode](https://developer.android.com/training/monitoring-device-state/doze-standby#understand_doze).
 *
 * On Android 12 (API Level 31) or above, this contract would automatically initiate the OS-required
 * user consent flow for Exact Alarm usage. If the permission has been granted prior, this activity
 * contract shall return synchronously.
 *
 * On Android 13 (API Level 33) or above, you have the option to use (with platform policy caveats)
 * the [android.Manifest.permission.USE_EXACT_ALARM] permission instead, which does not require an
 * explicit consent flow. This contract would return synchronously in this scenario.
 *
 * Regardless of API Level, your App Manifest must declare [android.Manifest.permission.RECEIVE_BOOT_COMPLETED].
 * Otherwise, background sync stops once the phone encounters a cold reboot or a quick restart.
 *
 * @return `true` if the background sync has been enabled successfully. `false` otherwise.
 */
@ExperimentalVitalApi
fun VitalHealthConnectManager.enableBackgroundSyncContract() = object: ActivityResultContract<Unit, Boolean>() {
    override fun getSynchronousResult(
        context: Context,
        input: Unit?
    ): SynchronousResult<Boolean>? {
        val alarmManager = context.getSystemService(AlarmManager::class.java)

        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S
            || alarmManager.canScheduleExactAlarms()
        ) {
            VitalLogger.getOrCreate().info { "BgSync: can enable exact alarm non-interactively" }
            return enableBackgroundSync().let(::SynchronousResult)
        }

        return null
    }

    @SuppressLint("InlinedApi")
    override fun createIntent(context: Context, input: Unit?): Intent {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)

        VitalLogger.getOrCreate().info { "BgSync: will request explicit exact alarm permission" }
        return Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${context.packageName}")
        )
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
        VitalLogger.getOrCreate().info { "BgSync: permission request result: $resultCode" }

        return if (resultCode == Activity.RESULT_OK) {
            enableBackgroundSync()
        } else {
            false
        }
    }
}

@ExperimentalVitalApi
fun VitalHealthConnectManager.disableBackgroundSync() {
    check(Looper.getMainLooper().isCurrentThread)

    cancelPendingAlarm()

    sharedPreferences.edit()
        .putLong(UnSecurePrefKeys.nextAlarmAtKey, -1)
        .putBoolean(UnSecurePrefKeys.useExactAlarmKey, false)
        .apply()

    VitalLogger.getOrCreate().info { "BgSync: disabled" }
}

internal fun VitalHealthConnectManager.cancelPendingAlarm() {
    check(Looper.getMainLooper().isCurrentThread)

    val pendingIntent = makeSyncPendingIntent(PendingIntent.FLAG_NO_CREATE)
    if (pendingIntent != null) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        pendingIntent.cancel()
        alarmManager.cancel(pendingIntent)

        VitalLogger.getOrCreate().info { "BgSync: cancelled alarm" }
    } else {
        VitalLogger.getOrCreate().info { "BgSync: skipped cancelling; found none" }
    }
}

/**
 * This is private. Customers should all use [enableBackgroundSyncContract], which has integrated
 * permission handling.
 */
private fun VitalHealthConnectManager.enableBackgroundSync(): Boolean {
    return scheduleNextExactAlarm(force = false).also { success ->
        sharedPreferences.edit()
            .putBoolean(UnSecurePrefKeys.useExactAlarmKey, success)
            .apply()
    }
}

internal fun VitalHealthConnectManager.scheduleNextExactAlarm(force: Boolean): Boolean {
    check(Looper.getMainLooper().isCurrentThread)

    if (pauseSynchronization) {
        VitalLogger.getOrCreate().info { "BgSync: scheduling skipped; sync is paused" }
        return false
    }

    val alarmManager = context.getSystemService(AlarmManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        VitalLogger.getOrCreate().info { "BgSync: scheduling aborted; no permission to use exact alarm" }
        return false
    }

    val now = System.currentTimeMillis()
    val hasPendingIntent = makeSyncPendingIntent(PendingIntent.FLAG_NO_CREATE) != null

    val scheduledAt = sharedPreferences.getLong(UnSecurePrefKeys.nextAlarmAtKey, -1)
    if (hasPendingIntent && scheduledAt >= now && !force) {
        // Pending alarm not yet fired; skip setting a new one
        VitalLogger.getOrCreate().info { "BgSync: scheduling skipped; existing at ${Instant.ofEpochMilli(scheduledAt)}" }
        return true
    }

    val nextAlarmAt = now + MIN_SYNC_INTERVAL.inWholeMilliseconds
    sharedPreferences.edit()
        .putLong(UnSecurePrefKeys.nextAlarmAtKey, nextAlarmAt)
        .apply()

    // We simply ask for an exact alarm firing at T+1hour here.
    // `setExact` is already subjected to OS throttling e.g. Doze mode, so there is little to no
    // point for us to vary our request. Keep it simple & stupid, and let the OS do its job.
    AlarmManagerCompat.setExact(
        alarmManager,
        AlarmManager.RTC_WAKEUP,
        nextAlarmAt,
        makeSyncPendingIntent(PendingIntent.FLAG_UPDATE_CURRENT)!!,
    )

    VitalLogger.getOrCreate().info { "BgSync: scheduled next alarm at ${Instant.ofEpochMilli(nextAlarmAt)}" }
    return true
}

internal fun VitalHealthConnectManager.makeSyncPendingIntent(flags: Int): PendingIntent? {
    val syncBroadcastReceiver = Intent(context, SyncBroadcastReceiver::class.java)
    syncBroadcastReceiver.action = ACTION_SYNC_DATA

    return PendingIntent.getBroadcast(
        context,
        0,
        syncBroadcastReceiver,
        flags or PendingIntent.FLAG_IMMUTABLE,
    )
}
