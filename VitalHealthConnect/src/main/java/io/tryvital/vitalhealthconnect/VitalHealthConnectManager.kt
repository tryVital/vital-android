@file:OptIn(ExperimentalVitalApi::class)

package io.tryvital.vitalhealthconnect

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.http.NetworkException
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
import io.tryvital.client.VitalClient
import io.tryvital.client.createConnectedSourceIfNotExist
import io.tryvital.client.hasUserConnectedTo
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
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass


@Suppress("MemberVisibilityCanBePrivate")
class VitalHealthConnectManager private constructor(
    internal val context: Context,
    private val healthConnectClientProvider: HealthConnectClientProvider,
    private val vitalClient: VitalClient,
    private val recordReader: RecordReader,
    private val recordProcessor: RecordProcessor,
) {
    val sharedPreferences get() = vitalClient.sharedPreferences

    /**
     * Pause all synchronization, both automatic syncs and any manual [syncData] calls.
     *
     * When unpausing, a sync is automatically triggered on all previously asked-for resources.
     */
    var pauseSynchronization: Boolean
        get() = sharedPreferences.getBoolean(UnSecurePrefKeys.pauseSyncKey, false)
        set(newValue) {
            val oldValue = sharedPreferences.getBoolean(UnSecurePrefKeys.pauseSyncKey, false)
            sharedPreferences.edit()
                .putBoolean(UnSecurePrefKeys.pauseSyncKey, newValue)
                .apply()

            // Cancel exact alarm if we are pausing
            if (!oldValue && newValue) {
                disableBackgroundSync()
            }

            if (oldValue && !newValue) {
                // Re-schedule exact alarm if we are un-pausing.
                scheduleNextExactAlarm(force = true)

                // Auto-trigger a sync on unpausing, but only if there is no outstanding sync job
                if (currentSyncCall.availablePermits > 0) {
                    taskScope.launch { syncData() }
                }
            }
        }

    private val vitalLogger = vitalClient.vitalLogger

    /**
     * When the Vital SDK launches a Foreground Service to handle data synchronization,
     * it asks the current [SyncNotificationBuilder] to produce a user-facing notification
     * indicating that such sync task is ongoing. This is **required** by the Android platform.
     *
     * Running as a Foreground Service ensures that Vital SDK can read from Health Connect, as well
     * as synchronization not being interrupted when the user switches away from your app.
     *
     * In some cases, the system may grant a grace period of 10 seconds before notifying the user.
     * This means if the SDK data sync ends within the grace period, the user may not be notified
     * in the end.
     *
     * Ref: https://developer.android.com/develop/background-work/services/foreground-services
     */
    var syncNotificationBuilder: SyncNotificationBuilder
        get() = _syncNotificationBuilder.get()
        set(newValue) = _syncNotificationBuilder.set(newValue)

    private val _syncNotificationBuilder = AtomicReference<SyncNotificationBuilder>(
        DefaultSyncNotificationBuilder(vitalClient.sharedPreferences)
    )

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

        taskScope.launch(Dispatchers.Main) {
            checkAndUpdatePermissions()
            scheduleNextExactAlarm(force = false)
        }
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
    @Deprecated(
        "Use [VitalClient.signOut]. It resets both the Vital Core and Health SDKs."
    )
    @Suppress("unused")
    fun cleanUp() {
        @Suppress("DEPRECATION")
        vitalClient.cleanUp()
    }

    private fun resetAutoSync() {
        taskScope.cancel()
        taskScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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

    fun resourcesWithReadPermission(): Set<VitalResource> {
        return VitalResource.values()
            .filter { sharedPreferences.getBoolean(UnSecurePrefKeys.readResourceGrant(it), false) }
            .toSet()
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
        return resources
            .flatMapTo(mutableSetOf()) { it.recordTypeDependencies() }
            .map { HealthPermission.getReadPermission(it) }
            .toSet()
    }

    /**
     * @param syncNotificationBuilder When you provide a builder, Vital SDK will start resource
     * sync workers as foreground services, with the user-visible notification info returned by
     * your [SyncNotificationBuilder]. This allows the sync process to continue even when the
     * user has switched away from your app.
     */
    @SuppressLint("ApplySharedPref")
    fun configureHealthConnectClient(
        logsEnabled: Boolean = false,
        syncOnAppStart: Boolean = true,
        numberOfDaysToBackFill: Int = 30,
        syncNotificationBuilder: SyncNotificationBuilder? = null,
    ) {
        if (VitalClient.Status.Configured !in VitalClient.status) {
            throw IllegalStateException("VitalClient has not been configured.")
        }

        if (syncNotificationBuilder != null) {
            this._syncNotificationBuilder.set(syncNotificationBuilder)
        }

        vitalLogger.enabled = logsEnabled

        sharedPreferences.edit()
            .putBoolean(UnSecurePrefKeys.loggerEnabledKey, logsEnabled)
            .putBoolean(UnSecurePrefKeys.syncOnAppStartKey, syncOnAppStart)
            .putInt(UnSecurePrefKeys.numberOfDaysToBackFillKey, numberOfDaysToBackFill)
            .commit()

        if (VitalClient.Status.SignedIn in VitalClient.status && isAvailable(context) == HealthConnectAvailability.Installed) {
            vitalLogger.logI("Configuration set, starting sync")
            taskScope.launch {
                syncData(healthResources)
            }
        }
    }

    suspend fun syncData(resources: Set<VitalResource>? = null) {
        if (pauseSynchronization) {
            return
        }

        try {
            currentSyncCall.acquire()

            vitalClient.createConnectedSourceIfNotExist(ManualProviderSlug.HealthConnect)

            checkAndUpdatePermissions()

            val available = resourcesWithReadPermission()
            val candidate = resources?.let { resources.intersect(available) } ?: available

            if (candidate.isEmpty()) {
                return
            }

            // Remap and deduplicate resources before spawning workers
            // e.g. (Activity | ActiveEnergyBurned | BasalEnergyBurned) => Activity
            val remappedCandidates = candidate.mapTo(mutableSetOf()) { it.remapped() }

            // Sync each resource one by one for now to lower the possibility of
            // triggering rate limit
            val job = startSyncWorker(remappedCandidates)

            // Wait until the sync worker completes.
            job.join()

        } catch (e: Exception) {
            vitalLogger.logE("Error syncing data", e)
            _status.tryEmit(SyncStatus.Unknown)
        } finally {
            currentSyncCall.release()
        }
    }

    /**
     * Synchronous, no suspension
     * Also do not wait for [ResourceSyncStarter] completion before returning
     * Intended for [SyncBroadcastReceiver].
     *
     * @param beforeEnqueue Block to invoke before enqueuing the work. Will not be invoke if we
     * are not going to spawn a sync worker, e.g., due to [pauseSynchronization].
     */
    internal fun launchSyncWorkerFromBackground(beforeEnqueue: () -> Unit) {
        // We assume:
        // 1. Permissions in our SharedPerfs are up-to-date
        // 2. ConnectedSource is already created.
        // Fail gracefully if these assumptions are not satisfied.

        if (!vitalClient.hasUserConnectedTo(ProviderSlug.HealthConnect)) {
            return
        }

        val candidates = resourcesWithReadPermission()
        if (candidates.isEmpty()) {
            return
        }

        // Remap and deduplicate resources before spawning workers
        // e.g. (Activity | ActiveEnergyBurned | BasalEnergyBurned) => Activity
        val remappedCandidates = candidates.mapTo(mutableSetOf()) { it.remapped() }

        startSyncWorker(remappedCandidates, beforeEnqueue)
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
        startTime: Instant,
        endTime: Instant
    ): ProcessedResourceData {
        return readResourceByTimeRange(
            resource,
            startTime = startTime,
            endTime = endTime,
            timeZone = TimeZone.getDefault(),
            currentDevice = Build.MODEL,
            reader = recordReader,
            processor = recordProcessor,
        )
    }

    /**
     * Create a new [ResourceSyncStarter] for the given [resources].
     *
     * The returned Job completes only after [ResourceSyncStarter] has succeeded, cancelled
     * or failed.
     *
     * Worker status change will be mirrored to SDK sync status via [updateStatusFromJob].
     */
    @SuppressLint("MissingPermission")
    private fun startSyncWorker(resources: Set<VitalResource>, beforeEnqueue: (() -> Unit)? = null): Job {
        if (!context.isConnectedToInternet) {
            return taskScope.launch {
                cancel("OS reports no Internet connection")
            }
        }

        val workRequest = OneTimeWorkRequestBuilder<ResourceSyncStarter>()
            .setInputData(ResourceSyncStarterInput(resources = resources).toData())
            .build()
        val workRequestId = workRequest.id

        val work = WorkManager.getInstance(context).beginUniqueWork(
            "ResourceSyncStarter",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        beforeEnqueue?.invoke()
        work.enqueue()

        // Launch the work in `taskScope` so that it can be cancelled immediately when the whole
        // VitalHealthConnectManager is closed, independent of the caller of executeWork().
        return work.workInfosLiveData.asFlow()
            .mapNotNull { workInfos -> workInfos.firstOrNull { it.id == workRequestId } }
            .transformWhile { info ->
                emit(info)
                return@transformWhile when (info.state) {
                    // Work is running; continue the observation
                    WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> true
                    // Work has ended; stop the observation
                    WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED, WorkInfo.State.FAILED -> false
                }
            }
            .onEach {
                // TODO: Temporary
                for (resource in resources) {
                    updateStatusFromJob(listOf(it), resource)
                }
            }
            .onCompletion {
                _status.tryEmit(SyncStatus.SyncingCompleted)
            }
            .launchIn(taskScope + Dispatchers.Main)
    }

    private fun updateStatusFromJob(workInfos: List<WorkInfo>, resource: VitalResource) {
        workInfos.forEach {
            when (it.state) {
                WorkInfo.State.RUNNING -> _status.tryEmit(SyncStatus.ResourceSyncing(resource))
                WorkInfo.State.SUCCEEDED -> _status.tryEmit(SyncStatus.ResourceSyncingComplete(resource))
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

        @SuppressLint("StaticFieldLeak")
        private var sharedInstance: VitalHealthConnectManager? = null

        @Suppress("unused")
        fun isAvailable(context: Context): HealthConnectAvailability {
            return when (HealthConnectClient.getSdkStatus(context, packageName)) {
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

        fun getOrCreate(context: Context): VitalHealthConnectManager = synchronized(VitalHealthConnectManager) {
            val appContext = context.applicationContext
            var instance = sharedInstance

            if (instance == null) {
                val coreClient = VitalClient.getOrCreate(appContext)
                val healthConnectClientProvider = HealthConnectClientProvider()

                instance = VitalHealthConnectManager(
                    appContext,
                    healthConnectClientProvider,
                    coreClient,
                    HealthConnectRecordReader(appContext, healthConnectClientProvider),
                    HealthConnectRecordProcessor(
                        HealthConnectRecordReader(appContext, healthConnectClientProvider),
                        HealthConnectRecordAggregator(appContext, healthConnectClientProvider),
                    )
                )
                sharedInstance = instance
                bind(instance, coreClient, appContext)
            }

            return instance
        }

        /**
         * Must be called exactly once after both Core SDK and Health SDK are initialized.
         */
        @OptIn(DelicateCoroutinesApi::class)
        private fun bind(client: VitalHealthConnectManager, coreClient: VitalClient, context: Context) {
            VitalClient.Companion.statuses(context)
                .onEach { statuses ->
                    if (VitalClient.Status.SignedIn !in statuses) {
                        client.resetAutoSync()
                    }
                }
                .launchIn(GlobalScope)
        }
    }
}

