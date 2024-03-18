package io.tryvital.sample

import android.content.Context
import androidx.startup.Initializer
import io.tryvital.client.VitalClient
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalhealthconnect.VitalHealthConnectManager

class VitalSDKInitializer : Initializer<VitalHealthConnectManager> {
    override fun create(context: Context): VitalHealthConnectManager {
        return VitalHealthConnectManager.getOrCreate(context).apply {
            if (VitalClient.Status.SignedIn in VitalClient.status) {
                configureHealthConnectClient(logsEnabled = true)
            }
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}
