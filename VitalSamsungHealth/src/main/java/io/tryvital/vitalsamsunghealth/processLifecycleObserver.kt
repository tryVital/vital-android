@file:OptIn(ExperimentalVitalApi::class)

package io.tryvital.vitalsamsunghealth

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.tryvital.client.VitalClient
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalhealthcore.model.ProviderAvailability
import io.tryvital.vitalhealthcore.syncProgress.SyncProgress
import io.tryvital.vitalhealthcore.syncProgress.SyncProgress.SystemEventType
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

internal fun processLifecycleObserver(
    manager: VitalSamsungHealthManager
) = object: LifecycleEventObserver {
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event != Lifecycle.Event.ON_START) {
            return
        }

        val isSignedIn = VitalClient.Status.SignedIn in VitalClient.status

        if (VitalSamsungHealthManager.isAvailable(manager.context) != ProviderAvailability.Installed) {
            return
        }

        if (isSignedIn && manager.isBackgroundSyncEnabled) {
            manager.scheduleNextExactAlarm(force = false)
        }

        source.lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            manager.checkAndUpdatePermissions()
            val connectionActive = manager.checkConnectionActive()

            if (!isSignedIn || !connectionActive) {
                return@launch
            }

            manager.launchAutoSyncWorker(
                startForeground = true,
                systemEventType = SyncProgress.SystemEventType.healthConnectCalloutAppLaunching,
            ) {
                VitalLogger.getOrCreate().info { "BgSync: sync triggered by process ON_START" }
            }
        }
    }
}
