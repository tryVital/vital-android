package io.tryvital.vitalsamsunghealth

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.lifecycle.ProcessLifecycleInitializer
import androidx.startup.Initializer
import androidx.work.WorkManagerInitializer
import io.tryvital.client.BuildConfig
import io.tryvital.client.utils.VitalLogger

/**
 * AndroidX Startup Initializer for [VitalSamsungHealthManager.getOrCreate].
 */
@Suppress("unused")
class VitalSamsungHealthInitializer: Initializer<VitalSamsungHealthManager> {
    override fun create(context: Context): VitalSamsungHealthManager {
        VitalLogger.getOrCreate().enabled = BuildConfig.DEBUG || (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return VitalSamsungHealthManager.getOrCreate(context)
    }

    override fun dependencies(): MutableList<Class<out Initializer<*>>> = mutableListOf(
        WorkManagerInitializer::class.java,
        ProcessLifecycleInitializer::class.java,
    )
}
