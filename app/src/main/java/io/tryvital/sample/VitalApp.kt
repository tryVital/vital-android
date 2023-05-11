package io.tryvital.sample

import android.app.Application
import io.tryvital.client.Environment
import io.tryvital.client.Region
import io.tryvital.client.VitalClient
import io.tryvital.vitaldevices.VitalDeviceManager
import io.tryvital.vitalhealthconnect.VitalHealthConnectManager

const val apiKey = "API_KEY"

val region = Region.EU
val environment = Environment.Sandbox

class VitalApp : Application() {
    val client = VitalClient(
        context = this,
        region = region,
        environment = environment,
        apiKey = apiKey
    )

    val userRepository by lazy {
        UserRepository.create()
    }

    val vitalHealthConnectManager by lazy {
        VitalHealthConnectManager.create(this, apiKey, region, environment)
    }

    val vitalDeviceManager by lazy {
        VitalDeviceManager.create(this)
    }
}