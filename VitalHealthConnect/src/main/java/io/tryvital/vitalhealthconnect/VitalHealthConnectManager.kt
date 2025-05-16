@file:OptIn(ExperimentalVitalApi::class)

package io.tryvital.vitalhealthconnect

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectClient.Companion.ACTION_HEALTH_CONNECT_SETTINGS
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Volume
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.asFlow
import androidx.work.*
import io.tryvital.client.VitalClient
import io.tryvital.client.services.data.*
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalhealthconnect.model.*
import io.tryvital.vitalhealthconnect.model.processedresource.ProcessedResourceData
import io.tryvital.vitalhealthconnect.records.*
import io.tryvital.vitalhealthconnect.workers.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass


@Suppress("MemberVisibilityCanBePrivate")
class VitalHealthConnectManager private constructor(
    internal val context: Context,
    private val healthConnectClientProvider: HealthConnectClientProvider,
    internal val vitalClient: VitalClient,
    private val recordReader: RecordReader,
    private val recordProcessor: RecordProcessor,
) {
    val sharedPreferences get() = vitalClient.sharedPreferences
    private val permissionMutex = Mutex()

    /**
     * Pause all synchronization, both automatic syncs and any manual [syncData] calls.
     *
     * When unpausing, a sync is automatically triggered on all previously asked-for resources.
     */
    var pauseSynchronization: Boolean
        get() = sharedPreferences.getBoolean(UnSecurePrefKeys.pauseSyncKey, false)
        set(newValue) {
            check(Looper.getMainLooper().isCurrentThread)

            val oldValue = sharedPreferences.getBoolean(UnSecurePrefKeys.pauseSyncKey, false)
            sharedPreferences.edit()
                .putBoolean(UnSecurePrefKeys.pauseSyncKey, newValue)
                .apply()

            // Cancel exact alarm if we are pausing
            if (!oldValue && newValue && isBackgroundSyncEnabled) {
                cancelPendingAlarm()
                cancelSyncWorker()
            }

            if (oldValue && !newValue) {
                if (isBackgroundSyncEnabled) {
                    // Re-schedule exact alarm if we are un-pausing.
                    scheduleNextExactAlarm(force = true)
                }

                // Auto-trigger a sync on unpausing
                launchAutoSyncWorker(startForeground = true) {
                    vitalLogger.info { "BgSync: sync triggered by unpause" }
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

    // Use SupervisorJob so that:
    // 1. child job failure would not cancel the whole scope (it would by default of structured concurrency).
    // 2. cancelling the scope would cancel all running child jobs.
    internal var taskScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var processLifecycleObserver: LifecycleObserver

    init {
        vitalLogger.logI("VitalHealthConnectManager initialized")
        _status.tryEmit(SyncStatus.Unknown)

        taskScope.launch(Dispatchers.Main.immediate) {
            processLifecycleObserver = processLifecycleObserver(this@VitalHealthConnectManager)
                .also { ProcessLifecycleOwner.get().lifecycle.addObserver(it) }
        }

        setupSyncWorkerObservation()
    }

    @OptIn(ExperimentalVitalApi::class)
    private fun resetAutoSync() {
        cancelSyncWorker()
        disableBackgroundSync()
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
    fun hasAskedForPermissions(resources: List<VitalResource>): Map<VitalResource, PermissionStatus> {
        return resources.associateWith { resource ->
            val hasAsked =
                sharedPreferences.getBoolean(UnSecurePrefKeys.readResourceGrant(resource), false)
            if (hasAsked) PermissionStatus.Asked else PermissionStatus.NotAsked
        }
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

    @Suppress("unused")
    suspend fun reloadPermissions() {
        checkAndUpdatePermissions()
    }

    internal suspend fun checkAndUpdatePermissions(): Pair<Set<VitalResource>, Set<VitalResource>> = permissionMutex.withLock {
        if (isAvailable(context) != HealthConnectAvailability.Installed) {
            return emptySet<VitalResource>() to emptySet()
        }

        val lastKnownGrantedResources = resourcesWithReadPermission()
        val currentGrants = getGrantedPermissions(context)

        val readResourcesByStatus = VitalResource.values().groupBy { resource ->
            return@groupBy resource.recordTypeDependencies().isResourceActive { recordType ->
                val readPermission = HealthPermission.getReadPermission(recordType)
                return@isResourceActive readPermission in currentGrants
            }
        }

        val writeResourcesByStatus = WritableVitalResource.values()
            .groupBy { currentGrants.containsAll(permissionsRequiredToWriteResources(setOf(it))) }

        val upToDateGrantedResources = readResourcesByStatus.getOrDefault(true, listOf()).toSet()

        sharedPreferences.edit().run {
            readResourcesByStatus.forEach { (hasGranted, resources) ->
                resources.forEach { putBoolean(UnSecurePrefKeys.readResourceGrant(it), hasGranted) }
            }
            writeResourcesByStatus.forEach { (hasGranted, resources) ->
                resources.forEach { putBoolean(UnSecurePrefKeys.writeResourceGrant(it), hasGranted) }
            }
            apply()
        }

        return upToDateGrantedResources to upToDateGrantedResources - lastKnownGrantedResources
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

    internal fun readPermissionsRequiredByResources(resources: Set<VitalResource>): Set<String> {
        return resources
            .flatMapTo(mutableSetOf()) { it.recordTypeDependencies().required }
            .map { HealthPermission.getReadPermission(it) }
            .toSet()
    }

    internal fun readPermissionsToRequestForResources(resources: Set<VitalResource>): Set<String> {
        return resources
            .flatMapTo(mutableSetOf()) { it.recordTypeDependencies().allRecordTypes }
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
    }

    suspend fun syncData(resources: Set<VitalResource>? = null) {
        if (pauseSynchronization) {
            return
        }

        if (VitalClient.Status.SignedIn !in VitalClient.status) {
            return
        }

        if (isSyncWorkerActive()) {
            VitalLogger.getOrCreate().info { "syncData: found active worker; wait for completion" }
            observeSyncWorkerCompleted()
            VitalLogger.getOrCreate().info { "syncData: observed worker completion" }
            return
        }

        try {
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
            startSyncWorker(remappedCandidates, startForeground = true)

            // Wait until the sync worker completes.
            observeSyncWorkerCompleted()

        } catch (e: Exception) {
            vitalLogger.logE("Error syncing data", e)
            _status.tryEmit(SyncStatus.Unknown)
        }
    }

    /**
     * Synchronous, no suspension
     * Does not wait for [ResourceSyncStarter] completion before returning
     * Intended for [SyncBroadcastReceiver] and [processLifecycleObserver].
     *
     * If the last sync occurs recently within the [AUTO_SYNC_THROTTLE] threshold, the sync is
     * skipped.
     *
     * @param beforeEnqueue Block to invoke before enqueuing the work. Will not be invoke if we
     * are not going to spawn a sync worker, e.g., due to [pauseSynchronization].
     */
    internal fun launchAutoSyncWorker(startForeground: Boolean, beforeEnqueue: () -> Unit): Boolean {
        check(Looper.getMainLooper().isCurrentThread)

        // We assume:
        // 1. Permissions in our SharedPerfs are up-to-date
        // 2. ConnectedSource is already created.
        // Fail gracefully if these assumptions are not satisfied.

        if (pauseSynchronization) {
            VitalLogger.getOrCreate().info { "BgSync: skipped by pause" }
            return false
        }

        if (shouldSkipAutoSync) {
            VitalLogger.getOrCreate().info {
                "BgSync: skipped by throttle; last synced at ${Instant.ofEpochMilli(lastAutoSyncedAt)}"
            }
            return false
        }

        if (!context.isConnectedToInternet) {
            VitalLogger.getOrCreate().info { "BgSync: skipped; no internet" }
            return false
        }

        val candidates = resourcesWithReadPermission()
        if (candidates.isEmpty()) {
            VitalLogger.getOrCreate().info { "BgSync: skipped; no grant" }
            return false
        }

        // Remap and deduplicate resources before spawning workers
        // e.g. (Activity | ActiveEnergyBurned | BasalEnergyBurned) => Activity
        val remappedCandidates = candidates.mapTo(mutableSetOf()) { it.remapped() }

        startSyncWorker(remappedCandidates, startForeground, beforeEnqueue)
        return true
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
        endTime: Instant,
        processorOptions: ProcessorOptions = ProcessorOptions(),
    ): ProcessedResourceData {
        return readResourceByTimeRange(
            resource.remapped(),
            startTime = startTime,
            endTime = endTime,
            stage = DataStage.Daily,
            timeZone = TimeZone.getDefault(),
            reader = recordReader,
            processor = recordProcessor,
            processorOptions = processorOptions,
        )
    }

    /**
     * Create a new [ResourceSyncStarter] for the given [resources]. It uses [ExistingWorkPolicy.KEEP],
     * so if there is an active [ResourceSyncStarter], another one would not spawn.
     *
     * The returned Job completes only after [ResourceSyncStarter] has succeeded, cancelled
     * or failed.
     *
     * Worker status change will be mirrored to SDK sync status via [updateStatusFromJob].
     */
    @SuppressLint("MissingPermission")
    private fun startSyncWorker(
        resources: Set<RemappedVitalResource>,
        startForeground: Boolean,
        beforeEnqueue: (() -> Unit)? = null
    ) {
        val input = ResourceSyncStarterInput(resources = resources, startForeground = startForeground)
        val workRequest = OneTimeWorkRequestBuilder<ResourceSyncStarter>()
            .setInputData(input.toData())
            .build()

        val workManager = WorkManager.getInstance(context)
        val work = workManager.beginUniqueWork(
            "ResourceSyncStarter",
            ExistingWorkPolicy.KEEP,
            workRequest
        )

        beforeEnqueue?.invoke()
        work.enqueue()
    }

    private fun cancelSyncWorker() {
        WorkManager.getInstance(context)
            .cancelUniqueWork("ResourceSyncStarter")
    }

    private suspend fun isSyncWorkerActive(): Boolean {
        val work = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkLiveData("ResourceSyncStarter")
            .asFlow()
            .first()

        return work.hasActiveWork()
    }

    internal suspend fun observeSyncWorkerCompleted() {
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkLiveData("ResourceSyncStarter")
            .asFlow()
            .takeWhile { it.hasActiveWork() }
            .collect()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun setupSyncWorkerObservation() {
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkLiveData("ResourceSyncStarter")
            .asFlow()
            // There should only be one [ResourceSyncStarter] at any given time.
            .mapNotNull { it.singleOrNull() }
            .onEach {
                val resources = it.progress.getStringArray("resources") ?: emptyArray()
                for (resource in resources) {
                    updateStatusFromJob(it, VitalResource.valueOf(resource))
                }

                when (it.state) {
                    WorkInfo.State.SUCCEEDED -> _status.tryEmit(SyncStatus.SyncingCompleted)
                    else -> {}
                }
            }
            // This is a permanent observation, not a cancellable task on logOut().
            .launchIn(GlobalScope + Dispatchers.Main)
    }

    private fun updateStatusFromJob(workInfo: WorkInfo, resource: VitalResource) {
        when (workInfo.state) {
            WorkInfo.State.RUNNING -> _status.tryEmit(SyncStatus.ResourceSyncing(resource))
            WorkInfo.State.SUCCEEDED -> _status.tryEmit(SyncStatus.ResourceSyncingComplete(resource))
            WorkInfo.State.FAILED -> _status.tryEmit(SyncStatus.ResourceSyncFailed(resource))
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.CANCELLED -> {}
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
        fun openHealthConnectIntent(context: Context): Intent? {
            return when (isAvailable(context)) {
                HealthConnectAvailability.NotSupportedSDK -> null
                HealthConnectAvailability.NotInstalled -> {
                    Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
                        setPackage("com.android.vending")
                    }
                }
                HealthConnectAvailability.Installed -> {
                    Intent(ACTION_HEALTH_CONNECT_SETTINGS)
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
                bind(instance, coreClient)
            }

            return instance
        }

        /**
         * Must be called exactly once after both Core SDK and Health SDK are initialized.
         */
        @OptIn(DelicateCoroutinesApi::class)
        private fun bind(client: VitalHealthConnectManager, coreClient: VitalClient) {
            coreClient.childSDKShouldReset
                .onEach { client.resetAutoSync() }
                .launchIn(GlobalScope + Dispatchers.Main)
        }
    }
}

private fun List<WorkInfo>?.hasActiveWork(): Boolean {
    if (isNullOrEmpty()) {
        return false
    }

    return firstOrNull { it.state in setOf(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED) } != null
}
