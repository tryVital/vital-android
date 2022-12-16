package io.tryvital.vitalhealthconnect.workers

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import io.tryvital.client.Environment
import io.tryvital.client.Region
import io.tryvital.client.VitalClient
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalhealthconnect.*
import io.tryvital.vitalhealthconnect.ext.toDate
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

class UploadAllDataWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val sharedPreferences: SharedPreferences = appContext.getSharedPreferences(
        prefsFileName,
        Context.MODE_PRIVATE
    )

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
                sharedPreferences.edit().putString(
                    changeTokenKey,
                    HealthConnectClientProvider().getHealthConnectClient(applicationContext)
                        .getChangesToken(ChangesTokenRequest(vitalRecordTypes))
                ).commit()

                Result.success()
            } catch (e: Exception) {
                vitalLogger.logE("Error uploading data", e.message, e)
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

        vitalLogger.logI("getting workouts")
        val workoutPayloads =
            recordProcessor.processWorkoutsFromTimeRange(startTime, endTime, currentDevice)
        Log.e("asd", workoutPayloads.toString())
        vitalLogger.logI("uploading workouts")
        recordUploader.uploadWorkouts(userId, startDate, endDate, timeZoneId, workoutPayloads)

        vitalLogger.logI("getting activities")
        val activityPayloads = recordProcessor.processActivitiesFromTimeRange(
            startTime,
            endTime,
            currentDevice,
        )

        vitalLogger.logI("uploading activities")
        recordUploader.uploadActivities(userId, startDate, endDate, timeZoneId, activityPayloads)

        vitalLogger.logI("getting sleeps")
        val profilePayload = recordProcessor.processProfileFromTimeRange(startTime, endTime)
        vitalLogger.logI("uploading profile")
        recordUploader.uploadProfile(userId, startDate, endDate, timeZoneId, profilePayload)

        vitalLogger.logI("getting body")
        val bodyPayload =
            recordProcessor.processBodyFromTimeRange(startTime, endTime, currentDevice)
        vitalLogger.logI("uploading body")
        recordUploader.uploadBody(userId, startDate, endDate, timeZoneId, bodyPayload)

        vitalLogger.logI("getting sleeps")
        val sleepPayloads =
            recordProcessor.processSleepFromTimeRange(startTime, endTime, currentDevice)
        vitalLogger.logI("uploading sleeps")
        recordUploader.uploadSleeps(userId, startDate, endDate, timeZoneId, sleepPayloads)
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