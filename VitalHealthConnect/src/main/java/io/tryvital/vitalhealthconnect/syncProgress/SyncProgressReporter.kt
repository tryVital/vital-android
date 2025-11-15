@file:OptIn(VitalPrivateApi::class, ExperimentalStdlibApi::class)

package io.tryvital.vitalhealthconnect.syncProgress

import android.content.Context
import android.os.Build
import com.squareup.moshi.adapter
import io.tryvital.client.VitalClient
import io.tryvital.client.services.VitalPrivateApi
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalhealthconnect.model.VitalResource
import io.tryvital.vitalhealthconnect.workers.LocalSyncStateManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val SCHEDULE_KEY = "SyncReportSchedule"

internal class SyncProgressReporter(
    private val store: SyncProgressStore,
    private val client: VitalClient,
    private val localSyncStateManager: LocalSyncStateManager
) {

    private val preferences get() = client.sharedPreferences

    private val active: MutableSet<SyncProgress.SyncID> = mutableSetOf()
    private val lock = ReentrantLock()


    private val reportMutex = Mutex()

    fun syncBegin(id: SyncProgress.SyncID) {
        lock.withLock { active += id }
    }

    suspend fun syncEnded(context: Context, id: SyncProgress.SyncID) {
        val remaining = lock.withLock {
            active.remove(id)
            active.size
        }
        if (remaining == 0) {
            reportIfNeeded(context, force = false)
        }
    }

    fun syncingResources(): Set<VitalResource> =
        lock.withLock { active.map { it.resource }.toSet() }

    suspend fun report(context: Context) {
        reportIfNeeded(context, force = true)
    }

    fun nextSchedule(): Instant? = preferences.getString(SCHEDULE_KEY, null)?.let(Instant::parse)

    private suspend fun reportIfNeeded(context: Context, force: Boolean) = reportMutex.withLock {
        val schedule = preferences.getString(SCHEDULE_KEY, null)?.let(Instant::parse) ?: Instant.MIN

        val shouldReport = force || Instant.now().isAfter(schedule)
        if (!shouldReport) {
            VitalLogger.getOrCreate().info {
                "SyncProgressReporter: skipped; next at $schedule"
            }
            return@withLock
        }

        val userId = VitalClient.currentUserId ?: return@withLock

        val progress = store.get()
        try {
            val deviceInfo = captureDeviceInfo(context)
            val report = SyncProgressReport(progress, deviceInfo)
            val reportBody = VitalGistStorage.moshi.adapter<SyncProgressReport>().toJson(report)
                .toRequestBody("application/json".toMediaTypeOrNull())
            client.vitalPrivateService.reportSyncProgress(userId, reportBody)

        } catch (t: Throwable) {
            VitalLogger.getOrCreate().info {
                "SyncProgressReporter: failed to report sync progress: $t"
            }
        }

        // Default: every 1 hour unless overridden by local state
        val reportingInterval =
            localSyncStateManager.getPersistedLocalSyncState()?.reportingInterval ?: 3_600
        val newSchedule = Instant.now().plusSeconds(reportingInterval)

        preferences.edit().putString(SCHEDULE_KEY, newSchedule.toString()).apply()

        VitalLogger.getOrCreate().info {
            "SyncProgressReporter: done; next at $newSchedule"
        }
    }

    /**
     * Capture lightweight device‑side metadata. Must be called on the main thread.
     */
    private fun captureDeviceInfo(context: Context): SyncProgressReport.DeviceInfo {
        val appContext = context.applicationContext
        val packageInfo =
            appContext.packageManager.getPackageInfo(context.applicationContext.packageName, 0)

        return SyncProgressReport.DeviceInfo(
            osVersion = "${Build.VERSION.RELEASE} (API Level ${Build.VERSION.SDK_INT})",
            model = "${Build.MANUFACTURER} ${Build.MODEL}",
            appBundle = appContext.packageName,
            appVersion = packageInfo.versionName ?: "",
            appBuild = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toString()
            } else {
                packageInfo.versionCode.toString()
            }
        )
    }
}
