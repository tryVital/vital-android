package io.tryvital.client

import android.content.Context
import io.tryvital.client.dependencies.Dependencies
import io.tryvital.client.healthconnect.HealthConnectManager
import io.tryvital.client.services.*

@Suppress("unused")
class VitalClient(
    context: Context,
    region: Region,
    environment: Environment = Environment.Sandbox,
    apiKey: String,
) {

    private val dependencies: Dependencies by lazy {
        Dependencies(context, region, environment, apiKey)
    }

    val healthConnectManager by lazy {
        HealthConnectManager.create(
            dependencies.healthConnectClientProvider,
            summaryService,
            linkService,
            dependencies.recordProcessor,
            dependencies.vitalLogger
        )
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

    private val summaryService by lazy {
        SummaryService.create(dependencies.retrofit)
    }
}