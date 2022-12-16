package io.tryvital.vitalhealthconnect

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import io.tryvital.client.VitalClient
import io.tryvital.client.services.data.*
import io.tryvital.vitalhealthconnect.model.HealthConnectAvailability
import io.tryvital.vitalhealthconnect.model.SyncStatus
import io.tryvital.vitalhealthconnect.workers.UploadAllDataWorker
import io.tryvital.vitalhealthconnect.workers.UploadChangesWorker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

internal const val providerId = "health_connect"
internal const val prefsFileName: String = "vital_health_connect_prefs"
internal const val changeTokenKey = "changeToken"

private const val minSupportedSDK = Build.VERSION_CODES.P

private const val userIdKey = "userId"
private const val numberOfDaysToBackFillKey = "numberOfDaysToBackFill"
private const val encryptedPrefsFileName: String = "safe_vital_health_connect_prefs"

internal val vitalRecordTypes = setOf(
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

val vitalRequiredPermissions =
    vitalRecordTypes.map { HealthPermission.createReadPermission(it) }.toSet()

@Suppress("MemberVisibilityCanBePrivate")
class VitalHealthConnectManager private constructor(
    private val healthConnectClientProvider: HealthConnectClientProvider,
    private val vitalClient: VitalClient,
    private val context: Context
) {
    private val encryptedSharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        encryptedPrefsFileName,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        prefsFileName,
        Context.MODE_PRIVATE
    )

    private val vitalLogger = vitalClient.vitalLogger

    private val _status = MutableSharedFlow<SyncStatus>(replay = 1)
    val status: SharedFlow<SyncStatus> = _status

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

    suspend fun hasAllPermissions(context: Context): Boolean {
        return vitalRequiredPermissions == healthConnectClientProvider.getHealthConnectClient(
            context
        ).permissionController.getGrantedPermissions(
            vitalRequiredPermissions
        )
    }

    @SuppressLint("ApplySharedPref")
    fun setUserId(userId: String) {
        encryptedSharedPreferences.edit().apply {
            putString(userIdKey, userId)
            remove(changeTokenKey)
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
        numberOfDaysToBackFill: Int = 30,
    ) {
        checkUserId()

        encryptedSharedPreferences.edit().putInt(numberOfDaysToBackFillKey, numberOfDaysToBackFill)
            .commit()
        vitalLogger.enabled = logsEnabled
        syncData()
    }

    suspend fun syncData() {
        val userId = checkUserId()
        _status.tryEmit(SyncStatus.Syncing)

        try {
            val changeToken = sharedPreferences.getString(changeTokenKey, null)

            if (changeToken == null) {
                vitalLogger.logI("No change token found, syncing all data")

                startWorkerForAllData(userId)

                _status.tryEmit(SyncStatus.SyncingCompleted)
            } else {
                val changes =
                    healthConnectClientProvider.getHealthConnectClient(context)
                        .getChanges(changeToken)

                if (changes.changesTokenExpired) {
                    vitalLogger.logI("Changes token expired, reading all data")

                    startWorkerForAllData(userId)

                    _status.tryEmit(SyncStatus.SyncingCompleted)
                } else if (changes.changes.isEmpty()) {
                    vitalLogger.logI("No changes to sync")
                    _status.tryEmit(SyncStatus.NothingToSync)
                } else {
                    vitalLogger.logI("Syncing ${changes.changes.size} changes")

                    startWorkerForChanges(userId)
                    vitalLogger.logI("aaaaaa")
                }
            }
        } catch (e: Exception) {
            vitalLogger.logE("Error syncing data", e.message, e)
            _status.tryEmit(SyncStatus.FailedSyncing)
        }
    }

    private fun startWorkerForChanges(userId: String) {
        val operation = WorkManager
            .getInstance(context)
            .beginUniqueWork(
                "UploadChangesWorker",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<UploadChangesWorker>()
                    .setInputData(
                        UploadChangesWorker.createInputData(
                            userId = userId,
                            region = vitalClient.region,
                            environment = vitalClient.environment,
                            apiKey = vitalClient.apiKey,
                        )
                    )
                    .build()
            )
            .enqueue()


        operation.state.observeForever {
            when (it) {
                is Operation.State.SUCCESS -> {
                    _status.tryEmit(SyncStatus.SyncingCompleted)
                }
                is Operation.State.FAILURE -> {
                    _status.tryEmit(SyncStatus.FailedSyncing)
                }
                is Operation.State.IN_PROGRESS -> {}
            }
        }
    }

    private fun startWorkerForAllData(userId: String) {
        val operation = WorkManager
            .getInstance(context)
            .enqueue(
                OneTimeWorkRequestBuilder<UploadAllDataWorker>()
                    .setInputData(
                        UploadAllDataWorker.createInputData(
                            startTime = Instant.now().minus(
                                encryptedSharedPreferences.getInt(numberOfDaysToBackFillKey, 30)
                                    .toLong(),
                                ChronoUnit.DAYS
                            ),
                            endTime = Instant.now(),
                            userId = userId,
                            region = vitalClient.region,
                            environment = vitalClient.environment,
                            apiKey = vitalClient.apiKey,
                        )
                    )
                    .build()
            )

        operation.state.observeForever {
            when (it) {
                is Operation.State.SUCCESS -> {
                    _status.tryEmit(SyncStatus.SyncingCompleted)
                }
                is Operation.State.FAILURE -> {
                    _status.tryEmit(SyncStatus.FailedSyncing)
                }
                is Operation.State.IN_PROGRESS -> {}
            }
        }
    }

    private fun checkUserId(): String {
        return encryptedSharedPreferences.getString(userIdKey, null)
            ?: throw IllegalStateException("You need to call setUserId before you can read the health data")
    }

    companion object {
        fun create(
            context: Context, vitalClient: VitalClient
        ): VitalHealthConnectManager {
            val healthConnectClientProvider = HealthConnectClientProvider()

            return VitalHealthConnectManager(
                healthConnectClientProvider,
                vitalClient,
                context
            )
        }
    }
}

