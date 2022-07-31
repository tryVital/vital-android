package io.tryvital.client

import android.content.Context
import io.tryvital.client.dependencies.Dependencies
import io.tryvital.client.services.LinkService
import io.tryvital.client.services.UserService

class VitalClient(
    context: Context,
    region: Region,
    environment: Environment = Environment.sandbox
) {

    private val dependencies: Dependencies by lazy {
        Dependencies(context, region, environment)
    }

    private val userService by lazy {
        UserService.create(dependencies.retrofit)
    }

    private val linkService by lazy {
        LinkService.create(dependencies.retrofit)
    }


}