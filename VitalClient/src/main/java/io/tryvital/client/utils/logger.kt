package io.tryvital.client.utils

import android.util.Log

private const val VITAL_LOGGER = "vital-logger"

class VitalLogger private constructor() {
    @Volatile
    var enabled: Boolean = false

    fun info(message: () -> String) {
        if (enabled) {
            Log.i(VITAL_LOGGER, message())
        }
    }

    fun exception(throwable: Throwable, message: () -> String) {
        if (enabled) {
            Log.e(VITAL_LOGGER, message(), throwable)
        }
    }

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

        fun getOrCreate(): VitalLogger = synchronized(VitalLogger) {
            if (instance == null) {
                instance = VitalLogger()
            }
            return instance!!
        }
    }
}
