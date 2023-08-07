package io.tryvital.vitalhealthconnect.workers

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.response.ChangesResponse
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.tryvital.client.Environment
import io.tryvital.client.Region
import io.tryvital.client.VitalClient
import io.tryvital.client.services.data.DataStage
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalhealthconnect.HealthConnectClientProvider
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
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.TimeZone

internal val moshi by lazy {
    Moshi.Builder()
        .add(Date::class.java, Rfc3339DateJsonAdapter())
        .add(ResourceSyncState.adapterFactory)
        .addLast(KotlinJsonAdapterFactory())
        .build()
}

data class ResourceSyncWorkerInput(
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

sealed class ResourceSyncState {
    object Historical : ResourceSyncState()
    data class Incremental(val changesToken: String, val lastSync: Date) : ResourceSyncState()

    companion object {
        val adapterFactory: PolymorphicJsonAdapterFactory<ResourceSyncState>
            get() = PolymorphicJsonAdapterFactory.of(ResourceSyncState::class.java, "type")
                .withSubtype(Historical::class.java, "historical")
                .withSubtype(Incremental::class.java, "incremental")
    }
}

class ResourceSyncWorker(appContext: Context, workerParams: WorkerParameters) :
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
        val state = sharedPreferences.getJson<ResourceSyncState>(input.resource.syncStateKey)
            ?: ResourceSyncState.Historical
        val timeZone = TimeZone.getDefault()

        when (state) {
            is ResourceSyncState.Historical -> historicalBackfill(state, timeZone)
            is ResourceSyncState.Incremental -> incrementalBackfill(state, timeZone)
        }

        // TODO: Report synced vs nothing to sync
        return Result.success()
    }

    private suspend fun historicalBackfill(state: ResourceSyncState.Historical, timeZone: TimeZone) {
        val now = ZonedDateTime.now(timeZone.toZoneId())
        val start = now.minus(30, ChronoUnit.DAYS)

        genericBackfill(
            stage = DataStage.Historical,
            start = start.toInstant(),
            end = now.toInstant(),
            timeZone = timeZone,
        )
    }

    private suspend fun incrementalBackfill(state: ResourceSyncState.Incremental, timeZone: TimeZone) {
        val userId = vitalClient.checkUserId()
        val client = healthConnectClientProvider.getHealthConnectClient(applicationContext)

        var token = state.changesToken
        var changes: ChangesResponse

        do {
            changes = client.getChanges(token)
            token = state.changesToken

            if (changes.changesTokenExpired) {
                return genericBackfill(
                    stage = DataStage.Daily,
                    start = state.lastSync.toInstant(),
                    end = Instant.now(),
                    timeZone = timeZone,
                )
            }

            val delta = processChangesResponse(
                resource = input.resource,
                responses = changes,
                timeZone = timeZone,
                currentDevice = Build.MODEL,
                reader = recordReader,
                processor = recordProcessor,
            )

            uploadResources(
                delta,
                uploader = recordUploader,
                stage = DataStage.Daily,
                timeZoneId = timeZone.id,
                userId = userId,
            )

        } while (changes.hasMore)

        setIncremental(token = token)
    }

    private suspend fun genericBackfill(stage: DataStage, start: Instant, end: Instant, timeZone: TimeZone) {
        val userId = vitalClient.checkUserId()
        val client = healthConnectClientProvider.getHealthConnectClient(applicationContext)
        var token = client.getChangesToken(
            ChangesTokenRequest(
                recordTypes = input.resource.recordTypeChangesToTriggerSync().toSet(),
            )
        )

        val (stageStart, stageEnd) = when (stage) {
            // Historical stage must pass the same start ..< end throughout all the chunks.
            DataStage.Historical -> Pair(start, end)
            DataStage.Daily -> Pair(null, null)
        }

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

        uploadResources(
            allData.merged(),
            uploader = recordUploader,
            stage = stage,
            start = stageStart?.toDate(),
            end = stageEnd?.toDate(),
            timeZoneId = timeZone.id,
            userId = userId,
        )

        setIncremental(token = token)
    }

    private fun setIncremental(token: String) {
        val newState = ResourceSyncState.Incremental(token, lastSync = Date())

        sharedPreferences.edit()
            .putJson<ResourceSyncState>(input.resource.syncStateKey, newState)
            .apply()
    }
}

internal inline fun <reified T: Any> SharedPreferences.getJson(key: String): T?
    = getJson(key, default = null)

internal inline fun <reified T> SharedPreferences.getJson(key: String, default: T): T {
    val jsonString = getString(key, null) ?: return default
    val adapter = moshi.adapter(T::class.java)
    return adapter.fromJson(jsonString) ?: throw IllegalStateException("Failed to decode JSON string")
}

internal inline fun <reified T: Any> SharedPreferences.Editor.putJson(key: String, value: T?): SharedPreferences.Editor {
    val adapter = moshi.adapter(T::class.java)
    return putString(key, value?.let(adapter::toJson))
}

internal val VitalResource.syncStateKey get() = "sync-state.${this.name}"
