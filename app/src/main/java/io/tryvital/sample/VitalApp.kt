package io.tryvital.sample

import android.app.Application
import io.tryvital.vitaldevices.VitalDeviceManager


class VitalApp : Application() {
    val userRepository by lazy {
        UserRepository.create()
    }

    val vitalDeviceManager by lazy {
        VitalDeviceManager.create(this)
    }
}
