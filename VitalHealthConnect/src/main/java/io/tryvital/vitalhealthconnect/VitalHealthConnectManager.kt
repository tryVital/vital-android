package io.tryvital.vitalhealthconnect

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Volume
import androidx.work.*
import io.tryvital.client.Environment
import io.tryvital.client.Region
import io.tryvital.client.VitalClient
import io.tryvital.client.createConnectedSource
import io.tryvital.client.services.data.*
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalhealthconnect.model.*
import io.tryvital.vitalhealthconnect.model.processedresource.ProcessedResourceData
import io.tryvital.vitalhealthconnect.records.*
import io.tryvital.vitalhealthconnect.workers.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
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
    private val apiKey: String,
    private val region: Region,
    private val environment: Environment,
    private val recordReader: RecordReader,
    private val recordProcessor: RecordProcessor,
) {
    private val vitalClient: VitalClient = VitalClient(context, region, environment, apiKey)

    private val encryptedSharedPreferences: SharedPreferences by lazy {
        try {
            createEncryptedSharedPreferences(context)
        } catch (e: Exception) {
            vitalLogger.logE(
                "Failed to decrypt shared preferences, creating new encrypted shared preferences", e
            )
            context.deleteSharedPreferences(encryptedPrefsFileName)
            return@lazy createEncryptedSharedPreferences(context)
        }
    }

    internal val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        prefsFileName, Context.MODE_PRIVATE
    )

    private val vitalLogger = vitalClient.vitalLogger

    private val _status = MutableSharedFlow<SyncStatus>(replay = 1)
    val status: SharedFlow<SyncStatus> = _status

    private val taskScope = CoroutineScope(Dispatchers.Default)
    private var observerJob: Job? = null

    init {
        vitalLogger.logI("VitalHealthConnectManager initialized")
        _status.tryEmit(SyncStatus.Unknown)

        taskScope.launch { checkAndUpdatePermissions() }
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
    suspend fun setUserId(userId: String) {
        encryptedSharedPreferences.edit().apply {
            putString(SecurePrefKeys.userIdKey, userId)
            remove(UnSecurePrefKeys.changeTokenKey)
            commit()
        }

        if (hasConfigSet()) {
            vitalLogger.logI("User ID set, starting sync")
            syncData(healthResources)
        }
    }

    @SuppressLint("ApplySharedPref")
    suspend fun configureHealthConnectClient(
        logsEnabled: Boolean = false,
        syncOnAppStart: Boolean = true,
        numberOfDaysToBackFill: Int = 30,
    ) {
        vitalLogger.enabled = logsEnabled

        encryptedSharedPreferences.edit()
            .putString(SecurePrefKeys.apiKeyKey, apiKey)
            .putString(SecurePrefKeys.regionKey, region.name)
            .putString(SecurePrefKeys.environmentKey, environment.name)
            .commit()

        sharedPreferences.edit()
            .putBoolean(UnSecurePrefKeys.loggerEnabledKey, logsEnabled)
            .putBoolean(UnSecurePrefKeys.syncOnAppStartKey, syncOnAppStart)
            .putInt(UnSecurePrefKeys.numberOfDaysToBackFillKey, numberOfDaysToBackFill)
            .commit()

        if (hasUserId()) {
            vitalLogger.logI("Configuration set, starting sync")
            syncData(healthResources)
        }
    }

    suspend fun syncData(healthResource: Set<VitalResource> = healthResources) {
        val userId = checkUserId()

        try {
            // TODO: VIT-2924 Move userId management to VitalClient
            // TODO: VIT-2944 VitalClient to keep track of created connected sources in SharedPreferences.
            vitalClient.createConnectedSource(ManualProviderSlug.HealthConnect, userId = userId)

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

    private fun startWorkerForChanges(userId: String, healthResource: Set<VitalResource>) {
        val work = WorkManager.getInstance(context).beginUniqueWork(
            "UploadChangesWorker",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<UploadChangesWorker>().setInputData(
                UploadChangesWorker.createInputData(
                    userId = userId,
                    region = vitalClient.region,
                    environment = vitalClient.environment,
                    apiKey = vitalClient.apiKey,
                    resource = healthResource
                )
            ).build()
        )

        observerJob?.cancel()
        observerJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                work.workInfosLiveData.observeForever { workInfos ->
                    updateStatusFromJob(workInfos)
                }
            }
            work.enqueue()
        }

        work.enqueue()
    }

    private fun startWorkerForAllData(userId: String, healthResource: Set<VitalResource>) {
        val work = WorkManager.getInstance(context).beginUniqueWork(
            "UploadAllDataWorker",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<UploadAllDataWorker>().setInputData(
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
        )

        observerJob?.cancel()
        observerJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                work.workInfosLiveData.observeForever { workInfos ->
                    updateStatusFromJob(workInfos)
                }
            }
            work.enqueue()
        }
    }

    private fun updateStatusFromJob(workInfos: MutableList<WorkInfo>) {
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

    private fun checkUserId(): String {
        return encryptedSharedPreferences.getString(SecurePrefKeys.userIdKey, null)
            ?: throw IllegalStateException(
                "You need to call setUserId before you can read the health data"
            )
    }

    private fun hasUserId(): Boolean {
        return encryptedSharedPreferences.getString(SecurePrefKeys.userIdKey, null) != null
    }

    private fun hasConfigSet(): Boolean {
        return encryptedSharedPreferences.getString(SecurePrefKeys.apiKeyKey, null) != null &&
                encryptedSharedPreferences.getString(SecurePrefKeys.regionKey, null) != null &&
                encryptedSharedPreferences.getString(SecurePrefKeys.environmentKey, null) != null
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

        fun create(
            context: Context,
            apiKey: String,
            region: Region,
            environment: Environment,
        ): VitalHealthConnectManager {
            val healthConnectClientProvider = HealthConnectClientProvider()

            return VitalHealthConnectManager(
                context,
                healthConnectClientProvider,
                apiKey,
                region,
                environment,
                HealthConnectRecordReader(context, healthConnectClientProvider),
                HealthConnectRecordProcessor(
                    HealthConnectRecordReader(context, healthConnectClientProvider),
                    HealthConnectRecordAggregator(context, healthConnectClientProvider),
                )
            )
        }
    }
}

