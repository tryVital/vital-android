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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.*

private const val startTimeKey = "startTime"
private const val endTimeKey = "endTime"
private const val userIdKey = "userId"
private const val regionKey = "region"
private const val environmentKey = "environment"
private const val apiKeyKey = "apiKey"
private const val resourcesKey = "resourcesKey"

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

    private val healthConnectClientProvider by lazy { HealthConnectClientProvider() }

    private val recordReader: RecordReader by lazy {
        HealthConnectRecordReader(applicationContext, healthConnectClientProvider)
    }

    private val recordProcessor: RecordProcessor by lazy {
        HealthConnectRecordProcessor(
            recordReader,
            HealthConnectRecordAggregator(applicationContext, healthConnectClientProvider),
        )
    }

    private val recordUploader: RecordUploader by lazy {
        VitalClientRecordUploader(vitalClient)
    }

    private val vitalLogger = VitalLogger.getOrCreate()

    @SuppressLint("ApplySharedPref")
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                readAndUploadHealthData(
                    startTime = Instant.ofEpochMilli(inputData.getLong(startTimeKey, 0)),
                    endTime = Instant.ofEpochMilli(inputData.getLong(endTimeKey, 0)),
                    userId = inputData.getString(userIdKey) ?: "",
                    resourcesToSync = inputData.getStringArray(resourcesKey)?.mapNotNull {
                        HealthResource.valueOf(it)
                    }?.toSet() ?: emptySet()
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
        startTime: Instant, endTime: Instant, userId: String, resourcesToSync: Set<HealthResource>
    ) {
        val currentDevice = Build.MODEL
        val startDate = startTime.toDate()
        val endDate = endTime.toDate()
        val hostTimeZone = TimeZone.getDefault()
        val timeZoneId = hostTimeZone.id

        if (resourcesToSync.contains(HealthResource.Profile)) {
            getProfile(startTime, endTime, userId, startDate, endDate, timeZoneId)
        }
        if (resourcesToSync.contains(HealthResource.Body)) {
            getBody(startTime, endTime, currentDevice, userId, startDate, endDate, timeZoneId)
        }
        if (resourcesToSync.contains(HealthResource.Workout)) {
            getWorkouts(startTime, endTime, currentDevice, userId, startDate, endDate, timeZoneId)
        }
        if (resourcesToSync.contains(HealthResource.Activity)) {
            getActivities(startTime, endTime, currentDevice, userId, startDate, endDate, timeZoneId)
        }
        if (resourcesToSync.contains(HealthResource.Sleep)) {
            getSleep(startTime, endTime, currentDevice, userId, startDate, endDate, timeZoneId)
        }
        if (resourcesToSync.contains(HealthResource.Glucose)) {
            getGlucose(startTime, endTime, currentDevice, userId, startDate, endDate, timeZoneId)
        }
        if (resourcesToSync.contains(HealthResource.BloodPressure)) {
            getBloodPressure(
                startTime, endTime, currentDevice, userId, startDate, endDate, timeZoneId
            )
        }
        if (resourcesToSync.contains(HealthResource.HeartRate)) {
            getHeartRate(startTime, endTime, currentDevice, userId, startDate, endDate, timeZoneId)
        }
        if (resourcesToSync.contains(HealthResource.Water)) {
            getWater(startTime, endTime, currentDevice, userId, startDate, endDate, timeZoneId)
        }
        if (resourcesToSync.contains(HealthResource.HeartRateVariability)) {
            getHeartRateVariability(
                startTime,
                endTime,
                currentDevice,
                userId,
                startDate,
                endDate,
                timeZoneId
            )
        }
    }

    private suspend fun getWater(
        startTime: Instant,
        endTime: Instant,
        currentDevice: String,
        userId: String,
        startDate: Date,
        endDate: Date,
        timeZoneId: String?
    ) {
        reportStatus(HealthResource.Water, syncing)
        val waters = recordProcessor.processWaterFromRecords(
            startTime, endTime, currentDevice, recordReader.readHydration(startTime, endTime)
        )

        if (waters.samples.isEmpty()) {
            reportStatus(HealthResource.Water, nothingToSync)
        } else {
            recordUploader.uploadWater(userId,
                startDate,
                endDate,
                timeZoneId,
                waters.samples.map { it.toQuantitySamplePayload() })
            reportStatus(HealthResource.Water, synced)
        }
    }

    private suspend fun getHeartRate(
        startTime: Instant,
        endTime: Instant,
        currentDevice: String,
        userId: String,
        startDate: Date,
        endDate: Date,
        timeZoneId: String?
    ) {
        reportStatus(HealthResource.HeartRate, syncing)
        val heartRatePayloads = recordProcessor.processHeartRateFromRecords(
            startTime, endTime, currentDevice, recordReader.readHeartRate(startTime, endTime)
        )
        if (heartRatePayloads.samples.isEmpty()) {
            reportStatus(HealthResource.HeartRate, nothingToSync)
        } else {
            recordUploader.uploadHeartRate(userId,
                startDate,
                endDate,
                timeZoneId,
                heartRatePayloads.samples.map { it.toQuantitySamplePayload() })
            reportStatus(HealthResource.HeartRate, synced)
        }
    }

    private suspend fun getHeartRateVariability(
        startTime: Instant,
        endTime: Instant,
        currentDevice: String,
        userId: String,
        startDate: Date,
        endDate: Date,
        timeZoneId: String?
    ) {
        reportStatus(HealthResource.HeartRateVariability, syncing)
        val heartRatePayloads = recordProcessor.processHeartRateVariabilityRmssFromRecords(
            startTime,
            endTime,
            currentDevice,
            recordReader.readHeartRateVariabilityRmssd(startTime, endTime)
        )
        if (heartRatePayloads.samples.isEmpty()) {
            reportStatus(HealthResource.HeartRateVariability, nothingToSync)
        } else {
            recordUploader.uploadHeartRateVariability(userId,
                startDate,
                endDate,
                timeZoneId,
                heartRatePayloads.samples.map { it.toQuantitySamplePayload() })
            reportStatus(HealthResource.HeartRateVariability, synced)
        }
    }

    private suspend fun getBloodPressure(
        startTime: Instant,
        endTime: Instant,
        currentDevice: String,
        userId: String,
        startDate: Date,
        endDate: Date,
        timeZoneId: String?
    ) {
        reportStatus(HealthResource.BloodPressure, syncing)
        val bloodPressurePayloads = recordProcessor.processBloodPressureFromRecords(
            startTime, endTime, currentDevice, recordReader.readBloodPressure(
                startTime, endTime,
            )
        )
        if (bloodPressurePayloads.samples.isEmpty()) {
            reportStatus(HealthResource.BloodPressure, nothingToSync)
        } else {
            recordUploader.uploadBloodPressure(userId,
                startDate,
                endDate,
                timeZoneId,
                bloodPressurePayloads.samples.map { it.toBloodPressurePayload() })
            reportStatus(HealthResource.BloodPressure, synced)
        }
    }

    private suspend fun getGlucose(
        startTime: Instant,
        endTime: Instant,
        currentDevice: String,
        userId: String,
        startDate: Date,
        endDate: Date,
        timeZoneId: String?
    ) {
        reportStatus(HealthResource.Glucose, syncing)
        val glucosePayloads = recordProcessor.processGlucoseFromRecords(
            startTime, endTime, currentDevice, recordReader.readBloodGlucose(startTime, endTime)
        )
        if (glucosePayloads.samples.isEmpty()) {
            reportStatus(HealthResource.Glucose, nothingToSync)
        } else {
            recordUploader.uploadGlucose(userId,
                startDate,
                endDate,
                timeZoneId,
                glucosePayloads.samples.map { it.toQuantitySamplePayload() }
            )
            reportStatus(HealthResource.Glucose, synced)
        }
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
        val sleepPayloads =
            recordProcessor.processSleepFromRecords(
                startTime,
                endTime,
                currentDevice,
                recordReader.readSleepSession(startTime, endTime),
                recordReader.readSleepStages(startTime, endTime)
            )
        if (sleepPayloads.samples.isEmpty()) {
            reportStatus(HealthResource.Sleep, nothingToSync)
        } else {
            recordUploader.uploadSleeps(
                userId,
                startDate,
                endDate,
                timeZoneId,
                sleepPayloads.samples.map { it.toSleepPayload() })
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
        reportStatus(HealthResource.Body, syncing)
        val bodyPayload =
            recordProcessor.processBodyFromRecords(
                startTime,
                endTime,
                currentDevice,
                recordReader.readWeights(startTime, endTime),
                recordReader.readBodyFat(startTime, endTime),
            )
        recordUploader.uploadBody(
            userId,
            startDate,
            endDate,
            timeZoneId,
            bodyPayload.toBodyPayload()
        )
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
        reportStatus(HealthResource.Profile, syncing)
        val profilePayload = recordProcessor.processProfileFromRecords(
            startTime,
            endTime,
            recordReader.readHeights(startTime, endTime)
        )
        recordUploader.uploadProfile(
            userId,
            startDate,
            endDate,
            timeZoneId,
            profilePayload.toProfilePayload()
        )
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
        reportStatus(HealthResource.Activity, syncing)
        val activityPayloads = recordProcessor.processActivitiesFromRecords(
            startTime,
            endTime,
            currentDevice,
            recordReader.readActiveEnergyBurned(startTime, endTime),
            recordReader.readBasalMetabolicRate(startTime, endTime),
            recordReader.readSteps(startTime, endTime),
            recordReader.readDistance(startTime, endTime),
            recordReader.readFloorsClimbed(startTime, endTime),
            recordReader.readVo2Max(startTime, endTime),
        )
        if (activityPayloads.samples.isEmpty()) {
            reportStatus(HealthResource.Activity, nothingToSync)
        } else {
            recordUploader.uploadActivities(
                userId,
                startDate,
                endDate,
                timeZoneId,
                activityPayloads.samples.map { it.toActivityPayload() }
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
        reportStatus(HealthResource.Workout, syncing)
        val workoutPayloads = recordProcessor.processWorkoutsFromRecords(
            startTime, endTime, currentDevice, recordReader.readExerciseSessions(startTime, endTime)
        )
        if (workoutPayloads.samples.isEmpty()) {
            reportStatus(HealthResource.Workout, nothingToSync)
        } else {
            recordUploader.uploadWorkouts(
                userId,
                startDate,
                endDate,
                timeZoneId,
                workoutPayloads.samples.map { it.toWorkoutPayload() })
            reportStatus(HealthResource.Workout, synced)
        }
    }

    private suspend fun reportStatus(resource: HealthResource, status: String) {
        setProgress(
            Data.Builder().putString(statusTypeKey, resource.name).putString(syncStatusKey, status)
                .build()
        )
        delay(100)
    }

    companion object {
        fun createInputData(
            startTime: Instant,
            endTime: Instant,
            userId: String,
            region: Region,
            environment: Environment,
            apiKey: String,
            resource: Set<HealthResource>
        ): Data {
            return Data.Builder().putLong(startTimeKey, startTime.toEpochMilli())
                .putLong(endTimeKey, endTime.toEpochMilli()).putString(userIdKey, userId)
                .putString(regionKey, region.toString())
                .putString(environmentKey, environment.toString()).putString(apiKeyKey, apiKey)
                .putStringArray(resourcesKey, resource.map { it.name }.toTypedArray()).build()
        }
    }
}