package io.tryvital.sample

import android.app.Application
import io.tryvital.client.Configuration
import io.tryvital.client.Environment
import io.tryvital.client.Region
import io.tryvital.client.VitalClient
import io.tryvital.vitaldevices.VitalDeviceManager
import io.tryvital.vitalhealthconnect.VitalHealthConnectManager

const val apiKey = "sk_eu_S5LdXTS_CAtdFrkX9OYsiVq_jGHaIXtZyBPbBtPkzhA"

val region = Region.EU
val environment = Environment.Sandbox

class VitalApp : Application() {
    val client = VitalClient.getOrCreate(this).apply {
        configure(Configuration(region, environment, apiKey))
    }

    val userRepository by lazy {
        UserRepository.create()
    }

    val vitalHealthConnectManager by lazy {
        VitalHealthConnectManager.create(this)
    }

    val vitalDeviceManager by lazy {
        VitalDeviceManager.create(this)
    }
}