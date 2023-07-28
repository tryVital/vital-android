package io.tryvital.vitalhealthconnect

import android.content.Context
import androidx.startup.Initializer
import io.tryvital.client.*
import io.tryvital.client.utils.VitalLogger

@Suppress("unused")
class VitalHealthInitializer : Initializer<VitalHealthAutoStarter> {
    override fun create(context: Context): VitalHealthAutoStarter {
        return VitalHealthAutoStarter(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}

class VitalHealthAutoStarter(private val context: Context) {
    private val vitalLogger = VitalLogger.getOrCreate()

    init {
        // TODO: Reimplement this after we have singletonized the SDK
    }
}
