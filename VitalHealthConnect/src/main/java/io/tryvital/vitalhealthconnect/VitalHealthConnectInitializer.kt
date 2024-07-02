package io.tryvital.vitalhealthconnect

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.lifecycle.ProcessLifecycleInitializer
import androidx.startup.Initializer
import androidx.work.WorkManagerInitializer
import io.tryvital.client.BuildConfig
import io.tryvital.client.utils.VitalLogger

/**
 * AndroidX Startup Initializer for [VitalHealthConnectManager.getOrCreate].
 */
@Suppress("unused")
class VitalHealthConnectInitializer: Initializer<VitalHealthConnectManager> {
    override fun create(context: Context): VitalHealthConnectManager {
        VitalLogger.getOrCreate().enabled = BuildConfig.DEBUG || (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return VitalHealthConnectManager.getOrCreate(context)
    }

    override fun dependencies(): MutableList<Class<out Initializer<*>>> = mutableListOf(
        WorkManagerInitializer::class.java,
        ProcessLifecycleInitializer::class.java,
    )
}
