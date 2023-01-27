package io.tryvital.client.utils

import android.util.Log

private const val VITAL_LOGGER = "vital-logger"

class VitalLogger private constructor(var enabled: Boolean = false) {

    fun logI(message: String) {
        if (enabled) {
            Log.i(VITAL_LOGGER, message)
        }
    }

    fun logE(message: String, throwable: Throwable? = null) {
        if (enabled) {
            Log.e(VITAL_LOGGER, message, throwable)
        }
    }

    companion object {
        private var instance: VitalLogger? = null

        fun create(): VitalLogger {
            if (instance == null) {
                synchronized(VitalLogger::class.java) {
                    if (instance == null) {
                        instance = VitalLogger()
                    }
                }
            }
            return instance!!
        }
    }
}