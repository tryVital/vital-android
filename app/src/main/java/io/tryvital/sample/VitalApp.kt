package io.tryvital.sample

import android.app.Application
import io.tryvital.client.Environment
import io.tryvital.client.Region
import io.tryvital.client.VitalClient
import io.tryvital.vitaldevices.VitalDeviceManager
import io.tryvital.vitalhealthconnect.VitalHealthConnectManager

class VitalApp : Application() {
    val client = VitalClient(
        context = this,
        region = Region.EU,
        environment = Environment.Sandbox,
        apiKey = "sk_eu_S5Ld..." //TODO replace it with your own api key
    )

    val userRepository by lazy {
        UserRepository.create()
    }

    val vitalHealthConnectManager by lazy {
        VitalHealthConnectManager.create(this, client)
    }

    val vitalDeviceManager by lazy {
        VitalDeviceManager.create(this)
    }
}