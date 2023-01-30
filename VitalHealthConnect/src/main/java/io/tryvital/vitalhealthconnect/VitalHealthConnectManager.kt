package io.tryvital.vitalhealthconnect

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Volume
import androidx.work.*
import io.tryvital.client.Environment
import io.tryvital.client.Region
import io.tryvital.client.VitalClient
import io.tryvital.client.services.data.*
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

internal const val providerId = "health_connect"

private const val minSupportedSDK = Build.VERSION_CODES.P

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
    OxygenSaturationRecord::class,
    HeartRateVariabilitySdnnRecord::class,
    RestingHeartRateRecord::class,
    ActiveCaloriesBurnedRecord::class,
    BasalMetabolicRateRecord::class,
    StepsRecord::class,
    DistanceRecord::class,
    FloorsClimbedRecord::class,
    HydrationRecord::class,
    BloodGlucoseRecord::class,
    BloodPressureRecord::class,
    HeartRateRecord::class,
)

internal val writeRecordTypes = setOf(
    HydrationRecord::class,
    BloodGlucoseRecord::class,
)

internal fun vitalRecordTypes(healthPermission: Set<HealthPermission>): Set<KClass<out Record>> {
    return readRecordTypes.filter { recordType ->
        healthPermission.contains(HealthPermission.createReadPermission(recordType))
    }.toSet()
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

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        prefsFileName, Context.MODE_PRIVATE
    )

    private val vitalLogger = vitalClient.vitalLogger

    private val _status = MutableSharedFlow<SyncStatus>(replay = 1)
    val status: SharedFlow<SyncStatus> = _status

    private var observerJob: Job? = null

    init {
        vitalLogger.logI("VitalHealthConnectManager initialized")
        _status.tryEmit(SyncStatus.Unknown)
    }

    fun isAvailable(context: Context): HealthConnectAvailability {
        return when {
            Build.VERSION.SDK_INT < minSupportedSDK -> HealthConnectAvailability.NotSupportedSDK
            HealthConnectClient.isProviderAvailable(context) -> HealthConnectAvailability.Installed
            else -> HealthConnectAvailability.NotInstalled
        }
    }

    suspend fun getGrantedPermissions(context: Context): Set<HealthPermission> {
        return healthConnectClientProvider.getHealthConnectClient(context).permissionController.getGrantedPermissions(
            vitalRequiredPermissions
        )
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

    suspend fun linkUserHealthConnectProvider(callbackURI: String) {
        val userId = checkUserId()

        val token = vitalClient.linkService.createLink(
            CreateLinkRequest(
                userId, providerId, callbackURI
            )
        )

        vitalClient.linkService.manualProvider(
            provider = providerId, linkToken = token.linkToken!!, ManualProviderRequest(
                userId = userId, providerId = providerId
            )
        )
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

    suspend fun syncData(healthResource: Set<HealthResource> = healthResources) {
        val userId = checkUserId()

        try {
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

    suspend fun addHealthResource(
        resource: HealthResource,
        startDate: Instant,
        endDate: Instant,
        value: Double
    ) {
        val healthConnectClient = healthConnectClientProvider.getHealthConnectClient(context)

        when (resource) {
            HealthResource.Water -> {
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
            HealthResource.Glucose -> {
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
            HealthResource.ActiveEnergyBurned,
            HealthResource.Activity,
            HealthResource.BasalEnergyBurned,
            HealthResource.BloodPressure,
            HealthResource.Body,
            HealthResource.HeartRate,
            HealthResource.Profile,
            HealthResource.Sleep,
            HealthResource.Steps,
            HealthResource.Workout -> {
                vitalLogger.logI("Not supported resource $resource")
            }
        }

        syncData()
    }

    @Suppress("unused")
    suspend fun read(
        resource: HealthResource,
        startDate: Instant,
        endDate: Instant
    ): ProcessedResourceData {
        val currentDevice = Build.MODEL

        return when (resource) {
            HealthResource.ActiveEnergyBurned -> ProcessedResourceData.Summary(
                recordProcessor.processActivitiesFromRecords(
                    startDate,
                    endDate,
                    currentDevice,
                    recordReader.readActiveEnergyBurned(startDate, endDate),
                    recordReader.readBasalMetabolicRate(startDate, endDate),
                    recordReader.readSteps(startDate, endDate),
                    recordReader.readDistance(startDate, endDate),
                    recordReader.readFloorsClimbed(startDate, endDate),
                    recordReader.readVo2Max(startDate, endDate),
                )
            )
            HealthResource.BasalEnergyBurned -> ProcessedResourceData.Summary(
                recordProcessor.processActivitiesFromRecords(
                    startDate,
                    endDate,
                    currentDevice,
                    recordReader.readActiveEnergyBurned(startDate, endDate),
                    recordReader.readBasalMetabolicRate(startDate, endDate),
                    recordReader.readSteps(startDate, endDate),
                    recordReader.readDistance(startDate, endDate),
                    recordReader.readFloorsClimbed(startDate, endDate),
                    recordReader.readVo2Max(startDate, endDate),
                )
            )
            HealthResource.BloodPressure -> ProcessedResourceData.TimeSeries(
                recordProcessor.processBloodPressureFromRecords(
                    startDate,
                    endDate,
                    currentDevice,
                    recordReader.readBloodPressure(startDate, endDate)
                )
            )
            HealthResource.Glucose -> ProcessedResourceData.TimeSeries(
                recordProcessor.processGlucoseFromRecords(
                    startDate,
                    endDate,
                    currentDevice,
                    recordReader.readBloodGlucose(startDate, endDate)
                )
            )
            HealthResource.HeartRate -> ProcessedResourceData.TimeSeries(
                recordProcessor.processHeartRateFromRecords(
                    startDate,
                    endDate,
                    currentDevice,
                    recordReader.readHeartRate(startDate, endDate)
                )
            )
            HealthResource.Steps -> ProcessedResourceData.Summary(
                recordProcessor.processActivitiesFromRecords(
                    startDate,
                    endDate,
                    currentDevice,
                    recordReader.readActiveEnergyBurned(startDate, endDate),
                    recordReader.readBasalMetabolicRate(startDate, endDate),
                    recordReader.readSteps(startDate, endDate),
                    recordReader.readDistance(startDate, endDate),
                    recordReader.readFloorsClimbed(startDate, endDate),
                    recordReader.readVo2Max(startDate, endDate),
                )
            )
            HealthResource.Water -> ProcessedResourceData.TimeSeries(
                recordProcessor.processWaterFromRecords(
                    startDate,
                    endDate,
                    currentDevice,
                    recordReader.readHydration(startDate, endDate)
                )
            )
            HealthResource.Body -> ProcessedResourceData.Summary(
                recordProcessor.processBodyFromRecords(
                    startDate,
                    endDate,
                    currentDevice,
                    recordReader.readWeights(startDate, endDate),
                    recordReader.readBodyFat(startDate, endDate)
                )
            )
            HealthResource.Profile -> ProcessedResourceData.Summary(
                recordProcessor.processProfileFromRecords(
                    startDate,
                    endDate,
                    recordReader.readHeights(startDate, endDate)
                )
            )
            HealthResource.Sleep -> ProcessedResourceData.Summary(
                recordProcessor.processSleepFromRecords(
                    startDate,
                    endDate,
                    currentDevice,
                    recordReader.readSleepSession(startDate, endDate)
                )
            )
            HealthResource.Activity -> ProcessedResourceData.Summary(
                recordProcessor.processActivitiesFromRecords(
                    startDate,
                    endDate,
                    currentDevice,
                    recordReader.readActiveEnergyBurned(startDate, endDate),
                    recordReader.readBasalMetabolicRate(startDate, endDate),
                    recordReader.readSteps(startDate, endDate),
                    recordReader.readDistance(startDate, endDate),
                    recordReader.readFloorsClimbed(startDate, endDate),
                    recordReader.readVo2Max(startDate, endDate),
                )
            )
            HealthResource.Workout -> ProcessedResourceData.Summary(
                recordProcessor.processWorkoutsFromRecords(
                    startDate,
                    endDate,
                    currentDevice,
                    recordReader.readExerciseSessions(startDate, endDate)
                )
            )
        }
    }

    @Suppress("unused")
    fun resetChangeToken() {
        vitalLogger.logI("Resetting change token")
        sharedPreferences.edit().remove(UnSecurePrefKeys.changeTokenKey).apply()
    }

    private fun startWorkerForChanges(userId: String, healthResource: Set<HealthResource>) {
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

    private fun startWorkerForAllData(userId: String, healthResource: Set<HealthResource>) {
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
                        val resource = HealthResource.valueOf(this)

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

        val vitalRequiredPermissions =
            readRecordTypes.map { HealthPermission.createReadPermission(it) }.toSet()
                .plus(writeRecordTypes.map { HealthPermission.createWritePermission(it) })
    }
}

