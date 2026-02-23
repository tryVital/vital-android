package io.tryvital.vitalsamsunghealth

import android.content.Context
import androidx.health.connect.client.HealthConnectClient

class SamsungHealthClientProvider {
    fun getSamsungHealthClient(context: Context): HealthConnectClient {
        return HealthConnectClient.getOrCreate(context)
    }
}