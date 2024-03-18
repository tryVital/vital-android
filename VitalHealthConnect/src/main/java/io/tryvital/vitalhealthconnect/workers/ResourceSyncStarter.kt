package io.tryvital.vitalhealthconnect.workers

import android.content.Context
import android.os.Build
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
import io.tryvital.vitalhealthconnect.VitalHealthConnectManager
import io.tryvital.vitalhealthconnect.model.VitalResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformWhile
import kotlin.random.Random

data class ResourceSyncStarterInput(
    val resources: Set<VitalResource>,
) {
    fun toData(): Data = Data.Builder().run {
        putStringArray("resources", resources.map { it.toString() }.toTypedArray())
        build()
    }

    companion object {
        fun fromData(data: Data) = ResourceSyncStarterInput(
            resources = data.getStringArray("resources")?.mapTo(mutableSetOf()) { VitalResource.valueOf(it) } ?: emptySet()
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
class ResourceSyncStarter(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val input: ResourceSyncStarterInput by lazy {
        ResourceSyncStarterInput.fromData(inputData)
    }

    private val syncNotificationBuilder: SyncNotificationBuilder by lazy {
        VitalHealthConnectManager.getOrCreate(applicationContext).syncNotificationBuilder
    }

    override suspend fun doWork(): Result {
        val logger = VitalLogger.getOrCreate()

        logger.logI("ResourceSyncStarter begin")

        val notification = syncNotificationBuilder.build(applicationContext, input.resources)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            setForeground(
                ForegroundInfo(
                    VITAL_SYNC_NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE,
                )
            )
        } else {
            setForeground(
                ForegroundInfo(VITAL_SYNC_NOTIFICATION_ID, notification)
            )
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