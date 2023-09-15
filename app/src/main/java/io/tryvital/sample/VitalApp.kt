package io.tryvital.sample

import android.app.Application
import io.tryvital.client.Environment
import io.tryvital.client.Region
import io.tryvital.client.VitalClient
import io.tryvital.vitaldevices.VitalDeviceManager
import io.tryvital.vitalhealthconnect.VitalHealthConnectManager

const val apiKey = "sk_us_ezSsjWaS1R20tQZKxQZRTFFLP1FrdmMFU2atQsWwY_8"

val region = Region.US
val environment = Environment.Dev

class VitalApp : Application() {
    val client by lazy {
        VitalClient.getOrCreate(applicationContext).apply {
            VitalClient.configure(applicationContext, region, environment, apiKey)
        }
    }

    val userRepository by lazy {
        UserRepository.create()
    }

    val vitalHealthConnectManager by lazy {
        check(VitalClient.Status.Configured in VitalClient.status)
        VitalHealthConnectManager.getOrCreate(this).apply {
            configureHealthConnectClient(
                syncNotificationBuilder = VitalSyncNotificationBuilder
            )
        }
    }

    val vitalDeviceManager by lazy {
        VitalDeviceManager.create(this)
    }
}