package io.tryvital.vitalhealthconnect.workers

import android.content.Context
import android.content.SharedPreferences
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.tryvital.client.Environment
import io.tryvital.client.Region
import io.tryvital.client.VitalClient
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalhealthconnect.HealthConnectClientProvider
import io.tryvital.vitalhealthconnect.model.VitalResource
import io.tryvital.vitalhealthconnect.records.*
import io.tryvital.vitalhealthconnect.records.HealthConnectRecordAggregator
import io.tryvital.vitalhealthconnect.records.HealthConnectRecordProcessor
import io.tryvital.vitalhealthconnect.records.HealthConnectRecordReader
import kotlinx.coroutines.delay
import java.util.*

internal val moshi by lazy {
    Moshi.Builder()
        .add(Date::class.java, Rfc3339DateJsonAdapter())
        .add(ResourceSyncState.adapterFactory)
        .addLast(KotlinJsonAdapterFactory())
        .build()
}

data class ResourceSyncWorkerInput(
    val resource: VitalResource,

    // TODO: Remove after SDK singletonization
    val region: Region,
    val environment: Environment,
    val apiKey: String,
) {
    fun toData(): Data = Data.Builder().run {
        putString("resource", resource.toString())
        putString("environment", environment.name)
        putString("region", region.name)
        putString("apiKey", apiKey)
        build()
    }

    companion object {
        fun fromData(data: Data) = ResourceSyncWorkerInput(
            resource = VitalResource.valueOf(
                data.getString("resource") ?: throw IllegalArgumentException("Missing resource")
            ),
            environment = Environment.valueOf(
                data.getString("environment") ?: throw IllegalArgumentException("Missing environment")
            ),
            region = Region.valueOf(
                data.getString("region") ?: throw IllegalArgumentException("Missing region")
            ),
            apiKey = data.getString("apiKey") ?: throw IllegalArgumentException("Missing API key"),
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
        VitalClient(applicationContext, input.region, input.environment, input.apiKey)
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

        when (state) {
            is ResourceSyncState.Historical -> {}
            is ResourceSyncState.Incremental -> {}
        }

        return Result.failure()
    }

    private suspend fun reportStatus(resource: VitalResource, status: String) {
        setProgress(
            Data.Builder().putString(statusTypeKey, resource.name).putString(syncStatusKey, status)
                .build()
        )
        delay(100)
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