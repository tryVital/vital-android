package io.tryvital.client

import android.content.Context
import android.content.SharedPreferences
import io.tryvital.client.dependencies.Dependencies
import io.tryvital.client.services.*

const val VITAL_PERFS_FILE_NAME: String = "vital_health_connect_prefs"

@Suppress("unused")
class VitalClient(
    context: Context,
    val region: Region,
    val environment: Environment = Environment.Sandbox,
    val apiKey: String,
) {

    val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(
            VITAL_PERFS_FILE_NAME, Context.MODE_PRIVATE
        )
    }

    private val dependencies: Dependencies by lazy {
        Dependencies(context, region, environment, apiKey)
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

    fun cleanUp() {
        sharedPreferences.edit().clear().apply()
    }
}
