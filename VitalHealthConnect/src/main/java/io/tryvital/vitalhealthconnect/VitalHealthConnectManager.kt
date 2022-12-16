package io.tryvital.vitalhealthconnect

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.Change
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.response.ChangesResponse
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import io.tryvital.client.VitalClient
import io.tryvital.client.services.data.*
import io.tryvital.vitalhealthconnect.ext.toDate
import io.tryvital.vitalhealthconnect.model.HealthConnectAvailability
import io.tryvital.vitalhealthconnect.model.SyncStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

private const val minSupportedSDK = Build.VERSION_CODES.P
private const val providerId = "health_connect"
private const val encryptedPrefsFileName: String = "vital_health_connect_prefs"

private const val userIdKey = "userId"
private const val changeTokenKey = "changeToken"
private const val numberOfDaysToBackFillKey = "numberOfDaysToBackFill"

private const val stage = "daily"

@Suppress("MemberVisibilityCanBePrivate")
class VitalHealthConnectManager private constructor(
    private val healthConnectClientProvider: HealthConnectClientProvider,
    private val recordProcessor: RecordProcessor,
    private val vitalClient: VitalClient,
    private val context: Context
) {
    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        encryptedPrefsFileName,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    val recordTypes = setOf(
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

    val requiredPermissions = recordTypes.map { HealthPermission.createReadPermission(it) }.toSet()

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
        return requiredPermissions == healthConnectClientProvider.getHealthConnectClient(context).permissionController.getGrantedPermissions(
            requiredPermissions
        )
    }

    @SuppressLint("ApplySharedPref")
    fun setUserId(userId: String) {
        val edit = sharedPreferences.edit()
        edit.putString(userIdKey, userId)
        edit.remove(changeTokenKey)
        edit.commit()
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
        numberOfDaysToBackFill: Int = 1,
    ) {
        checkUserId()

        sharedPreferences.edit().putInt(numberOfDaysToBackFillKey, numberOfDaysToBackFill).commit()
        vitalLogger.enabled = logsEnabled
        syncData()
    }

    suspend fun syncData() {
        checkUserId()
        _status.tryEmit(SyncStatus.Syncing)

        try {
            val changeToken = sharedPreferences.getString(changeTokenKey, null)

            if (changeToken == null) {
                vitalLogger.logI("No change token found, syncing all data")

                readAndUploadHealthData(
                    startTime = Instant.now().minus(
                        sharedPreferences.getInt(numberOfDaysToBackFillKey, 30).toLong(),
                        ChronoUnit.DAYS
                    ),
                    endTime = Instant.now()
                )

                _status.tryEmit(SyncStatus.SyncingCompleted)
            } else {
                val changes =
                    healthConnectClientProvider.getHealthConnectClient(context)
                        .getChanges(changeToken)

                if (changes.changesTokenExpired) {
                    vitalLogger.logI("Changes token expired, reading all data")

                    readAndUploadHealthData(
                        startTime = Instant.now().minus(
                            sharedPreferences.getInt(numberOfDaysToBackFillKey, 30).toLong(),
                            ChronoUnit.DAYS
                        ),
                        endTime = Instant.now()
                    )

                    _status.tryEmit(SyncStatus.SyncingCompleted)
                } else if (changes.changes.isEmpty()) {
                    vitalLogger.logI("No changes to sync")
                    _status.tryEmit(SyncStatus.NothingToSync)
                } else {
                    vitalLogger.logI("Syncing ${changes.changes.size} changes")

                    var currentChanges: ChangesResponse? = changes
                    while (currentChanges != null) {
                        readAndUploadHealthDataFromChange(changes.changes)

                        if (changes.hasMore) {
                            currentChanges =
                                healthConnectClientProvider.getHealthConnectClient(context)
                                    .getChanges(changes.nextChangesToken)
                            sharedPreferences.edit()
                                .putString(changeTokenKey, changes.nextChangesToken)
                                .apply()
                        } else {
                            currentChanges = null
                        }
                    }

                    _status.tryEmit(SyncStatus.SyncingCompleted)
                }
            }

            vitalLogger.logI("Sync complete")

            sharedPreferences.edit().putString(
                changeTokenKey,
                healthConnectClientProvider.getHealthConnectClient(context)
                    .getChangesToken(ChangesTokenRequest(recordTypes))
            ).apply()
        } catch (e: Exception) {
            vitalLogger.logE("Error syncing data", e.message, e)
            _status.tryEmit(SyncStatus.FailedSyncing)
        }
    }

    /**
     * We are ignoring deletions for now
     */
    private suspend fun readAndUploadHealthDataFromChange(changes: List<Change>) {
        val userId = checkUserId()
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
            vitalLogger.logI("Reading ${change.record} data")
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

            uploadSleeps(
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

            uploadWorkouts(
                userId, exercisesStartTime.toDate(), exercisesEndTime.toDate(), timeZoneId,
                recordProcessor.processWorkoutsFromRecords(
                    exercisesStartTime, exercisesEndTime, currentDevice, exercises
                )
            )
        }

        if (heights.isNotEmpty()) {
            val height = heights.last()

            uploadProfile(
                userId, height.time.toDate(), height.time.toDate(), timeZoneId,
                recordProcessor.processProfileFromRecords(height.time, height.time, height)
            )
        }

        if (bodyFats.isNotEmpty() || weights.isNotEmpty()) {
            val bodyStartTime = bodyFats.map { it.time }.plus(weights.map { it.time }).minOf { it }
            val bodyEndTime = bodyFats.map { it.time }.plus(weights.map { it.time }).maxOf { it }

            uploadBody(
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

            uploadActivities(
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

    private suspend fun readAndUploadHealthData(
        startTime: Instant,
        endTime: Instant,
    ) {
        val userId = checkUserId()

        val currentDevice = Build.MODEL
        val startDate = startTime.toDate()
        val endDate = endTime.toDate()
        val hostTimeZone = TimeZone.getDefault()
        val timeZoneId = hostTimeZone.id

        uploadWorkouts(
            userId, startDate, endDate, timeZoneId,
            recordProcessor.processWorkoutsFromTimeRange(startTime, endTime, currentDevice),
        )

        uploadActivities(
            userId, startDate, endDate, timeZoneId,
            recordProcessor.processActivitiesFromTimeRange(
                startTime,
                endTime,
                currentDevice,
            ),
        )

        uploadProfile(
            userId, startDate, endDate, timeZoneId,
            recordProcessor.processProfileFromTimeRange(startTime, endTime)
        )

        uploadBody(
            userId, startDate, endDate, timeZoneId,
            recordProcessor.processBodyFromTimeRange(startTime, endTime, currentDevice)
        )

        uploadSleeps(
            userId, startDate, endDate, timeZoneId,
            recordProcessor.processSleepFromTimeRange(startTime, endTime, currentDevice),
        )
    }

    private suspend fun uploadSleeps(
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        sleepPayloads: List<SleepPayload>,
    ) {
        if (sleepPayloads.isNotEmpty()) {
            vitalClient.summaryService.addSleeps(
                userId, SummaryPayload(
                    stage = stage,
                    provider = providerId,
                    startDate = startDate,
                    endDate = endDate,
                    timeZoneId = timeZoneId,
                    data = sleepPayloads,
                )
            )
        }
    }

    private suspend fun uploadBody(
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        bodyPayload: BodyPayload
    ) {
        vitalClient.summaryService.addBody(
            userId, SummaryPayload(
                stage = stage,
                provider = providerId,
                startDate = startDate,
                endDate = endDate,
                timeZoneId = timeZoneId,
                data = bodyPayload
            )
        )
    }

    private suspend fun uploadProfile(
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        profilePayload: ProfilePayload
    ) {
        vitalClient.summaryService.addProfile(
            userId, SummaryPayload(
                stage = stage,
                provider = providerId,
                startDate = startDate,
                endDate = endDate,
                timeZoneId = timeZoneId,
                data = profilePayload
            )
        )
    }

    private suspend fun uploadActivities(
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        activityPayloads: List<ActivityPayload>,
    ) {
        if (activityPayloads.isNotEmpty()) {
            vitalClient.summaryService.addActivities(
                userId, SummaryPayload(
                    stage = stage,
                    provider = providerId,
                    startDate = startDate,
                    endDate = endDate,
                    timeZoneId = timeZoneId,
                    data = activityPayloads,
                )
            )
        }
    }

    private suspend fun uploadWorkouts(
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        workoutPayloads: List<WorkoutPayload>,
    ) {
        if (workoutPayloads.isNotEmpty()) {
            vitalClient.summaryService.addWorkouts(
                userId, SummaryPayload(
                    stage = stage,
                    provider = providerId,
                    startDate = startDate,
                    endDate = endDate,
                    timeZoneId = timeZoneId,
                    data = workoutPayloads,
                )
            )
        }
    }

    private fun checkUserId(): String {
        return sharedPreferences.getString(userIdKey, null)
            ?: throw IllegalStateException("You need to call setUserId before you can read the health data")
    }

    companion object {
        fun create(
            context: Context, vitalClient: VitalClient
        ): VitalHealthConnectManager {
            val healthConnectClientProvider = HealthConnectClientProvider()

            return VitalHealthConnectManager(
                healthConnectClientProvider,
                HealthConnectRecordProcessor(
                    HealthConnectRecordReader(context, healthConnectClientProvider),
                    HealthConnectRecordAggregator(context, healthConnectClientProvider),
                    vitalClient
                ),
                vitalClient,
                context
            )
        }
    }
}

