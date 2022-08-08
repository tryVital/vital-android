package io.tryvital.client

import android.content.Context
import io.tryvital.client.dependencies.Dependencies
import io.tryvital.client.services.*

class VitalClient(
    context: Context,
    region: Region,
    environment: Environment = Environment.sandbox
) {

    private val dependencies: Dependencies by lazy {
        Dependencies(context, region, environment)
    }

    private val activityService by lazy {
        ActivityService.create(dependencies.retrofit)
    }

    private val bodyService by lazy {
        BodyService.create(dependencies.retrofit)
    }

    private val linkService by lazy {
        LinkService.create(dependencies.retrofit)
    }

    private val profileService by lazy {
        ProfileService.create(dependencies.retrofit)
    }

    private val sleepService by lazy {
        SleepService.create(dependencies.retrofit)
    }

    private val testKitService by lazy {
        TestkitService.create(dependencies.retrofit)
    }

    private val userService by lazy {
        UserService.create(dependencies.retrofit)
    }

}