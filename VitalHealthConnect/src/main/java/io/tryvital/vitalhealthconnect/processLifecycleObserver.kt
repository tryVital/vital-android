@file:OptIn(ExperimentalVitalApi::class)

package io.tryvital.vitalhealthconnect

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.tryvital.client.VitalClient
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalhealthconnect.model.HealthConnectAvailability
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

internal fun processLifecycleObserver(
    manager: VitalHealthConnectManager
) = object: LifecycleEventObserver {
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event != Lifecycle.Event.ON_START) {
            return
        }
        if (VitalClient.Status.SignedIn !in VitalClient.status) {
            return
        }
        if (VitalHealthConnectManager.isAvailable(manager.context) != HealthConnectAvailability.Installed) {
            return
        }

        if (manager.isBackgroundSyncEnabled) {
            manager.scheduleNextExactAlarm(force = false)
        }

        source.lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            manager.checkAndUpdatePermissions()
            manager.launchAutoSyncWorker(startForeground = true) {
                VitalLogger.getOrCreate().info { "BgSync: sync triggered by process ON_START" }
            }
        }
    }
}
