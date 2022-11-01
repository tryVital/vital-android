package io.tryvital.client.utils

import android.util.Log

class VitalLogger(var enabled: Boolean = false) {

    fun logI(message: String, source: String? = null) {
        if (enabled) {
            Log.i(source ?: "vital-logger", message)
        }
    }

    companion object {
        private val instance: VitalLogger = VitalLogger()

        fun create() = instance
    }
}