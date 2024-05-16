package io.tryvital.vitalhealthconnect.workers

import UserSDKSyncStateBody
import UserSDKSyncStateResponse
import UserSDKSyncStatus
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.response.ChangesResponse
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import io.tryvital.client.VitalClient
import io.tryvital.client.services.VitalPrivateApi
import io.tryvital.client.services.data.DataStage
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalhealthconnect.HealthConnectClientProvider
import io.tryvital.vitalhealthconnect.UnSecurePrefKeys
import io.tryvital.vitalhealthconnect.ext.toDate
import io.tryvital.vitalhealthconnect.model.VitalResource
import io.tryvital.vitalhealthconnect.model.processedresource.ProcessedResourceData
import io.tryvital.vitalhealthconnect.model.processedresource.merged
import io.tryvital.vitalhealthconnect.model.recordTypeChangesToTriggerSync
import io.tryvital.vitalhealthconnect.records.HealthConnectRecordAggregator
import io.tryvital.vitalhealthconnect.records.HealthConnectRecordProcessor
import io.tryvital.vitalhealthconnect.records.HealthConnectRecordReader
import io.tryvital.vitalhealthconnect.records.RecordProcessor
import io.tryvital.vitalhealthconnect.records.RecordReader
import io.tryvital.vitalhealthconnect.records.RecordUploader
import io.tryvital.vitalhealthconnect.records.VitalClientRecordUploader
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.TimeZone
import kotlin.reflect.KClass

const val VITAL_SYNC_NOTIFICATION_ID = 123

internal val moshi by lazy {
    Moshi.Builder()
        .add(Date::class.java, Rfc3339DateJsonAdapter())
        .add(ResourceSyncState.adapterFactory)
        .build()
}

internal data class ResourceSyncWorkerInput(
    val resource: VitalResource,
) {
    fun toData(): Data = Data.Builder().run {
        putString("resource", resource.toString())
        build()
    }

    companion object {
        fun fromData(data: Data) = ResourceSyncWorkerInput(
            resource = VitalResource.valueOf(
                data.getString("resource") ?: throw IllegalArgumentException("Missing resource")
            ),
        )
    }
}

@JsonClass(generateAdapter = false)
internal sealed class ResourceSyncState {
    @JsonClass(generateAdapter = true)
    data class Historical(val start: Date, val end: Date) : ResourceSyncState() {
        override fun toString(): String = "historical(${start.toInstant()} ..< ${end.toInstant()})"
    }

    @JsonClass(generateAdapter = true)
    data class Incremental(val changesToken: String, val lastSync: Date, val end: Date? = null) :
        ResourceSyncState() {
        override fun toString(): String =
            "incremental($changesToken at ${lastSync.toInstant()}; end = ${end})"
    }

    companion object {
        val adapterFactory: PolymorphicJsonAdapterFactory<ResourceSyncState>
            get() = PolymorphicJsonAdapterFactory.of(ResourceSyncState::class.java, "type")
                .withSubtype(Historical::class.java, "historical")
                .withSubtype(Incremental::class.java, "incremental")
    }
}

internal class ResourceSyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val input: ResourceSyncWorkerInput by lazy {
        ResourceSyncWorkerInput.fromData(inputData)
    }

    private val vitalClient: VitalClient by lazy {
        VitalClient.getOrCreate(applicationContext)
    }
    private val sharedPreferences: SharedPreferences get() = vitalClient.sharedPreferences
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

    /**
     * Each instance of ResourceSyncWorker is responsible to sync one specific VitalResource type.
     * Sync state of each VitalResource type is stored and advanced independently.
     *
     * This separation enables the SDK to function with only partial permission grants (e.g., due to
     * resource types not needed by the customers, permissions not having been requested, or user
     * having revoked the permission).
     *
     * ## Generic Backfill process
     * generic_backfill(data_stage, start, end)
     * 1. Fetch initial changes token T_0.
     * 2. Fetch data given `[start, end)`
     * 3. Fetch any changes since T_0, and receive a new token T_1.
     * 4. Upload all fetched data with `stage=${data_stage}`
     * 5. Store (end, token T_1) as ResourceSyncState.Incremental.
     *
     * ## Historical stage
     * i.e. state is ResourceSyncState.Historical
     *
     * 1. `generic_backfill(stage="historical", start=now() - N days, end=now())`
     *
     * ## Daily/Incremental stage
     * i.e. state is ResourceSyncState.Incremental
     *
     * 1. Fetch any changes since `state.changesToken`.
     * 2. If fetch is successful:
     *     a. Upload all fetched data with `stage=daily`
     *     b. Store (now, token T_n) as ResourceSyncState.Incremental.
     * 3. If fetch has failed because e.g., token has expired:
     *    https://developer.android.com/guide/health-and-fitness/health-connect/data-and-data-types/differential-changes-api#integrating_with_the_differential_changes_api
     *    https://developer.android.com/guide/health-and-fitness/health-connect/common-workflows/sync-data#practical_considerations
     *     a. Fetch the maximum timestamp of the resource type as `max`.
     *     b. `generic_backfill(stage="daily", start=max(), end=now())`
     */
    override suspend fun doWork(): Result {
        val timeZone = TimeZone.getDefault()
        val state = sharedPreferences.getJson(input.resource.syncStateKey)
            ?: initialState(timeZone)

        val backendState = fetchBackendSyncState(state, timeZone)

        val reconciledState = when (backendState.status) {
            UserSDKSyncStatus.Paused, UserSDKSyncStatus.Error -> {
                vitalLogger.logI("${input.resource}: skipped because backend status is ${backendState.status}")
                return Result.success()
            }

            UserSDKSyncStatus.Active -> when (state) {
                // Prefer backend provided pull range if present.
                is ResourceSyncState.Historical -> state.copy(
                    start = backendState.requestStartDate ?: state.start,
                    end = backendState.requestEndDate ?: state.end,
                )

                is ResourceSyncState.Incremental -> state.copy(
                    end = backendState.requestEndDate
                )
            }
        }

        vitalLogger.logI("${input.resource}: begin at $state")

        when (reconciledState) {
            is ResourceSyncState.Historical -> historicalBackfill(reconciledState, timeZone)
            is ResourceSyncState.Incremental -> incrementalBackfill(reconciledState, timeZone)
        }

        // TODO: Report synced vs nothing to sync
        return Result.success()
    }

    private fun initialState(timeZone: TimeZone): ResourceSyncState {
        val now = ZonedDateTime.now(timeZone.toZoneId())
        val start = now.minus(30, ChronoUnit.DAYS)
        return ResourceSyncState.Historical(
            start = start.toInstant().toDate(),
            end = now.toInstant().toDate()
        )
    }

    @OptIn(VitalPrivateApi::class)
    private suspend fun fetchBackendSyncState(
        state: ResourceSyncState,
        timeZone: TimeZone
    ): UserSDKSyncStateResponse {
        val backendState = vitalClient.vitalPrivateService.healthConnectSdkSyncState(
            vitalClient.checkUserId(),
            when (state) {
                is ResourceSyncState.Historical -> UserSDKSyncStateBody(
                    stage = DataStage.Historical,
                    tzinfo = timeZone.id,
                    requestStartDate = state.start,
                    requestEndDate = state.end,
                )

                is ResourceSyncState.Incremental -> UserSDKSyncStateBody(
                    stage = DataStage.Daily,
                    tzinfo = timeZone.id,
                    requestStartDate = null,
                    requestEndDate = null,
                )
            },
        )
        vitalLogger.info { "BackendSyncState: fetched $backendState" }
        return backendState
    }

    private suspend fun historicalBackfill(
        state: ResourceSyncState.Historical,
        timeZone: TimeZone
    ) {
        genericBackfill(
            stage = DataStage.Historical,
            start = state.start.toInstant(),
            end = state.end.toInstant(),
            timeZone = timeZone,
        )
    }

    private suspend fun incrementalBackfill(
        state: ResourceSyncState.Incremental,
        timeZone: TimeZone
    ) {
        val userId = vitalClient.checkUserId()
        val client = healthConnectClientProvider.getHealthConnectClient(applicationContext)

        val recordTypesToMonitor = recordTypesToMonitor().toSimpleNameSet()
        val monitoringTypes = monitoringRecordTypes()

        // The types being monitored by the current `changesToken` no longer match the set
        // we want to monitor, probably due to permission changes.
        // Treat this as if the changesToken has expired.
        if (recordTypesToMonitor != monitoringTypes) {
            vitalLogger.info { "${input.resource}: types to monitor have changed from $monitoringTypes to $recordTypesToMonitor" }

            return genericBackfill(
                stage = DataStage.Daily,
                start = state.lastSync.toInstant(),
                end = minOf(Instant.now(), state.end?.toInstant() ?: Instant.now()),
                timeZone = timeZone,
            )
        }

        var token = state.changesToken
        var changes: ChangesResponse

        do {
            changes = client.getChanges(token)

            if (changes.changesTokenExpired) {
                vitalLogger.info { "${input.resource}: changesToken expired" }

                return genericBackfill(
                    stage = DataStage.Daily,
                    start = state.lastSync.toInstant(),
                    end = minOf(Instant.now(), state.end?.toInstant() ?: Instant.now()),
                    timeZone = timeZone,
                )
            }

            if (changes.changes.isNotEmpty()) {
                vitalLogger.info { "${input.resource}: found ${changes.changes.count()} changes" }

                val delta = processChangesResponse(
                    resource = input.resource,
                    responses = changes,
                    timeZone = timeZone,
                    currentDevice = Build.MODEL,
                    reader = recordReader,
                    processor = recordProcessor,
                    end = state.end?.toInstant()
                )

                // Skip empty POST requests
                if (delta.isNotEmpty()) {
                    uploadResources(
                        delta,
                        uploader = recordUploader,
                        stage = DataStage.Daily,
                        timeZoneId = timeZone.id,
                        userId = userId,
                    )
                }
            } else {
                vitalLogger.info { "${input.resource}: found no change" }
            }

            // Since we have successfully uploaded this batch of changes,
            // save the next change token, in case we get rate limited on the
            // next `getChanges(token)` call.
            setIncremental(token = changes.nextChangesToken)
            token = changes.nextChangesToken

        } while (changes.hasMore)
    }

    private suspend fun genericBackfill(
        stage: DataStage,
        start: Instant,
        end: Instant,
        timeZone: TimeZone
    ) {
        val userId = vitalClient.checkUserId()
        val client = healthConnectClientProvider.getHealthConnectClient(applicationContext)

        val recordTypesToMonitor = recordTypesToMonitor()
        var token = client.getChangesToken(ChangesTokenRequest(recordTypes = recordTypesToMonitor))

        sharedPreferences.edit()
            .putStringSet(input.resource.monitoringTypesKey, recordTypesToMonitor.toSimpleNameSet())
            .apply()

        val (stageStart, stageEnd) = when (stage) {
            // Historical stage must pass the same start ..< end throughout all the chunks.
            DataStage.Historical -> Pair(start, end)
            DataStage.Daily -> Pair(null, null)
        }

        vitalLogger.info { "${input.resource}: generic backfill $start ..< $end" }

        // TODO: Chunk by days
        val allData = mutableListOf<ProcessedResourceData>()
        allData += readResourceByTimeRange(
            resource = input.resource,
            startTime = start,
            endTime = end,
            timeZone = timeZone,
            currentDevice = Build.MODEL,
            reader = recordReader,
            processor = recordProcessor,
        )

        var changes: ChangesResponse

        do {
            changes = client.getChanges(token)
            check(!changes.changesTokenExpired)

            vitalLogger.info { "${input.resource}: found ${changes.changes.count()} new changes after range request" }

            token = changes.nextChangesToken

            allData += processChangesResponse(
                resource = input.resource,
                responses = changes,
                timeZone = timeZone,
                currentDevice = Build.MODEL,
                reader = recordReader,
                processor = recordProcessor,
            )

        } while (changes.hasMore)

        val mergedData = allData.merged()
        // We always make a POST request in DataStage.Historical, even if there is no data, so that
        // the historical.data.*.created event is consistently triggered.
        //
        // Only skip empty POST requests when we are in DataStage.Daily.
        val shouldUpload = stage == DataStage.Historical || mergedData.isNotEmpty()

        if (shouldUpload) {
            uploadResources(
                mergedData,
                uploader = recordUploader,
                stage = stage,
                start = stageStart?.toDate(),
                end = stageEnd?.toDate(),
                timeZoneId = timeZone.id,
                userId = userId,
            )
        }

        setIncremental(token = token)
    }

    private fun monitoringRecordTypes(): Set<String> {
        return sharedPreferences.getStringSet(input.resource.monitoringTypesKey, null) ?: setOf()
    }

    /**
     * Health Connect rejects the request if we include [Record] types we do not have permission
     * for. So we need to proactively filter out [Record] types based on what read permissions we
     * have at the moment.
     */
    private suspend fun recordTypesToMonitor(): Set<KClass<out Record>> {
        val client = healthConnectClientProvider.getHealthConnectClient(applicationContext)
        val grantedPermissions = client.permissionController.getGrantedPermissions()

        return input.resource.recordTypeChangesToTriggerSync()
            .filterTo(mutableSetOf()) { recordType ->
                HealthPermission.getReadPermission(recordType) in grantedPermissions
            }
    }

    private fun setIncremental(token: String) {
        val newState = ResourceSyncState.Incremental(token, lastSync = Date())

        sharedPreferences.edit()
            .putJson<ResourceSyncState>(input.resource.syncStateKey, newState)
            .apply()

        vitalLogger.info { "${input.resource}: updated to $newState" }
    }
}

internal inline fun <reified T : Any> SharedPreferences.getJson(key: String): T? =
    getJson(key, default = null)

internal inline fun <reified T> SharedPreferences.getJson(key: String, default: T): T {
    val jsonString = getString(key, null) ?: return default
    val adapter = moshi.adapter(T::class.java)
    return adapter.fromJson(jsonString)
        ?: throw IllegalStateException("Failed to decode JSON string")
}

internal inline fun <reified T : Any> SharedPreferences.Editor.putJson(
    key: String,
    value: T?
): SharedPreferences.Editor {
    val adapter = moshi.adapter(T::class.java)
    return putString(key, value?.let(adapter::toJson))
}

internal val VitalResource.syncStateKey get() = UnSecurePrefKeys.syncStateKey(this)
internal val VitalResource.monitoringTypesKey get() = UnSecurePrefKeys.monitoringTypesKey(this)

// All Record types are public JVM types, so they must have a simple name.
private fun Set<KClass<out Record>>.toSimpleNameSet(): Set<String> =
    mapTo(mutableSetOf()) { it.simpleName!! }