package io.tryvital.vitalhealthconnect

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectClient.Companion.ACTION_HEALTH_CONNECT_SETTINGS
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Volume
import androidx.lifecycle.asFlow
import androidx.work.*
import io.tryvital.client.Environment
import io.tryvital.client.Region
import io.tryvital.client.VitalClient
import io.tryvital.client.createConnectedSourceIfNotExist
import io.tryvital.client.services.data.*
import io.tryvital.vitalhealthconnect.model.*
import io.tryvital.vitalhealthconnect.model.processedresource.ProcessedResourceData
import io.tryvital.vitalhealthconnect.records.*
import io.tryvital.vitalhealthconnect.workers.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.reflect.KClass

internal val readRecordTypes = setOf(
    ExerciseSessionRecord::class,
    DistanceRecord::class,
    ActiveCaloriesBurnedRecord::class,
    HeartRateRecord::class,
    RespiratoryRateRecord::class,
    HeightRecord::class,
    BodyFatRecord::class,
    WeightRecord::class,
    SleepSessionRecord::class,
    SleepStageRecord::class,
    OxygenSaturationRecord::class,
    HeartRateVariabilityRmssdRecord::class,
    RestingHeartRateRecord::class,
    BasalMetabolicRateRecord::class,
    StepsRecord::class,
    DistanceRecord::class,
    FloorsClimbedRecord::class,
    HydrationRecord::class,
    BloodGlucoseRecord::class,
    BloodPressureRecord::class,
    HeartRateRecord::class,
)

internal fun vitalRecordTypes(healthPermission: Set<String>): Set<KClass<out Record>> {
    return readRecordTypes.filterTo(mutableSetOf()) { recordType ->
        healthPermission.contains(HealthPermission.getReadPermission(recordType))
    }
}

@Suppress("MemberVisibilityCanBePrivate")
class VitalHealthConnectManager private constructor(
    private val context: Context,
    private val healthConnectClientProvider: HealthConnectClientProvider,
    val vitalClient: VitalClient,
    private val recordReader: RecordReader,
    private val recordProcessor: RecordProcessor,
) {
    val sharedPreferences get() = vitalClient.sharedPreferences
    val encryptedSharedPreferences get() = vitalClient.encryptedSharedPreferences

    private val vitalLogger = vitalClient.vitalLogger

    // Unlimited buffering for slow subscribers.
    // https://github.com/Kotlin/kotlinx.coroutines/issues/2034#issuecomment-630381961
    private val _status = MutableSharedFlow<SyncStatus>(replay = 1, extraBufferCapacity = Int.MAX_VALUE)
    val status: SharedFlow<SyncStatus> = _status

    private val currentSyncCall = Semaphore(1, 0)

    // Use SupervisorJob so that:
    // 1. child job failure would not cancel the whole scope (it would by default of structured concurrency).
    // 2. cancelling the scope would cancel all running child jobs.
    private var taskScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        vitalLogger.logI("VitalHealthConnectManager initialized")
        _status.tryEmit(SyncStatus.Unknown)

        taskScope.launch { checkAndUpdatePermissions() }
    }

    /**
     * Stop any running task, and close this VitalHealthConnectManager instance.
     *
     * Note that this is not [cleanUp], which erases all SDK settings and persistent state.
     */
    @Suppress("unused")
    fun close() {
        taskScope.cancel()
    }

    /**
     * Erase all SDK settings and persistent state for the current user.
     *
     * You typically only need to [cleanUp] when your application has logged out the current user.
     */
    @SuppressLint("ApplySharedPref")
    @Suppress("unused")
    fun cleanUp() {
        taskScope.cancel()
        taskScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        vitalClient.cleanUp()
    }

    @Suppress("unused")
    fun createPermissionRequestContract(
        readResources: Set<VitalResource> = emptySet(),
        writeResources: Set<WritableVitalResource> = emptySet(),
    ): ActivityResultContract<Unit, Deferred<PermissionOutcome>>
        = VitalPermissionRequestContract(readResources, writeResources, this, taskScope)

    @Suppress("unused")
    fun hasAskedForPermission(resource: VitalResource): Boolean {
        return sharedPreferences.getBoolean(UnSecurePrefKeys.readResourceGrant(resource), false)
    }

    @Suppress("unused")
    fun hasAskedForPermission(resource: WritableVitalResource): Boolean {
        return sharedPreferences.getBoolean(UnSecurePrefKeys.writeResourceGrant(resource), false)
    }

    suspend fun checkAndUpdatePermissions() {
        if (isAvailable(context) != HealthConnectAvailability.Installed) {
            return
        }

        val currentGrants = getGrantedPermissions(context)

        val readResourcesByStatus = VitalResource.values()
            .groupBy { currentGrants.containsAll(permissionsRequiredToSyncResources(setOf(it))) }
        val writeResourcesByStatus = WritableVitalResource.values()
            .groupBy { currentGrants.containsAll(permissionsRequiredToWriteResources(setOf(it))) }

        sharedPreferences.edit().run {
            readResourcesByStatus.forEach { (hasGranted, resources) ->
                resources.forEach { putBoolean(UnSecurePrefKeys.readResourceGrant(it), hasGranted) }
            }
            writeResourcesByStatus.forEach { (hasGranted, resources) ->
                resources.forEach { putBoolean(UnSecurePrefKeys.writeResourceGrant(it), hasGranted) }
            }
            apply()
        }
    }

    internal fun permissionsRequiredToWriteResources(resources: Set<WritableVitalResource>): Set<String> {
        val recordTypes = mutableSetOf<KClass<out Record>>()

        for (resource in resources) {
            val records = when (resource) {
                WritableVitalResource.Water -> listOf(HydrationRecord::class)
                WritableVitalResource.Glucose -> listOf(BloodGlucoseRecord::class)
            }

            recordTypes.addAll(records)
        }

        return recordTypes.map { HealthPermission.getWritePermission(it) }.toSet()
    }

    internal fun permissionsRequiredToSyncResources(resources: Set<VitalResource>): Set<String> {
        val recordTypes = mutableSetOf<KClass<out Record>>()

        for (resource in resources) {
            val records = when (resource) {
                VitalResource.Water -> listOf(HydrationRecord::class)
                VitalResource.ActiveEnergyBurned -> listOf(ActiveCaloriesBurnedRecord::class)
                VitalResource.Activity -> listOf(
                    ActiveCaloriesBurnedRecord::class,
                    BasalMetabolicRateRecord::class,
                    StepsRecord::class,
                    DistanceRecord::class,
                    FloorsClimbedRecord::class,
                )
                VitalResource.BasalEnergyBurned -> listOf(BasalMetabolicRateRecord::class)
                VitalResource.BloodPressure -> listOf(BloodPressureRecord::class)
                VitalResource.Body -> listOf(
                    BodyFatRecord::class,
                    WeightRecord::class,
                )
                VitalResource.Glucose -> listOf(BloodGlucoseRecord::class)
                VitalResource.HeartRate -> listOf(HeartRateRecord::class)
                VitalResource.HeartRateVariability -> listOf(HeartRateVariabilityRmssdRecord::class)
                VitalResource.Profile -> listOf(HeightRecord::class)
                VitalResource.Sleep -> listOf(
                    SleepSessionRecord::class,
                    SleepStageRecord::class,
                )
                VitalResource.Steps -> listOf(StepsRecord::class)
                VitalResource.Workout -> listOf(
                    ExerciseSessionRecord::class,
                    HeartRateRecord::class,
                )
            }

            recordTypes.addAll(records)
        }

        return recordTypes.map { HealthPermission.getReadPermission(it) }.toSet()
    }

    @SuppressLint("ApplySharedPref")
    fun configureHealthConnectClient(
        logsEnabled: Boolean = false,
        syncOnAppStart: Boolean = true,
        numberOfDaysToBackFill: Int = 30,
    ) {
        if (!vitalClient.isConfigured) {
            throw IllegalStateException("VitalClient has not been configured.")
        }

        vitalLogger.enabled = logsEnabled

        sharedPreferences.edit()
            .putBoolean(UnSecurePrefKeys.loggerEnabledKey, logsEnabled)
            .putBoolean(UnSecurePrefKeys.syncOnAppStartKey, syncOnAppStart)
            .putInt(UnSecurePrefKeys.numberOfDaysToBackFillKey, numberOfDaysToBackFill)
            .commit()

        if (vitalClient.hasUserId() && isAvailable(context) == HealthConnectAvailability.Installed) {
            vitalLogger.logI("Configuration set, starting sync")
            taskScope.launch {
                syncData(healthResources)
            }
        }
    }

    suspend fun syncData(healthResource: Set<VitalResource> = healthResources) {
        val userId = vitalClient.checkUserId()

        try {
            currentSyncCall.acquire()

            // TODO: VIT-2924 Move userId management to VitalClient
            vitalClient.createConnectedSourceIfNotExist(ManualProviderSlug.HealthConnect, userId = userId)

            val changeToken = sharedPreferences.getString(UnSecurePrefKeys.changeTokenKey, null)

            if (changeToken == null) {
                vitalLogger.logI("No change token found, syncing all data")

                startWorkerForAllData(userId, healthResource)
            } else {
                val changes = try {
                    healthConnectClientProvider.getHealthConnectClient(context)
                        .getChanges(changeToken)
                } catch (e: Exception) {
                    vitalLogger.logE("Failed to get changes", e)
                    sharedPreferences.edit().remove(UnSecurePrefKeys.changeTokenKey).apply()
                    null
                }

                if (changes == null) {
                    vitalLogger.logI("Change token problem, syncing all data")
                    startWorkerForAllData(userId, healthResource)
                } else if (changes.changesTokenExpired) {
                    vitalLogger.logI("Changes token expired, reading all data")
                    startWorkerForAllData(userId, healthResource)
                } else if (changes.changes.isEmpty()) {
                    vitalLogger.logI("No changes to sync")
                    healthResources.map { _status.tryEmit(SyncStatus.ResourceNothingToSync(it)) }
                } else {
                    vitalLogger.logI("Syncing ${changes.changes.size} changes")
                    startWorkerForChanges(userId, healthResource)
                }
            }

        } catch (e: Exception) {
            vitalLogger.logE("Error syncing data", e)
            _status.tryEmit(SyncStatus.Unknown)
        } finally {
            currentSyncCall.release()
        }
    }

    suspend fun writeRecord(
        resource: WritableVitalResource,
        startDate: Instant,
        endDate: Instant,
        value: Double
    ) {
        val healthConnectClient = healthConnectClientProvider.getHealthConnectClient(context)

        when (resource) {
            WritableVitalResource.Water -> {
                healthConnectClient.insertRecords(
                    listOf(
                        HydrationRecord(
                            startTime = startDate,
                            startZoneOffset = ZoneOffset.UTC,
                            endTime = if (startDate <= endDate) startDate.plusSeconds(1) else endDate,
                            endZoneOffset = ZoneOffset.UTC,
                            volume = Volume.milliliters(value)
                        )
                    )
                )
            }
            WritableVitalResource.Glucose -> {
                healthConnectClient.insertRecords(
                    listOf(
                        BloodGlucoseRecord(
                            time = startDate,
                            zoneOffset = ZoneOffset.UTC,
                            level = BloodGlucose.milligramsPerDeciliter(value),
                        )
                    )
                )
            }
        }

        syncData()
    }

    @Suppress("unused")
    suspend fun read(
        resource: VitalResource,
        startDate: Instant,
        endDate: Instant
    ): ProcessedResourceData {
        val currentDevice = Build.MODEL

        suspend fun readActivities(): ProcessedResourceData {
            val activities = recordProcessor.processActivitiesFromRecords(
                startDate,
                endDate,
                TimeZone.getDefault(),
                currentDevice,
                recordReader.readActiveEnergyBurned(startDate, endDate),
                recordReader.readBasalMetabolicRate(startDate, endDate),
                recordReader.readSteps(startDate, endDate),
                recordReader.readDistance(startDate, endDate),
                recordReader.readFloorsClimbed(startDate, endDate),
                recordReader.readVo2Max(startDate, endDate),
            )
            return ProcessedResourceData.Summary(activities)
        }

        return when (resource) {
            VitalResource.ActiveEnergyBurned -> readActivities()
            VitalResource.BasalEnergyBurned -> readActivities()
            VitalResource.BloodPressure -> ProcessedResourceData.TimeSeries(
                recordProcessor.processBloodPressureFromRecords(
                    startDate,
                    endDate,
                    currentDevice,
                    recordReader.readBloodPressure(startDate, endDate)
                )
            )
            VitalResource.Glucose -> ProcessedResourceData.TimeSeries(
                recordProcessor.processGlucoseFromRecords(
                    startDate,
                    endDate,
                    currentDevice,
                    recordReader.readBloodGlucose(startDate, endDate)
                )
            )
            VitalResource.HeartRate -> ProcessedResourceData.TimeSeries(
                recordProcessor.processHeartRateFromRecords(
                    startDate,
                    endDate,
                    currentDevice,
                    recordReader.readHeartRate(startDate, endDate)
                )
            )
            VitalResource.Steps -> readActivities()
            VitalResource.Water -> ProcessedResourceData.TimeSeries(
                recordProcessor.processWaterFromRecords(
                    startDate,
                    endDate,
                    currentDevice,
                    recordReader.readHydration(startDate, endDate)
                )
            )
            VitalResource.Body -> ProcessedResourceData.Summary(
                recordProcessor.processBodyFromRecords(
                    startDate,
                    endDate,
                    currentDevice,
                    recordReader.readWeights(startDate, endDate),
                    recordReader.readBodyFat(startDate, endDate)
                )
            )
            VitalResource.Profile -> ProcessedResourceData.Summary(
                recordProcessor.processProfileFromRecords(
                    startDate,
                    endDate,
                    recordReader.readHeights(startDate, endDate)
                )
            )
            VitalResource.Sleep -> ProcessedResourceData.Summary(
                recordProcessor.processSleepFromRecords(
                    startDate,
                    endDate,
                    currentDevice,
                    recordReader.readSleepSession(startDate, endDate),
                    recordReader.readSleepStages(startDate, endDate),
                )
            )
            VitalResource.Activity -> readActivities()
            VitalResource.Workout -> ProcessedResourceData.Summary(
                recordProcessor.processWorkoutsFromRecords(
                    startDate,
                    endDate,
                    currentDevice,
                    recordReader.readExerciseSessions(startDate, endDate)
                )
            )
            VitalResource.HeartRateVariability -> ProcessedResourceData.TimeSeries(
                recordProcessor.processHeartRateVariabilityRmssFromRecords(
                    startDate,
                    endDate,
                    currentDevice,
                    recordReader.readHeartRateVariabilityRmssd(startDate, endDate)
                )
            )
        }
    }

    @Suppress("unused")
    fun resetChangeToken() {
        vitalLogger.logI("Resetting change token")
        sharedPreferences.edit().remove(UnSecurePrefKeys.changeTokenKey).apply()
    }

    private suspend fun startWorkerForChanges(userId: String, healthResource: Set<VitalResource>) {
        val workRequest = OneTimeWorkRequestBuilder<UploadChangesWorker>().setInputData(
            UploadChangesWorker.createInputData(
                userId = userId,
                region = vitalClient.region,
                environment = vitalClient.environment,
                apiKey = vitalClient.apiKey,
                resource = healthResource
            )
        ).build()

        return executeWork(workRequest, "UploadChangesWorker")
    }

    private suspend fun startWorkerForAllData(userId: String, healthResource: Set<VitalResource>) {
        val workRequest = OneTimeWorkRequestBuilder<UploadAllDataWorker>().setInputData(
            UploadAllDataWorker.createInputData(
                startTime = Instant.now().minus(
                    encryptedSharedPreferences.getInt(
                        UnSecurePrefKeys.numberOfDaysToBackFillKey, 30
                    ).toLong(), ChronoUnit.DAYS
                ),
                endTime = Instant.now(),
                userId = userId,
                region = vitalClient.region,
                environment = vitalClient.environment,
                apiKey = vitalClient.apiKey,
                resource = healthResource
            )
        ).build()

        return executeWork(workRequest, "UploadAllDataWorker")
    }

    /**
     * Execute the given work request.
     * Suspend until the work has succeeded, cancelled or failed.
     *
     * Worker status change will be mirrored to SDK sync status via [updateStatusFromJob].
     */
    private suspend fun executeWork(workRequest: OneTimeWorkRequest, uniqueWorkName: String) {
        val work = WorkManager.getInstance(context).beginUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        // Launch the work in `taskScope` so that it can be cancelled immediately when the whole
        // VitalHealthConnectManager is closed, independent of the caller of executeWork().
        val job = work.workInfosLiveData.asFlow()
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
            .onEach { updateStatusFromJob(listOf(it)) }
            .onStart { work.enqueue() }
            .flowOn(Dispatchers.Main)
            .launchIn(taskScope)

        // Wait for the job to complete.
        job.join()
    }

    private fun updateStatusFromJob(workInfos: List<WorkInfo>) {
        workInfos.forEach {
            when (it.state) {
                WorkInfo.State.RUNNING -> {
                    it.progress.getString(statusTypeKey)?.run {
                        val resource = VitalResource.valueOf(this)

                        when (it.progress.getString(syncStatusKey)!!) {
                            synced -> _status.tryEmit(
                                SyncStatus.ResourceSyncingComplete(
                                    resource
                                )
                            )
                            syncing -> _status.tryEmit(
                                SyncStatus.ResourceSyncing(
                                    resource
                                )
                            )
                            nothingToSync -> _status.tryEmit(
                                SyncStatus.ResourceNothingToSync(
                                    resource
                                )
                            )
                            else -> {
                                // Do nothing
                            }
                        }
                    }
                }
                WorkInfo.State.SUCCEEDED -> _status.tryEmit(SyncStatus.SyncingCompleted)
                WorkInfo.State.FAILED -> _status.tryEmit(SyncStatus.Unknown)
                WorkInfo.State.ENQUEUED,
                WorkInfo.State.BLOCKED,
                WorkInfo.State.CANCELLED -> {
                    //do nothing
                }
            }
        }
    }

    companion object {
        private const val packageName = "com.google.android.apps.healthdata"

        @Suppress("unused")
        fun isAvailable(context: Context): HealthConnectAvailability {
            return when (HealthConnectClient.sdkStatus(context, packageName)) {
                HealthConnectClient.SDK_UNAVAILABLE -> HealthConnectAvailability.NotSupportedSDK
                HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.Installed
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.NotInstalled
                else -> HealthConnectAvailability.NotSupportedSDK
            }
        }

        @Suppress("unused")
        fun openHealthConnect(context: Context) {
            when (isAvailable(context)) {
                HealthConnectAvailability.NotSupportedSDK -> {}
                HealthConnectAvailability.NotInstalled -> {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
                        setPackage("com.android.vending")
                    }
                    context.startActivity(intent)
                }
                HealthConnectAvailability.Installed -> {
                    context.startActivity(Intent(ACTION_HEALTH_CONNECT_SETTINGS))
                }
            }
        }

        fun create(
            context: Context,
            vitalClient: VitalClient
        ): VitalHealthConnectManager {
            val healthConnectClientProvider = HealthConnectClientProvider()

            return VitalHealthConnectManager(
                context,
                healthConnectClientProvider,
                vitalClient,
                HealthConnectRecordReader(context, healthConnectClientProvider),
                HealthConnectRecordProcessor(
                    HealthConnectRecordReader(context, healthConnectClientProvider),
                    HealthConnectRecordAggregator(context, healthConnectClientProvider),
                )
            )
        }
    }
}

