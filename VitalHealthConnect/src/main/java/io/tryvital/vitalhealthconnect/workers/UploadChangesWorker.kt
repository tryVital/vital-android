package io.tryvital.vitalhealthconnect.workers

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.health.connect.client.changes.Change
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.records.*
import androidx.health.connect.client.response.ChangesResponse
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
import java.util.*

private const val userIdKey = "userId"
private const val regionKey = "region"
private const val environmentKey = "environment"
private const val apiKeyKey = "apiKey"
private const val resourcesKey = "resourcesKey"

class UploadChangesWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val sharedPreferences: SharedPreferences = appContext.getSharedPreferences(
        prefsFileName, Context.MODE_PRIVATE
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
                val originalChanges =
                    HealthConnectClientProvider().getHealthConnectClient(applicationContext)
                        .getChanges(
                            sharedPreferences.getString(
                                UnSecurePrefKeys.changeTokenKey, null
                            )!!
                        )


                var currentChanges: ChangesResponse? = originalChanges
                while (currentChanges != null) {
                    readAndUploadHealthDataFromChange(
                        inputData.getString(userIdKey)!!,
                        currentChanges.changes,
                        resourcesToSync = inputData.getStringArray(resourcesKey)?.mapNotNull {
                            HealthResource.valueOf(it)
                        }?.toSet() ?: emptySet()
                    )

                    if (currentChanges.hasMore) {
                        currentChanges =
                            HealthConnectClientProvider().getHealthConnectClient(applicationContext)
                                .getChanges(currentChanges.nextChangesToken)
                        sharedPreferences.edit().putString(
                            UnSecurePrefKeys.changeTokenKey, currentChanges.nextChangesToken
                        ).apply()
                    } else {
                        currentChanges = null
                    }
                }

                vitalLogger.logI("Updating changes token")
                saveNewChangeToken(applicationContext)

                Result.success()
            } catch (e: Exception) {
                vitalLogger.logE("Error uploading data", e)
                Result.failure()
            }
        }
    }

    private suspend fun readAndUploadHealthDataFromChange(
        userId: String, changes: List<Change>, resourcesToSync: Set<HealthResource>
    ) {
        val heights = mutableListOf<HeightRecord>()
        val weights = mutableListOf<WeightRecord>()
        val exercises = mutableListOf<ExerciseSessionRecord>()
        val sleeps = mutableListOf<SleepSessionRecord>()
        val sleepStages = mutableListOf<SleepStageRecord>()
        val bodyFats = mutableListOf<BodyFatRecord>()
        val activeEnergyBurned = mutableListOf<ActiveCaloriesBurnedRecord>()
        val basalMetabolicRate = mutableListOf<BasalMetabolicRateRecord>()
        val stepsRate = mutableListOf<StepsRecord>()
        val distance = mutableListOf<DistanceRecord>()
        val floorsClimbed = mutableListOf<FloorsClimbedRecord>()
        val vo2Max = mutableListOf<Vo2MaxRecord>()
        val heartRate = mutableListOf<HeartRateRecord>()
        val bloodPressure = mutableListOf<BloodPressureRecord>()
        val bloodGlucose = mutableListOf<BloodGlucoseRecord>()
        val respiratoryRate = mutableListOf<RespiratoryRateRecord>()
        val water = mutableListOf<HydrationRecord>()
        val heartRateVariabilityRmssd = mutableListOf<HeartRateVariabilityRmssdRecord>()

        changes.filterIsInstance(UpsertionChange::class.java).forEach { change ->
            when (change.record) {
                is ExerciseSessionRecord -> exercises.add(change.record as ExerciseSessionRecord)
                is SleepSessionRecord -> sleeps.add(change.record as SleepSessionRecord)
                is SleepStageRecord -> sleepStages.add(change.record as SleepStageRecord)
                is HeightRecord -> heights.add(change.record as HeightRecord)
                is BodyFatRecord -> bodyFats.add(change.record as BodyFatRecord)
                is WeightRecord -> weights.add(change.record as WeightRecord)
                is ActiveCaloriesBurnedRecord -> activeEnergyBurned.add(change.record as ActiveCaloriesBurnedRecord)
                is BasalMetabolicRateRecord -> basalMetabolicRate.add(change.record as BasalMetabolicRateRecord)
                is StepsRecord -> stepsRate.add(change.record as StepsRecord)
                is DistanceRecord -> distance.add(change.record as DistanceRecord)
                is FloorsClimbedRecord -> floorsClimbed.add(change.record as FloorsClimbedRecord)
                is Vo2MaxRecord -> vo2Max.add(change.record as Vo2MaxRecord)
                is HeartRateRecord -> heartRate.add(change.record as HeartRateRecord)
                is BloodPressureRecord -> bloodPressure.add(change.record as BloodPressureRecord)
                is BloodGlucoseRecord -> bloodGlucose.add(change.record as BloodGlucoseRecord)
                is RespiratoryRateRecord -> respiratoryRate.add(change.record as RespiratoryRateRecord)
                is HydrationRecord -> water.add(change.record as HydrationRecord)
                is HeartRateVariabilityRmssdRecord -> heartRateVariabilityRmssd.add(change.record as HeartRateVariabilityRmssdRecord)
            }
        }

        vitalLogger.logI(
            "Syncing ${exercises.size} exercises and ${sleeps.size} sleeps and ${sleepStages.size} " + "and ${heights.size} heights and ${bodyFats.size} bodyFats and ${weights.size} weights " + "and ${activeEnergyBurned.size} activeEnergyBurned and ${basalMetabolicRate.size} basalMetabolicRate " + "and ${stepsRate.size} stepsRate and ${distance.size} distance and ${floorsClimbed.size} floorsClimbed " + "and ${vo2Max.size} vo2Max and ${heartRate.size} heartRate and ${bloodPressure.size} bloodPressure" + "and ${bloodGlucose.size} bloodGlucose and ${respiratoryRate.size} respiratoryRate"
        )

        val currentDevice = Build.MODEL
        val hostTimeZone = TimeZone.getDefault()
        val timeZoneId = hostTimeZone.id

        if (resourcesToSync.contains(HealthResource.Profile)) {
            getProfile(heights, userId, timeZoneId)
        }
        if (resourcesToSync.contains(HealthResource.Body)) {
            getBody(bodyFats, weights, userId, timeZoneId, currentDevice)
        }
        if (resourcesToSync.contains(HealthResource.Workout)) {
            getWorkouts(exercises, userId, timeZoneId, currentDevice)
        }
        if (resourcesToSync.contains(HealthResource.Activity)) {
            getActivity(
                activeEnergyBurned,
                basalMetabolicRate,
                stepsRate,
                distance,
                floorsClimbed,
                vo2Max,
                userId,
                timeZoneId,
                currentDevice
            )
        }
        if (resourcesToSync.contains(HealthResource.Sleep)) {
            getSleep(sleeps, sleepStages, userId, timeZoneId, currentDevice)
        }
        if (resourcesToSync.contains(HealthResource.Glucose)) {
            getGlucose(bloodGlucose, userId, timeZoneId, currentDevice)
        }
        if (resourcesToSync.contains(HealthResource.BloodPressure)) {
            getBloodPressure(bloodPressure, userId, timeZoneId, currentDevice)
        }
        if (resourcesToSync.contains(HealthResource.HeartRate)) {
            getHeartRate(heartRate, userId, timeZoneId, currentDevice)
        }
        if (resourcesToSync.contains(HealthResource.Water)) {
            getWater(water, userId, timeZoneId, currentDevice)
        }
        if (resourcesToSync.contains(HealthResource.HeartRateVariability)) {
            getHeartRateVariability(heartRateVariabilityRmssd, userId, timeZoneId, currentDevice)
        }
    }

    private suspend fun getBloodPressure(
        bloodPressure: List<BloodPressureRecord>,
        userId: String,
        timeZoneId: String?,
        currentDevice: String
    ) {
        reportStatus(HealthResource.BloodPressure, syncing)
        if (bloodPressure.isNotEmpty()) {
            val bloodPressureStartTime = bloodPressure.minOf { it.time }
            val bloodPressureEndTime = bloodPressure.maxOf { it.time }

            recordUploader.uploadBloodPressure(userId,
                bloodPressureStartTime.toDate(),
                bloodPressureEndTime.toDate(),
                timeZoneId,
                recordProcessor.processBloodPressureFromRecords(
                    bloodPressureStartTime, bloodPressureEndTime, currentDevice, bloodPressure
                ).samples.map { it.toBloodPressurePayload() })

            reportStatus(HealthResource.BloodPressure, synced)
        } else {
            reportStatus(HealthResource.BloodPressure, nothingToSync)
        }

    }

    private suspend fun getWater(
        water: List<HydrationRecord>, userId: String, timeZoneId: String?, currentDevice: String
    ) {
        reportStatus(HealthResource.Water, syncing)
        if (water.isNotEmpty()) {
            val waterStartTime = water.minOf { it.startTime }
            val waterEndTime = water.maxOf { it.endTime }

            recordUploader.uploadWater(userId,
                waterStartTime.toDate(),
                waterEndTime.toDate(),
                timeZoneId,
                recordProcessor.processWaterFromRecords(
                    waterStartTime, waterEndTime, currentDevice, water
                ).samples.map { it.toQuantitySamplePayload() })

            reportStatus(HealthResource.Water, synced)
        } else {
            reportStatus(HealthResource.Water, nothingToSync)
        }
    }

    private suspend fun getHeartRate(
        heartRate: List<HeartRateRecord>, userId: String, timeZoneId: String?, currentDevice: String
    ) {
        reportStatus(HealthResource.HeartRate, syncing)
        if (heartRate.isNotEmpty()) {
            val heartRateStartTime = heartRate.minOf { it.startTime }
            val heartRateEndTime = heartRate.maxOf { it.endTime }

            recordUploader.uploadHeartRate(userId,
                heartRateStartTime.toDate(),
                heartRateEndTime.toDate(),
                timeZoneId,
                recordProcessor.processHeartRateFromRecords(
                    heartRateStartTime, heartRateEndTime, currentDevice, heartRate
                ).samples.map { it.toQuantitySamplePayload() })

            reportStatus(HealthResource.HeartRate, synced)
        } else {
            reportStatus(HealthResource.HeartRate, nothingToSync)
        }
    }

    private suspend fun getHeartRateVariability(
        rmssdRecords: List<HeartRateVariabilityRmssdRecord>,
        userId: String,
        timeZoneId: String?,
        currentDevice: String
    ) {
        reportStatus(HealthResource.HeartRateVariability, syncing)
        if (rmssdRecords.isNotEmpty()) {
            val heartRateStartTime = rmssdRecords.minOf { it.time }
            val heartRateEndTime = rmssdRecords.maxOf { it.time }

            recordUploader.uploadHeartRateVariability(userId,
                heartRateStartTime.toDate(),
                heartRateEndTime.toDate(),
                timeZoneId,
                recordProcessor.processHeartRateVariabilityRmssFromRecords(
                    heartRateStartTime, heartRateEndTime, currentDevice, rmssdRecords
                ).samples.map { it.toQuantitySamplePayload() })

            reportStatus(HealthResource.HeartRateVariability, synced)
        } else {
            reportStatus(HealthResource.HeartRateVariability, nothingToSync)
        }
    }

    private suspend fun getGlucose(
        bloodGlucose: List<BloodGlucoseRecord>,
        userId: String,
        timeZoneId: String?,
        currentDevice: String
    ) {
        reportStatus(HealthResource.Glucose, syncing)
        if (bloodGlucose.isNotEmpty()) {
            val bloodGlucoseStartTime = bloodGlucose.minOf { it.time }
            val bloodGlucoseEndTime = bloodGlucose.maxOf { it.time }

            recordUploader.uploadGlucose(userId,
                bloodGlucoseStartTime.toDate(),
                bloodGlucoseEndTime.toDate(),
                timeZoneId,
                recordProcessor.processGlucoseFromRecords(
                    bloodGlucoseStartTime, bloodGlucoseEndTime, currentDevice, bloodGlucose
                ).samples.map { it.toQuantitySamplePayload() })

            reportStatus(HealthResource.Glucose, synced)
        } else {
            reportStatus(HealthResource.Glucose, nothingToSync)
        }

    }

    private suspend fun getSleep(
        sleeps: List<SleepSessionRecord>,
        stages: List<SleepStageRecord>,
        userId: String,
        timeZoneId: String?,
        currentDevice: String
    ) {
        reportStatus(HealthResource.Sleep, syncing)
        if (sleeps.isNotEmpty()) {
            val sleepStartTime = sleeps.minOf { it.startTime }
            val sleepEndTime = sleeps.maxOf { it.endTime }

            recordUploader.uploadSleeps(userId,
                sleepStartTime.toDate(),
                sleepEndTime.toDate(),
                timeZoneId,
                recordProcessor.processSleepFromRecords(
                    sleepStartTime,
                    sleepEndTime,
                    currentDevice,
                    sleeps,
                    stages,
                ).samples.map { it.toSleepPayload() })
            reportStatus(HealthResource.Sleep, synced)
        } else {
            reportStatus(HealthResource.Sleep, nothingToSync)
        }
    }

    private suspend fun getActivity(
        activeEnergyBurned: List<ActiveCaloriesBurnedRecord>,
        basalMetabolicRate: List<BasalMetabolicRateRecord>,
        stepsRate: List<StepsRecord>,
        distance: List<DistanceRecord>,
        floorsClimbed: List<FloorsClimbedRecord>,
        vo2Max: List<Vo2MaxRecord>,
        userId: String,
        timeZoneId: String?,
        currentDevice: String
    ) {
        reportStatus(HealthResource.Activity, syncing)
        if (activeEnergyBurned.isNotEmpty() || basalMetabolicRate.isNotEmpty() || stepsRate.isNotEmpty() || distance.isNotEmpty() || floorsClimbed.isNotEmpty() || vo2Max.isNotEmpty()) {
            val activityStartTime = activeEnergyBurned.asSequence().map { it.startTime }
                .plus(basalMetabolicRate.map { it.time }).plus(stepsRate.map { it.startTime })
                .plus(distance.map { it.startTime }).plus(floorsClimbed.map { it.startTime })
                .plus(vo2Max.map { it.time }).minOf { it }
            val activityEndTime = activeEnergyBurned.asSequence().map { it.endTime }
                .plus(basalMetabolicRate.map { it.time }).plus(stepsRate.map { it.endTime })
                .plus(distance.map { it.endTime }).plus(floorsClimbed.map { it.endTime })
                .plus(vo2Max.map { it.time }).maxOf { it }

            recordUploader.uploadActivities(userId,
                activityStartTime.toDate(),
                activityEndTime.toDate(),
                timeZoneId,
                recordProcessor.processActivitiesFromRecords(
                    activityStartTime,
                    activityEndTime,
                    currentDevice,
                    activeEnergyBurned,
                    basalMetabolicRate,
                    stepsRate,
                    distance,
                    floorsClimbed,
                    vo2Max
                ).samples.map { it.toActivityPayload() })
            reportStatus(HealthResource.Activity, synced)
        } else {
            reportStatus(HealthResource.Activity, nothingToSync)
        }
    }

    private suspend fun getWorkouts(
        exercises: List<ExerciseSessionRecord>,
        userId: String,
        timeZoneId: String?,
        currentDevice: String
    ) {
        reportStatus(HealthResource.Workout, syncing)
        if (exercises.isNotEmpty()) {
            val exercisesStartTime = exercises.minOf { it.startTime }
            val exercisesEndTime = exercises.maxOf { it.endTime }

            recordUploader.uploadWorkouts(userId,
                exercisesStartTime.toDate(),
                exercisesEndTime.toDate(),
                timeZoneId,
                recordProcessor.processWorkoutsFromRecords(
                    exercisesStartTime, exercisesEndTime, currentDevice, exercises
                ).samples.map { it.toWorkoutPayload() })

            reportStatus(HealthResource.Workout, synced)
        } else {
            reportStatus(HealthResource.Workout, nothingToSync)
        }
    }

    private suspend fun getBody(
        bodyFats: List<BodyFatRecord>,
        weights: List<WeightRecord>,
        userId: String,
        timeZoneId: String?,
        currentDevice: String
    ) {
        reportStatus(HealthResource.Body, syncing)
        if (bodyFats.isNotEmpty() || weights.isNotEmpty()) {
            val bodyStartTime = bodyFats.map { it.time }.plus(weights.map { it.time }).minOf { it }
            val bodyEndTime = bodyFats.map { it.time }.plus(weights.map { it.time }).maxOf { it }

            recordUploader.uploadBody(
                userId,
                bodyStartTime.toDate(),
                bodyEndTime.toDate(),
                timeZoneId,
                recordProcessor.processBodyFromRecords(
                    bodyStartTime, bodyEndTime, currentDevice, weights, bodyFats
                ).toBodyPayload()
            )
            reportStatus(HealthResource.Body, synced)
        } else {
            reportStatus(HealthResource.Body, nothingToSync)
        }
    }

    private suspend fun getProfile(
        heights: List<HeightRecord>, userId: String, timeZoneId: String?
    ) {
        reportStatus(HealthResource.Profile, syncing)
        if (heights.isNotEmpty()) {
            val height = heights.last()

            recordUploader.uploadProfile(
                userId,
                height.time.toDate(),
                height.time.toDate(),
                timeZoneId,
                recordProcessor.processProfileFromRecords(height.time, height.time, heights)
                    .toProfilePayload()
            )
            reportStatus(HealthResource.Profile, synced)
        } else {
            reportStatus(HealthResource.Profile, nothingToSync)
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
            userId: String,
            region: Region,
            environment: Environment,
            apiKey: String,
            resource: Set<HealthResource>
        ): Data {
            return Data.Builder().putString(userIdKey, userId)
                .putString(regionKey, region.toString())
                .putString(environmentKey, environment.toString()).putString(apiKeyKey, apiKey)
                .putStringArray(resourcesKey, resource.map { it.name }.toTypedArray()).build()
        }
    }
}