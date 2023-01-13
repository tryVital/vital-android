package io.tryvital.vitalhealthconnect.workers

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import io.tryvital.client.Environment
import io.tryvital.client.Region
import io.tryvital.client.VitalClient
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalhealthconnect.*
import io.tryvital.vitalhealthconnect.ext.toDate
import io.tryvital.vitalhealthconnect.model.HealthResource
import io.tryvital.vitalhealthconnect.records.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.*

private const val startTimeKey = "startTime"
private const val endTimeKey = "endTime"
private const val userIdKey = "userId"
private const val regionKey = "region"
private const val environmentKey = "environment"
private const val apiKeyKey = "apiKey"

internal const val statusTypeKey = "type"
internal const val syncStatusKey = "status"

internal const val nothingToSync = "nothingToSync"
internal const val synced = "synced"
internal const val syncing = "syncing"

class UploadAllDataWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val vitalClient: VitalClient by lazy {
        VitalClient(
            applicationContext,
            Region.valueOf(inputData.getString(regionKey) ?: Region.US.toString()),
            Environment.valueOf(
                inputData.getString(environmentKey) ?: Environment.Sandbox.toString()
            ),
            inputData.getString(apiKeyKey) ?: ""
        )
    }

    private val recordProcessor: RecordProcessor by lazy {
        val healthConnectClientProvider = HealthConnectClientProvider()

        HealthConnectRecordProcessor(
            HealthConnectRecordReader(applicationContext, healthConnectClientProvider),
            HealthConnectRecordAggregator(applicationContext, healthConnectClientProvider),
            vitalClient
        )
    }

    private val recordUploader: RecordUploader by lazy {
        VitalClientRecordUploader(vitalClient)
    }

    private val vitalLogger: VitalLogger by lazy {
        vitalClient.vitalLogger
    }

    @SuppressLint("ApplySharedPref")
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                readAndUploadHealthData(
                    startTime = Instant.ofEpochMilli(inputData.getLong(startTimeKey, 0)),
                    endTime = Instant.ofEpochMilli(inputData.getLong(endTimeKey, 0)),
                    userId = inputData.getString(userIdKey) ?: ""
                )

                vitalLogger.logI("Updating changes token")
                saveNewChangeToken(applicationContext)
                Result.success()
            } catch (e: Exception) {
                vitalLogger.logE("Error uploading data", e)
                Result.failure()
            }
        }
    }

    private suspend fun readAndUploadHealthData(
        startTime: Instant,
        endTime: Instant,
        userId: String,
    ) {
        val currentDevice = Build.MODEL
        val startDate = startTime.toDate()
        val endDate = endTime.toDate()
        val hostTimeZone = TimeZone.getDefault()
        val timeZoneId = hostTimeZone.id

        getWorkouts(startTime, endTime, currentDevice, userId, startDate, endDate, timeZoneId)
        getActivities(startTime, endTime, currentDevice, userId, startDate, endDate, timeZoneId)
        getProfile(startTime, endTime, userId, startDate, endDate, timeZoneId)
        getBody(startTime, endTime, currentDevice, userId, startDate, endDate, timeZoneId)
        getSleep(startTime, endTime, currentDevice, userId, startDate, endDate, timeZoneId)
    }

    private suspend fun getSleep(
        startTime: Instant,
        endTime: Instant,
        currentDevice: String,
        userId: String,
        startDate: Date,
        endDate: Date,
        timeZoneId: String?
    ) {
        reportStatus(HealthResource.Sleep, syncing)
        vitalLogger.logI("getting sleeps")
        val sleepPayloads =
            recordProcessor.processSleepFromTimeRange(startTime, endTime, currentDevice)
        if (sleepPayloads.isEmpty()) {
            reportStatus(HealthResource.Sleep, nothingToSync)
        } else {
            vitalLogger.logI("uploading sleeps")
            recordUploader.uploadSleeps(userId, startDate, endDate, timeZoneId, sleepPayloads)
            reportStatus(HealthResource.Sleep, synced)
        }
    }

    private suspend fun getBody(
        startTime: Instant,
        endTime: Instant,
        currentDevice: String,
        userId: String,
        startDate: Date,
        endDate: Date,
        timeZoneId: String?
    ) {
        vitalLogger.logI("getting body")
        reportStatus(HealthResource.Body, syncing)
        val bodyPayload =
            recordProcessor.processBodyFromTimeRange(startTime, endTime, currentDevice)
        vitalLogger.logI("uploading body")
        recordUploader.uploadBody(userId, startDate, endDate, timeZoneId, bodyPayload)
        reportStatus(HealthResource.Body, synced)
    }

    private suspend fun getProfile(
        startTime: Instant,
        endTime: Instant,
        userId: String,
        startDate: Date,
        endDate: Date,
        timeZoneId: String?
    ) {
        vitalLogger.logI("getting profile")
        reportStatus(HealthResource.Profile, syncing)
        val profilePayload = recordProcessor.processProfileFromTimeRange(startTime, endTime)
        vitalLogger.logI("uploading profile")
        recordUploader.uploadProfile(userId, startDate, endDate, timeZoneId, profilePayload)
        reportStatus(HealthResource.Profile, synced)
    }

    private suspend fun getActivities(
        startTime: Instant,
        endTime: Instant,
        currentDevice: String,
        userId: String,
        startDate: Date,
        endDate: Date,
        timeZoneId: String?
    ) {
        vitalLogger.logI("getting activities")
        reportStatus(HealthResource.Activity, syncing)
        val activityPayloads = recordProcessor.processActivitiesFromTimeRange(
            startTime,
            endTime,
            currentDevice,
        )
        vitalLogger.logI("uploading activities")
        if (activityPayloads.isEmpty()) {
            reportStatus(HealthResource.Activity, nothingToSync)
        } else {
            recordUploader.uploadActivities(
                userId,
                startDate,
                endDate,
                timeZoneId,
                activityPayloads
            )
            reportStatus(HealthResource.Activity, synced)
        }
    }

    private suspend fun getWorkouts(
        startTime: Instant,
        endTime: Instant,
        currentDevice: String,
        userId: String,
        startDate: Date,
        endDate: Date,
        timeZoneId: String?
    ) {
        vitalLogger.logI("getting workouts")
        reportStatus(HealthResource.Workout, syncing)
        val workoutPayloads =
            recordProcessor.processWorkoutsFromTimeRange(startTime, endTime, currentDevice)
        vitalLogger.logI("uploading workouts")
        if (workoutPayloads.isEmpty()) {
            reportStatus(HealthResource.Workout, nothingToSync)
        } else {
            recordUploader.uploadWorkouts(userId, startDate, endDate, timeZoneId, workoutPayloads)
            reportStatus(HealthResource.Workout, synced)
        }
    }

    private suspend fun reportStatus(resource: HealthResource, status: String) {
        setProgress(
            Data.Builder().putString(statusTypeKey, resource.name)
                .putString(syncStatusKey, status)
                .build()
        )
    }

    companion object {
        fun createInputData(
            startTime: Instant,
            endTime: Instant,
            userId: String,
            region: Region,
            environment: Environment,
            apiKey: String,
        ): Data {
            return Data.Builder()
                .putLong(startTimeKey, startTime.toEpochMilli())
                .putLong(endTimeKey, endTime.toEpochMilli())
                .putString(userIdKey, userId)
                .putString(regionKey, region.toString())
                .putString(environmentKey, environment.toString())
                .putString(apiKeyKey, apiKey)
                .build()
        }
    }
}