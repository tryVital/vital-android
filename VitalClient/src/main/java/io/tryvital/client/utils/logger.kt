package io.tryvital.client.utils

import android.util.Log

class VitalLogger private constructor(var enabled: Boolean = false) {

    fun logI(message: String, source: String? = null) {
        if (enabled) {
            Log.i(source ?: "vital-logger", message)
        }
    }

    fun logE(message: String, source: String? = null, throwable: Throwable? = null) {
        if (enabled) {
            Log.e(source ?: "vital-logger", message, throwable)
        }
    }

    companion object {
        private val instance: VitalLogger = VitalLogger()

        fun create() = instance
    }
}