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

        manager.scheduleNextExactAlarm(force = false)

        source.lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            manager.checkAndUpdatePermissions()

            if (
                VitalClient.Status.SignedIn in VitalClient.status
                && VitalHealthConnectManager.isAvailable(manager.context) == HealthConnectAvailability.Installed
            ) {
                manager.launchAutoSyncWorker {
                    VitalLogger.getOrCreate().info { "BgSync: triggered by process ON_START" }
                }
            }
        }
    }
}