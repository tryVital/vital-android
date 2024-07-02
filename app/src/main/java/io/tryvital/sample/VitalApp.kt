package io.tryvital.sample

import android.app.Application
import android.util.Log
import io.tryvital.client.Environment
import io.tryvital.client.Region
import io.tryvital.client.VitalClient
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitaldevices.VitalDeviceManager
import io.tryvital.vitalhealthconnect.VitalHealthConnectManager
import io.tryvital.vitalhealthconnect.model.VitalResource
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class VitalApp : Application() {
    val userRepository by lazy {
        UserRepository.create()
    }

    val vitalDeviceManager by lazy {
        VitalDeviceManager.create(this)
    }
}
