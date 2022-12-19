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
import io.tryvital.vitalhealthconnect.records.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

private const val userIdKey = "userId"
private const val regionKey = "region"
private const val environmentKey = "environment"
private const val apiKeyKey = "apiKey"

class UploadChangesWorker(appContext: Context, workerParams: WorkerParameters) :
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
                val originalChanges =
                    HealthConnectClientProvider().getHealthConnectClient(applicationContext)
                        .getChanges(sharedPreferences.getString(changeTokenKey, null)!!)


                var currentChanges: ChangesResponse? = originalChanges
                while (currentChanges != null) {
                    readAndUploadHealthDataFromChange(
                        inputData.getString(userIdKey)!!,
                        currentChanges.changes
                    )

                    if (currentChanges.hasMore) {
                        currentChanges =
                            HealthConnectClientProvider().getHealthConnectClient(applicationContext)
                                .getChanges(currentChanges.nextChangesToken)
                        sharedPreferences.edit()
                            .putString(changeTokenKey, currentChanges.nextChangesToken)
                            .apply()
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

    private suspend fun readAndUploadHealthDataFromChange(userId: String, changes: List<Change>) {
        val exercises = mutableListOf<ExerciseSessionRecord>()
        val sleeps = mutableListOf<SleepSessionRecord>()
        val heights = mutableListOf<HeightRecord>()
        val bodyFats = mutableListOf<BodyFatRecord>()
        val weights = mutableListOf<WeightRecord>()
        val activeEnergyBurned = mutableListOf<ActiveCaloriesBurnedRecord>()
        val basalMetabolicRate = mutableListOf<BasalMetabolicRateRecord>()
        val stepsRate = mutableListOf<StepsRecord>()
        val distance = mutableListOf<DistanceRecord>()
        val floorsClimbed = mutableListOf<FloorsClimbedRecord>()
        val vo2Max = mutableListOf<Vo2MaxRecord>()


        changes.filterIsInstance(UpsertionChange::class.java).forEach { change ->
            when (change.record) {
                is ExerciseSessionRecord -> exercises.add(change.record as ExerciseSessionRecord)
                is SleepSessionRecord -> sleeps.add(change.record as SleepSessionRecord)
                is HeightRecord -> heights.add(change.record as HeightRecord)
                is BodyFatRecord -> bodyFats.add(change.record as BodyFatRecord)
                is WeightRecord -> weights.add(change.record as WeightRecord)
                is ActiveCaloriesBurnedRecord -> activeEnergyBurned.add(change.record as ActiveCaloriesBurnedRecord)
                is BasalMetabolicRateRecord -> basalMetabolicRate.add(change.record as BasalMetabolicRateRecord)
                is StepsRecord -> stepsRate.add(change.record as StepsRecord)
                is DistanceRecord -> distance.add(change.record as DistanceRecord)
                is FloorsClimbedRecord -> floorsClimbed.add(change.record as FloorsClimbedRecord)
                is Vo2MaxRecord -> vo2Max.add(change.record as Vo2MaxRecord)
            }
        }

        vitalLogger.logI(
            "Syncing ${exercises.size} exercises and ${sleeps.size} sleeps " +
                    "and ${heights.size} heights and ${bodyFats.size} bodyFats and ${weights.size} weights " +
                    "and ${activeEnergyBurned.size} activeEnergyBurned and ${basalMetabolicRate.size} basalMetabolicRate " +
                    "and ${stepsRate.size} stepsRate and ${distance.size} distance and ${floorsClimbed.size} floorsClimbed " +
                    "and ${vo2Max.size} vo2Max"
        )

        val currentDevice = Build.MODEL
        val hostTimeZone = TimeZone.getDefault()
        val timeZoneId = hostTimeZone.id

        if (sleeps.isNotEmpty()) {
            val sleepStartTime = sleeps.minOf { it.startTime }
            val sleepEndTime = sleeps.maxOf { it.endTime }

            recordUploader.uploadSleeps(
                userId, sleepStartTime.toDate(), sleepEndTime.toDate(), timeZoneId,
                recordProcessor.processSleepFromRecords(
                    sleepStartTime,
                    sleepEndTime,
                    currentDevice,
                    sleeps
                )
            )
        }

        if (exercises.isNotEmpty()) {
            val exercisesStartTime = exercises.minOf { it.startTime }
            val exercisesEndTime = exercises.maxOf { it.endTime }

            recordUploader.uploadWorkouts(
                userId, exercisesStartTime.toDate(), exercisesEndTime.toDate(), timeZoneId,
                recordProcessor.processWorkoutsFromRecords(
                    exercisesStartTime, exercisesEndTime, currentDevice, exercises
                )
            )
        }

        if (heights.isNotEmpty()) {
            val height = heights.last()

            recordUploader.uploadProfile(
                userId, height.time.toDate(), height.time.toDate(), timeZoneId,
                recordProcessor.processProfileFromRecords(height.time, height.time, height)
            )
        }

        if (bodyFats.isNotEmpty() || weights.isNotEmpty()) {
            val bodyStartTime = bodyFats.map { it.time }.plus(weights.map { it.time }).minOf { it }
            val bodyEndTime = bodyFats.map { it.time }.plus(weights.map { it.time }).maxOf { it }

            recordUploader.uploadBody(
                userId, bodyStartTime.toDate(), bodyEndTime.toDate(), timeZoneId,
                recordProcessor.processBodyFromRecords(
                    bodyStartTime,
                    bodyEndTime,
                    currentDevice,
                    weights,
                    bodyFats
                )
            )
        }

        if (activeEnergyBurned.isNotEmpty() || basalMetabolicRate.isNotEmpty() || stepsRate.isNotEmpty() ||
            distance.isNotEmpty() || floorsClimbed.isNotEmpty() || vo2Max.isNotEmpty()
        ) {
            val activityStartTime =
                activeEnergyBurned.asSequence().map { it.startTime }
                    .plus(basalMetabolicRate.map { it.time })
                    .plus(stepsRate.map { it.startTime }).plus(distance.map { it.startTime })
                    .plus(floorsClimbed.map { it.startTime }).plus(vo2Max.map { it.time })
                    .minOf { it }
            val activityEndTime =
                activeEnergyBurned.asSequence().map { it.endTime }
                    .plus(basalMetabolicRate.map { it.time })
                    .plus(stepsRate.map { it.endTime }).plus(distance.map { it.endTime })
                    .plus(floorsClimbed.map { it.endTime }).plus(vo2Max.map { it.time })
                    .maxOf { it }

            recordUploader.uploadActivities(
                userId, activityStartTime.toDate(), activityEndTime.toDate(), timeZoneId,
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
                )
            )
        }
    }


    companion object {
        fun createInputData(
            userId: String,
            region: Region,
            environment: Environment,
            apiKey: String,
        ): Data {
            return Data.Builder()
                .putString(userIdKey, userId)
                .putString(regionKey, region.toString())
                .putString(environmentKey, environment.toString())
                .putString(apiKeyKey, apiKey)
                .build()
        }
    }
}