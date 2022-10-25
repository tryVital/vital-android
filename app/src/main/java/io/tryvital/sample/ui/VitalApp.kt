package io.tryvital.sample.ui

import android.app.Application
import io.tryvital.client.Environment
import io.tryvital.client.Region
import io.tryvital.client.VitalClient

class VitalApp : Application() {
    val client = VitalClient(
        context = this,
        region = Region.EU,
        environment = Environment.Sandbox,
        apiKey = "sk_eu_S5LdXTS_CAtdFrkX9OYsiVq_jGHaIXtZyBPbBtPkzhA"
    )
}