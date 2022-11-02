package io.tryvital.sample

import android.app.Application
import io.tryvital.client.Environment
import io.tryvital.client.Region
import io.tryvital.client.VitalClient
import io.tryvital.vitalhealthconnect.VitalHealthConnectManager

class VitalApp : Application() {
    val client = VitalClient(
        context = this,
        region = Region.US,
        environment = Environment.Dev,
        apiKey = "sk_us_Wk1rrNAYxFPN9HFOiAwja0_DBJKS3igHP8GEd9JUd6M"
    )

    val userRepository = UserRepository.create()

    val vitalHealthConnectManager = VitalHealthConnectManager.create(this, client)
}