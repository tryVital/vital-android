package io.tryvital.vitalhealthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient

class HealthConnectClientProvider {
    fun getHealthConnectClient(context: Context): HealthConnectClient {
        return HealthConnectClient.getOrCreate(context)
    }
}