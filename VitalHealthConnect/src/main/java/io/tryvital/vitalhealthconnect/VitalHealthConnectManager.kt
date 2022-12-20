package io.tryvital.vitalhealthconnect

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import io.tryvital.client.Environment
import io.tryvital.client.Region
import io.tryvital.client.VitalClient
import io.tryvital.client.services.data.*
import io.tryvital.vitalhealthconnect.model.HealthConnectAvailability
import io.tryvital.vitalhealthconnect.model.SyncStatus
import io.tryvital.vitalhealthconnect.workers.UploadAllDataWorker
import io.tryvital.vitalhealthconnect.workers.UploadChangesWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.reflect.KClass

internal const val providerId = "health_connect"

private const val minSupportedSDK = Build.VERSION_CODES.P

private const val userIdKey = "userId"

internal val vitalRequiredRecordTypes = setOf(
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
)

internal fun vitalRecordTypes(healthPermission: Set<HealthPermission>): Set<KClass<out Record>> {
    return vitalRequiredRecordTypes.filter { recordType ->
        healthPermission.contains(HealthPermission.createReadPermission(recordType))
    }.toSet()
}

@Suppress("MemberVisibilityCanBePrivate")
class VitalHealthConnectManager private constructor(
    private val context: Context,
    private val healthConnectClientProvider: HealthConnectClientProvider,
    private val apiKey: String,
    private val region: Region,
    private val environment: Environment
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
        return healthConnectClientProvider.getHealthConnectClient(context)
            .permissionController.getGrantedPermissions(vitalRequiredPermissions)
    }

    @SuppressLint("ApplySharedPref")
    fun setUserId(userId: String) {
        encryptedSharedPreferences.edit().apply {
            putString(userIdKey, userId)
            remove(UnSecurePrefKeys.changeTokenKey)
            commit()
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
        checkUserId()

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

        syncData(getGrantedPermissions(context))
    }

    //TODO we should respect the requested permissions. It will always sync all the permitted data
    suspend fun syncData(@Suppress("UNUSED_PARAMETER") healthPermission: Set<HealthPermission>? = null) {
        val userId = checkUserId()
        _status.tryEmit(SyncStatus.Syncing)

        try {
            val changeToken = sharedPreferences.getString(UnSecurePrefKeys.changeTokenKey, null)

            if (changeToken == null) {
                vitalLogger.logI("No change token found, syncing all data")

                startWorkerForAllData(userId)

                _status.tryEmit(SyncStatus.SyncingCompleted)
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
                    startWorkerForAllData(userId)
                } else if (changes.changesTokenExpired) {
                    vitalLogger.logI("Changes token expired, reading all data")
                    startWorkerForAllData(userId)
                } else if (changes.changes.isEmpty()) {
                    vitalLogger.logI("No changes to sync")
                    _status.tryEmit(SyncStatus.NothingToSync)
                } else {
                    vitalLogger.logI("Syncing ${changes.changes.size} changes")
                    startWorkerForChanges(userId)
                }
            }
        } catch (e: Exception) {
            vitalLogger.logE("Error syncing data", e)
            _status.tryEmit(SyncStatus.FailedSyncing)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun startWorkerForChanges(userId: String) {
        val operation = WorkManager.getInstance(context).beginUniqueWork(
            "UploadChangesWorker",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<UploadChangesWorker>().setInputData(
                UploadChangesWorker.createInputData(
                    userId = userId,
                    region = vitalClient.region,
                    environment = vitalClient.environment,
                    apiKey = vitalClient.apiKey,
                )
            ).build()
        ).enqueue()

        observerJob?.cancel()
        observerJob = GlobalScope.launch(Dispatchers.Main) {
            operation.state.observeForever {
                when (it) {
                    is Operation.State.SUCCESS -> {
                        _status.tryEmit(SyncStatus.SyncingCompleted)
                        observerJob?.cancel()
                    }
                    is Operation.State.FAILURE -> {
                        _status.tryEmit(SyncStatus.FailedSyncing)
                        observerJob?.cancel()
                    }
                    is Operation.State.IN_PROGRESS -> {}
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun startWorkerForAllData(userId: String) {
        val operation = WorkManager.getInstance(context).beginUniqueWork(
            "UploadAllDataWorker",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<UploadAllDataWorker>().setInputData(
                UploadAllDataWorker.createInputData(
                    startTime = Instant.now().minus(
                        encryptedSharedPreferences.getInt(
                            UnSecurePrefKeys.numberOfDaysToBackFillKey,
                            30
                        )
                            .toLong(), ChronoUnit.DAYS
                    ),
                    endTime = Instant.now(),
                    userId = userId,
                    region = vitalClient.region,
                    environment = vitalClient.environment,
                    apiKey = vitalClient.apiKey,
                )
            ).build()
        ).enqueue()

        observerJob?.cancel()
        observerJob = GlobalScope.launch(Dispatchers.Main) {
            operation.state.observeForever {
                when (it) {
                    is Operation.State.SUCCESS -> {
                        _status.tryEmit(SyncStatus.SyncingCompleted)
                        observerJob?.cancel()
                    }
                    is Operation.State.FAILURE -> {
                        _status.tryEmit(SyncStatus.FailedSyncing)
                        observerJob?.cancel()
                    }
                    is Operation.State.IN_PROGRESS -> {}
                }
            }
        }
    }

    private fun checkUserId(): String {
        return encryptedSharedPreferences.getString(userIdKey, null) ?: throw IllegalStateException(
            "You need to call setUserId before you can read the health data"
        )
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
            )
        }

        val vitalRequiredPermissions =
            vitalRequiredRecordTypes.map { HealthPermission.createReadPermission(it) }.toSet()
    }
}

