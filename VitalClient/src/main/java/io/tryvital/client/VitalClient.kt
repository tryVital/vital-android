package io.tryvital.client

import android.content.Context
import io.tryvital.client.dependencies.Dependencies
import io.tryvital.client.services.*
import io.tryvital.client.utils.VitalLogger
import java.util.concurrent.atomic.AtomicReference

@Suppress("unused")
class VitalClient private constructor (context: Context) {
    private val _configuration = AtomicReference<Configuration?>(null)

    val configuration: Configuration? get() = _configuration.get()
    val isConfigured: Boolean get() = _configuration.get() != null

    private val dependencies: Dependencies by lazy {
        Dependencies(context, _configuration)
    }

    val activityService by lazy {
        ActivityService.create(dependencies.retrofit)
    }

    val bodyService by lazy {
        BodyService.create(dependencies.retrofit)
    }

    val linkService by lazy {
        LinkService.create(dependencies.retrofit)
    }

    val profileService by lazy {
        ProfileService.create(dependencies.retrofit)
    }

    val sleepService by lazy {
        SleepService.create(dependencies.retrofit)
    }

    val testKitService by lazy {
        TestkitService.create(dependencies.retrofit)
    }

    val userService by lazy {
        UserService.create(dependencies.retrofit)
    }

    val summaryService by lazy {
        SummaryService.create(dependencies.retrofit)
    }

    val vitalsService by lazy {
        VitalsService.create(dependencies.retrofit)
    }

    val vitalLogger by lazy {
        dependencies.vitalLogger
    }

    fun configure(configuration: Configuration) = synchronized(this) {
        _configuration.set(configuration)
    }

    companion object {
        private var sharedInstance: VitalClient? = null

        fun getOrCreate(context: Context): VitalClient = synchronized(this) {
            var instance = sharedInstance
            if (instance != null) {
                return instance
            } else {
                instance = VitalClient(context)
                sharedInstance = instance
                return instance
            }
        }

        fun getForWorker(context: Context): VitalClient = synchronized(this) {
            val client = getOrCreate(context)
            if (!client.isConfigured) {
                automaticConfiguration(context)
            }
            return if (client.isConfigured) client else throw VitalClientNotConfigured()
        }

        @Suppress("MemberVisibilityCanBePrivate")
        fun automaticConfiguration(context: Context): Boolean = synchronized(this) {
            val client = getOrCreate(context)
            val logger = VitalLogger.getOrCreate()

            // TODO: Move SharedPeferences to VitalCore

            return false
        }
    }
}
