package io.tryvital.vitalhealthconnect.workers

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.asFlow
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalhealthconnect.SyncNotificationBuilder
import io.tryvital.vitalhealthconnect.UnSecurePrefKeys
import io.tryvital.vitalhealthconnect.VitalHealthConnectManager
import io.tryvital.vitalhealthconnect.model.VitalResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformWhile
import java.util.UUID
import kotlin.random.Random

internal data class ResourceSyncStarterInput(
    val resources: Set<VitalResource>,
    /**
     * Whether the work should request WorkManager to start a foregorund service.
     * For sync triggered by user interaction, or sync triggered by ProcessLifecycle ON_START,
     * this should be `true`.
     *
     * However, if the sync is triggered by [SyncOnExactAlarmService], the service itself must
     * have been an FGS. So [ResourceSyncStarter] should not start another FGS. Otherwise, it
     * violates the short service FGS requirement.
     */
    val startForeground: Boolean,
) {
    fun toData(): Data = Data.Builder().run {
        putStringArray("resources", resources.map { it.toString() }.toTypedArray())
        putBoolean("startForeground", startForeground)
        build()
    }

    companion object {
        fun fromData(data: Data) = ResourceSyncStarterInput(
            resources = data.getStringArray("resources")?.mapTo(mutableSetOf()) { VitalResource.valueOf(it) } ?: emptySet(),
            startForeground = data.getBoolean("startForeground", true),
        )
    }
}

/**
 * An umbrella worker which we use to spawn `ResourceSyncWorker`s.
 *
 * This ensures that we only need to start Foreground Service once at the beginning
 * for all the requested [VitalResource], while the app is most likely still in the foreground.
 *
 * Android OS rejects [setForeground] when the app is in background.
 */
internal class ResourceSyncStarter(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val input: ResourceSyncStarterInput by lazy {
        ResourceSyncStarterInput.fromData(inputData)
    }

    private val manager by lazy {
        VitalHealthConnectManager.getOrCreate(applicationContext)
    }

    private val syncNotificationBuilder: SyncNotificationBuilder by lazy {
        VitalHealthConnectManager.getOrCreate(applicationContext).syncNotificationBuilder
    }

    override suspend fun doWork(): Result {
        val logger = VitalLogger.getOrCreate()

        logger.logI("ResourceSyncStarter begin")

        // Normally we would want to start foreground.
        // But if this worker is enqueued by SyncOnExactAlarmService, the service would have
        // already started a shortService FGS. Starting another one would violate the shortService
        // FGS requirement (that it cannot start another FGS).
        if (input.startForeground) {
            val processState = ProcessLifecycleOwner.get().lifecycle.currentState
            if (!processState.isAtLeast(Lifecycle.State.CREATED)) {
                // We aren't in foreground
                logger.logI("ResourceSyncStarter cancelled: not in foreground")
                return Result.failure()
            }

            val notification = syncNotificationBuilder.build(applicationContext, input.resources)

            setForeground(
                ForegroundInfo(VITAL_SYNC_NOTIFICATION_ID, notification, foregroundServiceType())
            )
        } else {
            val lastSeenWorkId = manager.sharedPreferences
                .getString(UnSecurePrefKeys.lastSeenWorkIdKey, null)
                ?.let { UUID.fromString(it) }
            val workId = id

            if (lastSeenWorkId == workId) {
                // This work request is likely a retry of an interrupted ResourceSyncStarter
                // due to system restart. Since we are not in an FGS in this case, we can't read
                // Health Connect. No point to continue.
                logger.logI("ResourceSyncStarter cancelled: likely not inside FGS")
                return Result.failure()
            }

            manager.sharedPreferences.edit()
                .putString(UnSecurePrefKeys.lastSeenWorkIdKey, workId.toString())
                .apply()
        }

        for (resource in input.resources) {
            val workRequest = OneTimeWorkRequestBuilder<ResourceSyncWorker>()
                .setInputData(ResourceSyncWorkerInput(resource = resource).toData())
                .addTag(resource.name)
                .build()

            val work = WorkManager.getInstance(applicationContext).beginUniqueWork(
                "ResourceSyncWorker.${resource}",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

            work.workInfosLiveData.asFlow()
                .mapNotNull { workInfos -> workInfos.firstOrNull { it.id == workRequest.id } }
                .transformWhile { info ->
                    emit(info)
                    return@transformWhile when (info.state) {
                        // Work is running; continue the observation
                        WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> true
                        // Work has ended; stop the observation
                        WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED, WorkInfo.State.FAILED -> false
                    }
                }
                .onStart { work.enqueue() }
                .flowOn(Dispatchers.Main)
                .collect {}

            // Rate limit mitigation, ugh
            delay(Random.nextLong(1, 15) * 100)
        }

        logger.logI("ResourceSyncStarter ends")

        return Result.success()
    }
}