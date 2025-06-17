package io.tryvital.vitalhealthconnect.syncProgress

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.tryvital.vitalhealthconnect.model.BackfillType
import io.tryvital.vitalhealthconnect.model.RemappedVitalResource
import io.tryvital.vitalhealthconnect.model.backfillType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Date
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private object SyncProgressGistKey :
    VitalGistStorageKey<SyncProgress>(key = "vital_health_connect_progress")

internal class SyncProgressStore private constructor(private val storage: VitalGistStorage) {
    private val state = MutableStateFlow(SyncProgress())
    private var hasChanges = false
    private val lock = ReentrantLock()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val latestProcessState = AtomicReference<Lifecycle.State?>(null)

    init {
        // Restore previous snapshot if present
        state.value = storage.get(SyncProgressGistKey) ?: SyncProgress()

        // Periodic flush every 10s
        scope.launch {
            while (isActive) {
                delay(10_000)
                flush()
            }
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : LifecycleEventObserver {
                override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                    val state = source.lifecycle.currentState
                    latestProcessState.set(state)

                    if (event == Lifecycle.Event.ON_PAUSE) {
                        this@SyncProgressStore.flush()
                    }
                }
            }
        )
    }

    fun get(): SyncProgress = lock.withLock { state.value }

    fun flow(): Flow<SyncProgress> = state

    fun flush() = lock.withLock {
        if (hasChanges) {
            storage.set(state.value, SyncProgressGistKey)
            hasChanges = false
        }
    }

    fun clear() = lock.withLock {
        state.value = SyncProgress()
        storage.set(null, SyncProgressGistKey)
    }

    fun recordSync(
        id: SyncProgress.SyncID,
        status: SyncProgress.SyncStatus,
        errorDetails: String? = null,
        dataCount: Int = 0
    ) {
        val augmentedTags = id.tags.toMutableSet().also(::insertAppStateTags)
        mutate(listOf(id.resource.backfillType)) { res ->
            val now = Instant.now()
            val latest = res.syncs.lastOrNull()
            val appendsToLatest = latest?.start == id.start ||
                    (status == SyncProgress.SyncStatus.deprioritized &&
                            latest?.lastStatus == SyncProgress.SyncStatus.deprioritized)

            if (appendsToLatest && latest != null) {
                latest.append(status, now, errorDetails)
                latest.tags += augmentedTags
                latest.dataCount += dataCount

                when (status) {
                    SyncProgress.SyncStatus.completed,
                    SyncProgress.SyncStatus.error,
                    SyncProgress.SyncStatus.cancelled,
                    SyncProgress.SyncStatus.noData -> latest.end = now

                    SyncProgress.SyncStatus.deprioritized ->
                        latest.pruneDeprioritizedStatus(afterFirst = 10)

                    else -> Unit
                }
            } else {
                if (res.syncs.size > 50) res.syncs.removeAt(0)
                res.syncs += SyncProgress.Sync(
                    start = id.start,
                    statuses = mutableListOf(
                        SyncProgress.Event(id.start, status, errorDetails = errorDetails)
                    ),
                    tags = augmentedTags,
                    dataCount = dataCount
                )
            }

            res.dataCount += dataCount
        }
    }

    fun recordAsk(resources: Collection<RemappedVitalResource>) {
        val date = Instant.now()
        mutate(resources.map { it.wrapped.backfillType }) { it.firstAsked = date }
    }

    fun recordSystem(
        resources: Collection<RemappedVitalResource>,
        eventType: SyncProgress.SystemEventType
    ) {
        mutate(resources.map { it.wrapped.backfillType }) { res ->
            val now = Instant.now()
            val last = res.systemEvents.lastOrNull()
            val within5Seconds = last != null &&
                    last.type == eventType &&
                    now.epochSecond - last.timestamp.epochSecond <= 5

            if (within5Seconds) {
                last!!.count += 1
            } else {
                if (res.systemEvents.size > 25) res.systemEvents.removeAt(0)
                res.systemEvents += SyncProgress.Event(now, eventType)
            }
        }
    }

    private inline fun mutate(
        backfillTypes: Collection<BackfillType>,
        action: (SyncProgress.Resource) -> Unit
    ) {
        lock.withLock {
            val snapshot = state.value.copy()
            backfillTypes.forEach { type ->
                val res = snapshot.backfillTypes.getOrPut(type) { SyncProgress.Resource() }
                action(res)
            }
            state.value = snapshot
            hasChanges = true
        }
    }

    private fun insertAppStateTags(tags: MutableSet<SyncProgress.SyncContextTag>) {
        val processState = latestProcessState.get()

        when (processState) {
            null -> {
                tags += SyncProgress.SyncContextTag.appLaunching
            }

            Lifecycle.State.INITIALIZED, Lifecycle.State.CREATED -> {
                tags += SyncProgress.SyncContextTag.background
            }

            Lifecycle.State.STARTED, Lifecycle.State.RESUMED -> {
                tags += SyncProgress.SyncContextTag.foreground
            }

            Lifecycle.State.DESTROYED -> {
                // Impossible path for ProcessLifecycleOwner
            }
        }
    }

    companion object {
        private var shared: SyncProgressStore? = null

        fun getOrCreate(context: Context) = synchronized(this) {
            when (val shared = this.shared) {
                null -> {
                    this.shared = SyncProgressStore(
                        VitalGistStorage.getOrCreate(context)
                    )
                    return this.shared!!
                }

                else -> shared
            }
        }
    }
}
