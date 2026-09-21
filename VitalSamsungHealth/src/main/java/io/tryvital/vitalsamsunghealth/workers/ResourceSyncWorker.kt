package io.tryvital.vitalsamsunghealth.workers

import android.content.Context
import android.content.SharedPreferences
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.samsung.android.sdk.health.data.data.Change
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.request.ChangedDataRequest
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.InstantTimeFilter
import com.samsung.android.sdk.health.data.response.DataResponse
import io.tryvital.client.VitalClient
import io.tryvital.client.services.data.DataStage
import io.tryvital.client.utils.InstantJsonAdapter
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalsamsunghealth.UnSecurePrefKeys
import io.tryvital.vitalsamsunghealth.VitalSamsungHealthManager
import io.tryvital.vitalsamsunghealth.cachedGrantedPermissions
import io.tryvital.vitalsamsunghealth.permissionKey
import io.tryvital.vitalhealthcore.exceptions.ConnectionDestroyed
import io.tryvital.vitalhealthcore.exceptions.ConnectionPaused
import io.tryvital.vitalhealthcore.model.RemappedVitalResource
import io.tryvital.vitalhealthcore.model.VitalResource
import io.tryvital.vitalsamsunghealth.model.dataTypeChangesToTriggerSync
import io.tryvital.vitalsamsunghealth.model.processedresource.ProcessedResourceData
import io.tryvital.vitalsamsunghealth.model.processedresource.merged
import io.tryvital.vitalsamsunghealth.records.ProcessorOptions
import io.tryvital.vitalsamsunghealth.records.RecordProcessor
import io.tryvital.vitalsamsunghealth.records.RecordReader
import io.tryvital.vitalhealthcore.records.RecordUploader
import io.tryvital.vitalhealthcore.syncProgress.SyncProgress.SyncContextTag
import io.tryvital.vitalhealthcore.syncProgress.SyncProgress.SyncID
import io.tryvital.vitalhealthcore.syncProgress.SyncProgress
import io.tryvital.vitalhealthcore.syncProgress.SyncProgress.SyncStatus
import io.tryvital.vitalhealthcore.workers.LocalSyncState
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.util.TimeZone

const val VITAL_SYNC_NOTIFICATION_ID = 123

internal val moshi by lazy {
    Moshi.Builder()
        .add(Instant::class.java, InstantJsonAdapter)
        .add(ResourceSyncState.adapterFactory)
        .build()
}

internal data class ResourceSyncWorkerInput(
    val resource: RemappedVitalResource,
    val tags: List<SyncProgress.SyncContextTag> = emptyList(),
) {
    fun toData(): Data = Data.Builder().run {
        putString("resource", resource.toString())
        putIntArray("tags", tags.map { it.rawValue }.toIntArray())
        build()
    }

    companion object {
        fun fromData(data: Data) = ResourceSyncWorkerInput(
            resource = RemappedVitalResource(
                VitalResource.valueOf(
                    data.getString("resource") ?: throw IllegalArgumentException("Missing resource")
                )
            ),
            tags = data.getIntArray("tags")?.map(SyncProgress.SyncContextTag::of) ?: emptyList()
        )
    }
}

@JsonClass(generateAdapter = false)
internal sealed class ResourceSyncState {
    @JsonClass(generateAdapter = true)
    data class Historical(val start: Instant, val end: Instant) : ResourceSyncState() {
        override fun toString(): String = "historical(${start} ..< ${end})"
    }

    @JsonClass(generateAdapter = true)
    data class Incremental(
        @Json(name = "lastSync") val lastRecordSync: Instant,
        val lastChangeSync: Instant? = null,
    ) : ResourceSyncState() {
        override fun toString(): String =
            "incremental(lastRecordSync=$lastRecordSync; lastChangeSync=$lastChangeSync)"
    }

    companion object {
        val adapterFactory: PolymorphicJsonAdapterFactory<ResourceSyncState>
            get() = PolymorphicJsonAdapterFactory.of(ResourceSyncState::class.java, "type")
                .withSubtype(Historical::class.java, "historical")
                .withSubtype(Incremental::class.java, "incremental")
    }
}

internal sealed class SyncInstruction {
    data class DoHistorical(val start: Instant, val end: Instant) : SyncInstruction() {
        override fun toString(): String = "doHistorical(${start} ..< ${end})"
    }

    data class DoIncremental(
        val lastRecordSync: Instant,
        val lastChangeSync: Instant?,
        val start: Instant,
        val end: Instant? = null,
    ) :
        SyncInstruction() {
        override fun toString(): String =
            "doIncremental(lastRecordSync=$lastRecordSync; lastChangeSync=$lastChangeSync; start=$start; end=$end)"
    }
}

private class RetryableChangeSyncException(cause: Throwable) : Exception(cause)

private const val MAX_CHANGE_SYNC_ATTEMPTS = 3

internal class ResourceSyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val input: ResourceSyncWorkerInput by lazy {
        ResourceSyncWorkerInput.fromData(inputData)
    }

    private val vitalClient: VitalClient by lazy {
        VitalClient.getOrCreate(applicationContext)
    }
    private val manager: VitalSamsungHealthManager by lazy {
        VitalSamsungHealthManager.getOrCreate(applicationContext)
    }
    private val sharedPreferences: SharedPreferences get() = vitalClient.sharedPreferences
    private val samsungHealthClientProvider get() = manager.samsungHealthClientProvider
    private val recordReader: RecordReader get() = manager.recordReader
    private val recordProcessor: RecordProcessor get() = manager.recordProcessor
    private val recordUploader: RecordUploader get() = manager.recordUploader
    private val localSyncStateManager: LocalSyncStateManager get() = manager.localSyncStateManager
    private val syncProgressStore get() = manager.syncProgressStore
    private val syncProgressReporter get() = manager.syncProgressReporter
    private val vitalLogger = VitalLogger.getOrCreate()

    private val syncID by lazy {
        SyncProgress.SyncID(resource = input.resource.wrapped, tags = input.tags.toSet())
    }

    override suspend fun doWork(): Result {
        syncProgressReporter.syncBegin(syncID)

        try {
            val result = doActualWork()
            syncProgressStore.recordSync(syncID, SyncProgress.SyncStatus.completed)

            return result

        } catch (e: ConnectionPaused) {
            syncProgressStore.recordSync(
                syncID,
                SyncProgress.SyncStatus.connectionPaused,
            )
            vitalLogger.logI("${input.resource}: skipped because backend reported connection pause")
            return Result.success()

        } catch (e: ConnectionDestroyed) {
            syncProgressStore.recordSync(
                syncID,
                SyncProgress.SyncStatus.connectionDestroyed,
            )
            vitalLogger.logI("${input.resource}: skipped because backend reported connection destroyed")
            return Result.success()

        } catch (exc: CancellationException) {
            syncProgressStore.recordSync(
                syncID,
                SyncProgress.SyncStatus.cancelled,
            )
            throw exc

        } catch (exc: RetryableChangeSyncException) {
            syncProgressStore.recordSync(
                syncID,
                SyncProgress.SyncStatus.error,
                errorDetails = exc.stackTraceToString()
            )
            return if (runAttemptCount + 1 < MAX_CHANGE_SYNC_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure()
            }

        } catch (exc: Throwable) {
            syncProgressStore.recordSync(
                syncID,
                SyncProgress.SyncStatus.error,
                errorDetails = exc.stackTraceToString()
            )
            throw exc

        } finally {
            syncProgressStore.flush()
            syncProgressReporter.syncEnded(applicationContext, syncID)
        }
    }

    /**
     * Each instance of ResourceSyncWorker is responsible to sync one specific VitalResource type.
     * Sync state of each VitalResource type is stored and advanced independently.
     *
     * This separation enables the SDK to function with only partial permission grants (e.g., due to
     * resource types not needed by the customers, permissions not having been requested, or user
     * having revoked the permission).
     *
     * Historical and incremental record ranges use the data point's event-time axis. Samsung
     * Health changes use a separate change-time axis. Before a historical read begins, capture a
     * change-time anchor. Once the historical upload succeeds, that anchor becomes the starting
     * watermark for the next change query, so writes concurrent with the historical read are not
     * lost.
     *
     * ## Historical stage
     * i.e. state is ResourceSyncState.Historical
     *
     * 1. `generic_backfill(stage="historical", start=now() - N days, end=now())`
     *
     * ## Daily/Incremental stage
     * i.e. state is ResourceSyncState.Incremental
     *
     * 1. Fetch changes in `[state.lastChangeSync, changeTimeEnd)`.
     * 2. If fetch is successful:
     *     a. Upload all fetched data with `stage=daily`
     *     b. Store `changeTimeEnd` as the next change watermark.
     * 3. If the change query fails, retain the old watermark and retry the worker.
     */
    private suspend fun doActualWork(): Result {
        val timeZone = TimeZone.getDefault()

        val (instruction, localSyncState) = computeSyncInstruction()

        vitalLogger.logI("${input.resource}: $instruction")

        val processorOptions = ProcessorOptions(
            perDeviceActivityTS = localSyncState.perDeviceActivityTS
        )

        when (instruction) {
            is SyncInstruction.DoHistorical -> historicalBackfill(instruction, timeZone, processorOptions)
            is SyncInstruction.DoIncremental -> incrementalBackfill(instruction, timeZone, processorOptions)
        }

        // TODO: Report synced vs nothing to sync
        return Result.success()
    }


    private suspend fun computeSyncInstruction(): Pair<SyncInstruction, LocalSyncState> {
        val resourceSyncState = sharedPreferences.getJson<ResourceSyncState>(input.resource.wrapped.syncStateKey)

        val localSyncState = localSyncStateManager.getLocalSyncState(
            grantedPermissions = { manager.checkAndUpdatePermissions().third.sorted() },
            onRevalidation = {
                syncProgressStore.recordSync(syncID, SyncProgress.SyncStatus.revalidatingSyncState)
            }
        )

        val now = Instant.now()
        val reconciledStart = localSyncState.historicalStartDate(input.resource)
        val reconciledEnd = localSyncState.ingestionEnd ?: now

        val instruction = when (resourceSyncState) {
            is ResourceSyncState.Incremental -> SyncInstruction.DoIncremental(
                lastRecordSync = resourceSyncState.lastRecordSync,
                lastChangeSync = resourceSyncState.lastChangeSync,
                start = reconciledStart,
                end = reconciledEnd,
            )
            is ResourceSyncState.Historical -> SyncInstruction.DoHistorical(
                start = maxOf(reconciledStart, resourceSyncState.start),
                end = maxOf(resourceSyncState.end, reconciledEnd),
            )
            null -> SyncInstruction.DoHistorical(
                start = reconciledStart,
                end = reconciledEnd,
            )
        }

        return instruction to localSyncState
    }

    private suspend fun historicalBackfill(
        state: SyncInstruction.DoHistorical,
        timeZone: TimeZone,
        processorOptions: ProcessorOptions,
    ) {
        val changeTimeAnchor = if (useRecordChangesForIncrementalBackfill) Instant.now() else null
        genericBackfill(
            stage = DataStage.Historical,
            start = state.start,
            end = state.end,
            lastChangeSync = changeTimeAnchor,
            timeZone = timeZone,
            processorOptions = processorOptions,
        )
    }

    private suspend fun incrementalBackfill(
        state: SyncInstruction.DoIncremental,
        timeZone: TimeZone,
        processorOptions: ProcessorOptions,
    ) {
        val now = Instant.now()
        val recordTimeEnd = minOf(now, state.end ?: now)
        val monitoredTypes = dataTypesToMonitor()

        // No change-readable data types mapped for this resource, fall back to range-based sync.
        if (!useRecordChangesForIncrementalBackfill || monitoredTypes.isEmpty()) {
            return genericBackfill(
                stage = DataStage.Daily,
                start = state.lastRecordSync,
                end = recordTimeEnd,
                lastChangeSync = state.lastChangeSync,
                timeZone = timeZone,
                processorOptions = processorOptions,
            )
        }

        val lastChangeSync = state.lastChangeSync
        if (lastChangeSync == null) {
            vitalLogger.info { "${input.resource}: missing change-time watermark; fallback to historical backfill" }
            val changeTimeAnchor = Instant.now()
            return genericBackfill(
                stage = DataStage.Historical,
                start = state.start,
                end = recordTimeEnd,
                lastChangeSync = changeTimeAnchor,
                timeZone = timeZone,
                processorOptions = processorOptions,
            )
        }

        val changeTimeEnd = Instant.now()
        val changeTimeFilter = InstantTimeFilter.of(lastChangeSync, changeTimeEnd)
        val userId = VitalClient.checkUserId()
        val allData = mutableListOf<ProcessedResourceData>()

        try {
            monitoredTypes.forEach { dataType ->
                var pageToken: String? = null

                while (true) {
                    val response = readChanges(
                        dataType = dataType,
                        pageToken = pageToken,
                        changeTimeFilter = if (pageToken == null) changeTimeFilter else null,
                    )

                    val delta = processChangesResponse(
                        resource = input.resource,
                        changes = response.dataList,
                        timeZone = timeZone,
                        reader = recordReader,
                        processor = recordProcessor,
                        processorOptions = processorOptions,
                        end = recordTimeEnd,
                    )
                    if (delta != null) {
                        allData += delta
                    }

                    val nextToken = response.pageToken
                    if (nextToken.isNullOrBlank() || nextToken == pageToken) {
                        break
                    }

                    pageToken = nextToken
                }
            }
        } catch (exc: CancellationException) {
            throw exc
        } catch (t: Throwable) {
            vitalLogger.info { "${input.resource}: readChanges failed; retain watermark and retry: $t" }
            throw RetryableChangeSyncException(t)
        }

        val mergedData = if (allData.isNotEmpty()) allData.merged() else null
        if (mergedData != null && mergedData.isNotEmpty()) {
            uploadResources(
                mergedData,
                uploader = recordUploader,
                stage = DataStage.Daily,
                start = null,
                end = null,
                timeZoneId = timeZone.id,
                userId = userId,
            )
        } else {
            syncProgressStore.recordSync(syncID, SyncProgress.SyncStatus.noData)
        }

        setIncremental(lastRecordSync = recordTimeEnd, lastChangeSync = changeTimeEnd)
    }

    private suspend fun genericBackfill(
        stage: DataStage,
        start: Instant,
        end: Instant,
        lastChangeSync: Instant?,
        timeZone: TimeZone,
        processorOptions: ProcessorOptions,
    ) {
        val userId = VitalClient.checkUserId()

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
            stage = stage,
            timeZone = timeZone,
            reader = recordReader,
            processor = recordProcessor,
            processorOptions = processorOptions,
        )

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
                start = stageStart,
                end = stageEnd,
                timeZoneId = timeZone.id,
                userId = userId,
            )
        }

        setIncremental(lastRecordSync = end, lastChangeSync = lastChangeSync)
    }

    private suspend fun readChanges(
        dataType: DataType,
        pageToken: String?,
        changeTimeFilter: InstantTimeFilter?,
    ): DataResponse<Change<HealthDataPoint>> {
        val store = samsungHealthClientProvider.getHealthDataStore(applicationContext)
        val builder = changedDataRequestBuilder(dataType)
            ?: throw IllegalStateException("${dataType.name} is not change-readable")

        builder.setPageSize(1000)
        if (!pageToken.isNullOrBlank()) {
            builder.setPageToken(pageToken)
        }
        if (changeTimeFilter != null) {
            builder.setChangeTimeFilter(changeTimeFilter)
        }

        return store.readChanges(builder.build())
    }

    @Suppress("UNCHECKED_CAST")
    private fun changedDataRequestBuilder(dataType: DataType): ChangedDataRequest.BasicBuilder<HealthDataPoint>? {
        val getter = runCatching { dataType.javaClass.getMethod("getChangedDataRequestBuilder") }.getOrNull()
            ?: return null
        return runCatching { getter.invoke(dataType) as ChangedDataRequest.BasicBuilder<HealthDataPoint> }.getOrNull()
    }

    private suspend fun dataTypesToMonitor(): Set<DataType> {
        val requested = input.resource.wrapped.dataTypeChangesToTriggerSync().toSet()
        if (requested.isEmpty()) return emptySet()

        val granted = sharedPreferences.cachedGrantedPermissions()

        return requested.filterTo(mutableSetOf()) { dataType ->
            permissionKey(dataType, AccessType.READ) in granted
        }
    }

    private val useRecordChangesForIncrementalBackfill by lazy {
        input.resource.wrapped.dataTypeChangesToTriggerSync().isNotEmpty()
    }

    private fun setIncremental(lastRecordSync: Instant, lastChangeSync: Instant?) {
        val newState = ResourceSyncState.Incremental(
            lastRecordSync = lastRecordSync,
            lastChangeSync = lastChangeSync,
        )

        sharedPreferences.edit()
            .putJson<ResourceSyncState>(input.resource.wrapped.syncStateKey, newState)
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
