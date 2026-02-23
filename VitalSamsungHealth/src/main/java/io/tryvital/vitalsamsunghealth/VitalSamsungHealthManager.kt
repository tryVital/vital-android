@file:OptIn(ExperimentalVitalApi::class)

package io.tryvital.vitalsamsunghealth

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Looper
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.asFlow
import androidx.work.*
import com.samsung.android.sdk.health.data.error.AuthorizationException
import com.samsung.android.sdk.health.data.error.PlatformInternalException
import com.samsung.android.sdk.health.data.permission.AccessType
import io.tryvital.client.VitalClient
import io.tryvital.client.createConnectedSourceIfNotExist
import io.tryvital.client.services.VitalPrivateApi
import io.tryvital.client.services.data.*
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalhealthcore.model.ConnectionPolicy
import io.tryvital.vitalhealthcore.model.ConnectionStatus
import io.tryvital.vitalhealthcore.model.PermissionStatus
import io.tryvital.vitalhealthcore.model.ProviderAvailability
import io.tryvital.vitalhealthcore.model.RemappedVitalResource
import io.tryvital.vitalhealthcore.model.SyncStatus
import io.tryvital.vitalhealthcore.model.VitalResource
import io.tryvital.vitalhealthcore.records.RecordUploader
import io.tryvital.vitalhealthcore.exceptions.ConnectionDestroyed
import io.tryvital.vitalsamsunghealth.model.*
import io.tryvital.vitalsamsunghealth.model.processedresource.ProcessedResourceData
import io.tryvital.vitalsamsunghealth.records.*
import io.tryvital.vitalhealthcore.syncProgress.SyncProgress
import io.tryvital.vitalhealthcore.syncProgress.SyncProgressReporter
import io.tryvital.vitalhealthcore.syncProgress.SyncProgressStore
import io.tryvital.vitalsamsunghealth.workers.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference


@Suppress("MemberVisibilityCanBePrivate")
class VitalSamsungHealthManager private constructor(
    internal val context: Context,
    internal val samsungHealthClientProvider: SamsungHealthClientProvider,
    internal val vitalClient: VitalClient,
    internal val recordReader: RecordReader,
    internal val recordProcessor: RecordProcessor,
    internal val recordUploader: RecordUploader,
    internal val localSyncStateManager: LocalSyncStateManager,
    internal val syncProgressReporter: SyncProgressReporter,
    internal val syncProgressStore: SyncProgressStore,
) {
    val sharedPreferences get() = vitalClient.sharedPreferences
    private val permissionMutex = Mutex()
    private val connectionMutex = Mutex()

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

    val connectionStatus get() = localSyncStateManager.connectionStatus

    private val vitalLogger = vitalClient.vitalLogger

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
        vitalLogger.logI("VitalSamsungHealthManager initialized")
        _status.tryEmit(SyncStatus.Unknown)

        taskScope.launch(Dispatchers.Main.immediate) {
            processLifecycleObserver = processLifecycleObserver(this@VitalSamsungHealthManager)
                .also { ProcessLifecycleOwner.get().lifecycle.addObserver(it) }
        }

        setupSyncWorkerObservation()
    }

    private suspend fun resetAutoSync() {
        cancelSyncWorker().await()
        resetSyncProgress()
        disableBackgroundSync()
        syncProgressStore.clear()
        taskScope.cancel()
        taskScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Suppress("unused")
    fun createPermissionRequestContract(
        readResources: Set<VitalResource> = emptySet(),
    ): ActivityResultContract<Unit, Deferred<PermissionOutcome>>
        = VitalPermissionRequestContract(readResources, this, taskScope)

    @Suppress("unused")
    fun hasAskedForPermission(resource: VitalResource): Boolean {
        return sharedPreferences.getBoolean(UnSecurePrefKeys.readResourceGrant(resource), false)
    }

    @Suppress("unused")
    fun permissionStatus(resources: List<VitalResource>): Map<VitalResource, PermissionStatus> {
        return resources.associateWith { resource ->
            val hasAsked =
                sharedPreferences.getBoolean(UnSecurePrefKeys.readResourceGrant(resource), false)
            if (hasAsked) PermissionStatus.Asked else PermissionStatus.NotAsked
        }
    }

    fun resourcesWithReadPermission(): Set<VitalResource> {
        return VitalResource.values()
            .filter { it.supportedBySamsungDataApi() }
            .filter { sharedPreferences.getBoolean(UnSecurePrefKeys.readResourceGrant(it), false) }
            .toSet()
    }

    @Suppress("unused")
    suspend fun reloadPermissions() {
        checkAndUpdatePermissions()
    }

    internal fun resetSyncProgress() {
        val editor = sharedPreferences.edit()
        for (resource in VitalResource.values()) {
            editor.remove(UnSecurePrefKeys.syncStateKey(resource))
            editor.remove(UnSecurePrefKeys.monitoringTypesKey(resource))
        }
        editor.apply()
        localSyncStateManager.setPersistedLocalSyncState(null)
    }

    internal suspend fun checkConnectionActive(): Boolean {
        // Try to revalidate the LocalSyncState if a revalidation is due.
        // Gracefully ignore the exception thrown by getLocalSyncState().
        runCatching {
            localSyncStateManager.getLocalSyncState()
        }

        return localSyncStateManager.connectionStatus.value.let {
            it == ConnectionStatus.AutoConnect || it == ConnectionStatus.Connected
        }
    }

    internal suspend fun checkAndUpdatePermissions(): Pair<Set<VitalResource>, Set<VitalResource>> = permissionMutex.withLock {
        if (isAvailable(context) != ProviderAvailability.Installed) {
            return emptySet<VitalResource>() to emptySet()
        }

        val lastKnownGrantedResources = resourcesWithReadPermission()
        val currentGrants = try {
            getGrantedPermissions(context)
        } catch (_: AuthorizationException) {
            return emptySet<VitalResource>() to emptySet<VitalResource>()
        }

        val readResourcesByStatus = VitalResource.values().groupBy { resource ->
            if (!resource.supportedBySamsungDataApi()) {
                return@groupBy false
            }
            return@groupBy resource.recordTypeDependencies().isResourceActive { recordType ->
                val readPermission = permissionForRecordType(recordType, AccessType.READ)
                    ?.let { permissionKey(it.dataType, it.accessType) }
                    ?: return@isResourceActive false
                return@isResourceActive readPermission in currentGrants
            }
        }

        val upToDateGrantedResources = readResourcesByStatus.getOrDefault(true, listOf()).toSet()

        sharedPreferences.edit().run {
            readResourcesByStatus.forEach { (hasGranted, resources) ->
                resources.forEach { putBoolean(UnSecurePrefKeys.readResourceGrant(it), hasGranted) }
            }
            apply()
        }

        return upToDateGrantedResources to upToDateGrantedResources - lastKnownGrantedResources
    }

    internal fun readPermissionsRequiredByResources(resources: Set<VitalResource>): Set<String> {
        return resources
            .flatMapTo(mutableSetOf()) { it.recordTypeDependencies().required }
            .mapNotNull {
                permissionForRecordType(it, AccessType.READ)?.let { permission ->
                    permissionKey(permission.dataType, permission.accessType)
                }
            }
            .toSet()
    }

    internal fun readPermissionsToRequestForResources(resources: Set<VitalResource>): Set<String> {
        return resources
            .flatMapTo(mutableSetOf()) { it.recordTypeDependencies().allRecordTypes }
            .mapNotNull {
                permissionForRecordType(it, AccessType.READ)?.let { permission ->
                    permissionKey(permission.dataType, permission.accessType)
                }
            }
            .toSet()
    }

    /**
     * @param syncNotificationBuilder When you provide a builder, Vital SDK will start resource
     * sync workers as foreground services, with the user-visible notification info returned by
     * your [SyncNotificationBuilder]. This allows the sync process to continue even when the
     * user has switched away from your app.
     */
    @SuppressLint("ApplySharedPref")
    fun configureSamsungHealthClient(
        logsEnabled: Boolean = false,
        syncOnAppStart: Boolean = true,
        numberOfDaysToBackFill: Int = 30,
        syncNotificationBuilder: SyncNotificationBuilder? = null,
        connectionPolicy: ConnectionPolicy = ConnectionPolicy.AutoConnect,
    ) {
        if (VitalClient.Status.Configured !in VitalClient.status) {
            throw IllegalStateException("VitalClient has not been configured.")
        }

        if (syncNotificationBuilder != null) {
            setSyncNotificationBuilder(syncNotificationBuilder)
        }

        vitalLogger.enabled = logsEnabled

        localSyncStateManager.setConnectionPolicy(connectionPolicy)
        sharedPreferences.edit()
            .putBoolean(UnSecurePrefKeys.loggerEnabledKey, logsEnabled)
            .putBoolean(UnSecurePrefKeys.syncOnAppStartKey, syncOnAppStart)
            .putInt(UnSecurePrefKeys.numberOfDaysToBackFillKey, numberOfDaysToBackFill)
            .apply()

        taskScope.launch {
            // Try to revalidate the LocalSyncState if a revalidation is due.
            // Gracefully ignore the exception thrown by getLocalSyncState().
            runCatching {
                localSyncStateManager.getLocalSyncState()
            }
        }
    }

    /**
     * Setup a Health Connect connection with this device.
     *
     * @precondition You must configure the Health SDK to use [ConnectionPolicy.Explicit].
     */
    suspend fun connect(): Unit = connectionMutex.withLock {
        if (localSyncStateManager.connectionPolicy == ConnectionPolicy.AutoConnect) {
            throw IllegalStateException("connect() only works with ConnectionPolicy.Explicit.")
        }

        @OptIn(VitalPrivateApi::class)
        vitalClient.createConnectedSourceIfNotExist(ManualProviderSlug.SamsungHealth)

        try {
            localSyncStateManager.getLocalSyncState(forceRemoteCheck = true)

            // Check if there are already granted read permissions.
            // If so, trigger a data sync immediately.
            val (granted, _) = checkAndUpdatePermissions()
            if (granted.isNotEmpty()) {
                taskScope.launch {
                    syncData()
                }
            }

        } catch (e: ConnectionDestroyed) {
            throw IllegalStateException("connection has been destroyed concurrently through the Junction API")
        }
    }

    /**
     * Disconnect the active Health Connect connection on this device.
     *
     * @precondition You must configure the Health SDK to use [ConnectionPolicy.Explicit].
     */
    suspend fun disconnect(): Unit = connectionMutex.withLock {
        if (localSyncStateManager.connectionPolicy == ConnectionPolicy.AutoConnect) {
            throw IllegalStateException("connect() only works with ConnectionPolicy.Explicit.")
        }

        vitalClient.userService.deregisterProvider(provider = ProviderSlug.SamsungHealth)

        try {
            localSyncStateManager.getLocalSyncState(forceRemoteCheck = true)

            // Connection is still active unexpectedly.
            throw IllegalStateException("connection has been re-instated concurrently by another SDK installation")

        } catch (e: ConnectionDestroyed) {
            // Connection is destroyed as expected
            resetAutoSync()
            return
        }
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
            startSyncWorker(
                remappedCandidates,
                startForeground = true,
                tags = listOf(SyncProgress.SyncContextTag.manual)
            )

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
    internal fun launchAutoSyncWorker(
        startForeground: Boolean,
        systemEventType: SyncProgress.SystemEventType? = null,
        beforeEnqueue: () -> Unit
    ): Boolean {
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

        if (systemEventType != null) {
            syncProgressStore.recordSystem(remappedCandidates, systemEventType)
        }

        startSyncWorker(remappedCandidates, startForeground, emptyList(), beforeEnqueue)
        return true
    }

    @Suppress("unused")
    suspend fun read(
        resource: VitalResource,
        startTime: Instant,
        endTime: Instant,
        processorOptions: ProcessorOptions = ProcessorOptions(),
    ): ProcessedResourceData {
        require(resource.supportedBySamsungDataApi()) {
            "Resource ${resource.name} is not supported by Samsung Health Data API."
        }
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
        tags: List<SyncProgress.SyncContextTag> = emptyList(),
        beforeEnqueue: (() -> Unit)? = null
    ) {
        val input = ResourceSyncStarterInput(resources = resources, startForeground = startForeground, tags = tags)
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

    private fun cancelSyncWorker(): Operation {
        return WorkManager.getInstance(context)
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
        private const val samsungHealthPackageName = "com.sec.android.app.shealth"

        private val _customSyncNotificationBuilder = AtomicReference<SyncNotificationBuilder?>(null)

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
        fun syncNotificationBuilder(context: Context): SyncNotificationBuilder {
            // IMPORTANT: The logic here should be kept to the minimum.
            //
            // This runs between context.startForegroundService -> ServiceCompact.startForeground,
            // so every additional call increases the chance of breaching the 5-second timeout
            // imposed by the Android OS.
            return _customSyncNotificationBuilder.get()
                ?: DefaultSyncNotificationBuilder.getOrCreate(context)
        }

        fun setSyncNotificationBuilder(builder: SyncNotificationBuilder) {
            _customSyncNotificationBuilder.set(builder)
        }

        @SuppressLint("StaticFieldLeak")
        private var sharedInstance: VitalSamsungHealthManager? = null

        @Suppress("unused")
        fun isAvailable(context: Context): ProviderAvailability {
            if (context.packageManager == null) return ProviderAvailability.NotSupportedSDK
            return try {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(samsungHealthPackageName, 0)

                // Test if the Samsung Health SDK is usable.
//                HealthDataService.getStore(context).getDeviceManager().getLocalDeviceAsync().get(1, TimeUnit.SECONDS)

                ProviderAvailability.Installed
            } catch (_: TimeoutException) {
                ProviderAvailability.Installed
            } catch (_: PackageManager.NameNotFoundException) {
                ProviderAvailability.NotInstalled
            } catch (e: PlatformInternalException) {
                if (e.errorCode == 3001) {
                    ProviderAvailability.NotSupportedSDK
                } else {
                    Log.e("VitalSamsungHealthManager", "unexpected sdk error", e)
                    ProviderAvailability.NotInstalled
                }
            } catch (e: Throwable) {
                Log.e("VitalSamsungHealthManager", "unexpected sdk error", e)
                ProviderAvailability.NotInstalled
            }
        }

        @Suppress("unused")
        fun openSamsungHealthIntent(context: Context): Intent? {
            return when (isAvailable(context)) {
                ProviderAvailability.NotSupportedSDK -> null
                ProviderAvailability.NotInstalled -> {
                    Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://play.google.com/store/apps/details?id=$samsungHealthPackageName")
                        setPackage("com.android.vending")
                    }
                }
                ProviderAvailability.Installed -> {
                    context.packageManager.getLaunchIntentForPackage(samsungHealthPackageName)
                }
            }
        }

        fun getOrCreate(context: Context): VitalSamsungHealthManager = synchronized(VitalSamsungHealthManager) {
            val appContext = context.applicationContext
            var instance = sharedInstance

            if (instance == null) {
                val coreClient = VitalClient.getOrCreate(appContext)
                val samsungHealthClientProvider = SamsungHealthClientProvider()

                val localSyncStateManager = LocalSyncStateManager(coreClient, VitalLogger.getOrCreate(), coreClient.sharedPreferences)
                val syncProgressStore = SyncProgressStore.getOrCreate(appContext, ManualProviderSlug.SamsungHealth)

                instance = VitalSamsungHealthManager(
                    appContext,
                    samsungHealthClientProvider,
                    coreClient,
                    HealthConnectRecordReader(appContext, samsungHealthClientProvider),
                    HealthConnectRecordProcessor(
                        HealthConnectRecordReader(appContext, samsungHealthClientProvider),
                        HealthConnectRecordAggregator(appContext, samsungHealthClientProvider),
                    ),
                    VitalClientRecordUploader(coreClient),
                    localSyncStateManager,
                    SyncProgressReporter(
                        syncProgressStore,
                        coreClient,
                        localSyncStateManager,
                        ManualProviderSlug.SamsungHealth
                    ),
                    syncProgressStore,
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
        private fun bind(client: VitalSamsungHealthManager, coreClient: VitalClient) {
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
