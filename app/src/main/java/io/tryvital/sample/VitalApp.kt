package io.tryvital.sample

import android.app.Application
import io.tryvital.client.Environment
import io.tryvital.client.Region
import io.tryvital.client.VitalClient
import io.tryvital.vitaldevices.VitalDeviceManager
import io.tryvital.vitalhealthconnect.VitalHealthConnectManager

const val apiKey = "API_KEY"

val region = Region.US
val environment = Environment.Sandbox

class VitalApp : Application() {
    val client = VitalClient.getOrCreate(applicationContext).apply {
        configure(region, environment, apiKey)
    }

    val userRepository by lazy {
        UserRepository.create()
    }

    val vitalHealthConnectManager by lazy {
        VitalHealthConnectManager.getOrCreate(this).apply {
            configureHealthConnectClient()
        }
    }

    val vitalDeviceManager by lazy {
        VitalDeviceManager.create(this)
    }
}